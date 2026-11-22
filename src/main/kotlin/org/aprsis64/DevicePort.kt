// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortTimeoutException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.osmand.shared.aprs.*

data class PortStatus(val name: String, val detail: String, val received: Long=0, val transmitted: Long=0, val dropped: Long=0)

/** One serial/TCP owner. No disconnected transmit queue; reconnect creates fresh decoders. */
class DevicePort(val config: PortConfig, parent: CoroutineScope,
                 private val packet: suspend (RadioPacket)->Boolean,
                 private val nmea: (GpsReading)->Unit,
                 private val transmitted: (RadioPacket)->Unit = {}, private val weather:(AprsWeather)->Unit = {}) : AutoCloseable {
    private val scope=CoroutineScope(parent.coroutineContext+SupervisorJob(parent.coroutineContext[Job]))
    val status=MutableStateFlow(PortStatus(config.name,"Opening"))
    private val writes=Mutex()
    @Volatile private var resource: AutoCloseable?=null
    @Volatile private var output: OutputStream?=null
    @Volatile var ready=false;private set
    @Volatile private var expected: Pair<String,CompletableDeferred<Unit>>?=null
    private val transmissions=ArrayDeque<Long>()
    private var lastTransmit=0L
    private var service:ServicePort?=null
    val gpsSky=MutableStateFlow(GpsSky())
    init {scope.launch(Dispatchers.IO) {
        while(isActive) {
            try {session()} catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {status.value=status.value.copy(detail="Disconnected; retrying")}
            finally {ready=false;output=null;expected?.second?.cancel();expected=null;runCatching {resource?.close()};resource=null;nmea(GpsReading(detail="Port disconnected"))}
            delay(config.number("reconnect_seconds")*1000L)
        }
    }}
    fun replyTo(recipient:String,packet:RadioPacket):Boolean {
        if(!ready || !config.canTransmit)return false
        val sent=runCatching {service?.send(packet,recipient)==true}.getOrDefault(false)
        if(sent){transmitted(packet);status.value=status.value.copy(transmitted=status.value.transmitted+1)}
        return sent
    }
    private suspend fun session()=coroutineScope {
        if(config.protocol in PortPreferences.services) {
            val endpoint=ServicePort(config,this,{value->status.value=status.value.copy(received=status.value.received+1);if(!packet(value))status.value=status.value.copy(dropped=status.value.dropped+1)},{detail->ready=true;status.value=status.value.copy(detail=detail)})
            service=endpoint;resource=endpoint
            try {endpoint.run()}finally{endpoint.close();service=null};return@coroutineScope
        }
        val input: InputStream
        if(config.text("transport")=="serial") {
            val port=SerialPort.getCommPort(config.text("device"));resource=AutoCloseable {port.closePort()}
            port.setComPortParameters(config.number("baud"),8,SerialPort.ONE_STOP_BIT,SerialPort.NO_PARITY)
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING,1000,1000)
            check(port.openPort());input=port.inputStream;output=port.outputStream
        } else {
            val endpoint=AprsEndpoint.parse(config.text("endpoint"));val socket=Socket();resource=socket
            socket.connect(InetSocketAddress(endpoint.host,endpoint.port),5000);socket.soTimeout=1000
            input=socket.getInputStream();output=socket.getOutputStream()
        }
        val reader=async(Dispatchers.IO) {read(input)}
        try {
            writes.withLock {
                config.list("open_commands").forEach {command(it)}
                if(config.protocol=="AGW" && config.text("agw_user").isNotEmpty()) {
                    val credentials=ByteArray(510)
                    config.text("agw_user").toByteArray(Charsets.US_ASCII).copyInto(credentials,0)
                    config.text("agw_password").toByteArray(Charsets.US_ASCII).copyInto(credentials,255)
                    raw(Agw.encode(config.number("channel"),'P',credentials))
                }
                if(config.protocol=="AGW") raw(Agw.encode(config.number("channel"),'k'))
                if(isKiss()) listOf("kiss_txdelay","kiss_persistence","kiss_slot","kiss_tail","kiss_duplex").forEachIndexed {index,key->
                    if(config.number(key)>=0) raw(Kiss.command(config.number("channel"),index+1,byteArrayOf(config.number(key).toByte())))
                }
            }
            ready=true;status.value=status.value.copy(detail="Connected")
            reader.await()
        } finally {ready=false;runCatching {resource?.close()};reader.cancelAndJoin()}
    }
    private fun isKiss()=config.protocol in listOf("Simply(KISS)","KISS","KWD700(KISS)")
    private fun raw(bytes: ByteArray) {checkNotNull(output).apply {write(bytes);flush()}}
    private suspend fun command(command: String) {
        val parts=command.split('|',limit=2);val response=parts.getOrNull(1)
        val waiting=response?.takeIf {it.isNotEmpty()}?.let {CompletableDeferred<Unit>()}
        expected=if(waiting!=null) response to waiting else null
        try {
            raw(parts[0].toByteArray(Charsets.US_ASCII))
            if(waiting!=null) withTimeout(config.number("command_timeout_ms").toLong()) {waiting.await()}
            delay(config.number("command_delay_ms").toLong())
        } finally {expected=null}
    }
    suspend fun transmit(value: RadioPacket): Boolean=withContext(Dispatchers.IO) {writes.withLock {
        if(!ready || !config.canTransmit) return@withLock false
        if(config.protocol in PortPreferences.services) {
            val sent=runCatching {service?.send(value)==true}.getOrDefault(false)
            if(sent){transmitted(value);status.value=status.value.copy(transmitted=status.value.transmitted+1)}
            return@withLock sent
        }
        if(!RadioPacket.address(value.source) || !RadioPacket.address(value.destination) || value.path.size>8 ||
            value.path.any {!RadioPacket.address(it.trimEnd('*')) || it.trimEnd('*') in listOf("TCPIP","TCPXX")} ||
            value.payload.length>256) return@withLock false
        if(config.protocol in listOf("CONVerse","KWD710(APRS)","KWD710(Pkt)") &&
            (value.usedHops>0 || value.payload.any {it.code !in 32..126})) return@withLock false
        val now=System.currentTimeMillis()
        while(transmissions.isNotEmpty() && now-transmissions.first()>=60000) transmissions.removeFirst()
        if(transmissions.size>=config.number("transmit_per_minute") || now-lastTransmit<config.number("minimum_transmit_ms")) return@withLock false
        try {
            require(value.path.none {it.startsWith('q') || it.trimEnd('*') in listOf("TCPIP","TCPXX")} && value.payload.length<=256)
            when {
                isKiss()->raw(Kiss.encode(value,config.number("channel")))
                config.protocol=="AGW"->raw(Agw.packet(config.number("channel"),value))
                config.protocol=="TEXT"->raw((value.line()+"\r\n").toByteArray(Charsets.US_ASCII))
                else->{
                    // Command-mode TNCs cannot preserve repeated address bits. Do not fake a digi.
                    require(value.usedHops==0 && value.payload.all {it.code in 32..126})
                    val prompt=config.text("command_prompt")
                    if(config.protocol=="KWD710(APRS)") command("TC 0\r")
                    command("\u0003|$prompt")
                    command("MYCALL ${value.source}\r|$prompt")
                    command("UNPROTO ${value.destination}${if(value.path.isEmpty()) "" else " VIA "+value.path.joinToString(",")}\r|$prompt")
                    command("CONV\r");command(value.payload+"\r");command("\u0003|$prompt")
                    if(config.protocol=="KWD710(APRS)") {command("TC 1\r");command("TC 1\r")}
                }
            }
            transmitted(value);lastTransmit=now;transmissions.addLast(now);status.value=status.value.copy(transmitted=status.value.transmitted+1);true
        } catch(cancelled: CancellationException) {throw cancelled}
        catch(_: Exception) {ready=false;runCatching {resource?.close()};status.value=status.value.copy(detail="Transmit failed; reconnecting");false}
    } }
    private suspend fun read(input: InputStream) {
        val kiss=KissDecoder(config.number("channel").coerceAtMost(15));val agw=AgwDecoder();val text=TncTextDecoder();val gps=NmeaPositionDecoder();val diagnostics=GpsDiagnostics()
        val line=StringBuilder();val response=StringBuilder();var overflow=false;val buffer=ByteArray(512)
        var lastWaiting: CompletableDeferred<Unit>?=null
        suspend fun received(p: RadioPacket) {status.value=status.value.copy(received=status.value.received+1)
            if(!packet(p)) status.value=status.value.copy(dropped=status.value.dropped+1)}
        while(currentCoroutineContext().isActive) {
            val size=try {input.read(buffer)} catch(_: SocketTimeoutException) {continue} catch(_: SerialPortTimeoutException) {continue}
            if(size<0) return
            val bytes=buffer.copyOf(size)
            if(isKiss() && ready) kiss.accept(bytes).forEach {received(it)}
            else if(config.protocol=="AGW") agw.accept(bytes).filter {it.kind=='K' && it.port==config.number("channel")}.forEach {
                if(it.data.isNotEmpty()) Ax25Ui.decode(it.data.drop(1).toByteArray())?.let {p->received(p)}
            }
            if(!isKiss() && config.protocol!="AGW" || !ready || expected!=null) bytes.forEach {byte->
                val c=(byte.toInt() and 255).toChar();response.append(c)
                if(lastWaiting!==expected?.second) {response.clear();response.append(c);lastWaiting=expected?.second}
                if(response.length>2048) response.delete(0,response.length-1024)
                expected?.let {(match,waiting)->if(response.contains(match)) {response.clear();waiting.complete(Unit)}}
                if(c=='\r' || c=='\n') {
                    if(line.isNotEmpty() && !overflow && !isKiss() && config.protocol!="AGW") {
                        val original=line.toString()
                        val value=if('>' !in original && '$' in original) "$"+original.substringAfter('$') else original
                        if(config.protocol=="Weather") {
                            (UltimeterWeather.decode(config.text("service_call").ifBlank {"LOCAL"},value,System.currentTimeMillis()) ?: AprsObservations.weather(value,System.currentTimeMillis()))?.let(weather)
                        } else if(value.startsWith('$') && (config.protocol=="NMEA" || config.flag("provides_nmea"))) {
                            diagnostics.accept(value,System.currentTimeMillis())?.let {gpsSky.value=it}
                            val fix=gps.accept(value,System.currentTimeMillis())
                            if(fix!=null) nmea(GpsReading(fix,true,"GPS fix")) else if(!gps.valid) nmea(GpsReading(detail="No GPS fix"))
                        } else if(config.protocol!="NMEA") text.accept(value)?.let {received(it)}
                    }
                    line.clear();overflow=false
                } else if(line.length<1024) line.append(c) else overflow=true
            }
        }
    }
    suspend fun shutdown() {
        val closing=scope.launch(Dispatchers.IO) {writes.withLock {if(output!=null) config.list("close_commands").forEach {command(it)}}}
        try {withTimeoutOrNull(2000) {closing.join()}}
        finally {close()}
    }
    override fun close() {ready=false;runCatching {resource?.close()};scope.cancel()}
}
