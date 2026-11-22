// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*
import org.aprsfoundation.core.AprsConnectionState

class PortableSettingsTest {
    @Test fun namedPortsAndStaticObjectRoutesRoundTrip() {
        val folder=Files.createTempDirectory("APRSERJ64 ports ")
        val store=SettingsStore(folder)
        store.ports=listOf(
            PortPreferences.defaults("NMEA").apply {setProperty("name","GPS");setProperty("device","COM88")},
            PortPreferences.defaults("Simply(KISS)").apply {setProperty("name","VHF");setProperty("device","COM89");setProperty("digi_transforms","[\"WIDE1-1=\",\"WIDE2-2=WIDE2-1\"]")})
        store.values.setProperty("position.source","port");store.values.setProperty("position.port_name","GPS")
        store.entities=listOf(java.util.Properties().apply {
            BeaconPreferences.entityFields.forEach {setProperty(it.key,it.default)}
            setProperty("name","TESTOBJ");setProperty("ports","[\"VHF\"]");setProperty("internet","false")
        })
        store.save()
        val restored=SettingsStore(folder)
        assertEquals(listOf("GPS","VHF"),restored.ports.map {it.getProperty("name")})
        assertEquals("COM88",restored.ports.first().getProperty("device"))
        assertEquals("GPS",restored.values.getProperty("position.port_name"))
        assertEquals("false",restored.entities.single().getProperty("internet"))
        assertEquals("[\"VHF\"]",restored.entities.single().getProperty("ports"))
        restored.ports.forEach {it.setProperty("enabled","true");it.setProperty("device","COM88")}
        assertFailsWith<IllegalArgumentException> {restored.save()}
    }
    // Prepared for the next authorized test run; no network is used here.
    @Test fun customServicesAndServerListSurviveSave() {
        val folder=Files.createTempDirectory("APRSERJ64 custom settings ")
        Files.writeString(folder.resolve("settings.toml"), """
            [aprs]
            server = "custom.example"
            port = 15000
            [map]
            tile_url = "https://tiles.example/{z}/{x}/{y}.png"
            overlay_enabled = true
            overlay_url = "https://overlay.example/{z}/{x}/{y}.png"
            [[aprs.servers]]
            name = "My server"
            host = "custom.example"
            port = 15000
        """.trimIndent())
        val first=SettingsStore(folder)
        assertEquals("true",first.values.getProperty("beacon.enabled"))
        first.save()
        val restored=SettingsStore(folder)
        assertEquals(listOf(ServerPreset("My server","custom.example",15000)),restored.servers)
        assertEquals("custom.example:15000",restored.values.getProperty("endpoint"))
        assertEquals("https://tiles.example/{z}/{x}/{y}.png",restored.values.getProperty("map.tile_url"))
        assertEquals("true",restored.values.getProperty("map.overlay_enabled"))
        assertTrue(ServicePreferences.style(restored.values).contains("overlay.example"))
        restored.servers=emptyList()
        restored.save()
        assertTrue(SettingsStore(folder).servers.isEmpty())
    }
    @Test fun presetOrderMatchesAndroid() {
        assertEquals(listOf("rotate.aprs2.net", "noam.aprs2.net", "euro.aprs2.net", "asia.aprs2.net", "aunz.aprs2.net",
            "soam.aprs2.net", "rotate.aprs.net", "germany.aprs2.net", "japan.aprs2.net", "nordic.aprs2.net", "swiss.aprs2.net", "china.aprs2.net"),
            AprsServers.presets.map { it.host })
        assertEquals("rotate.aprs2.net:14580", AprsServers.defaultEndpoint)
    }
    @Test fun portableSettingsAndAutomaticReconnect(): Unit = runBlocking {
        val folder = Files.createTempDirectory("APRSERJ64 portable ")
        PortableHome.prepare(folder)
        DemoServer().use { server ->
            AppModel(folder).use { model ->
                assertTrue(model.connect("N1TEST-12", "12345", "tcp://127.0.0.1:${server.port}", 28.536725, -81.242932))
                withTimeout(10_000) { while(model.state.value != AprsConnectionState.VERIFIED) delay(20) }
            }
            assertTrue(Files.exists(folder.resolve("settings.toml")))
            assertTrue(Files.exists(folder.resolve("data/chats.sqlite")))
            val restored = SettingsStore(folder).values
            assertEquals("12345", restored.getProperty("passcode"))
            AppModel(folder).use { model ->
                model.connectSaved()
                withTimeout(10_000) { while(model.state.value != AprsConnectionState.VERIFIED) delay(20) }
                model.disconnect(remember=true)
            }
            AppModel(folder).use { model ->
                model.connectSaved()
                assertEquals(AprsConnectionState.STOPPED, model.state.value)
                assertTrue(model.connect("N1TEST-12", "12345", "127.0.0.1:${server.port}", 28.536725, -81.242932))
            }
            AppModel(folder).use {model->
                model.connectSaved()
                withTimeout(10_000) {while(model.state.value!=AprsConnectionState.VERIFIED) delay(20)}
            }
        }
    }
}
