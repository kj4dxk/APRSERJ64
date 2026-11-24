// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.awt.Rectangle
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.*

class OperatorReviewTest {
    @Test fun dialogsFitSmallTaskbarAndSecondaryMonitor() {
        val area=Rectangle(-1280,24,1280,696)
        assertEquals(Rectangle(-1140,24,1000,696),fitWindow(area,1000,790))
        val tools=fitWindow(area,1100,800,170)
        assertTrue(area.contains(tools))
    }

    @Test fun weatherUnitSelectionAndConnectionWindows() {
        assertEquals("°F",WeatherDisplay.US.temperatureUnit)
        assertEquals("mph",WeatherDisplay.US.windUnit)
        assertEquals("inHg",WeatherDisplay.US.pressureUnit)
        assertEquals("in",WeatherDisplay.US.rainUnit)
        assertContentEquals(WeatherDisplay.US.temperature(0.0).map {it.digitToIntOrNull()}.filterNotNull(),listOf(3,2,0))
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            PacketMonitor(Files.createTempDirectory("aprserj64-monitor-"),scope).use {monitor->
                val line="N0CALL>APRS:!2830.00N>08112.00W/"
                monitor.record("TNC","RX",line)
                monitor.record("APRS-IS","TX",line)
                val rf=monitor.entries.value.filter(PacketStream("TNC","RX")::matches).single()
                val sent=monitor.entries.value.filter(PacketStream("APRS-IS","TX")::matches).single()
                assertEquals(rf.packet,sent.packet)
                assertTrue(rf.invalid)
                assertEquals("Invalid symbol table or overlay '>'",rf.issue)
            }
        } finally {scope.cancel()}
    }
}
