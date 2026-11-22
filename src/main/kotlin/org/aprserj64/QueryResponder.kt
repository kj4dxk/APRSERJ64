// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.util.Properties
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*

/** Runs alongside chat; addressed chat ACKs remain owned by ChatSession. */
internal class QueryResponder(private val model:AppModel,private val scope:CoroutineScope,
 private val radio:()->RadioPorts?) {
 private val standard=StandardQueries(model,scope,radio)
 private val last=mutableMapOf<String,Long>()
 private val seen=mutableMapOf<String,Long>()
 private val running=mutableSetOf<String>()
 fun receive(port:String,line:String) {
  if(standard.receive(port,line))return
  if(model.demo.value) return
  val message=AprsMessages.parse(line) ?: return
  if(message.sender==model.account.value || message.acknowledgement!=null) return
  val settings=Properties().apply {putAll(model.settings)}
  val owner=model.account.value
  val epoch=model.settingsRevision.value
  val direct=message.recipient==model.account.value
  val qru=settings.getProperty("tactical.qru_enabled").toBoolean() && (direct || message.recipient==settings.getProperty("tactical.qru_address"))
  val dx=direct && settings.getProperty("tactical.dx_query").toBoolean() && message.text.trim().uppercase()=="?DX"
  if(!qru && !dx) return
  val query=if(qru) QruObjects.query(message.text,settings.getProperty("tactical.qru_radius_km").toInt(),settings.getProperty("tactical.qru_maximum_radius_km").toInt()) else null
  if(!dx && query==null) return
  val groups=model.entities.map {it.getProperty("qru_group","")}.filter {it.isNotEmpty()}.toSet()
  if(!dx && query!!.group!="INFO" && query.group !in groups) return
  val now=System.currentTimeMillis()
  synchronized(last) {
   seen.entries.removeAll {now-it.value>3600000}
   val identity=message.id?.let {"${message.sender}:$it"}
   if(identity!=null && identity in seen) return
   if(message.sender in running || now-(last[message.sender] ?: 0)<settings.getProperty("kiosk.minimum_seconds").toLong()*1000) return
   if(last.size>=1000) last.entries.removeAll {now-it.value>86400000}
   if(last.size>=1000) return
   if(identity!=null) {if(seen.size>=1000) return;seen[identity]=now}
   last[message.sender]=now;running.add(message.sender)
  }
  scope.launch {
   try {
    if(dx) {model.send(message.sender,model.tactical.summary() ?: "No direct-heard RF DX records");return@launch}
    if(!settings.getProperty("beacon.enabled").toBoolean()) return@launch
    val requester=model.stations.value.firstOrNull {it.callsign==message.sender && now-it.heardAtMillis in 0..settings.getProperty("tactical.qru_position_age_seconds").toLong()*1000}
    if(requester==null) {if(direct)model.send(message.sender,"Send a fresh position before requesting nearby objects");return@launch}
    val entities=model.entities.filter {it.getProperty("enabled").toBoolean() && it.getProperty("alive").toBoolean() && it.getProperty("qru_group","").isNotBlank()}
    val configuration=BeaconConfiguration(settings,entities)
    val candidates=entities.map {e->
     val fix=if(e.getProperty("source")=="follow") model.beaconState.value.position?.takeIf {it.current}?.fix else configuration.entityPosition(e,now).fix
     fix?.let {QueryObject(e.getProperty("name"),e.getProperty("qru_group"),it)}
    }.filterNotNull()
    val nearby=QruObjects.nearby(candidates,AprsSpatial.fix(requester),query!!,if(query.group=="INFO") 32 else settings.getProperty("tactical.qru_maximum_objects").toInt())
    if(query.group=="INFO") {
     val text=nearby.groupingBy {it.group}.eachCount().entries.joinToString(" ") {"${it.key}(${it.value})"}.ifEmpty {"No nearby object groups"}
     text.chunked(67).forEach {model.send(message.sender,it)}
     return@launch
    }
    var sent=0
    for(candidate in nearby) {
     if(System.currentTimeMillis()-requester.heardAtMillis>settings.getProperty("tactical.qru_position_age_seconds").toLong()*1000)break
     if(model.demo.value || model.account.value!=owner || model.settingsRevision.value!=epoch || !model.settings.getProperty("tactical.qru_enabled").toBoolean() || !model.settings.getProperty("beacon.enabled").toBoolean()) break
     val e=model.entities.firstOrNull {it.getProperty("name")==candidate.name && it.getProperty("enabled").toBoolean() && it.getProperty("alive").toBoolean() && it.getProperty("qru_group")==query.group} ?: continue
     val current=BeaconConfiguration(model.settings,listOf(e))
     val position=if(e.getProperty("source")=="follow") model.beaconState.value.position?.takeIf {it.current} ?: continue else current.entityPosition(e,System.currentTimeMillis())
     if(AdaptiveBeaconPolicy.distance(AprsSpatial.fix(requester),position.fix)>query.radiusKm*1000.0) continue
     val output=radio() ?: break
     if(port=="APRS-IS") {
      if(e.getProperty("internet").toBoolean() && output.queryItem(message.sender,QruObjects.item(model.account.value,candidate.name,position,current.entityFormat(e),e.getProperty("comment")))) {
       sent++
       if(e.getProperty("shape","point")!="point")output.broadcast(BeaconPackets.entity(model.account.value,candidate.name,false,true,position,current.entityFormat(e),ObjectGeometry.comment(e)),true,false)
      }
     }else if(e.getProperty("radio").toBoolean()) {
      val configured=names(e.getProperty("ports"));if(configured.isNotEmpty() && port !in configured)continue
      val packet=BeaconPackets.entity(model.account.value,candidate.name,false,true,position,current.entityFormat(e),ObjectGeometry.comment(e))
      if(output.broadcast(packet,false,true,listOf(port),e.getProperty("rf_path").ifBlank {null}))sent++
     }
     delay(settings.getProperty("tactical.qru_spacing_seconds").toLong()*1000)
    }
    model.send(message.sender,"Sent $sent ${query.group} objects within ${query.radiusKm}km")
   }catch(cancelled:CancellationException){throw cancelled}
   catch(_:Exception){model.error.value="Unable to complete query response"}
   finally {synchronized(last){running.remove(message.sender)}}
  }
 }
}
