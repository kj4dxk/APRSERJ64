// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*
import net.osmand.shared.aprs.AprsConnectionState

class PortableSettingsTest {
    // Prepared for the next authorized test run; no network is used here.
    @Test fun customServicesAndServerListSurviveSave() {
        val folder=Files.createTempDirectory("APRSIS64 custom settings ")
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
        val folder = Files.createTempDirectory("APRSIS64 portable ")
        PortableHome.prepare(folder)
        DemoServer().use { server ->
            AppModel(folder).use { model ->
                assertTrue(model.connect("N0CALL-12", "12345", "tcp://127.0.0.1:${server.port}", 28.536725, -81.242932))
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
            }
        }
    }
}
