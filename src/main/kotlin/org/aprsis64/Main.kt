// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import java.awt.Desktop
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import net.osmand.shared.aprs.*
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.map.*
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private val Accent = Color(0xFF72E4C3)
private val Panel = Color(0xFF15252D)
private val Muted = Color(0xFFA2B4BE)

@OptIn(org.maplibre.compose.util.ExperimentalMaplibreComposeApi::class)
fun main(args: Array<String>) {
    val home = PortableHome.resolve()
    PortableHome.prepare(home)
    SerialDevices.prepare(home)
    if("--probe" in args) System.setProperty("aprsis64.probe","true")
    val demoMode = "--demo" in args || System.getProperty("aprsis64.demo") == "true"
    val model = try {AppModel(home, demoMode)} catch(_: Exception) {
        javax.swing.JOptionPane.showMessageDialog(null,"Unable to load settings.toml in $home. Check the settings syntax and values. Your file has not been reset.","APRSIS64",javax.swing.JOptionPane.ERROR_MESSAGE)
        return
    }
    DefaultMapRuntime.configure {
        cacheFile = kotlinx.io.files.Path(home.resolve("cache/tiles.db").toString())
        maximumCacheSizeBytes = model.settings.getProperty("map.cache_megabytes").toLong() * 1024 * 1024
    }
    println("APRSIS64 0.5.0 | Java ${System.getProperty("java.version")} | Vulkan / Direct3D12 | home=$home")
    if(demoMode) model.localDemo()
    else model.connectSaved()
    application {
        Window(onCloseRequest={ model.close(); exitApplication() }, title="APRSIS64",
            state=rememberWindowState(width=1480.dp,height=900.dp)) {
            MaterialTheme(colors=darkColors(primary=Accent,background=Color(0xFF0E1B22),surface=Panel)) {
                Surface(Modifier.fillMaxSize()) { ProvideMapPresentationHost(host=rememberAwtComposeMapPresentationHost(window)) { App(model) } }
            }
        }
    }
}

