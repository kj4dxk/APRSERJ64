// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import kotlinx.coroutines.flow.MutableStateFlow
import net.osmand.shared.aprs.*
import kotlinx.serialization.json.*

data class StationMetadata(val call:String,val time:Long,val rf:Boolean,val messageCapable:Boolean,val platform:String,val frequency:AprsFrequency?,val direct:Boolean=false,val igate:Boolean=false,val telemetry:Boolean=false,val status:Boolean=false,val frequencyIssue:Boolean=false)
class StationViews(private val home:java.nio.file.Path?=null,private val setting:(String)->String?={null}) {
 private val identityCache=linkedMapOf<String,String>()
 private var platformKey="";private var platforms=emptyMap<String,String>();private var custom=emptyMap<String,String>();private var mice=emptyMap<String,String>()
 private fun platform(destination:String,payload:String):String {
  val key=setting("platforms.entries").orEmpty()+"|"+setting("platforms.file").orEmpty()
  if(key!=platformKey){platformKey=key;identityCache.clear()
   custom=names(setting("platforms.entries")?:"[]").associate {it.substringBefore('=') to it.substringAfter('=')}
   val file=setting("platforms.file").orEmpty()
   val data=runCatching {
    val text=if(file.isBlank() || home==null)checkNotNull(javaClass.getResourceAsStream("/tocalls.json")).bufferedReader().use {it.readText()}
     else {val path=home.resolve(file);require(java.nio.file.Files.size(path)<=2_000_000);java.nio.file.Files.readString(path)}
    Json.parseToJsonElement(text).jsonObject
   }.getOrNull()
   fun entries(name:String)=data?.get(name)?.jsonObject?.mapValues {(_,v)->val item=v.jsonObject;listOfNotNull(item["vendor"]?.jsonPrimitive?.content,item["model"]?.jsonPrimitive?.content).joinToString(" ")}.orEmpty()
   platforms=entries("tocalls");mice=entries("mice")
  }
  custom.entries.filter {destination.startsWith(it.key)}.maxByOrNull {it.key.length}?.let {return it.value}
  if(payload.firstOrNull() in listOf('`','\'')) {
   val text=payload.drop(9)
   return mice.entries.sortedByDescending {it.key.count {c->c!=' '}}.firstOrNull {val id=it.key;id.length==2 && (id[0]==' ' || text.firstOrNull()==id[0]) && (id[1]==' ' || text.lastOrNull()==id[1])}?.value?:"Mic-E"
  }
  identityCache[destination]?.let {return it}
  platforms[destination]?.let {return it}
  val result=platforms.entries.filter {entry->
   val pattern=entry.key.map {when(it){'?'->".";'n'->"[0-9]";'*'->".*";else->Regex.escape(it.toString())}}.joinToString("")
   Regex("^$pattern$").matches(destination)
  }.maxByOrNull {it.key.takeWhile {c->c !in "?n*"}.length}?.value?:StationCapabilities.platform(destination)
  if(identityCache.size>=2000)identityCache.remove(identityCache.keys.first());identityCache[destination]=result;return result
 }

 val stations=MutableStateFlow<Map<String,StationMetadata>>(emptyMap())
 @Synchronized fun receive(port:String,line:String,time:Long=System.currentTimeMillis(),rf:Boolean=port!="APRS-IS") {
  val p=RadioPacket.parse(line)?:return
  val s=AprsProtocol.parse(line,time)
  val call=s?.callsign?:p.source
  val previous=stations.value[call]
  val metadata=StationMetadata(call,time,rf || previous?.rf==true,
   StationCapabilities.messageCapable(p.payload) || previous?.messageCapable==true,
   platform(p.destination,p.payload),
   s?.let {StationCapabilities.frequency(it.comment)}?:previous?.frequency,
   rf && p.usedHops==0 && !p.payload.startsWith('}'),
   p.path.any {it.startsWith('q')} && p.source==p.path.lastOrNull() || previous?.igate==true,
   AprsObservations.telemetry(line,time)!=null || previous?.telemetry==true,
   p.payload.startsWith('>') || previous?.status==true,
   s?.comment?.let {"MHz" in it && (StationCapabilities.frequency(it)==null || StationCapabilities.frequency(it)?.tone==null)}?:previous?.frequencyIssue?:false)
  stations.value=(stations.value.filterValues {time-it.time<86400000}+ (call to metadata)).entries.toList().takeLast(5000).associate {it.toPair()}
 }
}

internal fun stationKind(station:AprsStation,kind:String,metadata:Map<String,StationMetadata>,objects:Set<String>)=when(kind) {
 "weather"->station.symbol=='_';"moving"->(AprsSpatial.details(station).speedKnots?:0.0)>0
 "objects"->station.callsign in objects;"rf"->metadata[station.callsign]?.rf==true
 "message-capable"->metadata[station.callsign]?.messageCapable==true;"frequency"->metadata[station.callsign]?.frequency!=null
 "frequency-issues"->metadata[station.callsign]?.frequencyIssue==true;"telemetry"->metadata[station.callsign]?.telemetry==true
 "status"->metadata[station.callsign]?.status==true;"altitude"->AprsSpatial.details(station).altitudeMeters!=null
 "direct-rf"->metadata[station.callsign]?.direct==true;"igates"->metadata[station.callsign]?.igate==true
 "ambiguous"->station.ambiguityDigits>0;"mic-e"->station.micEStatus!=null;else->true
}
