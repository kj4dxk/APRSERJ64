// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.io.*
import java.net.*
import java.nio.file.Path
import java.security.SecureRandom
import java.sql.DriverManager
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import org.aprsfoundation.core.AprsEndpoint
import org.aprsfoundation.core.AprsProtocol

internal class PeerGroupConfig(val name:String,val call:String,val key:ByteArray,
    val endpoints:Map<String,InetSocketAddress>,val eligible:Boolean) {
    init {
        require(name.matches(Regex("[A-Z0-9_-]{1,20}")) && key.size==32)
        require(endpoints.size in 2..8 && call in endpoints && endpoints.keys.all(AprsProtocol::validCallsign))
        require(endpoints.values.distinct().size==endpoints.size)
    }
    companion object {
        fun parse(p:Properties,call:String):PeerGroupConfig {
            val entries=names(p.getProperty("groups.peer_addresses"))
            val endpoints=entries.associate {entry->
                val address=AprsEndpoint.parse(entry.substringAfter('='))
                entry.substringBefore('=').trim().uppercase() to InetSocketAddress(address.host,address.port)
            }
            require(entries.size==endpoints.size)
            return PeerGroupConfig(p.getProperty("groups.peer_name"),call,Base64.getDecoder().decode(p.getProperty("groups.peer_key")),endpoints,p.getProperty("groups.relay_eligible").toBoolean())
        }
    }
}

internal data class GroupEvent(val author:String,val sequence:Long,val time:Long,val kind:String,val body:String)
internal data class PeerGroupView(val primary:String?,val members:Set<String>,val history:List<GroupEvent>)

