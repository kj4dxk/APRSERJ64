// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
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
import java.nio.file.Path
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
import org.maplibre.compose.map.*
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private val Accent = Color(0xFF72E4C3)
private val Panel = Color(0xFF15252D)
private val Muted = Color(0xFFA2B4BE)

fun main(args: Array<String>) {
    val data = System.getProperty("aprsis64.dataDir")?.let(Path::of)
        ?: Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),"APRSIS64")
    val model = AppModel(data)
    println("APRSIS64 0.1.0 | Java ${System.getProperty("java.version")} | MapLibre Compose 0.20.0 | Vulkan / Direct3D12")
    if("--demo" in args || System.getProperty("aprsis64.demo") == "true") model.localDemo()
    application {
        Window(onCloseRequest={ model.close(); exitApplication() }, title="APRSIS64",
            state=rememberWindowState(width=1480.dp,height=900.dp)) {
            MaterialTheme(colors=darkColors(primary=Accent,background=Color(0xFF0E1B22),surface=Panel)) {
                ProvideMapPresentationHost(host=rememberAwtComposeMapPresentationHost(window)) { App(model) }
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
    var selected by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<ChatRow>>(emptyList()) }
    LaunchedEffect(account,revision,selected) {
        rows=model.database.rows(account)
        selected?.let { model.database.markRead(account,it) }
    }
    LaunchedEffect(account) { selected=null }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(18.dp,12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column { Text("APRSIS64",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Accent); Text("WINDOWS PREVIEW  /  MAP + MESSAGES",fontSize=10.sp,color=Muted) }
            Spacer(Modifier.width(28.dp))
            Text(if(demo) "LOCAL DEMO · ${account}" else account.ifBlank { "Set your callsign to connect" },color=Muted)
            Spacer(Modifier.weight(1f))
            Text(connection.name,color=if(connection==AprsConnectionState.VERIFIED) Accent else Muted)
            Spacer(Modifier.width(14.dp))
            OutlinedButton(onClick={ settings=true }) { Text("Connection") }
            Spacer(Modifier.width(8.dp))
            if(connection==AprsConnectionState.STOPPED) Button(onClick={ model.localDemo() }) { Text("Local demo") }
            else OutlinedButton(onClick={ model.disconnect() }) { Text("Disconnect") }
            TextButton(onClick={about=true}) {Text("About")}
        }
        if(error.isNotBlank()) Text(error,Modifier.fillMaxWidth().background(Color(0xFF6B332B)).padding(10.dp))
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(225.dp).fillMaxHeight().background(Panel).padding(14.dp)) {
                Text("STATIONS",color=Muted,fontSize=12.sp)
                Text("${stations.size} on the map",Modifier.padding(vertical=10.dp),fontSize=20.sp)
                if(stations.isEmpty()) Text("Connect to APRS-IS or open the local demo. Select a station to start a chat.",color=Muted)
                LazyColumn(Modifier.weight(1f)) { items(stations.take(200),key={it.callsign}) { station ->
                    Column(Modifier.fillMaxWidth().clickable { selected=station.callsign }.padding(vertical=10.dp)) {
                        Text(station.callsign,color=if(selected==station.callsign) Accent else Color.White,fontWeight=FontWeight.Bold)
                        Text("%.3f, %.3f".format(station.latitude,station.longitude),fontSize=11.sp,color=Muted)
                    }; Divider(color=Color(0xFF2B3D46))
                } }
                if(stations.size>200) Text("First 200 listed; all rendered",fontSize=11.sp,color=Muted)
                if(demo) TextButton(onClick={model.stressStations(10_000)}) {Text("Render 10,000 test stations")}
                Text("NORTH UP · LOCKED",fontSize=11.sp,color=Accent)
            }
            MapPanel(stations,selected,Modifier.weight(1f).fillMaxHeight()) { selected=it }
            ChatPanel(account,selected,{selected=it},rows,model,Modifier.width(350.dp).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().padding(12.dp,7.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(demo) "Demo messages stay on this PC. No APRS-IS or RF transmissions." else "APRS-IS only · Position beaconing off · Chats saved locally",fontSize=11.sp,color=Muted)
            Text("pdeffenb maps · 100 km feed filter",fontSize=11.sp,color=Muted)
        }
    }
    if(settings) ConnectionDialog(model) {settings=false}
    if(about) AlertDialog(onDismissRequest={about=false},title={Text("APRSIS64 0.1.0")},text={
        Text("Copyright 2026 APRSIS64 contributors.\nAGPL-3.0, matching APRSFoundation/APRSISMO. No warranty. You may redistribute under its terms.\n\nShared core includes extracted GPLv3 code; original notices are preserved.\nMapLibre Native / Compose, JetBrains Compose, SQLite and other dependency notices are included with the source.\n\nThis is a map-and-chat prototype, not full APRSIS32 feature parity.")
    },confirmButton={TextButton(onClick={Desktop.getDesktop().browse(URI("https://github.com/kj4dxk/APRSIS64"))}) {Text("Source & license")}},dismissButton={TextButton(onClick={about=false}) {Text("Close")}})
}

