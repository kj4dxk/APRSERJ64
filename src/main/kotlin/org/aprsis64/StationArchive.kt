// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import java.nio.file.*
import java.sql.DriverManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import net.osmand.shared.aprs.*

class StationArchive(private val home: Path,parent: CoroutineScope,private val setting: (String)->String): AutoCloseable {
 val objects=MutableStateFlow<Set<String>>(emptySet())
 val weather=MutableStateFlow<Map<String,AprsWeather>>(emptyMap())
 val telemetry=MutableStateFlow<Map<String,List<TelemetrySample>>>(emptyMap())
 val definitions=MutableStateFlow<Map<String,Map<String,TelemetryDefinition>>>(emptyMap())
 val bulletins=MutableStateFlow<List<AprsBulletin>>(emptyList())
 val revision=MutableStateFlow(0L)
 val status=MutableStateFlow("")
 val dropped=MutableStateFlow(0L)
 private val lock=Any()
 private val db=DriverManager.getConnection("jdbc:sqlite:${home.resolve("data/stations.sqlite")}")
 private val input=Channel<Pair<String,Long>>(2048)
 private val worker: Job
 init {
  synchronized(lock) {db.createStatement().use {it.execute("PRAGMA journal_mode=WAL");it.execute("PRAGMA busy_timeout=5000")
   it.execute("CREATE TABLE IF NOT EXISTS tracks(id INTEGER PRIMARY KEY,call TEXT,time INTEGER,lat REAL,lon REAL)")
   it.execute("CREATE INDEX IF NOT EXISTS track_call ON tracks(call,time)")
   it.execute("CREATE TABLE IF NOT EXISTS observations(id INTEGER PRIMARY KEY,time INTEGER,packet TEXT)")
  }
  db.createStatement().use {s->s.executeQuery("SELECT time,packet FROM observations ORDER BY id").use {r->while(r.next()) decode(r.getString(2),r.getLong(1))}}
  }
  worker=parent.launch(Dispatchers.IO) {for((line,time) in input) try {ingest(line,time)} catch(_:Exception){status.value="Unable to store station history; check free disk space"}}
 }
 fun offer(line: String,time: Long=System.currentTimeMillis()) {if(!input.trySend(line to time).isSuccess) dropped.value++}
 private fun decode(line: String,time: Long): Boolean {
  var observation=false
  val packet=RadioPacket.parse(line)
  if(packet?.payload?.firstOrNull() in listOf(';',')')) AprsProtocol.parse(line,time)?.let {objects.value=(objects.value+it.callsign).toList().takeLast(2000).toSet();observation=true}
  AprsObservations.weather(line,time)?.let {weather.value=(weather.value+ (it.call to it)).entries.toList().takeLast(1000).associate {it.toPair()};observation=true}
  AprsObservations.telemetry(line,time)?.let {sample->telemetry.value=(telemetry.value+(sample.call to ((telemetry.value[sample.call].orEmpty()+sample).takeLast(300)))).entries.toList().takeLast(300).associate {it.toPair()};observation=true}
  AprsObservations.definition(line)?.let {d->definitions.value=(definitions.value+(d.call to (definitions.value[d.call].orEmpty()+(d.kind to d)))).entries.toList().takeLast(1000).associate {it.toPair()};observation=true}
  AprsObservations.bulletin(line,time)?.let {b->bulletins.value=(bulletins.value.filterNot {it.sender==b.sender && it.destination==b.destination}+b).takeLast(200);observation=true}
  return observation
 }
 private fun ingest(line: String,time: Long)=synchronized(lock) {
  val observation=decode(line,time)
  if(observation) db.prepareStatement("INSERT INTO observations(time,packet) VALUES(?,?)").use {it.setLong(1,time);it.setString(2,line);it.executeUpdate()}
  if(setting("history.enabled").toBoolean()) AprsProtocol.parse(line,time)?.let {s->
   db.prepareStatement("INSERT INTO tracks(call,time,lat,lon) VALUES(?,?,?,?)").use {it.setString(1,s.callsign);it.setLong(2,time);it.setDouble(3,s.latitude);it.setDouble(4,s.longitude);it.executeUpdate()}
  }
  revision.value++
  if(revision.value%100==0L) db.createStatement().use {
   it.executeUpdate("DELETE FROM tracks WHERE id <= (SELECT max(id)-${setting("history.maximum_points").toInt()} FROM tracks)")
   it.executeUpdate("DELETE FROM observations WHERE id <= (SELECT max(id)-10000 FROM observations)")
  }
 }
 suspend fun track(call: String): List<AprsFix> = withContext(Dispatchers.IO) {synchronized(lock) {
  db.prepareStatement("SELECT time,lat,lon FROM tracks WHERE call=? ORDER BY id DESC LIMIT ?").use {s->s.setString(1,call);s.setInt(2,setting("history.track_points").toInt());s.executeQuery().use {r->buildList {while(r.next()) add(AprsFix(r.getDouble(2),r.getDouble(3),r.getLong(1)))}.reversed()}}
 }}
 suspend fun export(call: String,format: String): Path {
  val points=track(call);require(points.isNotEmpty()) {"No recorded positions for this station"}
  return withContext(Dispatchers.IO) {
   val folder=home.resolve("exports");Files.createDirectories(folder)
   val path=Files.createTempFile(folder,"track-",if(format=="GPX") ".gpx" else ".csv")
   Files.writeString(path,if(format=="GPX") AprsServices.gpx(call,points) else "time,latitude,longitude\n"+points.joinToString("\n") {"${java.time.Instant.ofEpochMilli(it.timeMillis)},${it.latitude},${it.longitude}"});path
  }
 }
 suspend fun finish() {input.close();worker.join();synchronized(lock) {db.close()}}
 override fun close() {input.close();worker.cancel();synchronized(lock) {db.close()}}
}
