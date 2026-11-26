// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortTimeoutException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.aprsfoundation.core.*

data class PortStatus(val name: String, val detail: String, val received: Long=0, val transmitted: Long=0, val dropped: Long=0,
    val connection: String="", val modemSettings: List<String> = emptyList(), val connected: Boolean=false,
    val canQueryModem: Boolean=false, val startupTrace: List<String> = emptyList())

/** One serial/TCP owner. No disconnected transmit queue; reconnect creates fresh decoders. */
class DevicePort(val config: PortConfig, parent: CoroutineScope,
                 private val packet: suspend (RadioPacket)->Boolean,
                 private val nmea: (GpsReading)->Unit,
                 private val transmitted: (RadioPacket)->Unit = {}, private val weather:(AprsWeather)->Unit = {},
                 private val network: NetworkRoutes = NetworkRoutes()) : AutoCloseable {
    private val scope=CoroutineScope(parent.coroutineContext+SupervisorJob(parent.coroutineContext[Job]))
    val status=MutableStateFlow(PortStatus(config.name,"Opening",connection=config.connection))
    private val writes=Mutex()
    private val lifecycle=Any()
    @Volatile private var stopped=false
    private var opened=false
    @Volatile private var codec: AprsTnc?=null
    @Volatile private var resource: AutoCloseable?=null
    @Volatile private var output: OutputStream?=null
    @Volatile var ready=false;private set
    @Volatile private var expected: Pair<String,CompletableDeferred<Unit>>?=null
    private val transmissions=ArrayDeque<Long>()
    private var lastTransmit=0L
    private var service:ServicePort?=null
    val gpsSky=MutableStateFlow(GpsSky())
    init {scope.launch(Dispatchers.IO) {
        var attempt=0
        while(isActive && !stopped) {
            opened=false
            status.update {it.copy(detail="Opening",connected=false,canQueryModem=false,modemSettings=emptyList())}
            trace("Opening "+config.connection)
            var failure=""
            try {session()} catch(timeout: TimeoutCancellationException) {failure="Initialization timed out"}
            catch(cancelled: CancellationException) {throw cancelled}
            catch(error: Exception) {failure=error.message?.take(140).orEmpty()}
            finally {
                ready=false
                release()
                writes.withLock {output=null;codec=null}
                expected?.second?.cancel();expected=null
                if(!stopped) nmea(GpsReading(detail="Port disconnected"))
            }
            if(stopped) break
            if(opened) attempt=0
            val wait=MobileRadio.retrySeconds(attempt)
            attempt=(attempt+1).coerceAtMost(6)
            val reason=failure.ifBlank {"Disconnected"}
            status.update {it.copy(detail="$reason · retry in ${wait}s",connected=false,canQueryModem=false)}
            trace(reason+" · retry in "+wait+"s")
            delay(wait*1000L)
        }
    }}
    private fun trace(message: String) {
        val entry=java.time.Instant.now().toString()+" · "+message
        status.update {it.copy(startupTrace=(it.startupTrace+entry).takeLast(60))}
    }
    private fun own(value: AutoCloseable) = synchronized(lifecycle) {
        if(stopped) {
            value.close()
            throw CancellationException("Port closed")
        }
        resource=value
    }
    private fun release() = synchronized(lifecycle) {
        runCatching {resource?.close()}
        resource=null
    }
    private fun connected(detail: String) = synchronized(lifecycle) {
        if(stopped) throw CancellationException("Port closed")
        opened=true
        ready=true
        status.update {it.copy(detail=detail,connected=true,canQueryModem=isKiss())}
    }
    suspend fun queryModem(): Boolean = withContext(Dispatchers.IO) {
        writes.withLock {
            val session=codec?.takeIf {ready && isKiss()} ?: return@withLock false
            status.update {it.copy(modemSettings=emptyList())}
            try {
                session.mobilinkdSetupQueries().forEach(::raw)
                true
            } catch(error: java.io.IOException) {
                ready=false
                release()
                false
            }
        }
    }
    fun replyTo(recipient:String,packet:RadioPacket):Boolean {
        if(!ready || !config.canTransmit)return false
        val sent=runCatching {service?.send(packet,recipient)==true}.getOrDefault(false)
        if(sent) recordWrite(packet)
        return sent
    }
    private fun recordWrite(value: RadioPacket) {
        status.update {it.copy(transmitted=it.transmitted+1)}
        runCatching {transmitted(value)}
    }
    private suspend fun session()=coroutineScope {
        if(config.protocol in PortPreferences.services) {
            val endpoint=ServicePort(config,this,{value->status.update {it.copy(received=it.received+1)};if(!packet(value))status.update {it.copy(dropped=it.dropped+1)}},::connected,network)
            service=endpoint;own(endpoint)
            try {endpoint.run()}finally{endpoint.close();service=null};return@coroutineScope
        }
        val input: InputStream
        if(config.text("transport") in PortPreferences.comTransports) {
            val port=SerialPort.getCommPort(config.text("device"))
            val parity=when(config.text("serial_parity")) {
                "odd" -> SerialPort.ODD_PARITY
                "even" -> SerialPort.EVEN_PARITY
                "mark" -> SerialPort.MARK_PARITY
                "space" -> SerialPort.SPACE_PARITY
                else -> SerialPort.NO_PARITY
            }
            val stopBits=when(config.text("serial_stop_bits")) {
                "1.5" -> SerialPort.ONE_POINT_FIVE_STOP_BITS
                "2" -> SerialPort.TWO_STOP_BITS
                else -> SerialPort.ONE_STOP_BIT
            }
            check(port.setComPortParameters(config.number("baud"),config.number("serial_data_bits"),stopBits,parity)) {
                "Unable to set serial framing"
            }
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING,1000,1000)
            val handle=AutoCloseable {port.closePort()}
            own(handle)
            check(port.openPort()) {"Unable to open ${config.text("device")}"}
            own(handle)
            input=port.inputStream
            output=port.outputStream
        } else if(config.text("transport")=="ble") {
            val bridge=WindowsBleKiss(config.text("device"))
            own(bridge)
            bridge.awaitReady()
            input=bridge.input
            output=bridge.output
        } else {
            val endpoint=AprsEndpoint.parse(config.text("endpoint"));val socket=network.connect(endpoint);own(socket)
            socket.soTimeout=1000
            input=socket.getInputStream();output=socket.getOutputStream()
        }
        codec=when {
            isKiss()->AprsTnc(AprsTnc.Protocol.KISS,config.number("channel"))
            config.protocol=="AGW"->AprsTnc(AprsTnc.Protocol.AGW,config.number("channel"))
            config.protocol=="TEXT"->AprsTnc(AprsTnc.Protocol.TNC2)
            else->null
        }?.apply {
            diagnostic={report->status.update {old->
                old.copy(modemSettings=old.modemSettings.filterNot {it.substringBefore(':')==report.substringBefore(':')}+report)
            }}
        }
        val reader=async(Dispatchers.IO) {read(input)}
        try {
            writes.withLock {
                config.list("open_commands").forEachIndexed {index, entry -> command(entry,index+1) }
                if(config.protocol=="AGW" && config.text("agw_user").isNotEmpty()) {
                    val credentials=ByteArray(510)
                    config.text("agw_user").toByteArray(Charsets.US_ASCII).copyInto(credentials,0)
                    config.text("agw_password").toByteArray(Charsets.US_ASCII).copyInto(credentials,255)
                    raw(Agw.encode(config.number("channel"),'P',credentials))
                }
                codec?.openCommand()?.let(::raw)
                if(isKiss()) listOf("kiss_txdelay","kiss_persistence","kiss_slot","kiss_tail","kiss_duplex").forEachIndexed {index,key->
                    if(config.number(key)>=0) raw(Kiss.command(config.number("channel"),index+1,byteArrayOf(config.number(key).toByte())))
                }
            }
            trace("Host initialization complete")
            connected("Connected")
            reader.await()
        } finally {ready=false;reader.cancel();release();reader.join()}
    }
    private fun isKiss()=config.protocol in PortPreferences.kissProtocols
    private fun raw(bytes: ByteArray) {checkNotNull(output).apply {write(bytes);flush()}}
    private suspend fun command(command: String, startupStep: Int? = null) {
        val parts=command.split('|',limit=2);val response=parts.getOrNull(1)
        val waiting=response?.takeIf {it.isNotEmpty()}?.let {CompletableDeferred<Unit>()}
        expected=if(waiting!=null) response to waiting else null
        try {
            // Custom commands and prompts may contain credentials. Report their position and outcome.
            if(startupStep!=null) trace("Startup command $startupStep · "+if(waiting==null) "write only" else "wait for configured response")
            raw(parts[0].toByteArray(Charsets.US_ASCII))
            if(waiting!=null) {
                val timeoutMs=config.number("command_timeout_ms").toLong()
                val matched=withTimeoutOrNull(timeoutMs) {waiting.await(); true} ?: false
                val step=startupStep?.let {"Startup command "+it} ?: "Close command"
                check(matched) {"$step timed out waiting for response ("+timeoutMs+"ms)"}
                if(startupStep!=null) trace("$step · response matched")
            } else if(startupStep!=null) trace("Startup command $startupStep · host write completed")
            delay(config.number("command_delay_ms").toLong())
        } finally {expected=null}
    }
    suspend fun transmit(value: RadioPacket): Boolean=withContext(Dispatchers.IO) {writes.withLock {
        if(!ready || !config.canTransmit) return@withLock false
        if(config.protocol in PortPreferences.services) {
            val sent=runCatching {service?.send(value)==true}.getOrDefault(false)
            if(sent) recordWrite(value)
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
                codec!=null->raw(checkNotNull(codec).encode(value))
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
            lastTransmit=System.currentTimeMillis()
            transmissions.addLast(lastTransmit)
            recordWrite(value)
            true
        } catch(cancelled: CancellationException) {throw cancelled}
        catch(_: Exception) {ready=false;release();status.update {it.copy(detail="Transmit failed; reconnecting",connected=false,canQueryModem=false)};false}
    } }
    private suspend fun read(input: InputStream) {
        val decoder=codec;val text=TncTextDecoder();val gps=NmeaPositionDecoder();val diagnostics=GpsDiagnostics()
        val line=StringBuilder();val response=StringBuilder();var overflow=false;val buffer=ByteArray(512)
        var lastWaiting: CompletableDeferred<Unit>?=null
        suspend fun received(p: RadioPacket) {status.update {it.copy(received=it.received+1)}
            if(!packet(p)) status.update {it.copy(dropped=it.dropped+1)}}
        while(currentCoroutineContext().isActive) {
            val size=try {input.read(buffer)} catch(_: SocketTimeoutException) {continue} catch(_: SerialPortTimeoutException) {continue}
            catch(error: java.io.IOException) {
                currentCoroutineContext().ensureActive()
                throw error
            }
            if(size<0) return
            val bytes=buffer.copyOf(size)
            if(config.protocol=="AGW" || isKiss() && (ready || expected==null))
                decoder?.receive(bytes)?.forEach {received(it)}
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
                        } else if(config.protocol!="NMEA") {
                            val legacy=text.accept(value)
                            val parsed=if(config.protocol=="TEXT") decoder?.receive((value+"\n").toByteArray(Charsets.ISO_8859_1))?.firstOrNull() else null
                            (parsed ?: legacy)?.let {received(it)}
                        }
                    }
                    line.clear();overflow=false
                } else if(line.length<1024) line.append(c) else overflow=true
            }
        }
    }
    suspend fun shutdown() {
        synchronized(lifecycle) {stopped=true;ready=false}
        val closing=scope.launch(Dispatchers.IO) {writes.withLock {if(output!=null) config.list("close_commands").forEach {command(it)}}}
        try {withTimeoutOrNull(2000) {closing.join()}}
        finally {close()}
    }
    override fun close() {
        synchronized(lifecycle) {stopped=true;ready=false}
        scope.cancel()
        release()
        status.update {it.copy(detail="Disconnected",connected=false,canQueryModem=false)}
    }
}
