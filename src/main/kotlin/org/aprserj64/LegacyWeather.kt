// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.RandomAccessFile
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.aprsfoundation.core.*

/** NWS-TTTTT / NWS_TTTTT and APRS warning objects; no network downloads. */
class LegacyWeather(private val home:Path,parent:CoroutineScope,private val setting:(String)->String):AutoCloseable {
 val alerts=MutableStateFlow<List<WeatherAlert>>(emptyList())
 val status=MutableStateFlow("")
 private val input=Channel<Pair<String,Long>>(1024)
 private var cacheKey="";private var boundaries=emptyMap<String,JsonObject>()
 private val worker=parent.launch(Dispatchers.IO) {for((line,now) in input)try {
  if(!setting("legacyweather.enabled").toBoolean()){alerts.value=emptyList();continue}
  val parsed=parse(line,now)?:continue
  val key=setting("legacyweather.shapefiles")+setting("legacyweather.code_field")
  if(cacheKey!=key) {
   val loaded=mutableMapOf<String,JsonObject>()
   var failed=0
   names(setting("legacyweather.shapefiles")).forEach {name->runCatching {ShapeBoundaries.read(home.resolve(name).normalize(),setting("legacyweather.code_field"))}.onSuccess {loaded.putAll(it)}.onFailure {failed++}}
   boundaries=loaded;cacheKey=key;status.value="${loaded.size} local warning boundaries"+if(failed>0)"; $failed files could not be read (WGS84 .shp/.dbf required)" else ""
  }
  val geometry=parsed.geometry?:parsed.zones.mapNotNull {boundaries[it]}.takeIf {it.isNotEmpty()}?.let {buildJsonObject {put("type","GeometryCollection");put("geometries",JsonArray(it))}}
  alerts.value=(alerts.value.filter {it.id!=parsed.id && Instant.parse(it.expires).toEpochMilli()>now}+parsed.copy(geometry=geometry)).filter {Instant.parse(it.expires).toEpochMilli()>now}.takeLast(200)
 }catch(c:CancellationException){throw c}catch(_:Exception){status.value="Cannot read warning or local shapefile; check the WGS84 .shp/.dbf files"}}
 fun offer(line:String,time:Long=System.currentTimeMillis()){input.trySend(line to time)}
 override fun close(){input.close();worker.cancel()}
 companion object {
  fun zones(text:String):List<String> {
   val result=linkedSetOf<String>();var prefix=""
   for(token in text.split('-',',')) {
    if(token.isBlank())continue
    val m=Regex("^([A-Z]{2}[CZ])?(\\d{1,3})(?:>(\\d{1,3}))?$").matchEntire(token.trim())?:continue
    if(m.groupValues[1].isNotEmpty())prefix=m.groupValues[1]
    if(prefix.isEmpty())continue
    val start=m.groupValues[2].toInt();val end=m.groupValues[3].toIntOrNull()?:start
    require(end in start..999 && result.size+end-start<2000)
    for(i in start..end)result.add(prefix+i.toString().padStart(3,'0'))
   }
   return result.toList()
  }
  fun expiry(text:String,now:Long):Long? {
   val stamp=Regex("^(\\d{2})(\\d{2})(\\d{2})z").find(text)?:return null
   val current=Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC)
   return (-1L..1L).mapNotNull {offset->runCatching {current.plusMonths(offset).withDayOfMonth(stamp.groupValues[1].toInt()).withHour(stamp.groupValues[2].toInt()).withMinute(stamp.groupValues[3].toInt()).withSecond(0).withNano(0).toInstant().toEpochMilli()}.getOrNull()}.minByOrNull {kotlin.math.abs(it-now)}
  }
  fun parse(line:String,now:Long):WeatherAlert? = runCatching {
   val p=RadioPacket.parse(line)?:return null
   if(p.payload.startsWith(':') && p.payload.length>11 && p.payload[10]==':') {
    val recipient=p.payload.substring(1,10).trim()
    if(!recipient.startsWith("NWS-") && !recipient.startsWith("NWS_"))return null
    val text=p.payload.substring(11);val parts=text.substringBefore('{').split(',',limit=3);require(parts.size==3)
    val expires=expiry(parts[0],now)?:return null
    val code=text.substringAfter('{',text).take(5)
    WeatherAlert("${p.source}:$code",parts[1],if(recipient.contains("WARN"))"Severe" else "Moderate",parts[1].replace('_',' '),"${p.source}: ${parts[2]}",Instant.ofEpochMilli(expires).toString(),zones=zones(parts[2]))
   }else if(p.payload.startsWith(';') && p.payload.length>=18 && '{' in p.payload) {
    val s=AprsProtocol.parse(line,now)?:return null
    val shape=AprsSpatial.multiline(s)?:return null
    val expires=expiry(p.payload.substring(11,18),now)?:return null
    val geometry=buildJsonObject {put("type","Polygon");putJsonArray("coordinates") {addJsonArray {val points=shape.points.let {if(it.first()!=it.last())it+it.first() else it};points.forEach {addJsonArray {add(it.longitude);add(it.latitude)}}}}}
    WeatherAlert("${p.source}:${s.callsign}",s.comment.substringBefore('}').substringBefore('{'),"Severe",s.callsign,s.comment,Instant.ofEpochMilli(expires).toString(),geometry)
   }else null
  }.getOrNull()
 }
}

