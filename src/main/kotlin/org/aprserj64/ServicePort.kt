// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.net.*
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.aprsfoundation.core.*

/** Non-RF endpoints. Their input never populates RF heard tables or digipeats. */
internal class ServicePort(private val config:PortConfig,private val scope:CoroutineScope,
 private val receive:suspend (RadioPacket)->Unit,private val connected:(String)->Unit,private val network:NetworkRoutes=NetworkRoutes()):AutoCloseable {
 private var listener:AutoCloseable?=null
 private class Peer(val output:OutputStream) {val queue=Channel<ByteArray>(128);@Volatile var filter:PacketFilter?=null;@Volatile var call="";val gated=ConcurrentHashMap<String,Long>();val associated=ConcurrentHashMap<String,Long>()}
 private val clients=ConcurrentHashMap<Socket,Peer>()
 private var udp:DatagramSocket?=null
 private val positions=ConcurrentHashMap<String,AprsFix>()
 private val igates=ConcurrentHashMap.newKeySet<String>()
 private val seen=linkedMapOf<String,Long>()
 private val udpSources=ConcurrentHashMap<String,SocketAddress>()
 private val peers=ConcurrentHashMap<SocketAddress,Long>()
 @Volatile private var cwop:OutputStream?=null
 suspend fun run()=withContext(Dispatchers.IO) {
  when(config.protocol) {
   "Dummy"->{connected("Dummy ready");awaitCancellation()}
   "UDP-Listen","Syslog-UDP"->{
    val address=AprsEndpoint.parse(config.text("endpoint"));val socket=DatagramSocket(InetSocketAddress(address.host,address.port));listener=socket;udp=socket;socket.soTimeout=1000;connected("Listening")
    while(currentCoroutineContext().isActive){val data=DatagramPacket(ByteArray(4096),4096);try{socket.receive(data)}catch(_:SocketTimeoutException){continue}
     val allowed=config.list("service_peers");if(!data.address.isLoopbackAddress && data.address.hostAddress !in allowed)continue
     if(peers.size<32 || peers.containsKey(data.socketAddress))peers[data.socketAddress]=System.currentTimeMillis()
     String(data.data,0,data.length,Charsets.US_ASCII).lineSequence().take(16).forEach {line->
      val packet=RadioPacket.parse(line.trim()) ?: Regex("([A-Z0-9-]{3,9}>[^\\r\\n]+)").find(line)?.value?.let {RadioPacket.parse(it)}
      if(packet!=null){if(udpSources.size<4096)udpSources[packet.source]=data.socketAddress;accept(packet)}
     }
    }
   }
   "CWOP"->{
    val endpoint=AprsEndpoint.parse(config.text("endpoint"));val socket=network.connect(endpoint);listener=socket;ensureActive();socket.soTimeout=1000
    val out=socket.getOutputStream();out.write("user ${config.text("service_call")} pass -1 vers APRSERJ64 ${AppVersion.value}\r\n".toByteArray(Charsets.US_ASCII));out.flush();cwop=out;connected("CWOP connected")
    val buffer=ByteArray(512);while(currentCoroutineContext().isActive){try{if(socket.getInputStream().read(buffer)<0)break}catch(_:SocketTimeoutException){}}
   }
   else->{
    val endpoint=AprsEndpoint.parse(config.text("endpoint"));val socket=ServerSocket();listener=socket;socket.bind(InetSocketAddress(endpoint.host,endpoint.port));socket.soTimeout=1000;connected("Listening")
    coroutineScope {while(isActive){val client=try{socket.accept()}catch(_:SocketTimeoutException){continue}
     if(clients.size>=config.number("service_max_clients")){client.close();continue}
     client.soTimeout=1000;val peer=Peer(client.getOutputStream());clients[client]=peer
     launch {
      val writer=launch {try{for(bytes in peer.queue){val deadline=launch {delay(5000);client.close()};try{synchronized(peer.output){peer.output.write(bytes);peer.output.flush()}}finally{deadline.cancel()}}}catch(_:Exception){client.close()}}
      try{serve(client)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){}finally{peer.queue.close();clients.remove(client);client.close();writer.cancel()}
     }
    }}
   }
  }
 }
 private fun observe(packet:RadioPacket) {
  val now=System.currentTimeMillis();AprsProtocol.parse(packet.line(),now)?.let {positions[it.callsign]=AprsSpatial.fix(it)}
  if(positions.size>5000){positions.entries.removeIf {now-it.value.timeMillis>1800000};while(positions.size>5000)positions.keys.firstOrNull()?.let {positions.remove(it)}}
  packet.path.indexOfFirst {it in listOf("qAR","qAr","qAo")}.takeIf {it>=0}?.let {index->packet.path.getOrNull(index+1)?.let {if(igates.size<2000)igates.add(it)}}
 }
 private suspend fun accept(packet:RadioPacket) {
  observe(packet)
  if(!config.flag("service_input"))return
  val now=System.currentTimeMillis();val duplicate=synchronized(seen){seen.entries.removeAll {now-it.value>30000};val existed=packet.key in seen;if(seen.size>=4096)seen.remove(seen.keys.first());seen[packet.key]=now;existed}
  if(!duplicate)receive(packet)
 }
 private suspend fun serve(socket:Socket) {
  val peer=clients[socket]?:return;fun write(s:String){if(!peer.queue.trySend((s+"\r\n").toByteArray(Charsets.US_ASCII)).isSuccess)socket.close()}
  write("# APRSERJ64 local server")
  var identity="";var verified=false;val line=StringBuilder();var overflow=false
  val input=socket.getInputStream();var heartbeat=System.currentTimeMillis()
  while(currentCoroutineContext().isActive){
   if(System.currentTimeMillis()-heartbeat>=config.number("service_heartbeat_seconds")*1000L){write("# APRSERJ64 heartbeat");heartbeat=System.currentTimeMillis()}
   val byte=try{input.read()}catch(_:SocketTimeoutException){continue};if(byte<0)return
   if(byte==10){val value=line.toString().trimEnd('\r');line.clear();if(overflow){overflow=false;continue}
    if(value.startsWith("user ",true)){
     val parts=value.split(Regex("\\s+"));val who=parts.getOrNull(1)?.uppercase().orEmpty();val index=parts.indexOf("pass");val code=parts.getOrNull(index+1)?.toIntOrNull()
     identity=who;peer.call=who;verified=AprsProtocol.validCallsign(who) && index>0 && code==passcode(who)
     val filter=value.substringAfter(" filter ","");peer.filter=filter.takeIf {it.isNotBlank()}?.let {PacketFilter(it)}
     write("# logresp $who ${if(verified)"verified" else "unverified"}, APRSERJ64")
    }else if(value.startsWith("#filter ")){peer.filter=PacketFilter(value.removePrefix("#filter "))}
    else if(verified){RadioPacket.parse(value)?.let {packet->
     if(packet.source!=identity && (packet.payload.startsWith('}') || packet.path.any {it.trimEnd('*') in listOf("TCPIP","TCPXX") || it.startsWith('q')}))return@let
     peer.gated.entries.removeIf {System.currentTimeMillis()-it.value>1800000}
     if(peer.gated.size<4096)peer.gated[packet.source]=System.currentTimeMillis()
     accept(packet)
    }}
   }else if(line.length<512)line.append(byte.toChar()) else overflow=true
  }
 }
 fun send(packet:RadioPacket,recipient:String?=null):Boolean {
  observe(packet)
  if(config.protocol=="Dummy")return true
  if(config.protocol=="CWOP") {
   if(AprsObservations.weather(packet.line(),System.currentTimeMillis())==null)return false
   val output=cwop?:return false;val line=packet.copy(source=config.text("service_call"),path=listOf("TCPIP*")).line()
   synchronized(output){output.write((line+"\r\n").toByteArray(Charsets.US_ASCII));output.flush()};return true
  }
  val line=(if(config.protocol=="Syslog-UDP")"<134>APRSERJ64: " else "")+packet.line()+"\r\n"
  val bytes=line.toByteArray(Charsets.US_ASCII);var sent=false
  udp?.let {socket->peers.entries.removeIf {System.currentTimeMillis()-it.value>600000};peers.keys.filter {recipient==null || udpSources[recipient]==it}.forEach {peer->runCatching {socket.send(DatagramPacket(bytes,bytes.size,peer));sent=true}}}
  clients.forEach {(socket,peer)->
   val now=System.currentTimeMillis()
   peer.gated.entries.removeIf {now-it.value>1800000};peer.associated.entries.removeIf {now-it.value>1800000}
   val message=AprsMessages.parse(packet.line())
   val addressed=message!=null && (message.recipient==peer.call || peer.gated.containsKey(message.recipient))
   if(addressed && peer.associated.size<4096)peer.associated[packet.source]=now
   val associated=peer.associated.containsKey(packet.source) && AprsProtocol.parse(packet.line(),now)!=null
   val selected=recipient==null || recipient==peer.call || peer.gated.containsKey(recipient)
   val subscribed=peer.filter?.matches(packet,positions[peer.call],positions,igates)==true || config.protocol=="Local-Server" && peer.filter==null
   if(selected && (recipient!=null || addressed || associated || subscribed)) {
   if(peer.queue.trySend(bytes).isSuccess)sent=true else {clients.remove(socket);peer.queue.close();runCatching{socket.close()}}
  }}
  return sent
 }
 override fun close(){runCatching{listener?.close()};clients.keys.forEach {runCatching{it.close()}};clients.clear();udp=null;cwop=null}
 companion object {
  fun passcode(call:String):Int {var hash=0x73e2;val base=call.substringBefore('-');base.forEachIndexed {i,c->hash=hash xor (c.code shl if(i%2==0)8 else 0)};return hash and 0x7fff}
 }
}
