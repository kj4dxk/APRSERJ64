package org.aprsis64
import kotlin.test.*
import net.osmand.shared.aprs.*
class LiveFeedRegressionTest {
 @Test fun archiveBatchDrainsAllAcceptedPacketsOnFinish():Unit=kotlinx.coroutines.runBlocking {
  val home=java.nio.file.Files.createTempDirectory("APRS batch test");PortableHome.prepare(home)
  val settings=SettingsStore(home).values
  val parent=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO)
  val archive=StationArchive(home,parent) {settings.getProperty(it)}
  repeat(1200) {archive.offer("N0CALL>APRS:!2832.20N/08114.58W>batch",1000L+it)}
  archive.finish();assertEquals(0L,archive.dropped.value)
  java.sql.DriverManager.getConnection("jdbc:sqlite:${home.resolve("data/stations.sqlite")}").use {db->
   db.createStatement().use {stmt->stmt.executeQuery("SELECT count(*) FROM tracks").use {r->r.next();assertEquals(1200,r.getInt(1))}}
  }
  parent.coroutineContext[kotlinx.coroutines.Job]?.cancel()
 }

 @Test fun weatherFilterUsesReceivedReportsNotOnlySymbol() {
  val views=StationViews()
  views.receive("APRS-IS","N0CALL>APRS:_11201000c180s010g015t075r001p010P020h65b10132",0)
  val p=assertNotNull(AprsProtocol.parse("N0CALL>APRS:!2832.20N/08114.58W>Car with weather",0))
  assertTrue(stationKind(p,"weather",views.stations.value,emptySet()))
 }
}