private fun stationJson(stations: List<AprsStation>) = buildJsonObject {
    put("type","FeatureCollection")
    putJsonArray("features") { stations.forEach { s -> addJsonObject {
        put("type","Feature"); putJsonObject("properties") {put("callsign",s.callsign)}
        putJsonObject("geometry") {put("type","Point"); putJsonArray("coordinates") {add(s.longitude); add(s.latitude)}}
    } } }
}.toString()

@Composable private fun MapPanel(stations: List<AprsStation>, selected: String?, modifier: Modifier, select: (String)->Unit) {
    val data = remember(stations) { GeoJsonData.JsonString(stationJson(stations)) }
    val style = remember { BaseStyle.Json("""{"version":8,"name":"pdeffenb","sources":{"pdeffenb":{"type":"raster","tiles":["http://pdeffenb.homeip.net:82/raster/light/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":18,"attribution":"© OpenStreetMap contributors · pdeffenb"}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#d9e3df"}},{"id":"pdeffenb","type":"raster","source":"pdeffenb"}]}""") }
    val state=rememberMapState(baseStyle=style,initialCameraPosition=CameraPosition(center=Position(-81.242932,28.536725),zoom=12.0)) {
        val source=rememberGeoJsonSource(data)
        CircleLayer(id="aprs-stations",source=source,radius=const(7.dp),color=const(Color(0xFF006D65)),strokeWidth=const(2.dp),strokeColor=const(Color.White),hitPadding=8.dp,
            onClick={features -> features.firstOrNull()?.properties?.get("callsign")?.jsonPrimitive?.content?.let(select); ClickResult.Consume})
    }
    var mapStatus by remember {mutableStateOf("Loading pdeffenb map…")}
    LaunchedEffect(state) {
        if(System.getProperty("aprsis64.probe") == "true") {
            delay(15_000)
            val point=state.screenLocationFromPosition(Position(-81.242932,28.536725))
            if(point != null) {
                val hits=state.queryRenderedFeatures(point,layerIds=setOf("aprs-stations"))
                println("MAP_PROBE bearing=${state.cameraPosition.bearing} zoom=${state.cameraPosition.zoom} hits=${hits.map { it.properties?.get("callsign") }}")
            } else System.err.println("MAP_PROBE failed: no rendered viewport")
        }
    }
    LaunchedEffect(state) {state.events.collect { event -> when(event) {
        MapEvent.StyleLoaded -> {mapStatus="Style loaded"; println("MAP style loaded")}
        MapEvent.Idle -> {mapStatus="Map ready"; println("MAP idle")}
        is MapEvent.StyleLoadFailed -> {mapStatus="Map style could not load"; System.err.println("MAP style failed: ${event.reason}")}
        is MapEvent.SourceDataFailed -> {mapStatus="Station layer failed"; System.err.println("MAP source failed: ${event.sourceId}")}
        is MapEvent.FrameRendered -> if(event.stats?.frameCount == 1L) println("MAP_FIRST_FRAME ${event.stats}")
        else -> Unit
    } } }
    LaunchedEffect(selected) {
        stations.firstOrNull {it.callsign==selected}?.let {
            state.animateCamera(CameraUpdate(center=Position(it.longitude,it.latitude),bearing=0.0,pitch=0.0))
        }
    }
    Box(modifier) {
        MaplibreMap(state=state,interactions=MapInteractions {camera {rotate {enabled=false}; pitch {enabled=false}}},
            cameraConstraints=CameraConstraints {maxPitch=0.0; maxZoom=18.0},renderOptions=RenderOptions {maximumFps=30})
        Text("$mapStatus  ·  N ↑",Modifier.align(Alignment.TopStart).padding(12.dp).background(Panel).padding(10.dp),color=Accent,fontSize=12.sp)
        if(selected!=null) Text("$selected · conversation open →",Modifier.align(Alignment.BottomCenter).padding(bottom=48.dp).background(Panel).padding(10.dp),color=Color.White)
    }
}

