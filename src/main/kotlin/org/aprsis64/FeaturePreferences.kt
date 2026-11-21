// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import java.util.Properties
import java.net.URI
import net.osmand.shared.aprs.*

object FeaturePreferences {
 val fields=AdvancedPreferences.fields+AdditionalPreferences.fields+TacticalPreferences.fields+listOf(
  BeaconField("connectivity.failover","Try other saved APRS-IS presets on failure","false",SettingKind.BOOLEAN),
  BeaconField("display.search","Station search",""),
  BeaconField("display.kind","Station type","all",choices=listOf("all","weather","moving","objects","rf","message-capable","frequency","frequency-issues","telemetry","status","altitude","direct-rf","igates","ambiguous","mic-e")),
  BeaconField("display.sort","Station sorting","callsign",choices=listOf("callsign","recent","distance")),
  BeaconField("display.maximum_age_minutes","Station age limit (minutes)","30",SettingKind.INTEGER),
  BeaconField("display.symbols","Show APRS symbols","true",SettingKind.BOOLEAN),
  BeaconField("display.trails","Show selected station trail","true",SettingKind.BOOLEAN),
  BeaconField("display.tracking_calls","Saved tracking windows (up to 8)","[]",SettingKind.STRINGS),
  BeaconField("display.follow","Follow selected station","false",SettingKind.BOOLEAN),
  BeaconField("history.enabled","Record position history","true",SettingKind.BOOLEAN),
  BeaconField("history.maximum_points","Total stored track points","100000",SettingKind.INTEGER),
  BeaconField("history.track_points","Points per displayed/exported track","1000",SettingKind.INTEGER),
  BeaconField("offline.enabled","Use downloaded base map only","false",SettingKind.BOOLEAN),
  BeaconField("offline.maximum_tiles","Maximum tiles per download","1000",SettingKind.INTEGER),
  BeaconField("offline.maximum_megabytes","Offline tile storage budget (MB)","1024",SettingKind.INTEGER),
  BeaconField("weather.enabled","Fetch weather alerts","false",SettingKind.BOOLEAN),
  BeaconField("weather.alerts_url","GeoJSON alert service URL","https://api.weather.gov/alerts/active?point={lat},{lon}"),
  BeaconField("weather.refresh_seconds","Alert refresh interval (seconds)","300",SettingKind.INTEGER),
  BeaconField("weather.radar_enabled","Show radar overlay","false",SettingKind.BOOLEAN),
  BeaconField("weather.radar_url","Radar XYZ tile URL",""),
  BeaconField("weather.radar_opacity","Radar opacity","0.6",SettingKind.DECIMAL),
  BeaconField("groups.server","Announcement server","ANSRVR"),
  BeaconField("groups.names","Saved announcement groups","[]",SettingKind.STRINGS),
  BeaconField("groups.keepalive","Keep joined groups active","false",SettingKind.BOOLEAN),
  BeaconField("groups.keepalive_minutes","Group keepalive interval (minutes)","120",SettingKind.INTEGER),
  BeaconField("kiosk.server","QRU query destination","QRU"),
  BeaconField("kiosk.enabled","Answer queries to my callsign","false",SettingKind.BOOLEAN),
  BeaconField("kiosk.entries","Kiosk entries: TOPIC=response, one per line","[]",SettingKind.STRINGS),
  BeaconField("kiosk.minimum_seconds","Minimum reply interval per sender","60",SettingKind.INTEGER),
  BeaconField("alerts.messages","New message alerts","true",SettingKind.BOOLEAN),
  BeaconField("alerts.watch_calls","Alert on callsigns (one per line)","[]",SettingKind.STRINGS),
  BeaconField("alerts.desktop","Show desktop tray notifications","false",SettingKind.BOOLEAN),
  BeaconField("alerts.sound","Play alert sound","false",SettingKind.BOOLEAN),
  BeaconField("alerts.minimum_seconds","Minimum sound interval","30",SettingKind.INTEGER)
 )
 fun validate(p: Properties) {
  AdvancedPreferences.validate(p)
  TacticalPreferences.validate(p)
  AdditionalPreferences.validate(p)
  fun n(k:String)=p.getProperty(k).toInt()
  require(n("display.maximum_age_minutes") in 1..1440 && n("history.maximum_points") in 1000..1000000 && n("history.track_points") in 2..10000)
  require(n("offline.maximum_tiles") in 1..10000 && n("offline.maximum_megabytes") in 16..8192)
  require(n("weather.refresh_seconds") in 60..86400 && p.getProperty("weather.radar_opacity").toDouble() in 0.0..1.0)
  require(n("groups.keepalive_minutes") in 10..720 && n("kiosk.minimum_seconds") in 10..86400 && n("alerts.minimum_seconds") in 1..3600)
  require(AprsProtocol.validCallsign(p.getProperty("groups.server")) && AprsProtocol.validCallsign(p.getProperty("kiosk.server")))
  require(names(p.getProperty("groups.names")).all {it.matches(Regex("[A-Za-z0-9_-]{1,20}"))})
  require(names(p.getProperty("display.tracking_calls")).size<=8 && names(p.getProperty("display.tracking_calls")).all {it.length in 1..9 && it.all {c->c.code in 32..126}})
  require(names(p.getProperty("alerts.watch_calls")).all {AprsProtocol.validCallsign(it)})
  require(names(p.getProperty("kiosk.entries")).size<=50)
  names(p.getProperty("kiosk.entries")).forEach {require('=' in it && it.substringBefore('=').matches(Regex("[A-Z0-9_-]{1,20}")) && AprsMessages.validText(it.substringAfter('=')))}
  for(key in listOf("weather.alerts_url","weather.radar_url")) {
   val text=p.getProperty(key);if(text.isBlank()) {require(key=="weather.radar_url" && !p.getProperty("weather.radar_enabled").toBoolean());continue}
   val uri=URI(text.replace("{lat}","0").replace("{lon}","0").replace("{z}","0").replace("{x}","0").replace("{y}","0"))
   require(uri.scheme in listOf("http","https") && uri.host!=null)
  }
  if(p.getProperty("weather.radar_enabled").toBoolean()) require(listOf("{x}","{y}","{z}").all {it in p.getProperty("weather.radar_url")})
 }
}
