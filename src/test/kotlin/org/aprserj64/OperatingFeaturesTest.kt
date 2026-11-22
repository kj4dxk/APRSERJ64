// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Properties
import kotlinx.coroutines.*
import kotlin.test.*

class OperatingFeaturesTest {
    @Test fun appShutdownClosesListeningFeed() {
        val home=Files.createTempDirectory("APRS feed shutdown");PortableHome.prepare(home)
        val store=SettingsStore(home)
        val port=ServerSocket(0).use {it.localPort}
        store.values.setProperty("operating.feed_enabled","true")
        store.values.setProperty("operating.feed_port",port.toString());store.save()
        val model=AppModel(home)
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor {r->Thread(r).apply {isDaemon=true}}
        try {executor.submit {model.close()}.get(5,java.util.concurrent.TimeUnit.SECONDS)} finally {executor.shutdownNow()}
        ServerSocket(port,1,java.net.InetAddress.getByName("127.0.0.1")).close()
    }
    @Test fun savedCustomFilterReachesActualLogin(): Unit = runBlocking {
        val home=Files.createTempDirectory("APRS filter wire test");PortableHome.prepare(home)
        val store=SettingsStore(home)
        store.values.setProperty("operating.filter_mode","custom")
        store.values.setProperty("operating.custom_filter","b/N1CALL d/RS0ISS*");store.save()
        ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use {server ->
            server.soTimeout=5000
            val login=async(Dispatchers.IO) {server.accept().use {socket ->
                socket.soTimeout=5000
                socket.getOutputStream().write("# Local filter test\r\n".toByteArray())
                socket.getInputStream().bufferedReader().readLine()
            }}
            AppModel(home).use {model ->
                assertTrue(model.connect("N0CALL-13","12345","127.0.0.1:${server.localPort}",28.0,-81.0))
                assertTrue(withTimeout(6000) {login.await()}.endsWith("filter b/N1CALL d/RS0ISS* g/N0CALL-13"))
            }
        }
    }
    @Test fun editablePresetsFiltersAndSmartSettingsRoundTrip() {
        val home=Files.createTempDirectory("APRS operating test")
        val store=SettingsStore(home)
        assertEquals(6,store.portPresets.size)
        val iss=store.portPresets.single {it.getProperty("name")=="ISS / satellite KISS"}
        assertEquals("ARISS",iss.getProperty("beacon_path"))
        assertFalse(iss.getProperty("is_to_rf").toBoolean())
        assertFalse(iss.getProperty("digipeater").toBoolean())
        store.values.setProperty("operating.filter_mode","custom")
        store.values.setProperty("operating.custom_filter","b/N1CALL d/RS0ISS*")
        store.values.setProperty("beacon.smart","true")
        store.portPresets.first().setProperty("endpoint","127.0.0.1:9001")
        store.save()
        val restored=SettingsStore(home)
        assertEquals("127.0.0.1:9001",restored.portPresets.first().getProperty("endpoint"))
        assertTrue(BeaconConfiguration(restored.values,restored.entities).timing.smart)
        assertEquals("b/N1CALL d/RS0ISS*",restored.values.getProperty("operating.custom_filter"))
        restored.portPresets=emptyList();restored.save()
        assertTrue(SettingsStore(home).portPresets.isEmpty())
    }
    @Test fun localFeedMonitorExportAndDemoIsolation(): Unit = runBlocking {
        val home=Files.createTempDirectory("APRS monitor test")
        val settings=SettingsStore(home).values
        val port=ServerSocket(0).use {it.localPort}
        settings.setProperty("operating.feed_port",port.toString());settings.setProperty("operating.feed_enabled","true")
        settings.setProperty("operating.monitor_capacity","100")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val monitor=PacketMonitor(home,scope)
        try {
            monitor.configure(settings,false)
            Socket("127.0.0.1",port).use {socket ->
                socket.soTimeout=3000
                val input=socket.getInputStream().bufferedReader()
                assertTrue(input.readLine().startsWith("# APRSERJ64"))
                val packet="N1CALL>APRS,RS0ISS*:>Satellite test"
                monitor.record("ISS","RX",packet)
                assertEquals(packet,input.readLine())
                assertTrue(monitor.entries.value.single().satellite)
                repeat(110) {monitor.record("ISS","TX","N0CALL>APRS:>Test $it")}
                assertEquals(100,monitor.entries.value.size)
                val path=monitor.export(monitor.entries.value)
                assertEquals(100,Files.readAllLines(path).size)
                monitor.configure(settings,true)
                assertEquals("Local feed off",monitor.feedStatus.value)
                assertFails {Socket("127.0.0.1",port).close()}
            }
        } finally {monitor.close();scope.coroutineContext[Job]?.cancelAndJoin()}
    }
    @Test fun failedMessageRetriesHaveFreshIdsAndKeepHistory(): Unit = runBlocking {
        var now=1_000_000L
        ChatDatabase(Files.createTempDirectory("APRS retry test").resolve("chat.db"),{now}).use {db ->
            db.enqueue("N0CALL","N1CALL","Retry me")
            val old=db.pending("N0CALL").single();now+=900001;db.pending("N0CALL")
            db.retry("N0CALL",old.row)
            val next=db.pending("N0CALL").single()
            assertNotEquals(old.wireId,next.wireId)
            assertEquals(2,db.rows("N0CALL").size)
            assertEquals("NO_ACK",db.rows("N0CALL").first().status)
            db.retry("OTHER",old.row)
            assertTrue(db.rows("OTHER").isEmpty())
        }
    }
}