/** Only explicitly configured IP peers participate. The local journal is authoritative for this author's sequence. */
internal class PeerGroup(private val config:PeerGroupConfig,path:Path,
    private val heartbeat:Long=60_000,private val nanoTime:()->Long=System::nanoTime):AutoCloseable {
    private val socket=DatagramSocket(null)
    private val db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath())
    private val random=SecureRandom()
    private val secret=SecretKeySpec(config.key,"AES")
    private val call=config.call
    private val tips=config.endpoints.keys.associateWith {0L}.toMutableMap()
    private data class Peer(val epoch:Long,val serial:Long,val seen:Long,val eligible:Boolean,val gone:Boolean)
    private val peers=mutableMapOf<String,Peer>()
    private var serial=0L
    private val epoch:Long
    private var lastHello=0L
    private val lastReplay=mutableMapOf<String,Long>()
    private var running=true
    val view=MutableStateFlow(PeerGroupView(null,emptySet(),emptyList()))
    val issue=MutableStateFlow("")
    private val receiver:Thread

    init {
        try {
            socket.bind(config.endpoints.getValue(call))
            socket.soTimeout=heartbeat.coerceAtMost(500).toInt()
            db.createStatement().use {s->
                s.execute("CREATE TABLE IF NOT EXISTS events(author TEXT, seq INTEGER, time INTEGER, kind TEXT, body TEXT, PRIMARY KEY(author,seq))")
                s.execute("CREATE TABLE IF NOT EXISTS identity(epoch INTEGER)")
                s.executeUpdate("INSERT INTO identity SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM identity)")
                s.executeUpdate("UPDATE identity SET epoch=max(epoch+1,"+System.currentTimeMillis()+")")
                epoch=s.executeQuery("SELECT epoch FROM identity").use {it.next();it.getLong(1)}
                s.executeQuery("SELECT author,max(seq) FROM events GROUP BY author").use {r->while(r.next()) if(r.getString(1) in tips)tips[r.getString(1)]=r.getLong(2)}
            }
            refresh()
        } catch(e:Exception){socket.close();db.close();throw e}
        receiver=Thread(::receiveLoop,"APRSERJ64 peer group").apply {isDaemon=true;start()}
    }
    private fun elapsed()=nanoTime()/1_000_000
    private fun primary():String?=(peers.filterValues {!it.gone && it.eligible && elapsed()-it.seen<heartbeat*3}.keys+
        listOfNotNull(call.takeIf {config.eligible})).minOrNull()

    private fun refresh() {
        val members=mutableSetOf<String>()
        val history=mutableListOf<GroupEvent>()
        db.createStatement().use {s->
            s.executeQuery("SELECT author,kind FROM events WHERE kind IN ('join','leave') AND seq=(SELECT max(b.seq) FROM events b WHERE b.author=events.author AND b.kind IN ('join','leave'))").use {r->
                while(r.next()) if(r.getString(2)=="join")members.add(r.getString(1))
            }
            s.executeQuery("SELECT author,seq,time,kind,body FROM events WHERE kind='text' ORDER BY time DESC,author DESC,seq DESC LIMIT 200").use {r->
                while(r.next())history.add(GroupEvent(r.getString(1),r.getLong(2),r.getLong(3),r.getString(4),r.getString(5)))
            }
        }
        view.value=PeerGroupView(primary(),members,history.asReversed())
    }
    @Synchronized fun append(kind:String,body:String="") {
        check(running)
        require(kind in listOf("join","leave") || (kind=="text" && call in view.value.members && body.isNotBlank() && body.length<=300))
        val event=GroupEvent(call,tips.getValue(call)+1,System.currentTimeMillis(),kind,body)
        store(event)
        val leader=primary()
        if(leader==call)broadcastEvent(event) else if(leader!=null)sendEvent(leader,event)
    }
    private fun store(event:GroupEvent):Boolean {
        if(event.sequence!=tips.getValue(event.author)+1)return false
        db.prepareStatement("INSERT INTO events VALUES(?,?,?,?,?)").use {s->
            s.setString(1,event.author);s.setLong(2,event.sequence);s.setLong(3,event.time)
            s.setString(4,event.kind);s.setString(5,event.body);s.executeUpdate()
        }
        tips[event.author]=event.sequence
        refresh()
        return true
    }
    private fun send(to:String,type:Int,payload:DataOutputStream.()->Unit={}) {
        if(to==call)return
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {out->
            out.writeByte(1);out.writeUTF(config.name);out.writeUTF(call);out.writeLong(epoch)
            out.writeLong(++serial);out.writeByte(type);out.payload()
        }
        val nonce=ByteArray(12).also(random::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,secret,GCMParameterSpec(128,nonce))
        val packet=nonce+cipher.doFinal(bytes.toByteArray())
        socket.send(DatagramPacket(packet,packet.size,config.endpoints.getValue(to)))
    }
    private fun sendEvent(to:String,e:GroupEvent)=send(to,2) {
        writeUTF(e.author);writeLong(e.sequence);writeLong(e.time);writeUTF(e.kind);writeUTF(e.body)
    }
    private fun broadcastEvent(e:GroupEvent) {config.endpoints.keys.filter {it!=e.author}.forEach {sendEvent(it,e)}}
    private fun hello(to:String)=send(to,1) {
        writeBoolean(config.eligible);writeByte(tips.size)
        tips.forEach {(author,seq)->writeUTF(author);writeLong(seq)}
    }
    private fun replay(to:String,remote:Map<String,Long>) {
        if(elapsed()-(lastReplay[to] ?: -heartbeat)<heartbeat/2)return
        lastReplay[to]=elapsed()
        // Eight seats, one missing event per author each round: no origin can starve another.
        for((author,seq) in remote) {
            if(seq>=tips.getValue(author))continue
            db.prepareStatement("SELECT time,kind,body FROM events WHERE author=? AND seq=?").use {s->
                s.setString(1,author);s.setLong(2,seq+1)
                s.executeQuery().use {r->if(r.next())sendEvent(to,GroupEvent(author,seq+1,r.getLong(1),r.getString(2),r.getString(3)))}
            }
        }
    }
    private fun handle(packet:DatagramPacket) {
        val from=config.endpoints.entries.firstOrNull {it.value==packet.socketAddress}?.key ?: return
        if(from==call || packet.length !in 29..2047)return
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,secret,GCMParameterSpec(128,packet.data,0,12))
        val plain=cipher.doFinal(packet.data,12,packet.length-12)
        DataInputStream(plain.inputStream()).use {input->
            if(input.readUnsignedByte()!=1 || input.readUTF()!=config.name || input.readUTF()!=from)return
            val generation=input.readLong(); val number=input.readLong()
            val before=peers[from]
            if(before!=null && (generation<before.epoch || (generation==before.epoch && (number<=before.serial || before.gone))))return
            when(input.readUnsignedByte()) {
                1 -> {
                    val eligible=input.readBoolean()
                    val count=input.readUnsignedByte()
                    if(count!=tips.size)return
                    val remote=buildMap {repeat(count){put(input.readUTF(),input.readLong())}}
                    if(remote.keys!=tips.keys || remote.values.any {it<0})return
                    peers[from]=Peer(generation,number,elapsed(),eligible,false)
                    view.value=view.value.copy(primary=primary())
                    if(before==null || before.epoch!=generation)hello(from)
                    if(primary()==call || primary()==from)replay(from,remote)
                }
                2 -> {
                    if(before==null || before.epoch!=generation)return
                    val e=GroupEvent(input.readUTF(),input.readLong(),input.readLong(),input.readUTF(),input.readUTF())
                    if(e.author !in tips || e.author==call || e.kind !in listOf("join","leave","text") || e.body.length>300)return
                    if(from!=primary() && primary()!=call)return
                    peers[from]=before.copy(serial=number)
                    if(store(e) && primary()==call)broadcastEvent(e)
                }
                3 -> {
                    peers[from]=Peer(generation,number,elapsed(),false,true)
                    view.value=view.value.copy(primary=primary())
                }
            }
        }
    }
    private fun receiveLoop() {
        val buffer=ByteArray(2048)
        while(!socket.isClosed) {
            try {
                synchronized(this){
                    if(!running)return
                    if(lastHello==0L || elapsed()-lastHello>=heartbeat){
                        lastHello=elapsed();config.endpoints.keys.forEach(::hello)
                        view.value=view.value.copy(primary=primary())
                    }
                }
                val packet=DatagramPacket(buffer,buffer.size)
                socket.receive(packet)
                synchronized(this){if(running)handle(packet)}
            } catch(_:SocketTimeoutException){} catch(_:javax.crypto.AEADBadTagException){}
              catch(_:EOFException){} catch(_:UTFDataFormatException){}
              catch(e:Exception){if(!socket.isClosed)issue.value="Peer group communication: "+e.javaClass.simpleName}
        }
    }
    override fun close(){
        synchronized(this){
            if(!running)return
            running=false
            config.endpoints.keys.forEach {runCatching {send(it,3)}}
            socket.close()
        }
        receiver.join(2000)
        db.close()
    }
}
