// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import kotlinx.coroutines.flow.MutableStateFlow
import net.osmand.shared.aprs.*

data class AppNotice(val time:Long,val text:String)
class ActivityAlerts(private val home:java.nio.file.Path=java.nio.file.Path.of("."),private val setting:(String)->String):AutoCloseable {
 private var tray:java.awt.TrayIcon?=null
 fun configure() {
  if(!setting("alerts.desktop").toBoolean()){close();return}
  if(tray==null && java.awt.SystemTray.isSupported()) runCatching {
   val traySize=java.awt.SystemTray.getSystemTray().trayIconSize.width.coerceAtLeast(1)
   val image=StationSymbols.trayIcon(traySize)
   val icon=java.awt.TrayIcon(image,"APRSIS64");icon.isImageAutoSize=true
   java.awt.SystemTray.getSystemTray().add(icon);tray=icon
  }
 }
 val notices=MutableStateFlow<List<AppNotice>>(emptyList())
 private val seen=linkedMapOf<String,Long>()
 private var lastSound=0L
 private val watched=linkedMapOf<String,Long>()
 private val missing=mutableSetOf<String>()
 private fun sound(kind:String) {
  val now=System.currentTimeMillis();if(!setting("alerts.sound").toBoolean() || now-lastSound<setting("alerts.minimum_seconds").toLong()*1000)return
  lastSound=now
  val file=setting("alerts.${kind}_sound")
  if(file.isBlank()){runCatching{java.awt.Toolkit.getDefaultToolkit().beep()};return}
  Thread({runCatching {
   val path=home.resolve(file);require(java.nio.file.Files.size(path)<=10_000_000)
   javax.sound.sampled.AudioSystem.getAudioInputStream(path.toFile()).use {audio->
    val clip=javax.sound.sampled.AudioSystem.getClip();try{clip.open(audio);clip.start();Thread.sleep((clip.microsecondLength/1000+100).coerceAtMost(10000))}finally{clip.close()}
   }
  }},"APRSIS64 sound").apply {isDaemon=true;start()}
 }
 @Synchronized fun event(kind:String,key:String,text:String) {
  if(!setting("alerts.$kind").toBoolean())return
  val now=System.currentTimeMillis();val id="$kind:$key";if(now-(seen[id]?:0)<setting("alerts.minimum_seconds").toLong()*1000)return
  seen[id]=now;while(seen.size>1000)seen.remove(seen.keys.first());notices.value=(notices.value+AppNotice(now,text)).takeLast(100)
  if(setting("alerts.desktop").toBoolean())runCatching{tray?.displayMessage("APRSIS64",text,java.awt.TrayIcon.MessageType.INFO)}
  sound(kind)
 }
 @Synchronized fun tick() {
  watched.keys.retainAll(names(setting("alerts.watch_calls")).toSet());missing.retainAll(watched.keys)
  val minutes=setting("alerts.lost_minutes").toLong();if(minutes<=0)return
  watched.forEach {(call,time)->if(System.currentTimeMillis()-time>minutes*60000 && missing.add(call)){
   val text="Lost watched station $call";notices.value=(notices.value+AppNotice(System.currentTimeMillis(),text)).takeLast(100)
   if(setting("alerts.desktop").toBoolean())runCatching{tray?.displayMessage("APRSIS64",text,java.awt.TrayIcon.MessageType.INFO)};sound("station")
  }}
 }
 @Synchronized fun incoming(line:String,account:String,own:AprsFix?=null) {
  val packet=RadioPacket.parse(line) ?: return
  val message=AprsMessages.parse(line)
  val watchedCall=packet.source in names(setting("alerts.watch_calls"))
  if(watchedCall){watched[packet.source]=System.currentTimeMillis();missing.remove(packet.source)}
  val radius=setting("alerts.proximity_km").toDouble()
  val station=AprsProtocol.parse(line,System.currentTimeMillis())
  val watch=watchedCall && (radius==0.0 || own!=null && station!=null && AdaptiveBeaconPolicy.distance(own,AprsSpatial.fix(station))<=radius*1000)
  val text=when {
   message!=null && message.recipient==account && message.acknowledgement==null && setting("alerts.messages").toBoolean()->"Message from ${message.sender}: ${message.text}"
   watch->"Heard ${packet.source}"
   else->return
  }
  val now=System.currentTimeMillis();val key=if(message!=null) "${message.sender}/${message.id}/${message.text}" else packet.source
  if(now-(seen[key] ?: 0)<if(message!=null) 86400000 else setting("alerts.minimum_seconds").toLong()*1000)return
  seen[key]=now;while(seen.size>1000)seen.remove(seen.keys.first())
  notices.value=(notices.value+AppNotice(now,text)).takeLast(100)
  if(setting("alerts.desktop").toBoolean()) runCatching {tray?.displayMessage("APRSIS64",text,java.awt.TrayIcon.MessageType.INFO)}
  sound(if(message!=null)"message" else "station")
 }
 override fun close(){tray?.let {runCatching {java.awt.SystemTray.getSystemTray().remove(it)}};tray=null}
 fun dismiss(){notices.value=emptyList()}
}
