// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.*
import org.aprsfoundation.core.*
import kotlin.test.*

class MobileRadioIntegrationTest {
    private fun listener(port: Int=0)=ServerSocket().apply {
        reuseAddress=true
        bind(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(),port))
        soTimeout=7000
    }
    private fun port(name: String, server: ServerSocket, protocol: String="Simply(KISS)", channel: Int=0) =
        PortPreferences.defaults(protocol).apply {
            setProperty("name",name)
            setProperty("transport","tcp")
            setProperty("endpoint","127.0.0.1:${server.localPort}")
            setProperty("channel",channel.toString())
            setProperty("minimum_transmit_ms","100")
        }
    private suspend fun await(condition: suspend ()->Boolean)=withTimeout(8000) {while(!condition()) delay(20)}
    private suspend fun accept(server: ServerSocket)=withContext(Dispatchers.IO) {
        server.accept().apply {soTimeout=8000}
    }
    private fun Socket.send(bytes: ByteArray)=getOutputStream().apply {write(bytes);flush()}
    private suspend fun Socket.readKiss(channel: Int, match: (RadioPacket)->Boolean): RadioPacket =
        withContext(Dispatchers.IO) {
            val decoder=KissDecoder(channel)
            val input=getInputStream()
            val buffer=ByteArray(1024)
            var result: RadioPacket?=null
            while(result==null) {
                val size=input.read(buffer)
                check(size>0)
                result=decoder.accept(buffer.copyOf(size)).firstOrNull(match)
            }
            result
        }

    @Test fun fixedPositionNeedsConfirmationAndEditsKeepUnchangedPortsOpen(): Unit = runBlocking {
        val home=Files.createTempDirectory("aprserj64-confirm-").also(PortableHome::prepare)
        listener().use {original->listener().use {replacement->listener().use {second->
            val settings=SettingsStore(home)
            settings.values.putAll(mapOf("callsign" to "N0CALL","autoConnect" to "false","beacon.internet" to "false"))
            val first=port("RF",original)
            val other=port("Monitor",second).apply {setProperty("transmit","false")}
            settings.ports=listOf(first,other)
            settings.save()
            AppModel(home).use {app->
                accept(second).use {monitor->accept(original).use {modem->
                    await {app.portStatuses.value.count {it.connected}==2}
                    await {app.beaconState.value.detail.contains("Confirm your fixed")}
                    app.transmitBeacon()
                    modem.soTimeout=1200
                    assertFailsWith<SocketTimeoutException> {modem.getInputStream().read()}
                    val confirmed=app.settings.entriesMap().properties().apply {setProperty("position.fixed_confirmed","true")}
                    assertTrue(app.savePreferences(confirmed,app.entities))
                    modem.soTimeout=5000
                    assertEquals("N0CALL",modem.readKiss(0) {it.source=="N0CALL"}.source)
                    val changed=first.entriesMap().properties().apply {setProperty("endpoint","127.0.0.1:${replacement.localPort}")}
                    assertTrue(app.savePreferences(confirmed,app.entities,listOf(changed,other)))
                    accept(replacement).use {
                        val seen=RadioPacket("N2CALL","APRS",emptyList(),"!2832.20N/08114.58W>Unchanged link")
                        monitor.send(Kiss.encode(seen,0))
                        await {app.stations.value.any {it.callsign==seen.source}}
                    }
                    assertEquals(-1,modem.getInputStream().read())
                }}
            }
        }}}
    }

    @Test fun portRecoveryAndManualDisconnectLeaveOtherPortsActive(): Unit = runBlocking {
        val home=Files.createTempDirectory("aprserj64-reconnect-").also(PortableHome::prepare)
        val settings=SettingsStore(home)
        settings.values.setProperty("beacon.enabled","false")
        settings.values.setProperty("autoConnect","false")
        settings.values.setProperty("callsign","N0CALL")
        val restarting=listener()
        listener().use {receiveOnly->
            val first=port("Restarting",restarting,channel=3)
            val second=port("Monitor",receiveOnly).apply {setProperty("transmit","false")}
            settings.ports=listOf(first,second)
            settings.save()
            AppModel(home).use {app->
                val old=accept(restarting)
                accept(receiveOnly).use {monitor->
                    await {app.portStatuses.value.count {it.connected}==2}
                    val fixture=RadioPacket("N2CALL","APRS",emptyList(),"!2832.20N/08114.58W>Monitor")
                    monitor.send(Kiss.encode(fixture,0))
                    await {app.stations.value.any {it.callsign=="N2CALL"}}
                    old.send(Kiss.encode(fixture.copy(source="N3CALL"),3))
                    await {app.stations.value.any {it.callsign=="N3CALL"}}
                    val restartPort=restarting.localPort
                    restarting.close()
                    old.close()
                    await {app.portStatuses.value.any {it.name=="Restarting" && "retry in 1s" in it.detail}}
                    await {app.portStatuses.value.any {it.name=="Restarting" && "retry in 2s" in it.detail}}
                    listener(restartPort).use {replacement->
                        accept(replacement).use {active->
                            await {app.portStatuses.value.count {it.connected}==2}
                            assertEquals(1,app.portStatuses.value.first {it.name=="Restarting"}.received)
                            assertTrue(app.queryModem("Restarting"))
                            val expected=AprsTnc(AprsTnc.Protocol.KISS,3).mobilinkdSetupQueries().reduce(ByteArray::plus)
                            assertContentEquals(expected,active.getInputStream().readNBytes(expected.size))
                            active.send(Kiss.command(3,6,byteArrayOf(0x0c,0x01,0x24)))
                            active.send(Kiss.command(3,6,byteArrayOf(0x50,0x01)))
                            await {app.portStatuses.value.first {it.name=="Restarting"}.modemSettings.size==2}
                            assertTrue("TX audio gain: 292" in app.portStatuses.value.first {it.name=="Restarting"}.modemSettings)
                            assertEquals(0,app.portStatuses.value.first {it.name=="Restarting"}.transmitted)
                            active.close()
                            await {app.portStatuses.value.any {it.name=="Restarting" && "retry in 1s" in it.detail}}
                        }
                        val disabled=first.entriesMap().properties().apply {setProperty("enabled","false")}
                        assertTrue(app.savePreferences(app.settings.entriesMap().properties(),app.entities,listOf(disabled,second)))
                        replacement.soTimeout=1600
                        assertFailsWith<SocketTimeoutException> {replacement.accept().close()}
                        assertEquals("false",SettingsStore(home).ports.first().getProperty("enabled"))
                        monitor.send(Kiss.encode(fixture.copy(source="N4CALL"),0))
                        await {app.stations.value.any {it.callsign=="N4CALL"}}
                        assertEquals(2,app.portStatuses.value.single().received)
                        assertEquals(0,app.portStatuses.value.single().transmitted)
                        app.send("N2CALL","Receive-only fixture")
                        monitor.soTimeout=1200
                        assertFailsWith<SocketTimeoutException> {monitor.getInputStream().read()}
                    }
                }
            }
        }
    }

    @Test fun direWolfKissAndRawAgwBench(): Unit = runBlocking {
        val kiss=System.getenv("APRSERJ64_QA_DIREWOLF_KISS")
        val agw=System.getenv("APRSERJ64_QA_DIREWOLF_AGW")
        org.junit.Assume.assumeTrue("Optional Dire Wolf bench endpoints",kiss!=null && agw!=null)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val fixture=RadioPacket("N0CALL","APRS",emptyList(),">Windows host write")
        val received=kotlinx.coroutines.channels.Channel<Pair<String,RadioPacket>>(16)
        val devices=listOf("Simply(KISS)" to kiss!!,"AGW" to agw!!).map {(protocol,endpoint)->
            val config=PortConfig(PortPreferences.defaults(protocol).apply {
                setProperty("name",protocol)
                setProperty("transport","tcp")
                setProperty("endpoint",endpoint)
            })
            DevicePort(config,scope,{received.trySend(protocol to it).isSuccess},{})
        }
        try {
            await {devices.all {it.ready}}
            devices.forEach {device->
                assertTrue(device.transmit(fixture.copy(payload="${fixture.payload} ${device.config.name}")))
                assertEquals(1,device.status.value.transmitted)
            }
            val heard=mutableSetOf<String>()
            withTimeout(15000) {
                while(heard.size<devices.size) {
                    val (protocol,packet)=received.receive()
                    if(packet.source=="N1CALL" && "Dire Wolf AFSK fixture" in packet.payload) heard.add(protocol)
                }
            }
            assertEquals(setOf("Simply(KISS)","AGW"),heard)
        } finally {
            devices.forEach {it.close()}
            received.close()
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test fun offlineGpsBeaconGpxAndTwoWayRfMessaging(): Unit = runBlocking {
        val home=Files.createTempDirectory("aprserj64-gps-").also(PortableHome::prepare)
        listener().use {radio->
            listener().use {gps->
                val settings=SettingsStore(home)
                assertEquals("true",settings.values.getProperty("logging.gpx"))
                settings.values.putAll(mapOf(
                    "callsign" to "N0CALL","autoConnect" to "false","position.source" to "port",
                    "position.port_name" to "GPS","beacon.internet" to "false",
                    "offline.enabled" to "true","imports.bundled_osm" to "true"
                ))
                settings.ports=listOf(port("RF",radio).apply {
                    setProperty("beacon_path","WIDE2-1");setProperty("message_path","WIDE1-1")
                },port("GPS",gps,"NMEA"))
                settings.save()
                AppModel(home).use {app->
                    accept(radio).use {modem->
                        accept(gps).use {receiver->
                            await {app.portStatuses.value.count {it.connected}==2}
                            val utc=ZonedDateTime.now(ZoneOffset.UTC)
                            val body="GPRMC,${utc.format(DateTimeFormatter.ofPattern("HHmmss"))},A,4221.390,N,07103.600,W,022.4,084.4,${utc.format(DateTimeFormatter.ofPattern("ddMMyy"))},003.1,W"
                            val sum=body.fold(0) {total,c->total xor c.code}.toString(16).padStart(2,'0')
                            receiver.send("\$$body*$sum\r\n".toByteArray())
                            val beacon=modem.readKiss(0) {AprsProtocol.parse(it.line(),System.currentTimeMillis())!=null}
                            assertEquals(listOf("WIDE2-1"),beacon.path)
                            assertEquals("N0CALL",beacon.source)
                            await {app.imports.localMap.value!=null}
                            assertTrue(checkNotNull(app.imports.localMap.value).lines.contains("MultiLineString"))
                            await {runCatching {app.archive.latestGpx("N0CALL",false)}.isSuccess}
                            val gpx=app.archive.latestGpx("N0CALL",true)
                            val track=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(gpx.toFile())
                            assertEquals(1,track.getElementsByTagName("trkpt").length)
                            assertEquals(AprsConnectionState.STOPPED,app.state.value)

                            delay(250) // Let the configured spacing after the beacon expire before testing ACKs.
                            modem.send(Kiss.encode(AprsTnc.message("N1CALL","N0CALL","Incoming", "7",emptyList()),0))
                            await {app.database.rows("N0CALL").any {it.body=="Incoming"}}
                            assertEquals("7",AprsMessages.parse(modem.readKiss(0) {
                                AprsMessages.parse(it.line())?.acknowledgement=="7"
                            }.line())?.acknowledgement)
                            app.send("N1CALL","Outgoing")
                            val sent=modem.readKiss(0) {AprsMessages.parse(it.line())?.text=="Outgoing"}
                            assertEquals(listOf("WIDE1-1"),sent.path)
                            val id=assertNotNull(AprsMessages.parse(sent.line())?.id)
                            modem.send(Kiss.encode(AprsTnc.acknowledge("N1CALL","N0CALL",id,emptyList()),0))
                            await {app.database.rows("N0CALL").any {it.outgoing && it.status=="ACKNOWLEDGED"}}
                        }
                    }
                }
                settings.values.setProperty("logging.gpx","false")
                settings.save()
                assertEquals("false",SettingsStore(home).values.getProperty("logging.gpx"))
            }
        }
    }
}
