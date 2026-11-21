// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.osmand.shared.aprs.*
import java.util.Properties
import java.time.Instant
import java.util.Locale

@Composable internal fun TacticalTools(model:AppModel,selected:String?,page:String) {
 val stations by model.stations.collectAsState()
 val paths by model.tactical.paths.collectAsState()
 val dx by model.tactical.dx.collectAsState()
 val status by model.tactical.status.collectAsState()
 val revision by model.settingsRevision.collectAsState()
 val preferences=remember(revision) {Properties().apply {putAll(model.settings)}}
 val weather by model.archive.weather.collectAsState()
 val scope=rememberCoroutineScope()
 var call by remember {mutableStateOf(selected.orEmpty())}
 var feedback by remember {mutableStateOf("")}
 val station=stations.firstOrNull {it.callsign==call}
 fun save(key:String,value:String) {feedback=if(model.updateFeature(key,value)) "Saved" else "Unable to save; check values"}
 if(feedback.isNotBlank())Text(feedback,color=MaterialTheme.colors.primary)
 when(page) {
  "Station"->{
   OutlinedTextField(call,{call=it.uppercase()},label={Text("Station or object")},singleLine=true)
   var nickname by remember(call,revision) {mutableStateOf(TacticalPreferences.aliases(preferences)[call].orEmpty())}
   OutlinedTextField(nickname,{nickname=it},label={Text("Local nickname")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Button(onClick={
    val aliases=TacticalPreferences.aliases(preferences).toMutableMap()
    if(nickname.isBlank())aliases.remove(call) else aliases[call]=nickname.trim()
    save("tactical.nicknames",encodedNames(aliases.map {"${it.key}=${it.value}"}))
   },enabled=call.isNotBlank() && call.length<=9 && nickname.length<=40){Text("Save nickname")}
   Text("Nicknames change your display; messages still use the callsign.")
   if(station==null)Text("Waiting for a position from this station") else {
    val details=AprsSpatial.details(station)
    Text(stationLabel(station,preferences,weather[call]),fontSize=20.sp)
    Text("${station.latitude}, ${station.longitude} · received ${Instant.ofEpochMilli(station.heardAtMillis)}")
    Text(station.comment)
    details.course?.let {Text("Course: %.0f°".format(Locale.US,it))}
    details.coverage?.let {Text("Estimated ${if(it.strength==null) "radio coverage" else "DF contour"}: %.1f km".format(Locale.US,it.radiusMeters/1000))}
    details.direction?.let {Text("DF bearing %.0f° · range %.1f km · accuracy < %.0f°".format(Locale.US,it.bearing,it.rangeMeters/1000,it.accuracyDegrees))}
    details.altitudeMeters?.let {alt->Text("Radio horizon estimate: %.1f km".format(Locale.US,AprsSpatial.horizonMeters(alt,preferences.getProperty("tactical.ground_meters").toDouble(),preferences.getProperty("tactical.receiver_height_meters").toDouble())/1000))}
   }
   paths[call]?.let {path->
    Divider();Text("Last received path · ${path.port} · ${Instant.ofEpochMilli(path.time)}")
    Text(path.packet.source+" > "+path.packet.path.joinToString(" > "))
    Text("Orange lines connect known used hops. Missing positions and unexpanded aliases leave gaps.")
   }
   Divider()
   listOf("paths" to "Packet path", "dead_reckoning" to "Estimated moving positions", "phg" to "PHG range circle", "horizon" to "Radio horizon").forEach {(key,label)->
    Row {Checkbox(preferences.getProperty("tactical.$key").toBoolean(),{save("tactical.$key",it.toString())});Text(label,Modifier.padding(top=12.dp))}
   }
   Text("Small outlined circles mark estimated positions, not new reports. PHG and radio-horizon circles are estimates; terrain and antenna settings are in Settings → Tactical.")
  }
  "Radio DX"->{
   Text("Direct-heard RF stations",fontSize=20.sp)
   Text("Internet, third-party and already-digipeated packets are excluded. Records show the greatest received distance per station and port within the retention period.")
   if(status.isNotBlank())Text(status,color=MaterialTheme.colors.error)
   if(dx.isEmpty())Text("No qualifying direct RF receptions recorded yet.")
   var port by remember {mutableStateOf("All ports")}
   FlowRow { (listOf("All ports")+dx.map {it.port}.distinct()).forEach {name->TextButton(onClick={port=name}){Text(if(port==name) "[$name]" else name)}} }
   Button(onClick={scope.launch {val text=model.tactical.summary();feedback=if(text==null) "No DX to report" else if(runCatching {model.reportDx()}.getOrDefault(false)) "DX status transmitted" else "No available output"}},enabled=dx.isNotEmpty()){Text("Transmit DX status")}
   dx.filter {(port=="All ports" || it.port==port) && System.currentTimeMillis()-it.time<=preferences.getProperty("tactical.dx_retention_days").toLong()*86400000}.take(200).forEach {record->
    Text("${record.call} · %.1f km · ${record.port}".format(Locale.US,record.kilometers),fontSize=16.sp)
    Text("${Instant.ofEpochMilli(record.time)} · ${record.latitude}, ${record.longitude}");Divider()
   }
  }
  "Direction finding"->{
   Text("Add a local bearing",fontSize=20.sp)
   val own=model.beaconState.value.position?.fix
   var name by remember {mutableStateOf("")}
   var latitude by remember {mutableStateOf((station?.latitude ?: own?.latitude)?.toString().orEmpty())}
   var longitude by remember {mutableStateOf((station?.longitude ?: own?.longitude)?.toString().orEmpty())}
   var bearing by remember {mutableStateOf("")}
   var range by remember {mutableStateOf("10")}
   var width by remember {mutableStateOf("10")}
   OutlinedTextField(name,{name=it},label={Text("Measurement name")},singleLine=true)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(latitude,{latitude=it},Modifier.weight(1f),label={Text("Latitude")},singleLine=true)
    OutlinedTextField(longitude,{longitude=it},Modifier.weight(1f),label={Text("Longitude")},singleLine=true)
   }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(bearing,{bearing=it},Modifier.weight(1f),label={Text("True bearing °")},singleLine=true)
    OutlinedTextField(range,{range=it},Modifier.weight(1f),label={Text("Range km")},singleLine=true)
    OutlinedTextField(width,{width=it},Modifier.weight(1f),label={Text("Beam width °")},singleLine=true)
   }
   Button(onClick={runCatching {
    val line="$name,$latitude,$longitude,$bearing,$range,$width";TacticalPreferences.manualBearing(line)
    val entries=names(preferences.getProperty("tactical.df_measurements"));save("tactical.df_measurements",encodedNames(entries+line))
   }.onFailure {feedback="Check name, coordinates, bearing, range and beam width"}}){Text("Add to map")}
   Text("Purple beams compare bearings from different locations. Local measurements persist until removed; received DF reports expire automatically.")
   names(preferences.getProperty("tactical.df_measurements")).forEachIndexed {index,line->
    Row {Text(line,Modifier.weight(1f).padding(top=12.dp));TextButton(onClick={save("tactical.df_measurements",encodedNames(names(preferences.getProperty("tactical.df_measurements")).filterIndexed {i,_->i!=index}))}){Text("Remove")}}
   }
   Divider();Text("Received direction-finding reports")
   stations.filter {System.currentTimeMillis()-it.heardAtMillis in 0..preferences.getProperty("tactical.df_age_seconds").toLong()*1000}.forEach {s->
    val d=AprsSpatial.details(s);if(d.direction!=null || d.coverage?.strength!=null)Text("${s.callsign}: ${s.comment}")
   }
  }
 }
}
