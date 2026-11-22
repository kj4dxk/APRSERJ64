// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import kotlin.test.*
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.*
import org.aprsfoundation.core.*
class ParityFeaturesTest {
 @Test fun digitalLogFormatsRejectTransmitsAndReportsWithoutLocations() {
  val ft=DigitalLogs.parse("FT8","261121_043915 7.074 Rx FT8 -21 0.1 967 N7KDT N9YLZ EN51")!!
  assertEquals("N9YLZ",ft.call);assertEquals(41.5,ft.position(0).fix.latitude);assertEquals(-89.0,ft.position(0).fix.longitude)
  assertNull(DigitalLogs.parse("FT8","261121_043915 7.074 Tx FT8 -21 0.1 967 CQ N9YLZ EN51"))
  assertNull(DigitalLogs.parse("FT8","261121_043915 7.074 Rx FT8 -21 0.1 967 N7KDT N9YLZ RR73"))
  assertEquals("N0CALL",DigitalLogs.parse("JT65","\"2026-11-21\",\"16:44\",\"14076000\",\"4\",\"-12\",\"-0.2\",\"773\",\"B\",\"CQ N0CALL FN31\",\"65A\"")!!.call)
  assertEquals("N0CALL",DigitalLogs.parse("JS8Call","2026-11-21 18:28:30-13 -0.0 567 A encoded 3 N0CALL: HB SPOT EM73")!!.call)
 }
 @Test fun wxnowUsesEmbeddedUtcTimeAndUnits() {
  val now=Instant.parse("2026-11-21T12:00:30Z").toEpochMilli()
  val value=LocalSensors.wxnow("N0CALL","Nov 21 2026 12:00\n180/010g020t068r010p020P030h50b10132",now,600)!!
  assertEquals(20.0,value.temperatureC!!,1e-6);assertEquals(16.09344,value.windKph!!,1e-6)
  assertEquals(7.62,value.rainMidnightMm!!,1e-6)
  assertFailsWith<IllegalArgumentException>{LocalSensors.wxnow("N0CALL","Nov 20 2026 12:00\n180/010t068",now,600)}
 }
 @Test fun pendingQueryPreservesIdsAndDoesNotResendAcknowledgedOrRejected():Unit=runBlocking {
  ChatDatabase(Files.createTempDirectory("pending-query").resolve("chat.db")).use {db->
   db.enqueue("N0CALL","N1CALL","hello");val row=db.pending("N0CALL").single();db.attempted(row.row);db.transmitted(row.row,true)
   assertEquals(1,db.requeuePending("N0CALL","N1CALL"));assertEquals(row.wireId,db.pending("N0CALL").single().wireId)
   db.receive("N0CALL",AprsMessage("N1CALL","N0CALL","",acknowledgement=row.wireId))
   assertEquals(0,db.requeuePending("N0CALL","N1CALL"))
  }
 }
 @Test fun metadataKeepsRecentDirectAndIdentifiesEntryIgate() {
  val view=StationViews()
  view.receive("RF","N0CALL>APRS:!2830.00N/08112.00W>",1000)
  view.receive("RF","N0CALL>APRS,DIGI*:!2830.00N/08112.00W>",2000)
  assertTrue(view.stations.value.getValue("N0CALL").direct)
  view.receive("APRS-IS","OTHER>APRS,qAR,N0CALL:!2830.00N/08112.00W>",3000,false)
  assertTrue(view.stations.value.getValue("N0CALL").igate)
  view.receive("APRS-IS","OTHER>APRS:!2830.00N/08112.00W>",2000000,false)
  assertFalse(view.stations.value.getValue("N0CALL").direct)
 }

 @Test fun digitalObjectsImportOfflineExpireAndRememberOwnership():Unit=runBlocking {
  val home=Files.createTempDirectory("digital-lifecycle");PortableHome.prepare(home)
  val p=SettingsStore(home).values
  p.setProperty("digital.enabled","true");p.setProperty("digital.transmit","false");p.setProperty("digital.ft8_file","ALL.TXT")
  val time=Instant.parse("2026-11-21T04:39:15Z").toEpochMilli()
  Files.writeString(home.resolve("ALL.TXT"),"261121_043915 7.074 Rx FT8 -21 0.1 967 CQ N0CALL EN51\n")
  val feed=DigitalObjectFeed(home);val packets=mutableListOf<String>()
  feed.tick("N1CALL",p,time+1000){line,_,_,_->packets.add(line);true}
  assertEquals("N0CALL-FT",feed.stations.value.single().callsign);assertTrue(packets.isEmpty())
  p.setProperty("digital.transmit","true")
  feed.tick("N1CALL",p,time+12000){line,_,_,_->packets.add(line);true}
  assertEquals('*',packets.single().substringAfter(':')[10])
  val restarted=DigitalObjectFeed(home)
  restarted.tick("N1CALL",p,time+1900000){line,_,_,_->packets.add(line);true}
  assertTrue(restarted.stations.value.isEmpty());assertEquals('_',packets.last().substringAfter(':')[10])
  assertEquals(2,packets.size)
 }
 @Test fun groupsKeepEvidenceWithoutInventingListCompleteness() {
  val home=Files.createTempDirectory("groups-evidence");val groups=GroupMembership(home){"ANSRVR"}
  fun receive(text:String)=groups.observe("N0CALL","RX",AprsMessages.unnumbered("ANSRVR","N0CALL",text).trimEnd())
  receive("Joined WEATHER");assertEquals("Confirmed member",groups.entries.value.single().state)
  groups.observe("N0CALL","TX",AprsMessages.unnumbered("N0CALL","ANSRVR","L").trimEnd())
  assertEquals("Previously confirmed; awaiting list",groups.entries.value.single().state)
  receive("WEATHER(12)");assertEquals("Confirmed member",groups.entries.value.single().state)
  receive("WEATHER Removed From Group");assertEquals(1,groups.entries.value.size);assertEquals("Not a member",groups.entries.value.single().state)
 }

 @Test fun passCrossingsAndPeakAreRefinedRatherThanRoundedToSamples():Unit=runBlocking {
  val passes=orbitPasses("test",0,120000){t->10-kotlin.math.abs(t-60000)/1000.0}
  val pass=passes.single()
  assertTrue(kotlin.math.abs(pass.rise-50000)<=1000)
  assertTrue(kotlin.math.abs(pass.set-70000)<=1000)
  assertEquals(10.0,pass.maximumElevation,0.6)
 }
}
