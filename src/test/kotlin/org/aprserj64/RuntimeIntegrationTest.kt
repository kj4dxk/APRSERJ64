// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.aprsfoundation.core.*
import kotlin.test.*

class RuntimeIntegrationTest {
    private fun listener() = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).apply { soTimeout=5000 }
    private suspend fun ready(port: DevicePort) = withTimeout(5000) { while(!port.ready) delay(10) }
    private fun config(protocol: String, server: ServerSocket) = PortConfig(PortPreferences.defaults(protocol).apply {
        setProperty("transport","tcp"); setProperty("endpoint","127.0.0.1:${server.localPort}")
        setProperty("minimum_transmit_ms","100")
    })
    @Test fun pk88EntersKissWithoutWaitingForAnotherPrompt(): Unit = runBlocking {
        val received=Channel<RadioPacket>(1)
        listener().use { server ->
            DevicePort(config(PortPreferences.PK88,server),this,{received.send(it);true},{}).use { port ->
                withContext(Dispatchers.IO) {
                    server.accept().use { socket ->
                        socket.soTimeout=5000
                        val input=socket.getInputStream()
                        val output=socket.getOutputStream()
                        assertContentEquals(byteArrayOf(3,3,3,13),input.readNBytes(4))
                        output.write("cmd:".toByteArray())
                        assertEquals("KISS ON\r",input.readNBytes(8).decodeToString())

                        val packet=RadioPacket.parse("N1CALL>APRS:>PK-88 test")!!
                        output.write(Kiss.encode(packet,0))
                        ready(port)
                        assertTrue(port.status.value.startupTrace.any {"Startup command 2 · response matched" in it})
                        assertTrue(port.status.value.startupTrace.any {"Startup command 3 · host write completed" in it})
                        assertEquals(packet,withTimeout(5000) {received.receive()})
                        assertTrue(port.transmit(packet))
                        val encoded=Kiss.encode(packet,0)
                        assertContentEquals(encoded,input.readNBytes(encoded.size))
                    }
                }
            }
        }
    }
    @Test fun pk88StartupTimeoutIdentifiesTheFailedStep(): Unit = runBlocking {
        listener().use { server ->
            val config=config(PortPreferences.PK88,server).apply {values.setProperty("command_timeout_ms","150")}
            DevicePort(config,this,{true},{}).use { port ->
                withContext(Dispatchers.IO) {server.accept()}.use {
                    withTimeout(5000) {
                        while(port.status.value.startupTrace.none {"Startup command 2 timed out" in it}) delay(10)
                    }
                    assertFalse(port.ready)
                    assertTrue(port.status.value.startupTrace.any {"retry in 1s" in it})
                }
            }
        }
    }
    @Test fun kissAgwAndTnc2UseRealTcpStreams(): Unit = runBlocking {
        for(protocol in listOf("Simply(KISS)","AGW","TEXT")) {
            val parent=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val received=Channel<RadioPacket>(8)
            listener().use { server ->
                val port=DevicePort(config(protocol,server),parent,{received.send(it);true},{})
                try {
                    withContext(Dispatchers.IO) { server.accept() }.use { socket ->
                        socket.soTimeout=5000
                        ready(port)
                        val packet=RadioPacket.parse("N1CALL-7>APRS,WIDE1-1:!2832.20N/08114.58W>TCP test")!!
                        val encoded=when(protocol) {
                            "AGW"->Agw.packet(0,packet)
                            "TEXT"->(packet.line()+"\r\n").toByteArray()
                            else->Kiss.encode(packet,0)
                        }
                        // Split the wire frame to exercise incremental decoding in the live reader.
                        socket.getOutputStream().apply { write(encoded,0,7);flush();write(encoded,7,encoded.size-7);flush() }
                        assertEquals(packet,withTimeout(5000) {received.receive()})
                        assertTrue(port.transmit(packet))
                        val kiss=KissDecoder();val agw=AgwDecoder();val text=AprsTnc(AprsTnc.Protocol.TNC2);var decoded: RadioPacket?=null
                        withContext(Dispatchers.IO) {
                            val bytes=ByteArray(512)
                            while(decoded==null) {
                                val count=socket.getInputStream().read(bytes);assertTrue(count>0)
                                val part=bytes.copyOf(count)
                                decoded=when(protocol) {
                                    "AGW"->agw.accept(part).firstOrNull {it.kind=='K'}?.let {Ax25Ui.decode(it.data.drop(1).toByteArray())}
                                    "TEXT"->text.receive(part).firstOrNull()
                                    else->kiss.accept(part).firstOrNull()
                                }
                            }
                        }
                        assertEquals(packet,decoded)
                        assertFalse(port.transmit(packet),"Immediate repeat must hit the rate limit")
                        assertEquals(1,port.status.value.received)
                        assertEquals(1,port.status.value.transmitted)
                    }
                } finally {port.shutdown();parent.coroutineContext[Job]?.cancelAndJoin()}
                assertFalse(port.ready)
            }
        }
    }
    @Test fun nmeaPortSuppliesFixAndCannotTransmit(): Unit = runBlocking {
        val parent=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val readings=Channel<GpsReading>(Channel.UNLIMITED)
        listener().use {server ->
            val port=DevicePort(config("NMEA",server),parent,{error("NMEA must not emit RF packets")},{readings.trySend(it)})
            try {
                withContext(Dispatchers.IO) {server.accept()}.use {socket ->
                    ready(port)
                    val now=java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                    val body="GPRMC,${now.format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"))},A,4807.038,N,01131.000,E,022.4,084.4,${now.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyy"))},003.1,W"
                    val checksum=body.fold(0) {sum,c->sum xor c.code}.toString(16).padStart(2,'0')
                    socket.getOutputStream().apply {write("\$$body*$checksum\r\n".toByteArray());flush()}
                    val reading=withTimeout(5000) {var r=readings.receive();while(!r.valid) r=readings.receive();r}
                    assertNotNull(reading.position)
                    assertFalse(port.transmit(RadioPacket.parse("N0CALL>APRS:>No RF from GPS")!!))
                }
            } finally {port.shutdown();parent.coroutineContext[Job]?.cancelAndJoin()}
        }
    }
    @Test fun demoNeverOpensConfiguredPortsAndChatSurvivesRestart(): Unit = runBlocking {
        val home=Files.createTempDirectory("APRSERJ64 combined test ");PortableHome.prepare(home)
        listener().use {server ->
            val store=SettingsStore(home)
            store.values.setProperty("callsign","N0CALL-12")
            store.ports=listOf(config("Simply(KISS)",server).values);store.save()
            AppModel(home,demoMode=true).use {model ->
                model.localDemo()
                withTimeout(10000) {while(model.database.rows("N0CALL-12").size<2) delay(20)}
                withTimeout(10000) {while(model.stations.value.size!=3) delay(20)}
                model.send("N1CALL-7","Combined runtime test")
                withTimeout(10000) {while(model.database.rows("N0CALL-12").none {it.outgoing && it.status=="ACKNOWLEDGED"}) delay(20)}
                assertTrue(model.portStatuses.value.isEmpty())
                server.soTimeout=200
                assertFailsWith<SocketTimeoutException> {server.accept().close()}
            }
            ChatDatabase(home.resolve("data/chats.sqlite")).use {db ->
                assertEquals("ACKNOWLEDGED",db.rows("N0CALL-12").single {it.outgoing}.status)
                assertEquals(1,db.rows("N0CALL-12").count {it.body=="Hello! This chat stays on this PC."})
            }
            assertTrue(SettingsStore(home).ports.single().getProperty("enabled").toBoolean())
        }
    }
}