@Composable private fun ChatPanel(account: String, peer: String?, select: (String?)->Unit, all: List<ChatRow>,model: AppModel,modifier: Modifier) {
    val scope=rememberCoroutineScope()
    var recipient by remember {mutableStateOf("")}
    var draft by remember(account,peer) {mutableStateOf("")}
    var sending by remember {mutableStateOf(false)}
    val history=all.filter {it.peer==peer}
    val list=rememberLazyListState()
    LaunchedEffect(history.size) {if(history.isNotEmpty()) list.animateScrollToItem(history.lastIndex)}
    Column(modifier.background(Panel).padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(peer==null) "MESSAGES" else peer,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Accent)
            Spacer(Modifier.weight(1f)); if(peer!=null) TextButton(onClick={select(null)}) {Text("Inbox")}
        }
        if(peer==null) {
            Text("Conversations saved on this PC",color=Muted,fontSize=12.sp)
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
            if(threads.isEmpty()) Text("Select a station on the map, or enter a callsign above.",color=Muted)
        } else {
            Text("As ${account.ifBlank {"—"}}",fontSize=12.sp,color=Muted)
            LazyColumn(Modifier.weight(1f).padding(vertical=12.dp),state=list,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(history,key={it.id}) {row -> Column(Modifier.fillMaxWidth().background(if(row.outgoing) Color(0xFF24473F) else Color(0xFF263740)).padding(10.dp)) {
                    Text(if(row.outgoing) "You" else row.peer,color=Accent,fontSize=11.sp)
                    Text(row.body,Modifier.padding(vertical=5.dp),fontSize=14.sp)
                    Text(DateTimeFormatter.ofPattern("MMM d HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(row.time))+" · "+row.status.replace('_',' '),fontSize=9.sp,color=Muted)
                } }
            }
            Text("APRS messages are public and unencrypted. ACK means received, not read.",color=Muted,fontSize=10.sp)
            OutlinedTextField(draft,{draft=it},Modifier.fillMaxWidth().padding(top=8.dp),label={Text("Message")},maxLines=3)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${draft.length}/67 · printable ASCII",color=Muted,fontSize=10.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick={sending=true; scope.launch {try {model.send(peer,draft); draft=""} catch(_:Exception){model.error.value="Unable to save this message."} finally {sending=false}}},
                    enabled=!sending&&AprsMessages.validText(draft)&&AprsProtocol.validCallsign(account)&&account!=peer) {Text("Send")}
            }
        }
    }
}

@Composable private fun ConnectionDialog(model: AppModel, dismiss: ()->Unit) {
    var call by remember {mutableStateOf(model.settings.getProperty("callsign",""))}
    var pass by remember {mutableStateOf("")}
    var host by remember {mutableStateOf(model.settings.getProperty("endpoint","rotate.aprs2.net:14580"))}
    var lat by remember {mutableStateOf(model.settings.getProperty("latitude","28.536725"))}
    var lon by remember {mutableStateOf(model.settings.getProperty("longitude","-81.242932"))}
    DialogWindow(onCloseRequest=dismiss,title="APRS-IS connection",state=rememberDialogState(width=480.dp,height=590.dp)) {
        Surface {Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("APRS-IS connection",fontSize=22.sp)
            OutlinedTextField(call,{call=it.uppercase()},label={Text("Callsign with SSID, e.g. KJ4DXK-12")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(pass,{pass=it},label={Text("Passcode (blank = receive only)")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(host,{host=it},label={Text("Server host:port")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row {OutlinedTextField(lat,{lat=it},Modifier.weight(1f),label={Text("Filter latitude")}); Spacer(Modifier.width(8.dp)); OutlinedTextField(lon,{lon=it},Modifier.weight(1f),label={Text("Filter longitude")})}
            Text("100 km station filter. No location beaconing. Passcode stays in memory and is not saved.",color=Muted,fontSize=12.sp)
            Spacer(Modifier.weight(1f))
            Row {TextButton(onClick=dismiss){Text("Cancel")}; Spacer(Modifier.weight(1f)); Button(onClick={model.connect(call,pass,host,lat.toDoubleOrNull()?:Double.NaN,lon.toDoubleOrNull()?:Double.NaN); dismiss()}){Text("Connect")}}
        } }
    }
}
