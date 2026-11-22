// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import java.nio.file.*
import java.nio.*
import java.util.Properties
import java.time.Instant
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*
import kotlin.test.*

class RemainingFeaturesTest {
 private fun home()=Files.createTempDirectory("APRS remaining test").also {PortableHome.prepare(it)}
 @Test fun defaultSettingsAndNewFieldsSurviveTomlRoundTrip() {
  val dir=home();val s=SettingsStore(dir);FeaturePreferences.validate(s.values)
  Files.writeString(Path.of("build/default-settings-example.toml"),Files.readString(dir.resolve("settings.toml")))
  s.values.setProperty("beacon.mic_e","true")
  s.values.setProperty("bulletins.entries",encodedNames(listOf("1|TEST|600|is|Exercise bulletin")))
  s.values.setProperty("messaging.auto_reply","true");s.values.setProperty("logging.csv","true")
  s.save();val loaded=SettingsStore(dir)
  for(key in listOf("beacon.mic_e","bulletins.entries","messaging.auto_reply","logging.csv")) assertEquals(s.values.getProperty(key),loaded.values.getProperty(key))
  loaded.values.setProperty("legacyweather.enabled","true")
  assertTrue(OperatingPreferences.filterTerms(loaded.values).contains("g/NWS*"))
 }
 @Test fun longChatsRouteAndNoAckPersistAndFinish():Unit=runBlocking {
  val dir=home();val path=dir.resolve("data/chat-test.sqlite")
  ChatDatabase(path).use {db->db.saveOptions("N0CALL","N1CALL",ChatOptions("rf",false));db.enqueue("N0CALL","N1CALL","x".repeat(300));assertEquals(5,db.rows("N0CALL").size)}
  ChatDatabase(path).use {db->
   assertEquals(ChatOptions("rf",false),db.options("N0CALL","N1CALL"))
   repeat(5) {val message=db.pending("N0CALL").single();assertEquals("rf",message.route);assertFalse(message.requestAck);db.attempted(message.row);db.transmitted(message.row,true)}
   assertTrue(db.pending("N0CALL").isEmpty());assertTrue(db.rows("N0CALL").all {it.status=="SENT"})
  }
 }
 @Test fun incompleteObjectKillResumesAndRevivalResets() {
  val dir=home();val p=Properties().apply {setProperty("name","TEST");setProperty("alive","false");setProperty("longitude","1.0")}
  BeaconJournal(dir).write(p,2,123456)
  assertEquals(2 to 123456L,BeaconJournal(dir).read(p))
  p.setProperty("alive","true");BeaconJournal(dir).revive(p);p.setProperty("alive","false")
  assertEquals(0 to 0L,BeaconJournal(dir).read(p))
 }
 @Test fun automaticTrackFilesAreValidAcrossRestart() {
  val dir=home();val p=SettingsStore(dir).values;p.setProperty("logging.csv","true");p.setProperty("logging.gpx","true")
  val now=System.currentTimeMillis();val station=AprsStation("OBJ<&",28.5,-81.2,'/','-',"",now)
  TrackLog(dir){p.getProperty(it)}.record(station)
  TrackLog(dir){p.getProperty(it)}.record(station.copy(latitude=28.51,heardAtMillis=now+1000))
  val files=Files.list(dir.resolve("logs/tracks")).use {it.toList()}
  val gpx=files.single {it.toString().endsWith("gpx")}
  val doc=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(gpx.toFile())
  assertEquals(2,doc.getElementsByTagName("trkpt").length)
  assertEquals(3,Files.readAllLines(files.single {it.toString().endsWith("csv")}).size)
 }
 @Test fun ownTelemetryDefinitionsAndSamplesAreDecodable() {
  val p=SettingsStore(home()).values
  val channels=names(p.getProperty("telemetry.channels")).map {TelemetryChannel.parse(it)}
  val definitions=TelemetryPackets.definitions("N0CALL",channels,names(p.getProperty("telemetry.bit_names")),"Test")
  assertEquals(setOf("PARM","UNIT","EQNS","BITS"),definitions.map {assertNotNull(AprsObservations.definition(it.trimEnd())).kind}.toSet())
  val sample=assertNotNull(AprsObservations.telemetry(TelemetryPackets.sample("N0CALL",999,listOf(0,1,2,254,255),"10101010").trimEnd(),0))
  assertEquals(255.0,sample.analog.last());assertEquals("10101010",sample.bits)
 }
 @Test fun legacyWarningsExpandCodesAndExpireAcrossMonthBoundary() {
  assertEquals(listOf("COZ091","COZ092","KSZ027","KSZ028","KSZ029","KSZ041","KSZ042"),LegacyWeather.zones("COZ91-92-KSZ27>29-41-42"))
  val now=Instant.parse("2026-11-30T23:30:00Z").toEpochMilli()
  val alert=assertNotNull(LegacyWeather.parse("MLBFFW>APRS::NWS_WARN :010100z,FLASH_FLOOD,FLZ041-044{12345",now))
  assertEquals("2026-12-01T01:00:00Z",alert.expires);assertEquals(listOf("FLZ041","FLZ044"),alert.zones)
  assertNull(LegacyWeather.parse("MLBFFW>APRS::NWS_WARN :012500z,BAD,FLZ041{12345",now))
 }
 @Test fun packetViewsKeepInvalidAndDetectPathIndependentDuplicates() {
  val dir=home();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val monitor=PacketMonitor(dir,scope)
  try {
   monitor.record("APRS-IS","RX","malformed")
   monitor.record("RF","RX","N0CALL>APRS:!2830.00N/08112.00W>")
   monitor.record("APRS-IS","RX","N0CALL>APRS,DIGI*:!2830.00N/08112.00W>")
   assertTrue(monitor.entries.value.first().invalid);assertTrue(monitor.entries.value.last().duplicate)
   val views=StationViews();views.receive("RF","N0CALL>APWW01:=2830.00N/08112.00W>146.940MHz T123 -060")
   val s=views.stations.value.getValue("N0CALL");assertTrue(s.rf);assertTrue(s.messageCapable);assertEquals("KJ4ERJ APRSIS32",s.platform);assertNotNull(s.frequency)
  }finally {monitor.close();scope.cancel()}
 }
 @Test fun localShapefileBoundaryLookup() {
  val dir=home();val shp=dir.resolve("zones.shp");val dbf=dir.resolve("zones.dbf")
  val bytes=ByteBuffer.allocate(236).order(ByteOrder.BIG_ENDIAN);bytes.putInt(9994);bytes.position(24);bytes.putInt(118);bytes.order(ByteOrder.LITTLE_ENDIAN);bytes.putInt(1000);bytes.putInt(5)
  bytes.position(100);bytes.order(ByteOrder.BIG_ENDIAN);bytes.putInt(1);bytes.putInt(64);bytes.order(ByteOrder.LITTLE_ENDIAN);bytes.putInt(5)
  bytes.position(144);bytes.putInt(1);bytes.putInt(5);bytes.putInt(0)
  listOf(-81.0 to 28.0,-80.0 to 28.0,-80.0 to 29.0,-81.0 to 29.0,-81.0 to 28.0).forEach {(x,y)->bytes.putDouble(x);bytes.putDouble(y)}
  Files.write(shp,bytes.array())
  val d=ByteBuffer.allocate(73).order(ByteOrder.LITTLE_ENDIAN);d.put(3);d.position(4);d.putInt(1);d.putShort(65);d.putShort(7);d.position(32);d.put("UGC".toByteArray());d.position(43);d.put('C'.code.toByte());d.position(48);d.put(6);d.position(64);d.put(13);d.put(' '.code.toByte());d.put("FLZ041".toByteArray());d.put(26)
  Files.write(dbf,d.array())
  val result=ShapeBoundaries.read(shp,"UGC");assertEquals(setOf("FLZ041"),result.keys)
  Files.write(shp,byteArrayOf(0));assertFails {ShapeBoundaries.read(shp,"UGC")}
 }
}
