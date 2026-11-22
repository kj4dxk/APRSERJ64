// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.aprsfoundation.core.RadioPacket

data class TrafficEntry(val sequence: Long,val time: Long,val port: String,val direction: String,val packet: String,val satellite: Boolean,val duplicate:Boolean=false,val invalid:Boolean=false)

/** Bounded monitor plus optional receive-only TNC2 feed. Never accepts transmit input. */
class PacketMonitor(private val directory: Path,private val parent: CoroutineScope): AutoCloseable {
    val entries=MutableStateFlow<List<TrafficEntry>>(emptyList())
    val feedStatus=MutableStateFlow("Local feed off")
    private var capacity=1000
    private var aliases=emptyList<String>()
    private var sequence=0L
    private val seen=linkedMapOf<String,Long>()
    private var server: ServerSocket?=null
    private var worker: Job?=null
    private var feedPort: Int?=null
    private class Peer(val socket: Socket) {val queue=Channel<String>(128);fun close(){queue.close();runCatching {socket.close()}}}
    private val peers=CopyOnWriteArrayList<Peer>()
    @Synchronized fun configure(values: Properties, demo: Boolean) {
        capacity=values.getProperty("operating.monitor_capacity").toInt()
        aliases=names(values.getProperty("operating.satellite_aliases"))
        entries.value=entries.value.takeLast(capacity)
        val port=values.getProperty("operating.feed_port").toInt().takeIf {!demo && values.getProperty("operating.feed_enabled").toBoolean()}
        if(port==feedPort) return
        closeFeed();if(port==null) return
        try {
            val listener=ServerSocket(port,8,InetAddress.getByName("127.0.0.1"));server=listener;feedPort=port
            feedStatus.value="Receive-only feed: 127.0.0.1:$port"
            worker=parent.launch(Dispatchers.IO) {
                try {while(isActive) {
                    val socket=listener.accept()
                    if(peers.size>=8) {socket.close();continue}
                    socket.sendBufferSize=8192
                    val peer=Peer(socket);peers.add(peer)
                    launch {try {
                        val writer=socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                        writer.write("# APRSERJ64 receive-only TNC2 feed\r\n");writer.flush()
                        for(line in peer.queue) {writer.write(line);writer.write("\r\n");writer.flush()}
                    } catch(_: Exception) {} finally {peer.close();peers.remove(peer)}}
                }} catch(_: Exception) { /* Listener closed during reconfiguration. */ }
            }
        } catch(_: Exception) {feedStatus.value="Local feed unavailable: port $port is in use or cannot be opened";feedPort=null}
    }
    @Synchronized fun record(port: String,direction: String,line: String) {
        val clean=line.trimEnd('\r','\n').take(510);val packet=RadioPacket.parse(clean)
        val satellite=packet?.let {(listOf(it.source)+it.path).any {address->aliases.any {address.trimEnd('*').startsWith(it)}}} ?: false
        val now=System.currentTimeMillis()
        seen.entries.removeAll {now-it.value>=30000}
        val key=packet?.let {"$direction:${it.key}"}
        val duplicate=key!=null && key in seen
        if(key!=null) {seen.remove(key);seen[key]=now;while(seen.size>capacity)seen.remove(seen.keys.first())}
        val invalid=packet==null || packet.payload.firstOrNull() in listOf('!','=','/','@','`','\'') && org.aprsfoundation.core.AprsProtocol.parse(clean,now)==null
        entries.value=(entries.value+TrafficEntry(++sequence,now,port,direction,clean,satellite,duplicate,invalid)).takeLast(capacity)
        if(direction=="RX") peers.forEach {if(!it.queue.trySend(clean).isSuccess) {it.close();peers.remove(it)}}
    }
    suspend fun export(snapshot: List<TrafficEntry>): Path=withContext(Dispatchers.IO) {
        val folder=directory.resolve("exports");Files.createDirectories(folder)
        val path=Files.createTempFile(folder,"packets-",".txt")
        Files.write(path,snapshot.map {it.packet});path
    }
    private fun closeFeed() {
        runCatching {server?.close()};server=null;worker?.cancel();worker=null
        peers.forEach {it.close()};peers.clear();feedPort=null;feedStatus.value="Local feed off"
    }
    override fun close() {closeFeed()}
}
