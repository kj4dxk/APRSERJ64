// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.maplibre.compose.desktop.*
import kotlinx.serialization.json.*
import net.osmand.shared.aprs.*
import java.util.Properties
import kotlin.math.*

internal fun mapSettings(model:AppModel)=Properties().apply {
 putAll(model.settings)
 if(getProperty("offline.enabled").toBoolean()) {
  setProperty("map.tile_url",model.offline.template());setProperty("map.online_enabled","true")
  setProperty("map.overlay_enabled","false");setProperty("map.underlay_enabled","false")
 } else if(getProperty("weather.radar_enabled").toBoolean()) {
  setProperty("map.overlay_enabled","true");setProperty("map.overlay_url",getProperty("weather.radar_url"));setProperty("map.overlay_opacity",getProperty("weather.radar_opacity"))
 }
}
internal fun trailJson(points:List<AprsFix>):String {
 val segments=mutableListOf<MutableList<AprsFix>>()
 points.forEach {p->if(segments.isEmpty() || abs(segments.last().last().longitude-p.longitude)>180)segments.add(mutableListOf());segments.last().add(p)}
 return buildJsonObject {put("type","FeatureCollection");putJsonArray("features") {segments.filter {it.size>1}.forEach {segment->addJsonObject {
  put("type","Feature");putJsonObject("properties"){};putJsonObject("geometry"){put("type","LineString");putJsonArray("coordinates"){segment.forEach {p->addJsonArray {add(p.longitude);add(p.latitude)}}}}
 }}}}.toString()
}
internal fun navigationSummary(model:AppModel,station:AprsStation):String {
 val own=model.beaconState.value.position?.fix ?: AprsFix(model.settings.getProperty("position.latitude").toDouble(),model.settings.getProperty("position.longitude").toDouble(),0)
 val destination=AprsFix(station.latitude,station.longitude,0)
 val lat1=own.latitude*PI/180;val lat2=destination.latitude*PI/180;val lon=(destination.longitude-own.longitude)*PI/180
 val bearing=(atan2(sin(lon)*cos(lat2),cos(lat1)*sin(lat2)-sin(lat1)*cos(lat2)*cos(lon))*180/PI+360)%360
 return "%s · %.1f km · %.0f° from your position".format(station.callsign,AdaptiveBeaconPolicy.distance(own,destination)/1000,bearing)
}
@Composable fun TrackingWindow(model:AppModel,call:String,dismiss:()->Unit) {
 val stations by model.stations.collectAsState();val revision by model.settingsRevision.collectAsState()
 Window(onCloseRequest=dismiss,title="$call — APRSIS64",state=rememberWindowState(width=900.dp,height=700.dp)) {
  MaterialTheme(colors=darkColors()) {ProvideMapPresentationHost(host=rememberAwtComposeMapPresentationHost(window)) {
   key(revision) {MapPanel(stations,call,model,null,Modifier.fillMaxSize(),forceFollow=true) {}}
  }}
 }
}
