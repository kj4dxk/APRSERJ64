// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import kotlinx.coroutines.flow.MutableStateFlow
import net.osmand.shared.aprs.*

data class AppNotice(val time:Long,val text:String)
class ActivityAlerts(private val setting:(String)->String):AutoCloseable {
 private var tray:java.awt.TrayIcon?=null
 fun configure() {
  if(!setting("alerts.desktop").toBoolean()){close();return}
  if(tray==null && java.awt.SystemTray.isSupported()) runCatching {
   val image=javax.imageio.ImageIO.read(javaClass.getResource("/APRS1.png")).getSubimage(2,2,18,18)
   val icon=java.awt.TrayIcon(image,"APRSIS64");icon.isImageAutoSize=true
   java.awt.SystemTray.getSystemTray().add(icon);tray=icon
  }
 }
 val notices=MutableStateFlow<List<AppNotice>>(emptyList())
 private val seen=linkedMapOf<String,Long>()
 private var lastSound=0L
 @Synchronized fun incoming(line:String,account:String) {
  val packet=RadioPacket.parse(line) ?: return
  val message=AprsMessages.parse(line)
  val watch=packet.source in names(setting("alerts.watch_calls"))
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
  if(setting("alerts.sound").toBoolean() && now-lastSound>=setting("alerts.minimum_seconds").toLong()*1000) {
   lastSound=now;runCatching {java.awt.Toolkit.getDefaultToolkit().beep()}
  }
 }
 override fun close(){tray?.let {runCatching {java.awt.SystemTray.getSystemTray().remove(it)}};tray=null}
 fun dismiss(){notices.value=emptyList()}
}