/** Read-only ESRI polygon (5/15/25) + DBF reader, bounded and independent of native DLLs. */
object ShapeBoundaries {
 fun read(path:Path,codeField:String):Map<String,JsonObject> {
  require(path.toString().endsWith(".shp",true) && Files.size(path)<=256_000_000)
  val base=path.toString().dropLast(4);val dbf=Path.of("$base.dbf");require(Files.size(dbf)<=64_000_000)
  val codes=RandomAccessFile(dbf.toFile(),"r").use {f->
   val header=ByteArray(32);f.readFully(header);val h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
   val count=h.getInt(4);val headerSize=h.getShort(8).toInt() and 65535;val recordSize=h.getShort(10).toInt() and 65535
   require(count in 0..100000 && headerSize>=33 && recordSize in 2..65535 && headerSize.toLong()+count.toLong()*recordSize<=f.length())
   val fields=mutableMapOf<String,Pair<Int,Int>>();var offset=1
   while(f.filePointer+32<headerSize) {val d=ByteArray(32);f.readFully(d);val name=String(d.copyOfRange(0,11),Charsets.US_ASCII).substringBefore('\u0000');val size=d[16].toInt() and 255;fields[name.uppercase()]=offset to size;offset+=size}
   require(offset<=recordSize)
   f.seek(headerSize.toLong())
   List(count) {
    val row=ByteArray(recordSize);f.readFully(row)
    fun field(name:String)=fields[name]?.let {(start,size)->String(row,start,size,Charsets.US_ASCII).trim()}.orEmpty()
    if(row[0].toInt()==42)"" else field(codeField.uppercase()).ifBlank {
     val state=field("STATE");val zone=field("ZONE");val county=field("FIPS").takeLast(3)
     when {state.length==2 && zone.toIntOrNull()!=null->state+"Z"+zone.padStart(3,'0');state.length==2 && county.toIntOrNull()!=null->state+"C"+county.padStart(3,'0');else->""}
    }
   }
  }
  return RandomAccessFile(path.toFile(),"r").use {f->
   require(f.readInt()==9994 && f.length()>=100);f.seek(100)
   val result=mutableMapOf<String,JsonObject>();var index=0
   while(f.filePointer<f.length()) {
    f.readInt();val size=f.readInt().toLong()*2;require(size in 4..16_000_000 && f.filePointer+size<=f.length() && index<codes.size)
    val bytes=ByteArray(size.toInt());f.readFully(bytes);val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);val type=b.int;val code=codes[index++]
    if(type==0 || !code.matches(Regex("[A-Z]{2}[CZ][0-9]{3}")))continue
    require(type in listOf(5,15,25) && size>=44)
    b.position(36);val parts=b.int;val points=b.int;require(parts in 1..10000 && points in 4..200000 && 44L+parts*4L+points*16L<=size)
    val starts=List(parts){b.int}+points;require(starts.first()==0 && starts.zipWithNext().all {(a,z)->a<z})
    val coordinates=List(points){val lon=b.double;val lat=b.double;AprsFix(lat,lon,0);JsonArray(listOf(JsonPrimitive(lon),JsonPrimitive(lat)))}
    val geometry=buildJsonObject {put("type","Polygon");put("coordinates",JsonArray(starts.zipWithNext().map {(a,z)->JsonArray(coordinates.subList(a,z))}))}
    val old=result[code];result[code]=if(old==null)geometry else buildJsonObject {put("type","GeometryCollection");put("geometries",JsonArray(listOf(old,geometry)))}
   }
   result
  }
 }
}