@Composable private fun App(model: AppModel) {
    val scope = rememberCoroutineScope()
    val account by model.account.collectAsState()
    val connection by model.state.collectAsState()
    val stations by model.stations.collectAsState()
    val demo by model.demo.collectAsState()
    val error by model.error.collectAsState()
    val revision by model.database.revision.collectAsState()
    val settingsRevision by model.settingsRevision.collectAsState()
    val beacon by model.beaconState.collectAsState()
    val portStatuses by model.portStatuses.collectAsState()
    val beaconCenter by model.beaconCenter.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var tools by remember {mutableStateOf(false)}
    val tracking=remember {mutableStateListOf<String>().apply {addAll(names(model.settings.getProperty("display.tracking_calls")))}}
    var search by remember {mutableStateOf(model.settings.getProperty("display.search"))}
    var kind by remember {mutableStateOf(model.settings.getProperty("display.kind"))}
    var sort by remember {mutableStateOf(model.settings.getProperty("display.sort"))}
    val notices by model.activity.notices.collectAsState()
    val filtered=stations.filter {station->
        station.callsign.contains(search,true) && System.currentTimeMillis()-station.heardAtMillis<=model.settings.getProperty("display.maximum_age_minutes").toLong()*60000 &&
        when(kind) {"weather"->station.symbol=='_';"moving"->Regex("^\\d{3}/(\\d{3})").find(station.comment)?.groupValues?.get(1)?.toIntOrNull()?.let {it>0}==true;"objects"->station.callsign in model.archive.objects.value;else->true}
    }.let {list->when(sort) {"recent"->list.sortedByDescending {it.heardAtMillis};"distance"->list.sortedBy {AdaptiveBeaconPolicy.distance(AprsFix(model.settings.getProperty("position.latitude").toDouble(),model.settings.getProperty("position.longitude").toDouble(),0),AprsFix(it.latitude,it.longitude,0))};else->list.sortedBy {it.callsign}}}

    var settings by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf(false) }
    var packetMonitor by remember {mutableStateOf(false)}
    var portWorkspace by remember {mutableStateOf(false)}
    var rows by remember { mutableStateOf<List<ChatRow>>(emptyList()) }
    LaunchedEffect(account,revision,selected) {
        rows=model.database.rows(account)
        selected?.let { model.database.markRead(account,it) }
    }
    LaunchedEffect(account) { selected=null }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(18.dp,12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(demo) "Demo · $account" else account,color=Muted)
            Spacer(Modifier.weight(1f))
            Text(connection.name,color=if(connection==AprsConnectionState.VERIFIED) Accent else Muted)
            Spacer(Modifier.width(14.dp))
            OutlinedButton(onClick={ settings=true }) { Text("Connection") }
            TextButton(onClick={preferences=true}) {Text("Settings")}
            TextButton(onClick={portWorkspace=true}) {Text("Ports")}
            TextButton(onClick={packetMonitor=true}) {Text("Packets")}
            TextButton(onClick={tools=true}) {Text("Tools")}
            Button(onClick={model.transmitBeacon()},enabled=beacon.enabled && account.isNotBlank()) {Text("Transmit")}
            Spacer(Modifier.width(8.dp))
            if(connection==AprsConnectionState.STOPPED) Button(onClick={ model.localDemo() }) { Text("Local demo") }
            else OutlinedButton(onClick={ model.disconnect(remember=true) }) { Text("Disconnect IS") }
            TextButton(onClick={about=true}) {Text("About")}
        }
        if(error.isNotBlank()) Text(error,Modifier.fillMaxWidth().background(Color(0xFF6B332B)).padding(10.dp))
        if(notices.isNotEmpty()) Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal=12.dp)) {
            Text(notices.last().text,Modifier.weight(1f),fontSize=12.sp)
            TextButton(onClick={tools=true}) {Text("View alerts")};TextButton(onClick={model.activity.dismiss()}){Text("Dismiss")}
        }
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(225.dp).fillMaxHeight().background(Panel).padding(14.dp)) {
                Text("STATIONS",color=Muted,fontSize=12.sp)
                OutlinedTextField(search,{search=it},label={Text("Find station")},singleLine=true,modifier=Modifier.fillMaxWidth())
                TextButton(onClick={kind=listOf("all","weather","moving","objects").let {it[(it.indexOf(kind)+1)%it.size]}}){Text("Type: $kind")}
                TextButton(onClick={sort=listOf("callsign","recent","distance").let {it[(it.indexOf(sort)+1)%it.size]}}){Text("Sort: $sort")}
                TextButton(onClick={val p=java.util.Properties().apply {putAll(model.settings);setProperty("display.search",search);setProperty("display.kind",kind);setProperty("display.sort",sort);setProperty("display.tracking_calls",encodedNames(tracking.toList()))};model.savePreferences(p,model.entities)}){Text("Save view defaults")}
                Text("${filtered.size} / ${stations.size}",Modifier.padding(vertical=6.dp),fontSize=16.sp)
                if(selected!=null) TextButton(onClick={if(selected !in tracking && tracking.size<8)tracking.add(selected!!)}){Text("Open tracking window")}

                LazyColumn(Modifier.weight(1f)) { items(filtered,key={it.callsign}) { station ->
                    Column(Modifier.fillMaxWidth().clickable { selected=station.callsign }.padding(vertical=10.dp)) {
                        Text(station.callsign,color=if(selected==station.callsign) Accent else Color.White,fontWeight=FontWeight.Bold)
                        Text("%.3f, %.3f".format(station.latitude,station.longitude),fontSize=11.sp,color=Muted)
                    }; Divider(color=Color(0xFF2B3D46))
                } }
                Text(if(beacon.enabled) "Beacon: ${beacon.source}" else "Beacon: Off",fontSize=12.sp,color=Accent)
                Text(beacon.detail,fontSize=11.sp,color=Muted)
                portStatuses.forEach {p->Text("${p.name}: ${p.detail} · RX ${p.received} / TX ${p.transmitted}"+(if(p.dropped>0) " / Dropped ${p.dropped}" else ""),fontSize=11.sp,color=Muted)}
                beacon.sentAt?.let {Text("Last sent "+DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)),fontSize=11.sp,color=Muted)}
            }
            val own=beacon.position?.takeIf {account.isNotBlank()}?.let {p -> AprsStation(account,p.fix.latitude,p.fix.longitude,
                model.settings.getProperty("beacon.symbol_table").single(),model.settings.getProperty("beacon.symbol").single(),"",p.fix.timeMillis)}
            val localStations=listOfNotNull(own)+beacon.entities
            key(settingsRevision) {MapPanel(localStations+filtered.filter {remote->localStations.none {it.callsign==remote.callsign}},selected,model,beaconCenter,Modifier.weight(1f).fillMaxHeight()) { selected=it }}
            ChatPanel(account,selected,{selected=it},rows,model,Modifier.width(350.dp).fillMaxHeight())
        }
    }
    tracking.toList().forEach {call->key(call) {TrackingWindow(model,call) {tracking.remove(call)}}}
    if(tools) OperatingToolsWindow(model,selected) {tools=false}
    if(settings) ConnectionDialog(model) {settings=false}
    if(preferences) PreferencesDialog(model) {preferences=false}
    if(packetMonitor) PacketMonitorWindow(model) {packetMonitor=false}
    if(portWorkspace) PortWorkspaceWindow(model) {portWorkspace=false}
    if(about) AlertDialog(onDismissRequest={about=false},title={Text("APRSIS64 0.5.0")},text={
        Text("Copyright 2026 APRSIS64 contributors.\nAGPL-3.0. No warranty.\n\nContains GPLv3 shared components. Third-party license notices are included in the app folder.")
    },confirmButton={TextButton(onClick={Desktop.getDesktop().browse(URI(model.settings.getProperty("services.source_url")))}) {Text("Source & license")}},dismissButton={TextButton(onClick={about=false}) {Text("Close")}})
}

