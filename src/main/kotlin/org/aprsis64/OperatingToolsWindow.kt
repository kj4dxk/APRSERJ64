// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import kotlinx.coroutines.launch
import net.osmand.shared.aprs.*
import java.time.Instant

@Composable fun OperatingToolsWindow(model:AppModel,selected:String?,dismiss:()->Unit) {
 val scope=rememberCoroutineScope()
 val weather by model.archive.weather.collectAsState()
 val telemetry by model.archive.telemetry.collectAsState()
 val definitions by model.archive.definitions.collectAsState()
 val bulletins by model.archive.bulletins.collectAsState()
 val notices by model.activity.notices.collectAsState()
 val alerts by model.weatherAlerts.alerts.collectAsState()
 val weatherStatus by model.weatherAlerts.status.collectAsState()
 val download by model.offline.status.collectAsState()
 val downloading by model.offline.running.collectAsState()
 val revision by model.settingsRevision.collectAsState()
 val preferences=remember(revision) {java.util.Properties().apply {putAll(model.settings)}}
 val archiveStatus by model.archive.status.collectAsState()
 val dropped by model.archive.dropped.collectAsState()
 var tab by remember {mutableStateOf("Weather")}
 var result by remember {mutableStateOf("")}
 var call by remember {mutableStateOf(selected.orEmpty())}
 var channel by remember {mutableStateOf(0)}
 var group by remember {mutableStateOf("")}
 var message by remember {mutableStateOf("")}
 var qru by remember {mutableStateOf("INFO")}
 var radius by remember {mutableStateOf("50")}
 var slot by remember {mutableStateOf("1")}
 var south by remember {mutableStateOf((preferences.getProperty("latitude").toDouble()-0.03).toString())}
 var north by remember {mutableStateOf((preferences.getProperty("latitude").toDouble()+0.03).toString())}
 var west by remember {mutableStateOf((preferences.getProperty("longitude").toDouble()-0.03).toString())}
 var east by remember {mutableStateOf((preferences.getProperty("longitude").toDouble()+0.03).toString())}
 var zoomMin by remember {mutableStateOf("10")};var zoomMax by remember {mutableStateOf("13")}
 var plan by remember {mutableStateOf<List<Tile>>(emptyList())}
 fun send(peer:String,text:String){scope.launch {result=runCatching {model.send(peer,text);"Queued to $peer; see Messages for delivery"}.getOrElse {"Unable to queue: check callsigns and message length"}}}
 Window(onCloseRequest=dismiss,title="Operating tools — APRSIS64",state=rememberWindowState(width=1000.dp,height=800.dp)) {
  MaterialTheme(colors=darkColors()) {Surface {Column(Modifier.fillMaxSize().padding(18.dp)) {
   FlowRow(Modifier.fillMaxWidth()) {listOf("Weather","Telemetry","Tracks","Offline","Groups","QRU","Bulletins","Alerts").forEach {name->TextButton(onClick={tab=name;result=""}){Text(if(tab==name) "[$name]" else name)}}}
   if(result.isNotBlank()) Text(result,Modifier.padding(vertical=8.dp),color=MaterialTheme.colors.primary)
   ScrollableForm(Modifier.weight(1f).fillMaxWidth()) {when(tab) {
    "Weather"->{
     Text("Received APRS weather",fontSize=20.sp)
     weather.values.sortedBy {it.call}.forEach {w->
      Text("${w.call} · ${Instant.ofEpochMilli(w.time)}",color=MaterialTheme.colors.primary)
      fun f(n:Double?)=n?.let {"%.1f".format(it)} ?: "—"
      Text("${f(w.temperatureC)} °C · wind ${w.windDegrees ?: "—"}° ${f(w.windKph)} km/h · gust ${f(w.gustKph)} km/h · RH ${w.humidity ?: "—"}% · ${f(w.pressureHpa)} hPa · rain 1h ${f(w.rainHourMm)} mm / 24h ${f(w.rainDayMm)} mm")
      Divider()
     }
     if(weather.isEmpty())Text("No weather packets received yet.")
     Text(weatherStatus,fontSize=16.sp)
     Row {Checkbox(preferences.getProperty("weather.enabled").toBoolean(),{model.updateFeature("weather.enabled",it.toString())});Text("Fetch alerts for the configured fixed location",Modifier.padding(top=12.dp))}
     Text("Alert source and radar XYZ overlay are configurable in Settings → Weather.")
     alerts.forEach {a->Text("${a.severity}: ${a.event}",color=Color(0xFFFFC078));Text(a.headline);Text(a.description);Text("Expires ${a.expires}");Divider()}
    }
    "Telemetry"->{
     OutlinedTextField(call,{call=it.uppercase()},label={Text("Station callsign")},singleLine=true)
     FlowRow(Modifier.fillMaxWidth()) {telemetry.keys.sorted().forEach {c->TextButton(onClick={call=c}) {Text(c)}}}
     Row {(0..4).forEach {i->TextButton(onClick={channel=i}) {Text("A${i+1}")}}}
     val samples=telemetry[call].orEmpty();val defs=definitions[call].orEmpty()
     val label=defs["PARM"]?.values?.getOrNull(channel) ?: "Analog ${channel+1}"
     val unit=defs["UNIT"]?.values?.getOrNull(channel).orEmpty()
     val readings=samples.mapNotNull {sample->AprsObservations.engineering(sample.analog[channel],channel,defs["EQNS"])?.let {sample.time to it}}
     val values=readings.map {it.second}
     Text("$label $unit · ${samples.size} samples")
     if(values.isNotEmpty()) {
      val low=values.min();val high=values.max();Text("Latest ${values.last()} · min $low · max $high")
      Canvas(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFF14262C))) {
       if(readings.size>1) readings.zipWithNext().forEach {(a,b)->
        fun y(v:Double)=size.height-((v-low)/(high-low).takeIf {it>0}.let {it?:1.0}*size.height).toFloat()
        fun x(t:Long)=((t-readings.first().first).toDouble()/(readings.last().first-readings.first().first).coerceAtLeast(1)*size.width).toFloat()
        drawLine(Color(0xFF72E4C3),Offset(x(a.first),y(a.second)),Offset(x(b.first),y(b.second)),2f)
       }
      }
      Text("Time: ${Instant.ofEpochMilli(readings.first().first)} to ${Instant.ofEpochMilli(readings.last().first)}")
      Text("Digital bits: ${samples.last().bits}; sense: ${defs["BITS"]?.values?.firstOrNull() ?: "unspecified"}")
     } else Text("No classic T# telemetry received for this station.")
    }
    "Tracks"->{
     if(archiveStatus.isNotBlank())Text(archiveStatus)
     if(dropped>0)Text("$dropped packets omitted from history because the input queue filled")
     OutlinedTextField(call,{call=it.uppercase()},label={Text("Station or object name")},singleLine=true)
     Row {listOf("GPX","CSV").forEach {format->Button(onClick={scope.launch {result=runCatching {"Saved ${model.archive.export(call,format)}"}.getOrElse {it.message ?: "Export failed"}}},modifier=Modifier.padding(end=10.dp)){Text("Export $format")}}}
     Text("Export the selected station’s saved track. Files are saved in the exports folder beside APRSIS64.exe.")
     OutlinedTextField(message,{message=it.uppercase()},label={Text("Recipient callsign-SSID")},singleLine=true)
     Button(onClick={scope.launch {val point=model.archive.track(call).lastOrNull();if(point==null) result="No recorded position" else send(message,"$call ${"%.5f".format(java.util.Locale.US,point.latitude)},${"%.5f".format(java.util.Locale.US,point.longitude)}")}}){Text("Send position in message")}
    }
    "Offline"->{
     Text("Download a base-map area",fontSize=20.sp)
     Text("Uses your configured map provider. Choose a small region and preview the tile count before downloading.")
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedTextField(south,{south=it;plan=emptyList()},Modifier.weight(1f),label={Text("South")});OutlinedTextField(north,{north=it;plan=emptyList()},Modifier.weight(1f),label={Text("North")})}
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedTextField(west,{west=it;plan=emptyList()},Modifier.weight(1f),label={Text("West")});OutlinedTextField(east,{east=it;plan=emptyList()},Modifier.weight(1f),label={Text("East")})}
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedTextField(zoomMin,{zoomMin=it;plan=emptyList()},Modifier.weight(1f),label={Text("Minimum zoom")});OutlinedTextField(zoomMax,{zoomMax=it;plan=emptyList()},Modifier.weight(1f),label={Text("Maximum zoom")})}
     Row {
      Button(onClick={runCatching {TilePlan.region(south.toDouble(),west.toDouble(),north.toDouble(),east.toDouble(),zoomMin.toInt(),zoomMax.toInt(),preferences.getProperty("offline.maximum_tiles").toInt())}.onSuccess {plan=it;result="${it.size} tiles"}.onFailure {result=it.message ?: "Check area and zoom range"}}){Text("Preview")}
      Button(onClick={model.offline.download(plan)},enabled=plan.isNotEmpty()&&!downloading,modifier=Modifier.padding(horizontal=10.dp)){Text("Download")}
      TextButton(onClick={model.offline.cancel()},enabled=downloading){Text("Cancel download")}
     }
     Text(download)
     Row {Checkbox(preferences.getProperty("offline.enabled").toBoolean(),{model.updateFeature("offline.enabled",it.toString())});Text("Use downloaded base map only",Modifier.padding(top=12.dp))}
     Text("Offline mode disables network overlays. Areas or zoom levels not downloaded remain blank.")
    }
    "Groups"->{
     Text("Announcement server: ${preferences.getProperty("groups.server")}")
     OutlinedTextField(group,{group=it.uppercase()},label={Text("Group")},singleLine=true)
     FlowRow(Modifier.fillMaxWidth()) {names(preferences.getProperty("groups.names")).forEach {g->TextButton(onClick={group=g}){Text(g)}}}
     OutlinedTextField(message,{message=it},label={Text("Announcement text")},modifier=Modifier.fillMaxWidth())
     Row {listOf("J" to "Join quietly","U" to "Leave","CQ" to "Announce","?" to "List groups","L" to "My groups").forEach {(action,label)->TextButton(onClick={
      runCatching {AprsServices.group(action,if(action in listOf("?","L")) "" else group,if(action=="CQ")message else "")}.onSuccess {command->send(preferences.getProperty("groups.server"),command)
       if(action in listOf("J","U")){val groups=names(preferences.getProperty("groups.names")).toMutableSet();if(action=="J")groups.add(group) else groups.remove(group);model.updateFeature("groups.names",encodedNames(groups.toList()))}
      }.onFailure {result="Check group and message length"}
     }) {Text(label)}}}
     Text("Saved groups are requested memberships. Server responses and announcements appear in Messages. Automatic keepalive is opt-in under Settings → Groups.")
    }
    "QRU"->{
     Text("Query destination: ${preferences.getProperty("kiosk.server")}")
     OutlinedTextField(qru,{qru=it.uppercase()},label={Text("Object group (INFO lists groups)")},singleLine=true)
     OutlinedTextField(radius,{radius=it},label={Text("Radius in km")},singleLine=true)
     Button(onClick={runCatching {AprsServices.qru(qru,radius.toInt())}.onSuccess {send(preferences.getProperty("kiosk.server"),it)}.onFailure {result="Check group and radius"}}){Text("Send QRU query")}
     Text("Replies appear in Messages; returned positioned objects appear on the map. You can also answer INFO and configured topics sent to your own callsign: Settings → Kiosk.")
     Text("Your kiosk: "+if(preferences.getProperty("kiosk.enabled").toBoolean())"On" else "Off")
     names(preferences.getProperty("kiosk.entries")).forEach {Text(it)}
    }
    "Bulletins"->{
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedTextField(slot,{slot=it.uppercase()},Modifier.width(100.dp),label={Text("Slot")});OutlinedTextField(group,{group=it.uppercase()},Modifier.weight(1f),label={Text("Group (0–5 characters)")})}
     OutlinedTextField(message,{message=it},label={Text("Bulletin text")},modifier=Modifier.fillMaxWidth())
     Button(onClick={scope.launch {result=runCatching {if(model.sendBulletin(slot,group,message))"Bulletin transmitted" else "No available output"}.getOrElse {"Check slot, group and text"}}}){Text("Transmit bulletin")}
     bulletins.asReversed().forEach {b->Text("${b.destination} · ${b.sender} · ${Instant.ofEpochMilli(b.time)}",color=MaterialTheme.colors.primary);Text(b.text);Divider()}
    }
    else->{TextButton(onClick={model.activity.dismiss()}){Text("Dismiss notifications")};notices.asReversed().forEach {Text("${Instant.ofEpochMilli(it.time)}  ${it.text}");Divider()};Text("Message and watched-callsign alerts, sound and cooldown settings are under Settings → Alerts.")}
   }}
  }}}
 }
}
