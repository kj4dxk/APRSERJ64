// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.nio.file.*
import java.io.RandomAccessFile
import java.time.*
import org.aprsfoundation.core.*

/** Daily, per-station files. GPX remains a complete document after every write. */
class TrackLog(private val home:Path,private val setting:(String)->String) {
 private var cleaned=""
 private val folder=home.resolve("logs/tracks")
 private fun fileKey(call: String)=call.toByteArray(Charsets.UTF_8).joinToString("") {"%02x".format(it)}
 fun latestGpx(call: String): Path? {
  if(!Files.isDirectory(folder)) return null
  val suffix="-${fileKey(call)}.gpx"
  return Files.list(folder).use {paths->paths.filter {it.fileName.toString().endsWith(suffix)}
   .max(compareBy<Path> {it.fileName.toString()}).orElse(null)}
 }
 fun record(s:AprsStation) {
  if(!setting("logging.csv").toBoolean() && !setting("logging.gpx").toBoolean())return
  val calls=names(setting("logging.calls"));if(calls.isNotEmpty() && s.callsign !in calls)return
  val day=Instant.ofEpochMilli(s.heardAtMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()
  Files.createDirectories(folder)
  if(cleaned!=day) {
   val cutoff=System.currentTimeMillis()-setting("logging.retention_days").toLong()*86400000
   Files.list(folder).use {stream->stream.filter {it.fileName.toString().matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}-.+\\.(gpx|csv)"))}.forEach {if(Files.getLastModifiedTime(it).toMillis()<cutoff)Files.deleteIfExists(it)}}
   cleaned=day
  }
  // Encode arbitrary object names rather than permit paths or collisions.
  val name=fileKey(s.callsign)
  val stem="$day-$name"
  val time=Instant.ofEpochMilli(s.heardAtMillis).toString()
  if(setting("logging.csv").toBoolean()) {
   val path=folder.resolve("$stem.csv")
   if(!Files.exists(path))Files.writeString(path,"time,callsign,latitude,longitude,ambiguity\n",StandardOpenOption.CREATE_NEW)
   Files.writeString(path,"$time,\"${s.callsign.replace("\"","\"\"")}\",${s.latitude},${s.longitude},${s.ambiguityDigits}\n",StandardOpenOption.APPEND)
  }
  if(setting("logging.gpx").toBoolean()) {
   val path=folder.resolve("$stem.gpx");val tail=(MobileRadio.gpxEnd+"\n").toByteArray()
   val xml=s.callsign.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
   if(!Files.exists(path))Files.writeString(path,"<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"APRSERJ64\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>$xml</name><trkseg>"+String(tail),StandardOpenOption.CREATE_NEW)
   RandomAccessFile(path.toFile(),"rw").use {file->
    require(file.length()>=tail.size)
    file.seek(file.length()-tail.size);val old=ByteArray(tail.size);file.readFully(old);require(old.contentEquals(tail)) {"Incomplete GPX log"}
    file.seek(file.length()-tail.size)
    file.write(MobileRadio.gpxPoint(BeaconPosition(AprsSpatial.fix(s),altitudeMeters=s.altitudeMeters)).toByteArray())
    file.write(tail)
   }
  }
 }
}
