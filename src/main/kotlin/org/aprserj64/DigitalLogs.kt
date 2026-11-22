// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Properties
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.osmand.shared.aprs.*

data class DigitalSpot(val call:String,val grid:String,val time:Long,val mode:String,val frequencyMhz:Double?) {
 fun position(now:Long):BeaconPosition {
  val g=grid.uppercase();require(Regex("[A-R]{2}[0-9]{2}([A-X]{2})?").matches(g))
  var lon=-180+(g[0]-'A')*20+(g[2]-'0')*2.0;var lat=-90+(g[1]-'A')*10+(g[3]-'0')*1.0
  if(g.length==6){lon+=(g[4]-'A')/12.0+1.0/24;lat+=(g[5]-'A')/24.0+1.0/48}else{lon+=1;lat+=0.5}
  return BeaconPosition(AprsFix(lat,lon,now))
 }
}
/** Decode only received messages with an explicit sender and Maidenhead location. */
object DigitalLogs {
 private val grid=Regex("[A-R]{2}[0-9]{2}([A-X]{2})?")
 fun parse(mode:String,line:String,frequencyMhz:Double?=null):DigitalSpot?=runCatching {
  var frequency=frequencyMhz
  val time:Long;val exchange:String
  when(mode) {
   "JT65"->{val fields=csv(line);require(fields.size>=9)
    time=LocalDateTime.parse(fields[0]+"T"+fields[1]).toInstant(ZoneOffset.UTC).toEpochMilli()
    frequency=fields[2].toDoubleOrNull()?.div(1000000)?.takeIf {it>0};exchange=fields[8]}
   "FT8"->{val f=line.trim().split(Regex("\\s+"));require(f.size>=9 && f[2]=="Rx" && f[3]=="FT8")
    time=LocalDateTime.parse(f[0],DateTimeFormatter.ofPattern("yyMMdd_HHmmss")).toInstant(ZoneOffset.UTC).toEpochMilli()
    frequency=f[1].toDouble();exchange=f.drop(7).joinToString(" ")}
   "JS8Call"->{require(line.length>19 && !line.contains("Transmitting"))
    time=LocalDateTime.parse(line.take(19),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(ZoneOffset.UTC).toEpochMilli()
    exchange=Regex("([A-Z0-9/]{3,9}):\\s+(.+)$").find(line)?.let {it.groupValues[1]+" "+it.groupValues[2]}?:return null}
   else->return null
  }
  val words=exchange.trim().uppercase().split(Regex("\\s+"));val locator=words.lastOrNull()?.takeIf {grid.matches(it) && it!="RR73"}?:return null
  val call=if(mode=="JS8Call")words.first() else words.getOrNull(words.lastIndex-1)?:return null
  require(call.length in 3..9 && Regex("[A-Z0-9/]+").matches(call) && call.any {it.isDigit()} && call.any {it.isLetter()} && !grid.matches(call))
  DigitalSpot(call,locator,time,mode,frequency)
 }.getOrNull()
 private fun csv(line:String):List<String> {
  val result=mutableListOf<String>();val value=StringBuilder();var quoted=false;var i=0
  while(i<line.length){val c=line[i++];if(c=='"'){if(quoted && i<line.length && line[i]=='"'){value.append('"');i++}else quoted=!quoted}else if(c==','&&!quoted){result.add(value.toString());value.clear()}else value.append(c)}
  require(!quoted);result.add(value.toString());return result
 }
 fun read(path:Path,mode:String,now:Long,ageSeconds:Int,maximum:Int):List<DigitalSpot> {
  if(!Files.isRegularFile(path))return emptyList()
  // Bounded tail scan handles long-running ALL.TXT logs without reading them into memory.
  val text=java.io.RandomAccessFile(path.toFile(),"r").use {f->val offset=(f.length()-4_000_000).coerceAtLeast(0);f.seek(offset);val bytes=ByteArray((f.length()-offset).toInt());f.readFully(bytes);String(bytes,Charsets.UTF_8).let {if(offset>0)it.substringAfter('\n',"") else it}}
  var frequency:Double?=null;val spots=linkedMapOf<String,DigitalSpot>()
  for(line in text.lineSequence()) {
   if(mode=="JS8Call")Regex(" ([0-9]+\\.[0-9]+) MHz\\s+JS8").find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.let {frequency=it}
   val spot=parse(mode,line,frequency)?:continue
   if(now-spot.time !in 0..ageSeconds*1000L)continue
   if((spots[spot.call]?.time?:0)<=spot.time){spots.remove(spot.call);spots[spot.call]=spot}
   while(spots.size>maximum)spots.remove(spots.keys.first())
  }
  return spots.values.toList()
 }
}
class DigitalObjectFeed(private val home:Path) {
 private val sent=linkedMapOf<String,Pair<DigitalSpot,Long>>()
 val stations=MutableStateFlow<List<AprsStation>>(emptyList())
 private var lastScan=0L
 private var owner=""
 private fun journal()=home.resolve("data/digital-${owner.replace(Regex("[^A-Z0-9-]"),"_")}.json")
 private fun save() {
  val path=journal();Files.createDirectories(path.parent)
  val text=JsonArray(sent.map {(name,value)->buildJsonObject {put("name",name);put("call",value.first.call);put("grid",value.first.grid);put("time",value.first.time);put("mode",value.first.mode);put("sent",value.second)}}).toString()
  val temp=Files.createTempFile(path.parent,"digital-",".tmp")
  try{Files.writeString(temp,text);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING)}finally{Files.deleteIfExists(temp)}
 }
 private fun activate(account:String) {
  if(owner==account)return
  val path=home.resolve("data/digital-${account.replace(Regex("[^A-Z0-9-]"),"_")}.json")
  val loaded=linkedMapOf<String,Pair<DigitalSpot,Long>>()
  if(Files.exists(path)) {
   require(Files.size(path)<1000000){"Digital object journal is too large"}
   val rows=Json.parseToJsonElement(Files.readString(path)).jsonArray;require(rows.size<=1500)
   for(row in rows){val v=row.jsonObject;val spot=DigitalSpot(v.getValue("call").jsonPrimitive.content,v.getValue("grid").jsonPrimitive.content,v.getValue("time").jsonPrimitive.long,v.getValue("mode").jsonPrimitive.content,null);spot.position(0);loaded[v.getValue("name").jsonPrimitive.content]=spot to v.getValue("sent").jsonPrimitive.long}
  }
  sent.clear();sent.putAll(loaded);owner=account;lastScan=0
 }
 private fun name(spot:DigitalSpot)=if(spot.call.length<=6)spot.call+when(spot.mode){"FT8"->"-FT";"JT65"->"-JT";else->"-JS"}else spot.call
 suspend fun tick(account:String,p:Properties,now:Long,send:suspend(String,Boolean,Boolean,List<String>)->Boolean) {
  activate(account)
  if(now-lastScan<10000)return;lastScan=now
  val active=linkedMapOf<String,DigitalSpot>()
  if(p.getProperty("digital.enabled").toBoolean())for((mode,key) in listOf("JT65" to "jt65_file","JS8Call" to "js8_file","FT8" to "ft8_file")) {
   val file=p.getProperty("digital.$key");if(file.isBlank())continue
   for(spot in DigitalLogs.read(home.resolve(file),mode,now,p.getProperty("digital.maximum_age_seconds").toInt(),p.getProperty("digital.maximum_objects").toInt())) {
    if((active[name(spot)]?.time?:0)<spot.time)active[name(spot)]=spot
   }
  }
  stations.value=active.map {(name,spot)->val fix=spot.position(spot.time).fix;AprsStation(name,fix.latitude,fix.longitude,'/','r',"${spot.mode} ${spot.grid}",spot.time)}
  if(!p.getProperty("digital.transmit").toBoolean() || !p.getProperty("beacon.enabled").toBoolean())return
  val route=p.getProperty("digital.route");val ports=names(p.getProperty("digital.ports"))
  val interval=p.getProperty("digital.interval_seconds").toLong()*1000
  for((name,spot) in active) {
   if(now-(sent[name]?.second?:0)<interval)continue
   val comment="${spot.mode} ${spot.grid}"+(spot.frequencyMhz?.let {" ${java.lang.String.format(java.util.Locale.ROOT,"%.3f",it)}MHz"}?:"")
   if(send(BeaconPackets.entity(account,name,false,true,spot.position(now),BeaconFormat(table='/',symbol='r'),comment),route!="rf",route!="is",ports)){sent[name]=spot to now;save()}
  }
  for((name,previous) in sent.toMap())if(name !in active) {
   if(send(BeaconPackets.entity(account,name,false,false,previous.first.position(now),BeaconFormat(table='/',symbol='r'),"Expired ${previous.first.mode}"),route!="rf",route!="is",ports)){sent.remove(name);save()}
  }
 }
}
