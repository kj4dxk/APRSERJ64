// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.util.Properties
import java.net.URI

object AdvancedPreferences {
 val fields=listOf(
  BeaconField("digital.enabled","Import digital-mode logs","false",SettingKind.BOOLEAN),
  BeaconField("digital.transmit","Transmit imported digital objects","false",SettingKind.BOOLEAN),
  BeaconField("digital.jt65_file","JT65-HF CSV log file",""),
  BeaconField("digital.js8_file","JS8Call ALL.TXT file",""),
  BeaconField("digital.ft8_file","WSJT-X FT8 ALL.TXT file",""),
  BeaconField("digital.maximum_age_seconds","Expire digital spots after (seconds)","1800",SettingKind.INTEGER),
  BeaconField("digital.interval_seconds","Digital object interval (seconds)","600",SettingKind.INTEGER),
  BeaconField("digital.maximum_objects","Maximum objects per log","50",SettingKind.INTEGER),
  BeaconField("digital.route","Digital object outputs","is",choices=listOf("is","rf","both")),
  BeaconField("digital.ports","Digital object RF ports (empty: all eligible)","[]",SettingKind.STRINGS),
  BeaconField("queries.enabled","Answer standard APRS queries","true",SettingKind.BOOLEAN),
  BeaconField("queries.minimum_seconds","Query reply cooldown per station","30",SettingKind.INTEGER),
  BeaconField("queries.maximum_results","Maximum stations in a query response","10",SettingKind.INTEGER),
  BeaconField("satellites.enabled","Show tracked satellites","false",SettingKind.BOOLEAN),
  BeaconField("satellites.file","TLE file (relative to application folder)","data/satellites.tle"),
  BeaconField("satellites.url","TLE download URL","https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=tle"),
  BeaconField("satellites.names","Satellite names to track (empty: first 20)","[]",SettingKind.STRINGS),
  BeaconField("satellites.maximum_age_days","Maximum TLE age (days)","14",SettingKind.INTEGER),
  BeaconField("satellites.prediction_hours","Pass prediction window (hours)","24",SettingKind.INTEGER),
  BeaconField("imports.bundled_osm","Show bundled Boston map","false",SettingKind.BOOLEAN),
  BeaconField("imports.osm_file","Local OpenStreetMap XML file",""),
  BeaconField("imports.gpx_files","GPX files to display (one per line)","[]",SettingKind.STRINGS),
  BeaconField("imports.maximum_points","Maximum imported points","20000",SettingKind.INTEGER),
  BeaconField("platforms.entries","Platform overrides: destination-prefix=name","[]",SettingKind.STRINGS),
  BeaconField("platforms.file","Optional APRS tocall definitions JSON file",""),
  BeaconField("weatherstation.enabled","Transmit local weather","false",SettingKind.BOOLEAN),
  BeaconField("weatherstation.source","Weather input","file",choices=listOf("file","port")),
  BeaconField("weatherstation.port","Named weather port",""),
  BeaconField("weatherstation.file","Weather data file (JSON, APRS or wxnow.txt)","data/weather.json"),
  BeaconField("weatherstation.maximum_age_seconds","Maximum weather input age (seconds)","600",SettingKind.INTEGER),
  BeaconField("weatherstation.interval_seconds","Weather transmit interval (seconds)","600",SettingKind.INTEGER),
  BeaconField("weatherstation.route","Weather outputs","is",choices=listOf("is","rf","both")),
  BeaconField("weatherstation.object","Weather object name (empty: own station)",""),
  BeaconField("telemetry.input_file","External telemetry JSON file","data/telemetry.json"),
  BeaconField("telemetry.input_age_seconds","Maximum external telemetry age (seconds)","120",SettingKind.INTEGER),
  BeaconField("alerts.message_sound","Message sound WAV file (empty: beep)",""),
  BeaconField("alerts.station_sound","Watched station sound WAV file (empty: beep)",""),
  BeaconField("alerts.weather_sound","Weather warning sound WAV file (empty: beep)",""),
  BeaconField("alerts.connection_sound","Connection change sound WAV file (empty: beep)",""),
  BeaconField("alerts.connection","Connection change notifications","false",SettingKind.BOOLEAN),
  BeaconField("alerts.weather","New severe weather notifications","false",SettingKind.BOOLEAN),
  BeaconField("alerts.proximity_km","Watched station proximity (0: any distance)","0",SettingKind.DECIMAL),
  BeaconField("alerts.lost_minutes","Notify when watched station is lost (0: off)","0",SettingKind.INTEGER),
  BeaconField("multitrack.views","Per-window overrides: CALL|key=value|key=value","[]",SettingKind.STRINGS)
 )
 fun validate(p:Properties) {
  fun n(k:String)=p.getProperty(k).toInt()
  require(n("digital.maximum_age_seconds") in 60..86400 && n("digital.interval_seconds") in 60..86400 && n("digital.maximum_objects") in 1..500)
  require(n("queries.minimum_seconds") in 10..3600 && n("queries.maximum_results") in 1..30)
  require(n("satellites.maximum_age_days") in 1..365 && n("satellites.prediction_hours") in 1..168)
  val url=URI(p.getProperty("satellites.url"));require(url.scheme in listOf("http","https") && url.host!=null)
  require(names(p.getProperty("satellites.names")).size<=50)
  require(names(p.getProperty("imports.gpx_files")).size<=32 && n("imports.maximum_points") in 100..100000)
  require(names(p.getProperty("platforms.entries")).all {'=' in it && it.substringBefore('=').matches(Regex("[A-Z0-9]{2,6}")) && it.length<=100})
  require(n("weatherstation.maximum_age_seconds") in 10..86400 && n("weatherstation.interval_seconds") in 60..86400)
  require(n("telemetry.input_age_seconds") in 1..86400)
  require(p.getProperty("weatherstation.object").let {it.isEmpty() || it.length in 3..9 && it.all {c->c.code in 32..126}})
  require(p.getProperty("alerts.proximity_km").toDouble() in 0.0..40075.0 && n("alerts.lost_minutes") in 0..1440)
  names(p.getProperty("multitrack.views")).forEach {entry->
   val (_,overrides)=ViewOverrides.parse(entry);val view=Properties().apply {putAll(p);overrides.forEach {(k,v)->setProperty(k,v)}};ServicePreferences.validate(view)
   require(view.getProperty("display.maximum_age_minutes").toInt() in 1..1440)
   listOf("display.trails","tactical.paths","tactical.dead_reckoning").forEach {view.getProperty(it).toBooleanStrict()}
  }
 }
}
object ViewOverrides {
 val keys=setOf("map.tile_url","map.initial_zoom","display.trails","tactical.paths","tactical.dead_reckoning","display.maximum_age_minutes","display.search","display.kind")
 fun parse(text:String):Pair<String,Map<String,String>> {
  val parts=text.split('|');require(parts[0].length in 1..9)
  val values=parts.drop(1).associate {require('=' in it);it.substringBefore('=') to it.substringAfter('=')}
  require(values.keys.all {it in keys});return parts[0] to values
 }
 fun settings(model:AppModel,call:String)=Properties().apply {
  putAll(model.settings);names(getProperty("multitrack.views")).map {parse(it)}.firstOrNull {it.first==call}?.second?.forEach {(k,v)->setProperty(k,v)}
 }
}
