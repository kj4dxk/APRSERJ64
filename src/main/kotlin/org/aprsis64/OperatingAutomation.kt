// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*

/** Current-state automation. Disconnected timer events are never queued for later delivery. */
class OperatingAutomation(private val model:AppModel,parent:CoroutineScope,private val radio:()->RadioPorts?):AutoCloseable {
 private val scope=CoroutineScope(parent.coroutineContext+SupervisorJob(parent.coroutineContext[Job]))
 private val rx=AtomicLong();private val tx=AtomicLong();private val started=System.currentTimeMillis()
 @Volatile private var active=started
 private val replies=ConcurrentHashMap<String,Long>()
 private val due=mutableMapOf<String,Long>()
 private val listener=AWTEventListener {active=System.currentTimeMillis()}
 private val sequenceFile=model.directory.resolve("data/telemetry-sequence.txt")
 private var sequence=runCatching {Files.readString(sequenceFile).trim().toInt().coerceIn(0,999)}.getOrDefault(0)
 init {
  runCatching {Toolkit.getDefaultToolkit().addAWTEventListener(listener,AWTEvent.KEY_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK)}
  scope.launch {while(isActive) {delay(1000);try {tick(System.currentTimeMillis())}catch(c:CancellationException){throw c}catch(_:Exception){model.error.value="Unable to complete scheduled transmission; check settings and disk space"}}}
 }
 fun observe(direction:String,line:String,now:Long=System.currentTimeMillis()) {
  if(direction=="TX")tx.incrementAndGet() else rx.incrementAndGet()
  if(direction!="RX" || model.demo.value || !model.settings.getProperty("messaging.auto_reply").toBoolean())return
  val m=AprsMessages.parse(line) ?: return
  if(m.recipient!=model.account.value || m.sender==model.account.value || m.acknowledgement!=null || m.text.startsWith("AA:") || m.text.startsWith('?'))return
  if(now-active<model.settings.getProperty("messaging.inactive_minutes").toLong()*60000)return
  synchronized(replies) {
   if(now-(replies[m.sender]?:0)<model.settings.getProperty("messaging.reply_minutes").toLong()*60000)return
   if(replies.size>=2000)replies.entries.removeAll {now-it.value>86400000}
   if(replies.size>=2000)return
   replies[m.sender]=now
  }
  val call=model.account.value
  scope.launch {if(model.account.value==call)model.database.enqueue(call,m.sender,"AA: "+model.settings.getProperty("messaging.reply_text"))}
 }
 private suspend fun tick(now:Long) {
  if(model.demo.value || !AprsProtocol.validCallsign(model.account.value))return
  val output=radio()?:return
  if(!output.available())return
  val p=model.settings
  suspend fun send(key:String,seconds:Int,line:String,route:String):Boolean {
   if(now-(due[key]?:0)<seconds*1000L)return false
   val sent=output.broadcast(line,route!="rf",route!="is")
   // Avoid retrying a failed write every tick; retain no stale packet.
   due[key]=if(sent)now else now-seconds*1000L+30000
   return sent
  }
  if(p.getProperty("bulletins.enabled").toBoolean()) names(p.getProperty("bulletins.entries")).forEach {entry->
   val b=ScheduledBulletin.parse(entry)
   send("bulletin:$entry",b.seconds,AprsServices.bulletin(model.account.value,b.slot,b.group,b.text),b.route)
  }
  if(p.getProperty("telemetry.enabled").toBoolean()) {
   val channels=names(p.getProperty("telemetry.channels")).map {TelemetryChannel.parse(it)}
   val route=p.getProperty("telemetry.route")
   val call=model.account.value
   val definitions=TelemetryPackets.definitions(call,channels,names(p.getProperty("telemetry.bit_names")),p.getProperty("telemetry.title"))
   for((i,line) in definitions.withIndex())send("definitions:$call:$i:$line",p.getProperty("telemetry.definitions_seconds").toInt(),line,route)
   val external=if(channels.any {it.source.startsWith("input:")})LocalSensors.telemetry(model,now) else emptyMap()
   val values=channels.map {c-> when(c.source) {
    "rx"->rx.get().toDouble();"tx"->tx.get().toDouble();"stations"->model.stations.value.size.toDouble()
    "speed"->model.beaconState.value.position?.speedKnots?:0.0;"uptime"->(now-started)/60000.0;else->if(c.source.startsWith("input:"))external[c.source.removePrefix("input:")]?:error("Missing telemetry input") else c.source.toDouble()
   }.toInt().coerceIn(0,255)}
   if(send("telemetry:$call",p.getProperty("telemetry.interval_seconds").toInt(),TelemetryPackets.sample(call,sequence,values,p.getProperty("telemetry.bits")),route)) {
    sequence=(sequence+1)%1000
    val temporary=Files.createTempFile(sequenceFile.parent,"telemetry-",".tmp")
    try {Files.writeString(temporary,sequence.toString());Files.move(temporary,sequenceFile,StandardCopyOption.REPLACE_EXISTING)}finally {Files.deleteIfExists(temporary)}
   }
  }
  if(p.getProperty("weatherstation.enabled").toBoolean()) {
   runCatching {send("local-weather",p.getProperty("weatherstation.interval_seconds").toInt(),LocalSensors.weather(model,now),p.getProperty("weatherstation.route"))}.onFailure {model.error.value=it.message?:"Weather input unavailable"}
  }
  if(p.getProperty("qru.advertise").toBoolean() && p.getProperty("tactical.qru_enabled").toBoolean() && p.getProperty("beacon.enabled").toBoolean()) {
   val position=model.beaconState.value.position?.takeIf {it.current} ?: return
   val groups=model.entities.filter {it.getProperty("enabled").toBoolean() && it.getProperty("alive").toBoolean()}.map {it.getProperty("qru_group","")}.filter {it.isNotEmpty()}.distinct()
   if(groups.isNotEmpty()) {
    val comment="RNG"+(p.getProperty("tactical.qru_radius_km").toDouble()/1.609344).toInt().coerceIn(0,9999).toString().padStart(4,'0')+" "+groups.joinToString(" ")
    val name="QRU"+model.account.value.substringBefore('-').take(6)
    send("qru:${model.account.value}",p.getProperty("qru.interval_seconds").toInt(),BeaconPackets.entity(model.account.value,name,false,true,position,BeaconFormat(table='\\',symbol='?',timestamp=BeaconTimestamp.DHM),comment.take(120)),p.getProperty("qru.route"))
   }
  }
 }
 override fun close(){scope.cancel();runCatching {Toolkit.getDefaultToolkit().removeAWTEventListener(listener)}}
}

object TelemetryPackets {
 fun sample(call:String,sequence:Int,values:List<Int>,bits:String):String {
  require(AprsProtocol.validCallsign(call) && sequence in 0..999 && values.size==5 && values.all {it in 0..255} && bits.matches(Regex("[01]{8}")))
  return "$call>APRS,TCPIP*:T#${sequence.toString().padStart(3,'0')},${values.joinToString(",") {it.toString().padStart(3,'0')}},$bits\r\n"
 }
 fun definitions(call:String,channels:List<TelemetryChannel>,bits:List<String>,title:String):List<String> {
  require(channels.size==5 && bits.size==8)
  val texts=listOf("PARM."+(channels.map {it.name}+bits).joinToString(","),"UNIT."+channels.joinToString(",") {it.unit},
   "EQNS."+channels.flatMap {listOf(it.a,it.b,it.c)}.joinToString(",") {if(it==it.toLong().toDouble())it.toLong().toString() else it.toString()},"BITS.11111111,$title")
  return texts.map {AprsMessages.unnumbered(call,call,it)}
 }
}