private fun stationJson(stations: List<AprsStation>) = buildJsonObject {
    put("type","FeatureCollection")
    putJsonArray("features") { stations.forEach { s -> addJsonObject {
        put("type","Feature"); putJsonObject("properties") {put("callsign",s.callsign)}
        putJsonObject("geometry") {put("type","Point"); putJsonArray("coordinates") {add(s.longitude); add(s.latitude)}}
    } } }
}.toString()

@Composable internal fun MapPanel(stations: List<AprsStation>, selected: String?, model: AppModel, beaconCenter: AprsFix?, modifier: Modifier, forceFollow:Boolean=false, select: (String)->Unit) {
    val data = remember(stations) { GeoJsonData.JsonString(stationJson(stations)) }
    val style = remember { BaseStyle.Json(ServicePreferences.style(mapSettings(model))) }
    val historyRevision by model.archive.revision.collectAsState()
    var trail by remember(selected) {mutableStateOf<List<AprsFix>>(emptyList())}
    var follow by remember {mutableStateOf(forceFollow || model.settings.getProperty("display.follow").toBoolean())}
    var gotoLat by remember {mutableStateOf("")};var gotoLon by remember {mutableStateOf("")}
    var navigationError by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(selected,historyRevision) {trail=selected?.let {model.archive.track(it)} ?: emptyList()}

    val initial=remember {Position(model.settings.getProperty("longitude").toDouble(),model.settings.getProperty("latitude").toDouble())}
    val state=rememberMapState(baseStyle=style,initialCameraPosition=CameraPosition(center=if(forceFollow) stations.firstOrNull {it.callsign==selected}?.let {Position(it.longitude,it.latitude)} ?: initial else initial,zoom=model.settings.getProperty("map.initial_zoom").toDouble())) {
        if(model.settings.getProperty("display.trails").toBoolean() && trail.size>1) {
            val trailSource=rememberGeoJsonSource(GeoJsonData.JsonString(trailJson(trail)))
            LineLayer(id="station-trail",source=trailSource,color=const(Color(0xFFB33E20)),width=const(3.dp))
        }
        val source=rememberGeoJsonSource(data)
        CircleLayer(id="aprs-stations",source=source,radius=const(7.dp),color=const(Color(0xFF006D65)),strokeWidth=const(2.dp),strokeColor=const(Color.White),hitPadding=8.dp,
            onClick={features -> features.firstOrNull()?.properties?.get("callsign")?.jsonPrimitive?.content?.let(select); ClickResult.Consume})
        if(model.settings.getProperty("display.symbols").toBoolean()) stations.groupBy {"${it.symbolTable.code}-${it.symbol.code}"}.forEach {(id,group)->key(id) {
            val symbols=rememberGeoJsonSource(GeoJsonData.JsonString(stationJson(group)))
            SymbolLayer(id="symbol-$id",source=symbols,iconImage=image(StationSymbols.bitmap(group.first().symbolTable,group.first().symbol)),iconAllowOverlap=const(true),
                onClick={features->features.firstOrNull()?.properties?.get("callsign")?.jsonPrimitive?.content?.let(select);ClickResult.Consume})
        }}
    }
    val mapReady=state.style.loadState == StyleLoadState.Ready
    var mapError by remember {mutableStateOf("")}
    LaunchedEffect(state) {
        if(System.getProperty("aprsis64.probe") == "true") {
            delay(15_000)
            val point=state.screenLocationFromPosition(initial)
            if(point != null) {
                val hits=state.queryRenderedFeatures(point,layerIds=setOf("aprs-stations"))
                println("MAP_PROBE bearing=${state.cameraPosition.bearing} zoom=${state.cameraPosition.zoom} hits=${hits.map { it.properties?.get("callsign") }}")
            } else System.err.println("MAP_PROBE failed: no rendered viewport")
        }
    }
    LaunchedEffect(state) {state.events.collect { event -> when(event) {
        MapEvent.StyleLoaded -> {mapError=""; println("MAP style loaded")}
        MapEvent.Idle -> println("MAP idle")
        is MapEvent.StyleLoadFailed -> {mapError="Unable to load map"; System.err.println("MAP style failed: ${event.reason}")}
        is MapEvent.SourceDataFailed -> {mapError="Unable to load map data"; System.err.println("MAP source failed: ${event.sourceId}")}
        is MapEvent.FrameRendered -> if(event.stats?.frameCount == 1L) println("MAP_FIRST_FRAME ${event.stats}")
        else -> Unit
    } } }
    LaunchedEffect(selected,mapReady) {
        if(mapReady) stations.firstOrNull {it.callsign==selected}?.let {
            state.animateCamera(CameraUpdate(center=Position(it.longitude,it.latitude),bearing=0.0,pitch=0.0))
        }
    }
    val followed=stations.firstOrNull {it.callsign==selected}
    LaunchedEffect(mapReady,follow,followed?.latitude,followed?.longitude) {if(mapReady && follow && followed!=null) state.animateCamera(CameraUpdate(center=Position(followed.longitude,followed.latitude),bearing=0.0,pitch=0.0))}
    LaunchedEffect(beaconCenter,mapReady) {if(mapReady) beaconCenter?.let {state.animateCamera(CameraUpdate(center=Position(it.longitude,it.latitude),bearing=0.0,pitch=0.0))}}
    Box(modifier) {
        MaplibreMap(state=state,interactions=MapInteractions {camera {rotate {enabled=false}; pitch {enabled=false}}},
            cameraConstraints=CameraConstraints {maxPitch=0.0; minZoom=model.settings.getProperty("map.minimum_zoom").toDouble(); maxZoom=model.settings.getProperty("map.maximum_zoom").toDouble()},renderOptions=RenderOptions {maximumFps=model.settings.getProperty("map.maximum_fps").toInt()}, overlay={})
        Column(Modifier.align(Alignment.TopStart).padding(6.dp).background(Panel.copy(alpha=0.94f)).padding(6.dp)) {
            Row {
                TextButton(onClick={scope.launch {state.animateCamera(CameraUpdate(zoom=(state.cameraPosition.zoom+1).coerceAtMost(model.settings.getProperty("map.maximum_zoom").toDouble())))}}){Text("+")}
                TextButton(onClick={scope.launch {state.animateCamera(CameraUpdate(zoom=(state.cameraPosition.zoom-1).coerceAtLeast(model.settings.getProperty("map.minimum_zoom").toDouble())))}}){Text("−")}
                TextButton(onClick={scope.launch {state.animateCamera(CameraUpdate(center=initial,bearing=0.0,pitch=0.0))}}){Text("Home")}
                TextButton(onClick={follow=!follow}){Text(if(follow) "Following" else "Follow")}
            }
            Row {
                OutlinedTextField(gotoLat,{gotoLat=it},Modifier.width(100.dp),label={Text("Latitude")},singleLine=true)
                OutlinedTextField(gotoLon,{gotoLon=it},Modifier.width(110.dp),label={Text("Longitude")},singleLine=true)
                TextButton(onClick={runCatching {AprsFix(gotoLat.toDouble(),gotoLon.toDouble(),0)}.onSuccess {p->follow=false;navigationError="";scope.launch {state.animateCamera(CameraUpdate(center=Position(p.longitude,p.latitude),bearing=0.0,pitch=0.0))}}.onFailure {navigationError="Check coordinates"}}){Text("Go")}
            }
            if(followed!=null) Text(navigationSummary(model,followed),fontSize=11.sp)
            if(navigationError.isNotBlank())Text(navigationError,fontSize=11.sp)
        }
        Text(model.settings.getProperty("map.attribution"),Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.White.copy(alpha=0.88f))
            .clickable {Desktop.getDesktop().browse(URI(model.settings.getProperty("map.attribution_url")))}.padding(5.dp),color=Color(0xFF263740),fontSize=11.sp)
        if(mapError.isNotEmpty()) Text(mapError,Modifier.align(Alignment.TopStart).padding(12.dp).background(Panel).padding(10.dp),color=Accent,fontSize=12.sp)
    }
}

