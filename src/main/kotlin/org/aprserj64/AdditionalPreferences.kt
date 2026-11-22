// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.util.Properties
import net.osmand.shared.aprs.*

data class ScheduledBulletin(val slot:String,val group:String,val seconds:Int,val route:String,val text:String) {
 companion object {fun parse(line:String):ScheduledBulletin {
  val p=line.split('|',limit=5);require(p.size==5)
  return ScheduledBulletin(p[0],p[1],p[2].toInt(),p[3],p[4]).also {
   require(it.seconds in 60..86400 && it.route in listOf("best","is","rf","both"))
   AprsServices.bulletin("N0CALL",it.slot,it.group,it.text)
  }
 }}
}
object AdditionalPreferences {
 val fields=listOf(
  BeaconField("messaging.auto_reply","Reply while inactive","false",SettingKind.BOOLEAN),
  BeaconField("messaging.inactive_minutes","Inactivity before auto reply (minutes)","15",SettingKind.INTEGER),
  BeaconField("messaging.reply_minutes","Minimum auto reply interval per station (minutes)","30",SettingKind.INTEGER),
  BeaconField("messaging.reply_text","Automatic reply text","Away from keyboard"),
  BeaconField("messaging.email_gateway","Email gateway callsign","EMAIL-2"),
  BeaconField("bulletins.enabled","Transmit saved bulletins","false",SettingKind.BOOLEAN),
  BeaconField("bulletins.entries","Bulletins: slot|group|seconds|is/rf/both|text (one per line)","[]",SettingKind.STRINGS),
  BeaconField("telemetry.enabled","Transmit own telemetry","false",SettingKind.BOOLEAN),
  BeaconField("telemetry.interval_seconds","Telemetry interval (seconds)","600",SettingKind.INTEGER),
  BeaconField("telemetry.definitions_seconds","Definition interval (seconds)","3600",SettingKind.INTEGER),
  BeaconField("telemetry.route","Telemetry route","is",choices=listOf("is","rf","both")),
  BeaconField("telemetry.channels","Five channels: name|unit|source|a|b|c; sources rx, tx, stations, speed, uptime, input:key, or a number","[\"Rx|pkts|rx|0|1|0\",\"Tx|pkts|tx|0|1|0\",\"Sta|count|stations|0|1|0\",\"Spd|knots|speed|0|1|0\",\"Up|min|uptime|0|1|0\"]",SettingKind.STRINGS),
  BeaconField("telemetry.bits","Eight digital bits","00000000"),
  BeaconField("telemetry.bit_names","Eight digital channel names","[\"Bit1\",\"Bit2\",\"Bit3\",\"Bit4\",\"Bit5\",\"Bit6\",\"Bit7\",\"Bit8\"]",SettingKind.STRINGS),
  BeaconField("telemetry.title","Telemetry project title","Station telemetry"),
  BeaconField("logging.csv","Automatically log tracks to CSV","false",SettingKind.BOOLEAN),
  BeaconField("logging.gpx","Automatically log tracks to GPX","false",SettingKind.BOOLEAN),
  BeaconField("logging.calls","Stations to log (empty: all received positions)","[]",SettingKind.STRINGS),
  BeaconField("logging.retention_days","Automatic track file retention (days)","30",SettingKind.INTEGER),
  BeaconField("legacyweather.enabled","Receive APRS NWS warnings","false",SettingKind.BOOLEAN),
  BeaconField("legacyweather.filter","APRS-IS NWS warning filter","g/NWS*"),
  BeaconField("legacyweather.shapefiles","Local WGS84 polygon .shp files (paired .dbf required)","[]",SettingKind.STRINGS),
  BeaconField("legacyweather.code_field","DBF column with complete UGC code (e.g. FLZ041)","UGC"),
  BeaconField("qru.advertise","Advertise location when QRU responder is enabled","true",SettingKind.BOOLEAN),
  BeaconField("qru.route","QRU advertisement route","is",choices=listOf("is","rf","both")),
  BeaconField("qru.interval_seconds","QRU advertisement interval (seconds)","1800",SettingKind.INTEGER)
 )
 fun validate(p:Properties) {
  fun n(k:String)=p.getProperty(k).toInt()
  require(n("messaging.inactive_minutes") in 1..1440 && n("messaging.reply_minutes") in 1..1440)
  require(AprsMessages.validText("AA: "+p.getProperty("messaging.reply_text")))
  require(AprsProtocol.validCallsign(p.getProperty("messaging.email_gateway")))
  val entries=names(p.getProperty("bulletins.entries"));require(entries.size<=20);entries.forEach {ScheduledBulletin.parse(it)}
  require(n("telemetry.interval_seconds") in 60..86400 && n("telemetry.definitions_seconds") in 600..86400)
  val channels=names(p.getProperty("telemetry.channels"));require(channels.size==5);channels.forEach {TelemetryChannel.parse(it)}
  val bitNames=names(p.getProperty("telemetry.bit_names"));require(bitNames.size==8 && bitNames.all {it.matches(Regex("[A-Za-z0-9 _-]{1,8}"))})
  require(p.getProperty("telemetry.bits").matches(Regex("[01]{8}")))
  require(p.getProperty("telemetry.title").matches(Regex("[A-Za-z0-9 _-]{1,30}")))
  TelemetryPackets.definitions("N0CALL",channels.map {TelemetryChannel.parse(it)},bitNames,p.getProperty("telemetry.title"))
  require(n("logging.retention_days") in 1..3650 && n("qru.interval_seconds") in 600..86400)
  require(names(p.getProperty("logging.calls")).all {it.length in 1..9 && it.none {c->c=='/' || c=='\\'}})
  require(names(p.getProperty("legacyweather.shapefiles")).size<=12)
  require(p.getProperty("legacyweather.code_field").matches(Regex("[A-Za-z0-9_]{1,11}")))
 }
}
data class TelemetryChannel(val name:String,val unit:String,val source:String,val a:Double,val b:Double,val c:Double) {
 companion object {fun parse(line:String):TelemetryChannel {
  val p=line.split('|');require(p.size==6)
  require(p.take(2).all {it.matches(Regex("[A-Za-z0-9 _-]{1,8}"))})
  require(p[2] in listOf("rx","tx","stations","speed","uptime") || p[2].matches(Regex("input:[A-Za-z0-9_]{1,32}")) || p[2].toIntOrNull() in 0..255)
  return TelemetryChannel(p[0],p[1],p[2],p[3].toDouble(),p[4].toDouble(),p[5].toDouble()).also {require(listOf(it.a,it.b,it.c).all {v->v.isFinite()})}
 }}
}
