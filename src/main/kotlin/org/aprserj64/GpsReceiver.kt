// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortTimeoutException
import java.io.InputStream
import java.net.Socket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.aprsfoundation.core.*

data class GpsReading(val position: BeaconPosition? = null, val valid: Boolean = false, val detail: String = "Waiting for GPS")

class GpsReceiver(private val config: BeaconConfiguration, scope: CoroutineScope) : AutoCloseable {
    val reading=MutableStateFlow(GpsReading())
    val sky=MutableStateFlow(GpsSky())
    private val diagnostics=GpsDiagnostics()
    @Volatile private var resource: AutoCloseable? = null
    private val job=scope.launch(Dispatchers.IO) {
        while(isActive) {
            try {
                val decoder=NmeaPositionDecoder()
                if(config.text("position.source")=="gps_serial") {
                    val name=config.text("position.serial_port")
                    val port=if(name.isBlank()) SerialPort.getCommPorts().singleOrNull()
                        ?: error("Select a GPS serial port") else SerialPort.getCommPort(name)
                    resource=AutoCloseable {port.closePort()}
                    port.setComPortParameters(config.integer("position.serial_baud"),8,SerialPort.ONE_STOP_BIT,SerialPort.NO_PARITY)
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING,1000,0)
                    check(port.openPort())
                    port.inputStream.use { readLines(it,decoder) }
                } else {
                    val endpoint=config.gpsEndpoint()
                    val socket=Socket(); resource=socket
                    socket.connect(InetSocketAddress(endpoint.host,endpoint.port),5000); socket.soTimeout=1000
                    if(config.text("position.source")=="gpsd") socket.getOutputStream().write("?WATCH={\"enable\":true,\"json\":false,\"nmea\":true};\n".toByteArray(Charsets.US_ASCII))
                    socket.getInputStream().use { readLines(it,decoder) }
                }
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {reading.value=reading.value.copy(valid=false,detail="GPS reconnecting")}
            finally {runCatching {resource?.close()}; resource=null}
            delay(3000)
        }
    }
    private suspend fun readLines(input: InputStream, decoder: NmeaPositionDecoder) {
        val buffer=ByteArray(256); val line=StringBuilder(); var overflow=false
        while(currentCoroutineContext().isActive) {
            val count=try {input.read(buffer)} catch(_: SocketTimeoutException) {continue} catch(_: SerialPortTimeoutException) {continue}
            if(count<0) break
            for(index in 0 until count) {
                val c=(buffer[index].toInt() and 255).toChar()
                if(c=='\n') {
                    if(!overflow) {
                        val original=line.toString().trimEnd('\r');val sentence=if('>' !in original && '$' in original) "$"+original.substringAfter('$') else original
                        diagnostics.accept(sentence,System.currentTimeMillis())?.let {sky.value=it}
                        val position=decoder.accept(sentence,System.currentTimeMillis())
                        if(position!=null) reading.value=GpsReading(position,true,"GPS fix")
                        else if(!decoder.valid) reading.value=reading.value.copy(valid=false,detail="No GPS fix")
                    }
                    line.clear(); overflow=false
                } else if(line.length<160) line.append(c) else overflow=true
            }
        }
        reading.value=reading.value.copy(valid=false,detail="GPS disconnected")
    }
    override fun close() {job.cancel(); runCatching {resource?.close()}}
}
