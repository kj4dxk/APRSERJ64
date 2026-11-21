// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import java.util.Properties
import net.osmand.shared.aprs.*

object TacticalPreferences {
 val fields=listOf(
  BeaconField("tactical.paths","Show selected station's received packet path","true",SettingKind.BOOLEAN),
  BeaconField("tactical.path_age_seconds","Maximum path age (seconds)","1800",SettingKind.INTEGER),
  BeaconField("tactical.nicknames","Nicknames: CALLSIGN=Name, one per line","[]",SettingKind.STRINGS),
  BeaconField("tactical.labels","Map labels","selected",choices=listOf("none","selected","all")),
  BeaconField("tactical.maximum_labels","Maximum map labels","100",SettingKind.INTEGER),
  BeaconField("tactical.label_speed","Include reported speed","true",SettingKind.BOOLEAN),
  BeaconField("tactical.label_altitude","Include reported altitude","true",SettingKind.BOOLEAN),
  BeaconField("tactical.label_weather","Include received weather","true",SettingKind.BOOLEAN),
  BeaconField("tactical.dead_reckoning","Show estimated moving positions","true",SettingKind.BOOLEAN),
  BeaconField("tactical.prediction_seconds","Stop predictions after (seconds)","300",SettingKind.INTEGER),
  BeaconField("tactical.phg","Show selected station's estimated PHG range","true",SettingKind.BOOLEAN),
  BeaconField("tactical.shapes","Show received lines and areas","true",SettingKind.BOOLEAN),
  BeaconField("tactical.warning_polygons","Show weather warning boundaries","true",SettingKind.BOOLEAN),
  BeaconField("tactical.direction_finding","Show direction-finding reports","true",SettingKind.BOOLEAN),
  BeaconField("tactical.df_age_seconds","Maximum DF report age (seconds)","1800",SettingKind.INTEGER),
  BeaconField("tactical.df_zero_radius_km","S0 non-detection reference radius (km)","10",SettingKind.DECIMAL),
  BeaconField("tactical.df_measurements","Local DF bearings: name,lat,lon,bearing,range_km,width_degrees","[]",SettingKind.STRINGS),
  BeaconField("tactical.horizon","Show selected station's estimated radio horizon","true",SettingKind.BOOLEAN),
  BeaconField("tactical.ground_meters","Ground elevation MSL for horizon estimate (m)","0",SettingKind.DECIMAL),
  BeaconField("tactical.receiver_height_meters","Receiving antenna height above ground (m)","2",SettingKind.DECIMAL),
  BeaconField("tactical.dx_minimum_km","Minimum direct-heard DX distance (km)","10",SettingKind.DECIMAL),
  BeaconField("tactical.dx_retention_days","Retain direct-heard DX records (days)","30",SettingKind.INTEGER),
  BeaconField("tactical.dx_query","Answer directed ?DX queries","false",SettingKind.BOOLEAN),
  BeaconField("tactical.dx_status","Include furthest direct-heard DX in periodic status","false",SettingKind.BOOLEAN),
  BeaconField("tactical.qru_enabled","Serve configured QRU object groups","false",SettingKind.BOOLEAN),
  BeaconField("tactical.qru_address","QRU service address","QRU"),
  BeaconField("tactical.qru_radius_km","Default QRU radius (km)","50",SettingKind.INTEGER),
  BeaconField("tactical.qru_maximum_radius_km","Maximum QRU radius (km)","500",SettingKind.INTEGER),
  BeaconField("tactical.qru_maximum_objects","Maximum QRU objects per query","5",SettingKind.INTEGER),
  BeaconField("tactical.qru_position_age_seconds","Maximum requester position age (seconds)","1800",SettingKind.INTEGER),
  BeaconField("tactical.qru_spacing_seconds","Spacing between QRU object replies (seconds)","5",SettingKind.INTEGER)
 )
 fun aliases(p:Properties)=names(p.getProperty("tactical.nicknames","[]")).associate {it.substringBefore('=').uppercase() to it.substringAfter('=')}
 fun validate(p:Properties) {
  fun i(k:String)=p.getProperty("tactical.$k").toInt()
  require(i("prediction_seconds") in 10..1800 && i("path_age_seconds") in 10..86400 && i("df_age_seconds") in 10..86400)
  require(i("maximum_labels") in 1..200 && i("dx_retention_days") in 1..365)
  require(p.getProperty("tactical.dx_minimum_km").toDouble() in 0.0..20000.0)
  require(p.getProperty("tactical.df_zero_radius_km").toDouble() in 0.1..1000.0)
  require(p.getProperty("tactical.ground_meters").toDouble() in -500.0..9000.0 && p.getProperty("tactical.receiver_height_meters").toDouble() in 0.0..10000.0)
  require(i("qru_radius_km") in 1..i("qru_maximum_radius_km") && i("qru_maximum_radius_km") in 1..20000)
  require(i("qru_maximum_objects") in 1..32 && i("qru_position_age_seconds") in 30..86400 && i("qru_spacing_seconds") in 1..60)
  require(AprsProtocol.validCallsign(p.getProperty("tactical.qru_address")))
  val aliases=names(p.getProperty("tactical.nicknames"));require(aliases.size<=500 && aliases.map {it.substringBefore('=').uppercase()}.distinct().size==aliases.size)
  aliases.forEach {require('=' in it && it.substringBefore('=').length in 1..9 && it.substringAfter('=').length in 1..40 && it.none {c->c.isISOControl()})}
  val measurements=names(p.getProperty("tactical.df_measurements"));require(measurements.size<=100);measurements.forEach {manualBearing(it)}
 }
 fun manualBearing(text:String):Pair<String,AprsShape> {
  val v=text.split(',').map {it.trim()};require(v.size==6 && v[0].length in 1..30)
  val origin=AprsFix(v[1].toDouble(),v[2].toDouble(),0);val bearing=v[3].toDouble();val range=v[4].toDouble();val width=v[5].toDouble()
  require(bearing in 0.0..360.0 && range in 0.01..20000.0 && width in 0.0..360.0)
  val points=listOf(origin,AprsSpatial.project(origin,bearing-width/2,range*1000),AprsSpatial.project(origin,bearing,range*1000),AprsSpatial.project(origin,bearing+width/2,range*1000),origin)
  return v[0] to AprsShape(points,true,"#ad45cf",true)
 }
}
