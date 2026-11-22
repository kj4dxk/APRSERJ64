// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.maplibre.compose.desktop.*
import kotlinx.serialization.json.*
import org.aprsfoundation.core.*
import java.util.Properties
import kotlin.math.*

internal fun mapSettings(model:AppModel,values:Properties=model.settings)=Properties().apply {
 putAll(values)
 if(getProperty("offline.enabled").toBoolean()) {
  setProperty("map.tile_url",model.offline.template());setProperty("map.online_enabled","true")
  setProperty("map.overlay_enabled","false");setProperty("map.underlay_enabled","false")
 } else if(getProperty("weather.radar_enabled").toBoolean()) {
  setProperty("map.overlay_enabled","true");setProperty("map.overlay_url",getProperty("weather.radar_url"));setProperty("map.overlay_opacity",getProperty("weather.radar_opacity"))
 }
}
internal fun mapConfigurationKey(model:AppModel)=mapSettings(model).let {p->
 p.stringPropertyNames().filter {it.startsWith("map.") || it in setOf("latitude","longitude")}.associateWith {p.getProperty(it)}
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
 var editing by remember {mutableStateOf(false)}
 var preferences by remember {mutableStateOf(ViewOverrides.settings(model,call))}
 var source by remember {mutableStateOf(preferences.getProperty("map.tile_url"))}
 var zoom by remember {mutableStateOf(preferences.getProperty("map.initial_zoom"))}
 var search by remember {mutableStateOf(preferences.getProperty("display.search"))}
 var trails by remember {mutableStateOf(preferences.getProperty("display.trails").toBoolean())}
 var prediction by remember {mutableStateOf(preferences.getProperty("tactical.dead_reckoning").toBoolean())}
 var error by remember {mutableStateOf("")}
 Window(icon=AppIcon.window,onCloseRequest=dismiss,title=call,state=rememberWindowState(width=900.dp,height=700.dp)) {
  MaterialTheme(colors=darkColors()) {androidx.compose.material.Surface {Column(Modifier.fillMaxSize()) {
   Row {HelpTextButton(UiHelp.action("View settings"),onClick={editing=!editing}){HelpText("View settings")};HelpText(call,Modifier.padding(12.dp))}
   if(editing) {
    HelpField(UiHelp.field("Map tile URL"),source,{source=it},label={HelpText("Map tile URL")},modifier=Modifier.fillMaxWidth(),fill=true)
    Row {HelpField(UiHelp.field("Initial zoom"),zoom,{zoom=it},Modifier.weight(1f),label={HelpText("Initial zoom")},fill=true);HelpField(UiHelp.field("Station search"),search,{search=it},Modifier.weight(1f),label={HelpText("Station search")},fill=true)}
    Row {HelpCheckbox(UiHelp.action("Trails"),trails,{trails=it});HelpText("Trails");HelpCheckbox(UiHelp.action("Dead reckoning"),prediction,{prediction=it});HelpText("Dead reckoning")}
    HelpTextButton(UiHelp.action("Save this view"),onClick={runCatching {
     val next=Properties().apply {putAll(preferences);setProperty("map.tile_url",source);setProperty("map.initial_zoom",zoom);setProperty("display.search",search);setProperty("display.trails",trails.toString());setProperty("tactical.dead_reckoning",prediction.toString())}
     ServicePreferences.validate(next)
     val encoded=call+"|"+ViewOverrides.keys.joinToString("|"){"$it=${next.getProperty(it)}"};ViewOverrides.parse(encoded)
     val entries=names(model.settings.getProperty("multitrack.views")).filterNot {it.substringBefore('|')==call}+encoded
     check(model.updateFeature("multitrack.views",encodedNames(entries)));preferences=next;editing=false;error=""
    }.onFailure {error=it.message?:"Check view settings"}}){HelpText("Save this view")}
    if(error.isNotEmpty())HelpText(error,color=MaterialTheme.colors.error)
   }
   ProvideMapPresentationHost(host=rememberAwtComposeMapPresentationHost(window)) {
    val visible=stations.filter {it.callsign==call || it.callsign.contains(preferences.getProperty("display.search"),true) && System.currentTimeMillis()-it.heardAtMillis<=preferences.getProperty("display.maximum_age_minutes").toLong()*60000 && stationKind(it,preferences.getProperty("display.kind"),model.views.stations.value,model.archive.objects.value)}
    key(preferences) {MapPanel(visible,call,model,null,Modifier.weight(1f).fillMaxWidth(),forceFollow=true,viewSettings=preferences) {}}
   }
  }}}
 }
}
