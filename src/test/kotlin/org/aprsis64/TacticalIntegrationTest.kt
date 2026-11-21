// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.nio.file.Files
import java.util.Properties
import java.net.ServerSocket
import java.net.InetAddress
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*
import kotlin.test.*

class TacticalIntegrationTest {
 private fun home()=Files.createTempDirectory("APRS tactical test").also {PortableHome.prepare(it)}
 private fun entity()=Properties().apply {
  BeaconPreferences.entityFields.forEach {setProperty(it.key,it.default)}
  setProperty("name","HOSP");setProperty("latitude","35.0");setProperty("longitude","-77.5")
 }
 @Test fun settingsAndPolygonObjectSurviveRestart() {
  val home=home();val store=SettingsStore(home)
  store.values.setProperty("tactical.nicknames",encodedNames(listOf("N1CALL=Mobile")))
  store.values.setProperty("tactical.df_measurements",encodedNames(listOf("North,35,-77.5,180,10,8")))
  val e=entity().apply {setProperty("shape","polygon");setProperty("vertices",encodedNames(listOf("35,-77.5","35.01,-77.5","35.01,-77.49")))}
  store.entities=listOf(e);store.save()
  val reloaded=SettingsStore(home)
  assertEquals("Mobile",TacticalPreferences.aliases(reloaded.values)["N1CALL"])
  val config=BeaconConfiguration(reloaded.values,reloaded.entities);val loaded=reloaded.entities.single()
  val wire=BeaconPackets.entity("N0CALL","HOSP",false,true,config.entityPosition(loaded,1000),config.entityFormat(loaded),ObjectGeometry.comment(loaded)).trimEnd()
  val shape=assertNotNull(AprsSpatial.multiline(assertNotNull(AprsProtocol.parse(wire,1000))))
  assertTrue(shape.closed);assertEquals(4,shape.points.size)
  assertEquals(35.01,shape.points[2].latitude,0.0002)
 }
 @Test fun dxIgnoresInternetAndRepeatedPacketsAndPersistsMaximum():Unit=runBlocking {
  val home=home();val settings=SettingsStore(home).values;val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
  val own=AprsFix(0.0,0.0,System.currentTimeMillis());val history=TacticalHistory(home,scope){settings.getProperty(it)}
  history.offer("APRS-IS","N1CALL>APRS:!0100.00N/00000.00E-",own)
  history.offer("VHF","N2CALL>APRS,DIGI*:!0200.00N/00000.00E-",own)
  history.offer("VHF","N3CALL>APRS,WIDE1-1:!0100.00N/00000.00E-",own)
  history.offer("VHF","N3CALL>APRS:!0030.00N/00000.00E-",own)
  history.finish()
  val reopened=TacticalHistory(home,scope){settings.getProperty(it)}
  try {assertEquals("N3CALL",reopened.dx.value.single().call);assertTrue(reopened.dx.value.single().kilometers in 110.0..112.0)}
  finally {reopened.finish();scope.cancel()}
 }
 @Test fun expiredWarningsAndPredictionsAreRemoved() {
  val p=SettingsStore(home()).values;val now=System.currentTimeMillis()
  val alerts=WeatherAlerts.parse("""{"features":[{"properties":{"id":"test","event":"Warning","severity":"Severe","expires":"2000-01-01T00:00:00Z"},"geometry":{"type":"Polygon","coordinates":[[[-77,35],[-76,35],[-76,36],[-77,35]]]}}]}""")
  val s=AprsStation("N1CALL",35.0,-77.5,'/','>',"090/060",now-301000)
  assertTrue(tacticalGeometry(listOf(s),null,emptyMap(),alerts,p,now).isEmpty())
  val live=alerts.map {it.copy(expires="2099-01-01T00:00:00Z")}
  assertEquals("test",tacticalGeometry(emptyList(),null,emptyMap(),live,p,now).single().id)
 }
 @Test fun qruReplyTraversesVerifiedLoopbackConnection():Unit=runBlocking {
  val home=home();val store=SettingsStore(home)
  store.values.setProperty("tactical.qru_enabled","true")
  store.entities=listOf(entity().apply {setProperty("qru_group","HOSP");setProperty("query_only","true")})
  store.save()
  ServerSocket(0,5,InetAddress.getByName("127.0.0.1")).use {server->
   server.soTimeout=10000
   AppModel(home).use {model->
    assertTrue(model.connect("N0CALL-12","12345","127.0.0.1:${server.localPort}",35.0,-77.5,false))
    withContext(Dispatchers.IO) {server.accept()}.use {socket->
     socket.soTimeout=10000
     val reader=socket.getInputStream().bufferedReader();val writer=socket.getOutputStream().bufferedWriter()
     writer.write("# APRSIS64 loopback integration server\r\n");writer.flush()
     val login=withContext(Dispatchers.IO){reader.readLine()};assertTrue(login.contains("g/QRU"))
     writer.write("# logresp N0CALL-12 verified, test\r\nN1CALL>APRS:!3500.06N/07730.00W>\r\n");writer.flush()
     withTimeout(10000){while(model.stations.value.none {it.callsign=="N1CALL"})delay(10)}
     writer.write("N1CALL>APRS::N0CALL-12:HOSP 50{Q1\r\n");writer.flush()
     val reply=withContext(Dispatchers.IO) {
      var found:String?=null
      val received=mutableListOf<String>()
      while(found==null){val line=try {reader.readLine() ?: error("Connection closed")} catch(e:java.net.SocketTimeoutException){error("QRU timeout: ${model.error.value}; received=$received")};received.add(line);if(line.contains(":N1CALL   :)HOSP!"))found=line}
      found
     }
     val station=assertNotNull(AprsProtocol.parse(reply,1000));assertEquals("HOSP",station.callsign);assertEquals(35.0,station.latitude)
     assertTrue(model.portStatuses.value.isEmpty())
    }
   }
  }
 }
}