@Composable private fun ChatPanel(account: String, peer: String?, select: (String?)->Unit, all: List<ChatRow>,model: AppModel,modifier: Modifier) {
    val scope=rememberCoroutineScope()
    var recipient by remember {mutableStateOf("")}
    var draft by remember(account,peer) {mutableStateOf("")}
    var sending by remember {mutableStateOf(false)}
    val focusManager=LocalFocusManager.current
    val history=all.filter {it.peer==peer}
    val list=rememberLazyListState()
    LaunchedEffect(history.size) {if(history.isNotEmpty()) list.animateScrollToItem(history.lastIndex)}
    Column(modifier.background(Panel).padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(peer==null) "MESSAGES" else peer,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Accent)
            Spacer(Modifier.weight(1f)); if(peer!=null) TextButton(onClick={select(null)}) {Text("Inbox")}
        }
        if(peer==null) {
            Row(Modifier.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(recipient,{recipient=it.uppercase()},Modifier.weight(1f),label={Text("Callsign-SSID")},singleLine=true)
                TextButton(onClick={select(recipient)},enabled=AprsProtocol.validCallsign(recipient)&&recipient!=account&&account.isNotBlank()) {Text("Chat")}
            }
            val threads=all.groupBy {it.peer}.values.sortedByDescending {it.last().time}
            LazyColumn {items(threads,key={it.last().peer}) {thread ->
                Column(Modifier.fillMaxWidth().clickable {select(thread.last().peer)}.padding(vertical=12.dp)) {
                    Text(thread.last().peer+(if(thread.any {it.unread}) "  ●" else ""),fontWeight=FontWeight.Bold)
                    Text(thread.last().body,maxLines=2,color=Muted,fontSize=12.sp)
                }; Divider()
            } }
        } else {
            Text("As ${account.ifBlank {"—"}}",fontSize=12.sp,color=Muted)
            LazyColumn(Modifier.weight(1f).padding(vertical=12.dp),state=list,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(history,key={it.id}) {row -> Column(Modifier.fillMaxWidth().background(if(row.outgoing) Color(0xFF24473F) else Color(0xFF263740)).padding(10.dp)) {
                    Text(if(row.outgoing) "You" else row.peer,color=Accent,fontSize=11.sp)
                    Text(row.body,Modifier.padding(vertical=5.dp),fontSize=14.sp)
                    if(row.outgoing && row.status in listOf("NO_ACK","REJECTED")) TextButton(onClick={scope.launch {model.database.retry(account,row.id)}}) {Text("Retry as new message")}
                    Text(DateTimeFormatter.ofPattern("MMM d HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(row.time))+" · "+row.status.replace('_',' '),fontSize=9.sp,color=Muted)
                } }
            }
            OutlinedTextField(draft,{draft=it},Modifier.fillMaxWidth().onPreviewKeyEvent {event->if(event.type==KeyEventType.KeyDown && event.key==Key.Tab) {focusManager.moveFocus(if(event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next);true} else false}.padding(top=8.dp),label={Text("Message")},maxLines=3)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${draft.length}/67",color=Muted,fontSize=10.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick={sending=true; scope.launch {try {model.send(peer,draft); draft=""} catch(_:Exception){model.error.value="Unable to save this message."} finally {sending=false}}},
                    enabled=!sending&&AprsMessages.validText(draft)&&AprsProtocol.validCallsign(account)&&AprsProtocol.validCallsign(peer)&&account!=peer) {Text("Send")}
            }
        }
    }
}

