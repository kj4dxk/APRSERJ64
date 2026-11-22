package org.aprserj64

import java.nio.file.*
import kotlin.test.*
import org.junit.Assume.assumeTrue
import org.aprsfoundation.core.*
import kotlinx.serialization.json.*

/** Opt-in real-feed regression/performance probe. Raw captures stay outside source control. */
class LiveFeedProbeTest {
 @Test fun decodeCapturedFeed() {
  val path=System.getenv("APRSERJ64_FEED_FILE")
  assumeTrue("Set APRSERJ64_FEED_FILE to a local capture",path!=null)
  val input=Paths.get(path!!); val lines=Files.readAllLines(input,Charsets.ISO_8859_1)
  require(lines.size>=1000)
  val now=System.currentTimeMillis()
  // Warm the decoder before measuring.
  repeat(2) {lines.take(2000).forEach {AprsProtocol.parse(it,now)}}
  var positions=0;var messages=0;var weather=0;var telemetry=0;var envelopes=0
  val decoded=mutableListOf<String>(); val t=System.nanoTime()
  lines.forEachIndexed {index,line->
   if(RadioPacket.parse(line)!=null) envelopes++
   if(AprsMessages.parse(line)!=null) messages++
   if(AprsObservations.weather(line,now)!=null)weather++
   if(AprsObservations.telemetry(line,now)!=null)telemetry++
   val p=AprsProtocol.parse(line,now)
   if(p!=null){positions++;assertTrue(p.latitude.isFinite() && p.latitude in -90.0..90.0);assertTrue(p.longitude.isFinite() && p.longitude in -180.0..180.0)}
   decoded+=buildJsonObject {put("index",index);if(p!=null){put("call",p.callsign);put("lat",p.latitude);put("lon",p.longitude);put("table",p.symbolTable.toString());put("symbol",p.symbol.toString())}}.toString()
  }
  val decodeMs=(System.nanoTime()-t)/1e6
  val store=AprsStationStore();val st=System.nanoTime();lines.forEachIndexed {i,line->store.receive(line,now+i)}
  val storeMs=(System.nanoTime()-st)/1e6
  Files.write(input.resolveSibling("decoded.jsonl"),decoded)
  Files.writeString(input.resolveSibling("decoder-report.json"),buildJsonObject {
   put("packets",lines.size);put("envelopes",envelopes);put("positions",positions);put("messages",messages);put("weather",weather);put("telemetry",telemetry)
   put("decode_ms",decodeMs);put("decode_packets_per_second",lines.size*1000/decodeMs)
   put("store_ms",storeMs);put("store_packets_per_second",lines.size*1000/storeMs);put("retained_stations",store.snapshot(now+lines.size).size)
  }.toString())
 }
}
