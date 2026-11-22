// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import com.github.amsacode.predict4java.*
import java.nio.file.*
import java.net.URI
import java.net.http.*
import java.time.*
import java.util.Date
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.osmand.shared.aprs.*
import kotlin.math.*

data class OrbitPosition(val name:String,val latitude:Double,val longitude:Double,val altitudeKm:Double,val azimuth:Double,val elevation:Double,val rangeKm:Double,val rateKmSecond:Double,val epoch:Long)
data class OrbitPass(val name:String,val rise:Long,val set:Long,val maximumElevation:Double)
class SatelliteTracking(private val model:AppModel,private val scope:CoroutineScope) {
 private data class Orbit(val name:String,val tle:TLE,val epoch:Long)
 @Volatile private var orbits=listOf<Orbit>()
 val positions=MutableStateFlow<List<OrbitPosition>>(emptyList());val passes=MutableStateFlow<List<OrbitPass>>(emptyList());val status=MutableStateFlow("")
 private var loading:Job?=null
 init {scope.launch(Dispatchers.Default){while(isActive){
  if(model.settings.getProperty("satellites.enabled").toBoolean())runCatching{update()}.onFailure{status.value="Unable to propagate satellite orbit"} else positions.value=emptyList()
  delay(5000)
 }}}
 fun reload(){loading?.cancel();loading=scope.launch(Dispatchers.IO){runCatching {
  val path=model.directory.resolve(model.settings.getProperty("satellites.file"));if(!Files.exists(path)){orbits=emptyList();positions.value=emptyList();status.value="Load or download a TLE file";return@launch}
  orbits=parse(Files.readString(path));status.value="${orbits.size} orbital element sets loaded";if(model.settings.getProperty("satellites.enabled").toBoolean())update()
 }.onFailure{orbits=emptyList();positions.value=emptyList();status.value=it.message?:"Unable to load TLE file"}}}
 suspend fun download()=withContext(Dispatchers.IO){
  val request=HttpRequest.newBuilder(URI(model.settings.getProperty("satellites.url"))).timeout(Duration.ofSeconds(30)).GET().build()
  val response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build().send(request,HttpResponse.BodyHandlers.ofInputStream())
  require(response.statusCode()==200){"TLE download HTTP ${response.statusCode()}"}
  val bytes=response.body().use {it.readNBytes(2_000_001)};require(bytes.size<=2_000_000){"TLE download too large"};val text=String(bytes,Charsets.US_ASCII);parse(text)
  val target=model.directory.resolve(model.settings.getProperty("satellites.file"));Files.createDirectories(target.toAbsolutePath().parent);val temp=Files.createTempFile(target.toAbsolutePath().parent,"tle-",".tmp")
  try{Files.writeString(temp,text);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING)}finally{Files.deleteIfExists(temp)};reload()
 }
 private fun parse(text:String):List<Orbit> {
  require(text.length<=2_000_000);val lines=text.lineSequence().map {it.trimEnd()}.filter {it.isNotBlank()}.toList();val result=mutableListOf<Orbit>();var i=0
  while(i<lines.size){val name=if(lines[i].startsWith("1 "))"SAT-"+lines[i].substring(2,7).trim() else lines[i++].removePrefix("0 ")
   require(i+1<lines.size);val a=lines[i++];val b=lines[i++];require(a.startsWith("1 ") && b.startsWith("2 ") && a.length>=69 && b.length>=69 && a.substring(2,7)==b.substring(2,7)){"Invalid TLE pair"}
   for(line in listOf(a,b)){val sum=line.take(68).sumOf {if(it.isDigit())it-'0' else if(it=='-')1 else 0};require(sum%10==line[68]-'0'){"TLE checksum mismatch"}}
   val yy=a.substring(18,20).toInt();val days=a.substring(20,32).toDouble();val year=if(yy<57)2000+yy else 1900+yy
   val epoch=LocalDate.of(year,1,1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()+((days-1)*86400000).toLong()
   result.add(Orbit(name,TLE(arrayOf(name,a,b)),epoch));require(result.size<=5000)
  };require(result.isNotEmpty());return result
 }
 private fun selected(now:Long):List<Orbit> {
  val names=names(model.settings.getProperty("satellites.names"));val age=model.settings.getProperty("satellites.maximum_age_days").toLong()*86400000
  return orbits.filter {now-it.epoch in -86400000..age && (names.isEmpty() || it.name in names)}.take(if(names.isEmpty())20 else 50)
 }
 private fun ground():GroundStationPosition {
  val p=model.beaconState.value.position;val fix=p?.takeIf {it.current}?.fix
  return GroundStationPosition(fix?.latitude?:model.settings.getProperty("position.latitude").toDouble(),fix?.longitude?:model.settings.getProperty("position.longitude").toDouble(),p?.altitudeMeters?:0.0)
 }
 private fun update(){val now=System.currentTimeMillis();val ground=ground();positions.value=selected(now).map {o->val s=SatelliteFactory.createSatellite(o.tle).getPosition(ground,Date(now));OrbitPosition(o.name,Math.toDegrees(s.latitude),((Math.toDegrees(s.longitude)+540)%360)-180,s.altitude,Math.toDegrees(s.azimuth),Math.toDegrees(s.elevation),s.range,s.rangeRate,o.epoch)};if(orbits.isNotEmpty() && positions.value.isEmpty())status.value="No selected satellites with current TLEs"}
 suspend fun predict()=withContext(Dispatchers.Default){
  val start=System.currentTimeMillis();val end=start+model.settings.getProperty("satellites.prediction_hours").toLong()*3600000;val g=ground();val result=mutableListOf<OrbitPass>()
  selected(start).forEach {o->
   val satellite=SatelliteFactory.createSatellite(o.tle)
   result+=orbitPasses(o.name,start,end){t->Math.toDegrees(satellite.getPosition(g,Date(t)).elevation)}
  };passes.value=result.sortedBy {it.rise}.take(500)
 }
 fun geometry(now:Long)=positions.value.map {o->val center=AprsFix(o.latitude,o.longitude,now);val angle=acos(6371.0/(6371+o.altitudeKm.coerceAtLeast(0.0)));TacticalGeometry("satellite-${o.name}",AprsShape(AprsSpatial.circle(center,angle*6371000),true,"#8478d6"))}
}

/** Refine bracketed horizon crossings and pass peaks to one second. */
internal suspend fun orbitPasses(name:String,start:Long,end:Long,elevation:(Long)->Double):List<OrbitPass> {
 val result=mutableListOf<OrbitPass>()
   fun crossing(low:Long,high:Long,rising:Boolean):Long {
    var a=low;var b=high
    while(b-a>1000){val mid=(a+b)/2;if((elevation(mid)>0)==rising)b=mid else a=mid}
    return (a+b)/2
   }
   fun peak(a:Long,b:Long):Double {
    var left=a;var right=b
    while(right-left>1000){val x=left+(right-left)/3;val y=right-(right-left)/3;if(elevation(x)<elevation(y))left=x else right=y}
    return maxOf(elevation(a),elevation(b),elevation((left+right)/2))
   }
   var previous=start;var up=elevation(start)>0;var rise:Long?=if(up)start else null;var t=start+10000
   while(t<=end){currentCoroutineContext().ensureActive();val next=elevation(t)>0
    if(next&&!up)rise=crossing(previous,t,true)
    if(!next&&up){val set=crossing(previous,t,false);val began=rise?:start;result.add(OrbitPass(name,began,set,peak(began,set)));rise=null}
    up=next;previous=t;t+=10000
   }
   rise?.let {result.add(OrbitPass(name,it,end,peak(it,end)))}
 return result
}