@Composable private fun ConnectionDialog(model: AppModel, dismiss: ()->Unit) {
    var call by remember {mutableStateOf(model.settings.getProperty("callsign",""))}
    var ssidMenu by remember {mutableStateOf(false)}
    var pass by remember {mutableStateOf(model.settings.getProperty("passcode",""))}
    var host by remember {mutableStateOf(model.settings.getProperty("endpoint"))}
    val presets=model.servers
    var preset by remember {mutableStateOf(presets.indexOfFirst {it.endpoint==host})}
    var menu by remember {mutableStateOf(false)}
    var autoConnect by remember {mutableStateOf(model.settings.getProperty("autoConnect","true").toBoolean())}
    var lat by remember {mutableStateOf(model.settings.getProperty("latitude","28.536725"))}
    var lon by remember {mutableStateOf(model.settings.getProperty("longitude","-81.242932"))}
    DialogWindow(onCloseRequest=dismiss,title="Connection",state=rememberDialogState(width=560.dp,height=650.dp)) {
        Surface {Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("APRS-IS connection",fontSize=22.sp)
            Row {
                OutlinedTextField(call,{call=it.uppercase()},label={Text("Callsign-SSID")},singleLine=true,modifier=Modifier.weight(1f))
                Box {
                    TextButton(onClick={ssidMenu=true}) {Text("SSID")}
                    DropdownMenu(expanded=ssidMenu,onDismissRequest={ssidMenu=false}) {(0..15).forEach {ssid->
                        DropdownMenuItem(onClick={runCatching {call=AprsIdentity.callsign(call.substringBefore('-'),ssid)};ssidMenu=false}) {Text(if(ssid==0) "0 (no suffix)" else "-$ssid")}
                    }}
                }
            }
            OutlinedTextField(pass,{pass=it},label={Text("Passcode (blank = receive only)")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()) {Text(if(preset>=0) presets[preset].name else "Custom server")}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    presets.forEachIndexed { index, server -> DropdownMenuItem(onClick={preset=index; host=server.endpoint; menu=false}) {Text(server.name)} }
                    DropdownMenuItem(onClick={preset=-1; menu=false}) {Text("Custom server")}
                }
            }
            OutlinedTextField(host,{host=it},label={Text("Server URL or host:port")},enabled=preset<0,singleLine=true,modifier=Modifier.fillMaxWidth())
            Row {OutlinedTextField(lat,{lat=it},Modifier.weight(1f),label={Text("Filter latitude")}); Spacer(Modifier.width(8.dp)); OutlinedTextField(lon,{lon=it},Modifier.weight(1f),label={Text("Filter longitude")})}
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(autoConnect,{autoConnect=it}); Text("Connect on startup")}
            Spacer(Modifier.weight(1f))
            Row {TextButton(onClick=dismiss){Text("Cancel")}; Spacer(Modifier.weight(1f)); Button(onClick={if(model.connect(call,pass,host,lat.toDoubleOrNull()?:Double.NaN,lon.toDoubleOrNull()?:Double.NaN,autoConnect)) dismiss()}){Text("Connect")}}
        } }
    }
}
