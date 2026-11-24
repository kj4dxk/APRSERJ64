// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.nio.file.Files
import java.net.ServerSocket
import java.net.InetAddress
import kotlinx.coroutines.*
import org.aprsfoundation.core.*
import kotlin.test.*

class AutomationIntegrationTest {
 @Test fun gpsOdometerAndLoggingWorkWithoutAnAprsConnection():Unit=runBlocking {
  val home=Files.createTempDirectory("APRS GPS odometer");PortableHome.prepare(home)
  ServerSocket(0,5,InetAddress.getByName("127.0.0.1")).use {server->
   server.soTimeout=10000
   val store=SettingsStore(home)
   store.values.setProperty("callsign","N0CALL-12");store.values.setProperty("autoConnect","false")
   store.values.setProperty("position.source","port");store.values.setProperty("position.port_name","GPS")
   store.values.setProperty("logging.gpx","true")
   store.ports=listOf(PortPreferences.defaults("NMEA").apply {setProperty("name","GPS");setProperty("enabled","true");setProperty("transport","tcp");setProperty("endpoint","127.0.0.1:${server.localPort}")})
   store.save()
   AppModel(home).use {model->
    withContext(Dispatchers.IO){server.accept()}.use {socket->
     fun fix(latitude:String) {
      val now=java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
      val body="GPRMC,${now.format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"))},A,$latitude,N,01131.000,E,022.4,084.4,${now.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyy"))},003.1,W"
      val checksum=body.fold(0){sum,c->sum xor c.code}.toString(16).padStart(2,'0')
      socket.getOutputStream().apply {write("\$$body*$checksum\r\n".toByteArray());flush()}
     }
     fix("4807.038")
     withTimeout(6000){while(model.beaconState.value.position?.current!=true)delay(20)}
     delay(1200);fix("4807.048")
     withTimeout(6000){while(model.beaconState.value.odometerMeters<15)delay(20)}
     assertTrue(model.beaconState.value.odometerMeters in 15.0..25.0)
     assertEquals(AprsConnectionState.STOPPED,model.state.value)
    }
   }
   assertTrue(Files.readString(home.resolve("data/odometer-N0CALL-12.txt")).toDouble() in 15.0..25.0)
   val log=Files.list(home.resolve("logs/tracks")).use {it.filter {p->p.toString().endsWith(".gpx")}.findFirst().orElseThrow()}
   val document=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(log.toFile())
   assertTrue(document.getElementsByTagName("trkpt").length>=2)
  }
 }
 @Test fun scheduledReportsAndChatRouteTraverseOnlyIntendedOutput():Unit=runBlocking {
  val home=Files.createTempDirectory("APRS automation integration");PortableHome.prepare(home)
  val settings=SettingsStore(home)
  settings.values.setProperty("position.fixed_confirmed","true")
  settings.values.setProperty("beacon.mic_e","true")
  settings.values.setProperty("telemetry.enabled","true")
  settings.values.setProperty("bulletins.enabled","true")
  settings.values.setProperty("bulletins.entries",encodedNames(listOf("1|QA|600|is|Local test only")))
  settings.save()
  ServerSocket(0,5,InetAddress.getByName("127.0.0.1")).use {server->
   server.soTimeout=10000
   AppModel(home).use {model->
    assertTrue(model.connect("N0CALL-12","12345","127.0.0.1:${server.localPort}",28.5,-81.2,false))
    withContext(Dispatchers.IO){server.accept()}.use {socket->
     socket.soTimeout=10000
     val reader=socket.getInputStream().bufferedReader();val writer=socket.getOutputStream().bufferedWriter()
     writer.write("# automation loopback server\r\n");writer.flush()
     withContext(Dispatchers.IO){reader.readLine()}
     writer.write("# logresp N0CALL-12 verified, local\r\n");writer.flush()
     withTimeout(5000){while(model.state.value!=AprsConnectionState.VERIFIED)delay(10)}
     model.database.saveOptions("N0CALL-12","N1CALL",ChatOptions("is",false))
     model.send("N1CALL","No acknowledgement requested")
     model.database.saveOptions("N0CALL-12","N2CALL",ChatOptions("rf",false))
     model.send("N2CALL","RF only must never leak to IS")
     val received=withContext(Dispatchers.IO) {
      val lines=mutableListOf<String>()
      while(!(lines.any {it.contains(":T#")} && lines.any {it.contains("::BLN1QA")} && lines.any {it.contains(":No acknowledgement requested")} && lines.any {it.substringAfter(':').startsWith('`')})) {
       lines.add(reader.readLine()?:error("Connection closed"))
      }
      lines
     }
     assertFalse(received.any {"RF only" in it})
     assertFalse(received.single {"No acknowledgement requested" in it}.contains('{'))
     assertNotNull(AprsProtocol.parse(received.first {it.substringAfter(':').startsWith('`')},0))
     assertTrue(received.any {"PARM." in it});assertTrue(received.any {"EQNS." in it})
     withTimeout(5000){while(model.database.rows("N0CALL-12","N1CALL").single().status!="SENT")delay(10)}
    }
   }
  }
 }
 @Test fun inactivityAutoReplyDeduplicatesAndAvoidsReplyLoops():Unit=runBlocking {
  val home=Files.createTempDirectory("APRS auto reply");PortableHome.prepare(home)
  val store=SettingsStore(home);store.values.setProperty("callsign","N0CALL-12");store.values.setProperty("autoConnect","false");store.values.setProperty("messaging.auto_reply","true");store.save()
  AppModel(home).use {model->
   val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
   val automation=OperatingAutomation(model,scope){null}
   try {
    val now=System.currentTimeMillis()+16*60000
    automation.observe("RX","N1CALL>APRS::N0CALL-12:Hello{1",now)
    automation.observe("RX","N1CALL>APRS::N0CALL-12:Hello{1",now+1000)
    automation.observe("RX","N2CALL>APRS::N0CALL-12:AA: Away{2",now)
    automation.observe("RX","N3CALL>APRS::N0CALL-12:ackAB",now)
    withTimeout(5000){while(model.database.rows("N0CALL-12").isEmpty())delay(10)}
    assertEquals(listOf("N1CALL"),model.database.rows("N0CALL-12").map {it.peer})
    assertTrue(model.database.rows("N0CALL-12").single().body.startsWith("AA: "))
   }finally {automation.close();scope.cancel()}
  }
 }
}
