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

@Composable fun OperatingToolsWindow(model:AppModel,selected:String?,initialTab:String="Station",requestId:Int=0,dismiss:()->Unit) {
 val scope=rememberCoroutineScope()
 val weather by model.archive.weather.collectAsState()
 val telemetry by model.archive.telemetry.collectAsState()
 val definitions by model.archive.definitions.collectAsState()
 val bulletins by model.archive.bulletins.collectAsState()
 val notices by model.activity.notices.collectAsState()
 val webAlerts by model.weatherAlerts.alerts.collectAsState()
 val legacyAlerts by model.legacyWeather.alerts.collectAsState()
 val alerts=webAlerts+legacyAlerts
 val legacyStatus by model.legacyWeather.status.collectAsState()
 val weatherStatus by model.weatherAlerts.status.collectAsState()
 val download by model.offline.status.collectAsState()
 val downloading by model.offline.running.collectAsState()
 val revision by model.settingsRevision.collectAsState()
 val preferences=remember(revision) {java.util.Properties().apply {putAll(model.settings)}}
 val archiveStatus by model.archive.status.collectAsState()
 val dropped by model.archive.dropped.collectAsState()
 var tab by remember {mutableStateOf(initialTab)}
 var result by remember {mutableStateOf("")}
 var call by remember {mutableStateOf(selected.orEmpty())}
 var channel by remember {mutableStateOf(0)}
 var group by remember {mutableStateOf("")}
 var message by remember {mutableStateOf("")}
 var qru by remember {mutableStateOf("INFO")}
 var radius by remember {mutableStateOf("50")}
 var slot by remember {mutableStateOf("1")}
 var interval by remember {mutableStateOf("1800")}
 var route by remember {mutableStateOf("is")}
 var south by remember {mutableStateOf((preferences.getProperty("latitude").toDouble()-0.03).toString())}
 var north by remember {mutableStateOf((preferences.getProperty("latitude").toDouble()+0.03).toString())}
 var west by remember {mutableStateOf((preferences.getProperty("longitude").toDouble()-0.03).toString())}
 var east by remember {mutableStateOf((preferences.getProperty("longitude").toDouble()+0.03).toString())}
 var zoomMin by remember {mutableStateOf("10")};var zoomMax by remember {mutableStateOf("13")}
 var plan by remember {mutableStateOf<List<Tile>>(emptyList())}
 fun send(peer:String,text:String){scope.launch {result=runCatching {model.send(peer,text);"Queued to $peer; see Messages for delivery"}.getOrElse {"Unable to queue: check callsigns and message length"}}}
 Window(icon=AppIcon.window,onCloseRequest=dismiss,title="Tools",state=rememberWindowState(width=1100.dp,height=800.dp)) {
  LaunchedEffect(initialTab,requestId) {tab=initialTab;result="";window.toFront();window.requestFocus()}
  MaterialTheme(colors=MaterialTheme.colors) {Surface {Column(Modifier.fillMaxSize().padding(18.dp)) {
   Row(Modifier.weight(1f)) {
    SectionNavigator(toolSections,tab,Modifier.width(230.dp).fillMaxHeight()) {tab=it;result=""}
    Divider(Modifier.fillMaxHeight().width(1.dp))
    Column(Modifier.weight(1f).padding(start=18.dp)) {
     HelpText(toolSections.first {it.id==tab}.title,style=MaterialTheme.typography.h5)
   if(result.isNotBlank()) HelpText(result,Modifier.padding(vertical=8.dp),color=MaterialTheme.colors.primary)
   ScrollableForm(Modifier.weight(1f).fillMaxWidth()) {when(tab) {
    "GPX import","Satellites","GPS sky","Local sensors","Weather boundaries"->AdvancedTools(model,tab)
    "Frequency","Platforms","Email"->{AdditionalTools(model,tab);if(tab=="Frequency")FrequencyEditor(model)}
    "Station","Radio DX","Direction finding"->TacticalTools(model,selected,tab)
    "Weather"->{
     HelpText("Received APRS weather",fontSize=20.sp)
     weather.values.sortedBy {it.call}.forEach {w->
      HelpText("${w.call} · ${Instant.ofEpochMilli(w.time)}",color=MaterialTheme.colors.primary)
      fun f(n:Double?)=n?.let {"%.1f".format(it)} ?: "—"
      HelpText("${f(w.temperatureC)} °C · wind ${w.windDegrees ?: "—"}° ${f(w.windKph)} km/h · gust ${f(w.gustKph)} km/h · RH ${w.humidity ?: "—"}% · ${f(w.pressureHpa)} hPa · rain 1h ${f(w.rainHourMm)} mm / 24h ${f(w.rainDayMm)} mm")
      Divider()
     }
     if(weather.isEmpty())HelpText("No weather packets received yet.")
     HelpText(weatherStatus,fontSize=16.sp)
     if(legacyStatus.isNotBlank())HelpText(legacyStatus)
     Row {HelpCheckbox(UiHelp.action("Fetch alerts for the configured fixed location"),preferences.getProperty("weather.enabled").toBoolean(),{model.updateFeature("weather.enabled",it.toString())});HelpText("Fetch alerts for the configured fixed location",Modifier.padding(top=12.dp))}
     HelpText("Alert source and radar XYZ overlay are configurable in Settings → Weather alerts.")
     alerts.forEach {a->HelpText("${a.severity}: ${a.event}",color=Color(0xFFFFC078));HelpText(a.headline);HelpText(a.description);HelpText("Expires ${a.expires}");Divider()}
    }
    "Telemetry"->{
     HelpText("Own telemetry: ${if(preferences.getProperty("telemetry.enabled").toBoolean())"On" else "Off"}. Channels, equations, timing and routing: Settings → Telemetry.")
     HelpField(UiHelp.field("Station callsign"),call,{call=it.uppercase()},label={HelpText("Station callsign")},singleLine=true)
     FlowRow(Modifier.fillMaxWidth()) {telemetry.keys.sorted().forEach {c->HelpTextButton(UiHelp.action(c),onClick={call=c}) {HelpText(c)}}}
     Row {(0..4).forEach {i->HelpTextButton(UiHelp.action("A${i+1}"),onClick={channel=i}) {HelpText("A${i+1}")}}}
     val samples=telemetry[call].orEmpty();val defs=definitions[call].orEmpty()
     val label=defs["PARM"]?.values?.getOrNull(channel) ?: "Analog ${channel+1}"
     val unit=defs["UNIT"]?.values?.getOrNull(channel).orEmpty()
     val readings=samples.mapNotNull {sample->AprsObservations.engineering(sample.analog[channel],channel,defs["EQNS"])?.let {sample.time to it}}
     val values=readings.map {it.second}
     HelpText("$label $unit · ${samples.size} samples")
     if(values.isNotEmpty()) {
      val low=values.min();val high=values.max();HelpText("Latest ${values.last()} · min $low · max $high")
      Canvas(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFF14262C))) {
       if(readings.size>1) readings.zipWithNext().forEach {(a,b)->
        fun y(v:Double)=size.height-((v-low)/(high-low).takeIf {it>0}.let {it?:1.0}*size.height).toFloat()
        fun x(t:Long)=((t-readings.first().first).toDouble()/(readings.last().first-readings.first().first).coerceAtLeast(1)*size.width).toFloat()
        drawLine(Color(0xFF72E4C3),Offset(x(a.first),y(a.second)),Offset(x(b.first),y(b.second)),2f)
       }
      }
      HelpText("Time: ${Instant.ofEpochMilli(readings.first().first)} to ${Instant.ofEpochMilli(readings.last().first)}")
      HelpText("Digital bits: ${samples.last().bits}; sense: ${defs["BITS"]?.values?.firstOrNull() ?: "unspecified"}")
     } else HelpText("No classic T# telemetry received for this station.")
    }
    "Tracks"->{
     HelpText("Automatic CSV: ${preferences.getProperty("logging.csv")} · GPX: ${preferences.getProperty("logging.gpx")}. Configure under Settings → Track logging.")
     if(archiveStatus.isNotBlank())HelpText(archiveStatus)
     if(dropped>0)HelpText("$dropped packets omitted from history because the input queue filled")
     HelpField(UiHelp.field("Station or object name"),call,{call=it.uppercase()},label={HelpText("Station or object name")},singleLine=true)
     Row {listOf("GPX","CSV").forEach {format->HelpButton(UiHelp.action("Export $format"),onClick={scope.launch {result=runCatching {"Saved ${model.archive.export(call,format)}"}.getOrElse {it.message ?: "Export failed"}}},modifier=Modifier.padding(end=10.dp)){HelpText("Export $format")}}}
     HelpText("Export the selected station’s saved track. Files are saved in the exports folder beside APRSIS64.exe.")
     HelpField(UiHelp.field("Recipient callsign-SSID"),message,{message=it.uppercase()},label={HelpText("Recipient callsign-SSID")},singleLine=true)
     HelpButton(UiHelp.action("Send position in message"),onClick={scope.launch {val point=model.archive.track(call).lastOrNull();if(point==null) result="No recorded position" else send(message,"$call ${"%.5f".format(java.util.Locale.US,point.latitude)},${"%.5f".format(java.util.Locale.US,point.longitude)}")}}){HelpText("Send position in message")}
    }
    "Offline"->{
     HelpText("Download a base-map area",fontSize=20.sp)
     HelpText("Uses your configured map provider. Choose a small region and preview the tile count before downloading.")
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {HelpField(UiHelp.field("South"),south,{south=it;plan=emptyList()},Modifier.weight(1f),label={HelpText("South")},fill=true);HelpField(UiHelp.field("North"),north,{north=it;plan=emptyList()},Modifier.weight(1f),label={HelpText("North")},fill=true)}
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {HelpField(UiHelp.field("West"),west,{west=it;plan=emptyList()},Modifier.weight(1f),label={HelpText("West")},fill=true);HelpField(UiHelp.field("East"),east,{east=it;plan=emptyList()},Modifier.weight(1f),label={HelpText("East")},fill=true)}
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {HelpField(UiHelp.field("Minimum zoom"),zoomMin,{zoomMin=it;plan=emptyList()},Modifier.weight(1f),label={HelpText("Minimum zoom")},fill=true);HelpField(UiHelp.field("Maximum zoom"),zoomMax,{zoomMax=it;plan=emptyList()},Modifier.weight(1f),label={HelpText("Maximum zoom")},fill=true)}
     Row {
      HelpButton(UiHelp.action("Preview"),onClick={runCatching {TilePlan.region(south.toDouble(),west.toDouble(),north.toDouble(),east.toDouble(),zoomMin.toInt(),zoomMax.toInt(),preferences.getProperty("offline.maximum_tiles").toInt())}.onSuccess {plan=it;result="${it.size} tiles"}.onFailure {result=it.message ?: "Check area and zoom range"}}){HelpText("Preview")}
      HelpButton(UiHelp.action("Download"),onClick={model.offline.download(plan)},enabled=plan.isNotEmpty()&&!downloading,modifier=Modifier.padding(horizontal=10.dp)){HelpText("Download")}
      HelpTextButton(UiHelp.action("Cancel download"),onClick={model.offline.cancel()},enabled=downloading){HelpText("Cancel download")}
     }
     HelpText(download)
     Row {HelpCheckbox(UiHelp.action("Use downloaded base map only"),preferences.getProperty("offline.enabled").toBoolean(),{model.updateFeature("offline.enabled",it.toString())});HelpText("Use downloaded base map only",Modifier.padding(top=12.dp))}
     HelpText("Offline mode disables network overlays. Areas or zoom levels not downloaded remain blank.")
    }
    "Groups"->{
     val memberships by model.groups.entries.collectAsState();val groupReplies by model.groups.replies.collectAsState()
     memberships.forEach {m->HelpText("${m.group}: ${m.state} · ${Instant.ofEpochMilli(m.time)}")}
     groupReplies.takeLast(8).forEach {HelpText(it)}
     HelpText("Announcement server: ${preferences.getProperty("groups.server")}")
     HelpField(UiHelp.field("Group"),group,{group=it.uppercase()},label={HelpText("Group")},singleLine=true)
     FlowRow(Modifier.fillMaxWidth()) {names(preferences.getProperty("groups.names")).forEach {g->HelpTextButton(UiHelp.action(g),onClick={group=g}){HelpText(g)}}}
     HelpField(UiHelp.field("Announcement text"),message,{message=it},label={HelpText("Announcement text")},modifier=Modifier.fillMaxWidth(),fill=true)
     Row {listOf("J" to "Join quietly","U" to "Leave","CQ" to "Announce","?" to "List groups","L" to "My groups").forEach {(action,label)->HelpTextButton(UiHelp.action(label),onClick={
      runCatching {AprsServices.group(action,if(action in listOf("?","L")) "" else group,if(action=="CQ")message else "")}.onSuccess {command->send(preferences.getProperty("groups.server"),command)
       if(action in listOf("J","U")){val groups=names(preferences.getProperty("groups.names")).toMutableSet();if(action=="J")groups.add(group) else groups.remove(group);model.updateFeature("groups.names",encodedNames(groups.toList()))}
      }.onFailure {result="Check group and message length"}
     }) {HelpText(label)}}}
     HelpText("Membership confirmations show the last server response time. My groups requests a fresh list; unrecognized replies remain visible. Automatic keepalive is opt-in under Settings → Group messages.")
    }
    "QRU"->{
     HelpText("Query destination: ${preferences.getProperty("kiosk.server")}")
     HelpField(UiHelp.field("Object group (INFO lists groups)"),qru,{qru=it.uppercase()},label={HelpText("Object group (INFO lists groups)")},singleLine=true)
     HelpField(UiHelp.field("Radius in km"),radius,{radius=it},label={HelpText("Radius in km")},singleLine=true)
     HelpButton(UiHelp.action("Send QRU query"),onClick={runCatching {AprsServices.qru(qru,radius.toInt())}.onSuccess {send(preferences.getProperty("kiosk.server"),it)}.onFailure {result="Check group and radius"}}){HelpText("Send QRU query")}
     HelpText("Replies appear in Messages; returned positioned objects appear on the map. Configure your object groups in Settings → Objects & items and enable the QRU responder in Settings → Tactical overlays.")
     HelpText("QRU object responder: "+if(preferences.getProperty("tactical.qru_enabled").toBoolean())"On" else "Off")
     HelpText("Text kiosk: "+if(preferences.getProperty("kiosk.enabled").toBoolean())"On" else "Off")
     names(preferences.getProperty("kiosk.entries")).forEach {HelpText(it)}
    }
    "Bulletins"->{
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {HelpField(UiHelp.field("Slot"),slot,{slot=it.uppercase()},Modifier.width(100.dp),label={HelpText("Slot")},fill=true);HelpField(UiHelp.field("Group (0–5 characters)"),group,{group=it.uppercase()},Modifier.weight(1f),label={HelpText("Group (0–5 characters)")},fill=true)}
     HelpField(UiHelp.field("Bulletin text"),message,{message=it},label={HelpText("Bulletin text")},modifier=Modifier.fillMaxWidth(),fill=true)
     HelpButton(UiHelp.action("Transmit bulletin"),onClick={scope.launch {result=runCatching {if(model.sendBulletin(slot,group,message))"Bulletin transmitted" else "No available output"}.getOrElse {"Check slot, group and text"}}}){HelpText("Transmit bulletin")}
     HelpField(UiHelp.field("Repeat interval (seconds)"),interval,{interval=it},label={HelpText("Repeat interval (seconds)")},singleLine=true)
     Row {listOf("is","rf","both").forEach {r->HelpTextButton(UiHelp.action(if(route==r)"[${r.uppercase()}]" else r.uppercase()),onClick={route=r}){HelpText(if(route==r)"[${r.uppercase()}]" else r.uppercase())}}}
     HelpButton(UiHelp.action("Save scheduled bulletin"),onClick={runCatching {
      val entry="$slot|$group|$interval|$route|$message";ScheduledBulletin.parse(entry)
      val saved=names(model.settings.getProperty("bulletins.entries")).filterNot {val b=ScheduledBulletin.parse(it);b.slot==slot && b.group==group}+entry
      check(model.updateFeature("bulletins.entries",encodedNames(saved)));result="Saved bulletin"
     }.onFailure {result="Check slot, group, interval and text"}}){HelpText("Save scheduled bulletin")}
     Row {HelpCheckbox(UiHelp.action("Transmit saved bulletins"),preferences.getProperty("bulletins.enabled").toBoolean(),{model.updateFeature("bulletins.enabled",it.toString())});HelpText("Transmit saved bulletins",Modifier.padding(top=12.dp))}
     names(preferences.getProperty("bulletins.entries")).forEach {entry->
      val b=ScheduledBulletin.parse(entry)
      HelpText("${b.slot} ${b.group} · ${b.seconds}s · ${b.route.uppercase()} · ${b.text}")
      Row {HelpTextButton(UiHelp.action("Edit"),onClick={slot=b.slot;group=b.group;interval=b.seconds.toString();route=b.route;message=b.text}){HelpText("Edit")};HelpTextButton(UiHelp.action("Remove"),onClick={model.updateFeature("bulletins.entries",encodedNames(names(model.settings.getProperty("bulletins.entries"))-entry))}){HelpText("Remove")}}
     }
     bulletins.asReversed().forEach {b->HelpText("${b.destination} · ${b.sender} · ${Instant.ofEpochMilli(b.time)}",color=MaterialTheme.colors.primary);HelpText(b.text);Divider()}
    }
    else->{HelpTextButton(UiHelp.action("Dismiss notifications"),onClick={model.activity.dismiss()}){HelpText("Dismiss notifications")};notices.asReversed().forEach {HelpText("${Instant.ofEpochMilli(it.time)}  ${it.text}");Divider()};HelpText("Message and watched-callsign alerts, sound and cooldown settings are under Settings → Notifications.")}
   }}
    }
   }
  }}}
 }
}
