// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.*
import org.aprsfoundation.core.*
import androidx.compose.ui.layout.onSizeChanged
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
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.map.*
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberRasterTileSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Position

private val Accent:Color @Composable get()=MaterialTheme.colors.primary
private val Panel:Color @Composable get()=MaterialTheme.colors.surface
private val Muted:Color @Composable get()=if(MaterialTheme.colors.isLight)Color(0xFF47565C) else Color(0xFFA2B4BE)
@Composable internal fun conversationColor(unread:Boolean,outgoing:Boolean):Color {
    val light=MaterialTheme.colors.isLight
    return when {
        unread->if(light)Color(0xFF824B00) else Color(0xFFFFCF70)
        outgoing->if(light)Color(0xFF006547) else Color(0xFF72E4C3)
        else->if(light)Color(0xFF185985) else Color(0xFF96C9FF)
    }
}

@OptIn(org.maplibre.compose.util.ExperimentalMaplibreComposeApi::class)
fun main(args: Array<String>) {
    val home = PortableHome.resolve()
    PortableHome.prepare(home)
    SerialDevices.prepare(home)
    if("--probe" in args) System.setProperty("aprserj64.probe","true")
    val demoMode = "--demo" in args || System.getProperty("aprserj64.demo") == "true"
    val model = try {AppModel(home, demoMode)} catch(_: Exception) {
        javax.swing.JOptionPane.showMessageDialog(null,"Unable to load settings.toml in $home. Check the settings syntax and values. Your file has not been reset.","APRSERJ64",javax.swing.JOptionPane.ERROR_MESSAGE)
        return
    }
    DefaultMapRuntime.configure {
        cacheFile = kotlinx.io.files.Path(home.resolve("cache/tiles.db").toString())
        maximumCacheSizeBytes = model.settings.getProperty("map.cache_megabytes").toLong() * 1024 * 1024
    }
    println("APRSERJ64 ${AppVersion.value} | Java ${System.getProperty("java.version")} | Vulkan / Direct3D12 | home=$home")
    if(demoMode) model.localDemo()
    else model.connectSaved()
    val qaMetrics=startQaMetrics(model)
    application {
        Window(icon=AppIcon.window,onCloseRequest={ qaMetrics?.cancel(); model.close(); exitApplication() }, title="APRSERJ64",
            state=rememberWindowState(width=1480.dp,height=900.dp)) {
            MaterialTheme(colors=applicationColors(model)) {
                CompositionLocalProvider(LocalLocationModel provides model) {
                    Surface(Modifier.fillMaxSize()) { ProvideMapPresentationHost(host=rememberAwtComposeMapPresentationHost(window)) { App(model) { qaMetrics?.cancel();model.close();exitApplication() } } }
                }
            }
        }
    }
}

@Composable private fun App(model: AppModel, closeForUpdate:()->Unit) {
    LaunchedEffect(Unit) {
        System.getenv("APRSERJ64_UPDATE_READY")?.let {value->
            val marker=java.nio.file.Path.of(value).toAbsolutePath().normalize()
            if(marker.fileName.toString()=="ready" && marker.parent.parent==model.directory.resolve("cache") && marker.parent.fileName.toString().startsWith("update-"))
                java.nio.file.Files.writeString(marker,AppVersion.value)
        }
    }
    var checking by remember {mutableStateOf(false)}
    var updateStatus by remember {mutableStateOf("")}
    val scope = rememberCoroutineScope()
    val account by model.account.collectAsState()
    val connection by model.state.collectAsState()
    val stations by model.stations.collectAsState()
    val demo by model.demo.collectAsState()
    val error by model.error.collectAsState()
    val setupIssue by model.connectionSetupIssue.collectAsState()
    val revision by model.database.revision.collectAsState()
    val settingsRevision by model.settingsRevision.collectAsState()
    val beacon by model.beaconState.collectAsState()
    val portStatuses by model.portStatuses.collectAsState()
    val beaconCenter by model.beaconCenter.collectAsState()
    val mainFollow = remember(settingsRevision) { model.settings.getProperty("display.follow").toBoolean() }
    val savedTarget = remember(settingsRevision) { model.settings.getProperty("display.follow_call").orEmpty() }
    var selected by remember(account, demo) { mutableStateOf(savedTarget.takeIf { !demo && mainFollow && it.isNotBlank() }) }
    LaunchedEffect(mainFollow, savedTarget, account, demo) {
        if (mainFollow && savedTarget.isNotBlank() && !demo) selected=savedTarget
    }
    LaunchedEffect(mainFollow, selected) { model.followCall(selected.takeIf {mainFollow}) }
    fun selectStation(call: String?) {
        selected=call
        if (!demo && mainFollow && call.orEmpty()!=savedTarget) model.updateFeature("display.follow_call",call.orEmpty())
    }
    var tools by remember {mutableStateOf(false)}
    var toolPage by remember {mutableStateOf("Station")}
    var toolRequest by remember {mutableStateOf(0)}
    var appMenu by remember {mutableStateOf(false)}
    val expanded=remember(settingsRevision){model.settings.getProperty("display.ribbon_expanded","true").toBoolean()}
    fun saveFlag(key:String,value:Boolean) {
        val edited=java.util.Properties().apply {putAll(model.settings);setProperty(key,value.toString())}
        model.savePreferences(edited,model.entities)
    }
    val showStations=remember(settingsRevision){model.settings.getProperty("display.stations_visible","true").toBoolean()}
    var focusChat by remember {mutableStateOf(0)}
    val showChat=remember(settingsRevision){model.settings.getProperty("display.chat_visible","true").toBoolean()}
    var listMenu by remember {mutableStateOf(false)}
    fun openTool(page:String) {toolPage=page;toolRequest++;tools=true}
    val tracking=remember {mutableStateListOf<String>().apply {addAll(names(model.settings.getProperty("display.tracking_calls")))}}
    LaunchedEffect(tracking.toList()) {
        model.trackCalls(tracking.toSet())
        val saved=encodedNames(tracking.toList())
        if(!demo && saved!=model.settings.getProperty("display.tracking_calls")) model.updateFeature("display.tracking_calls",saved)
    }
    var search by remember {mutableStateOf(model.settings.getProperty("display.search"))}
    var kind by remember {mutableStateOf(model.settings.getProperty("display.kind"))}
    var sort by remember {mutableStateOf(model.settings.getProperty("display.sort"))}
    val notices by model.activity.notices.collectAsState()
    val metadata by model.views.stations.collectAsState()
    val filtered=stations.filter {station->
        station.callsign.contains(search,true) && System.currentTimeMillis()-station.heardAtMillis<=model.settings.getProperty("display.maximum_age_minutes").toLong()*60000 &&
        stationKind(station,kind,metadata,model.archive.objects.value)
    }.let {list->when(sort) {"recent"->list.sortedByDescending {it.heardAtMillis};"distance"->list.sortedBy {AdaptiveBeaconPolicy.distance(AprsFix(model.settings.getProperty("position.latitude").toDouble(),model.settings.getProperty("position.longitude").toDouble(),0),AprsFix(it.latitude,it.longitude,0))};else->list.sortedBy {it.callsign}}}

    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(setupIssue) {if(setupIssue!=null) settings=true}
    var about by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf(false) }
    var preferencesPage by remember {mutableStateOf("Position")}
    var preferencesRequest by remember {mutableStateOf(0)}
    var menuQuery by remember {mutableStateOf("")}
    var packetMonitor by remember {mutableStateOf(false)}
    val packetStreams=remember {mutableStateListOf<PacketStream>()}
    var portWorkspace by remember {mutableStateOf(false)}
    fun navigate(destination:String) {
        appMenu=false
        when {
            destination.startsWith("settings:")->{preferencesPage=destination.substringAfter(':');preferencesRequest++;preferences=true}
            destination.startsWith("tools:")->openTool(destination.substringAfter(':'))
            destination=="action:connection"->settings=true
            destination=="action:ports"->portWorkspace=true
            destination=="action:packets"->packetMonitor=true
            destination=="action:about"->about=true
            destination=="action:alltools"->openTool("Station")
            destination=="action:practice"->model.localDemo()
            destination=="action:disconnect"->model.disconnect(remember=!demo)
        }
    }
    var mapRequest by remember {mutableStateOf<StationMapRequest?>(null)}
    var historyCall by remember {mutableStateOf<String?>(null)}
    fun stationMenu(call:String):List<ContextMenuItem> {
        fun mapAction(action:String) {
            selectStation(call)
            mapRequest=StationMapRequest(call,action,(mapRequest?.id ?: 0)+1)
        }
        val following=selected==call && model.settings.getProperty("display.follow").toBoolean()
        return buildList {
            add(ContextMenuItem(if(following)"Unfollow "+call else "Follow "+call){mapAction(if(following)"unfollow" else "follow")})
            if(call !in tracking && tracking.size<8)add(ContextMenuItem("Open tracking window"){tracking.add(call)})
            add(ContextMenuItem("Message "+call){selectStation(call);if(!showChat)saveFlag("display.chat_visible",true);focusChat++})
            add(ContextMenuItem("Station details"){selectStation(call);openTool("Station")})
            add(ContextMenuItem("Locate on map"){mapAction("locate")})
            add(ContextMenuItem("Track history"){historyCall=call})
            add(ContextMenuItem("Copy callsign"){java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(call),null)})
        }
    }
    historyCall?.let {call->
        val points by produceState(emptyList<AprsFix>(),call){value=model.archive.track(call)}
        AlertDialog(onDismissRequest={historyCall=null},title={HelpText(call+" track history")},text={
            LazyColumn(Modifier.heightIn(max=400.dp)){if(points.isEmpty())item {HelpText("No recorded positions")};items(points){fix->HelpText(Instant.ofEpochMilli(fix.timeMillis).toString()+" · "+fix.latitude+", "+fix.longitude)}}
        },confirmButton={Command("Close"){historyCall=null}})
    }
    var rows by remember { mutableStateOf<List<ChatRow>>(emptyList()) }
    LaunchedEffect(account,revision,selected,showChat) {
        rows=model.database.rows(account)
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(12.dp,2.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HelpTextButton("Change your callsign, SSID or APRS-IS passcode.",onClick={settings=true},contentPadding=PaddingValues(0.dp)) {
                    HelpText(if(demo) "Practice · $account" else account.ifBlank {"Set up your station"},color=Accent,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.width(4.dp));Icon(AppIcons.ArrowDropDown,"Change callsign or passcode",Modifier.size(16.dp))
                }
                HelpText(connectionLabel(connection,demo),color=Muted,fontSize=12.sp)
            }
            HelpTextButton("Open connection settings",onClick={settings=true}) { HelpText("Connection") }
            HelpField(UiHelp.field("Find station"),search,{search=it;if(!showStations) saveFlag("display.stations_visible",true)},label={HelpText("Find station")},singleLine=true,modifier=Modifier.width(150.dp),fill=true)
            HelpButton(beacon.manualBlocks.joinToString("\n").ifEmpty {UiHelp.action("Beacon now")},onClick={model.transmitBeacon()},enabled=beacon.manualBlocks.isEmpty()) {Icon(AppIcons.Send,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));HelpText("Beacon now")}
            Spacer(Modifier.width(10.dp))
            HelpOutlinedButton(UiHelp.action("Settings"),onClick={preferences=true}) {Icon(AppIcons.Settings,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));HelpText("Settings")}
            Box {
                HelpTextButton(UiHelp.action("Menu"),onClick={appMenu=true}) {Icon(AppIcons.Menu,null);Spacer(Modifier.width(6.dp));HelpText("Search")}
                DropdownMenu(expanded=appMenu,onDismissRequest={appMenu=false}) {
                    HelpField("Search all settings and tools. Try my callsign, passcode, map or beacon.",menuQuery,{menuQuery=it},modifier=Modifier.width(340.dp).padding(8.dp),label={HelpText("Search settings & tools")},singleLine=true,fill=true)
                    if(menuQuery.isNotBlank()) {
                        val matches=navigationResults(menuQuery,account)
                        Column(Modifier.width(360.dp).heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
                            if(matches.isEmpty()) HelpText("No matches. Try callsign, map or messages.",Modifier.padding(12.dp))
                            matches.forEach {entry->HelpMenuItem(UiHelp.section(entry.title,entry.keywords),onClick={navigate(entry.id)}) {Column {HelpText(entry.title);HelpText(entry.group,fontSize=11.sp,color=Muted)}}}
                        }
                    } else {
                    HelpMenuItem("Change your callsign, SSID or APRS-IS passcode.",onClick={navigate("action:connection")}) {HelpText("My callsign & passcode")}
                    HelpMenuItem(UiHelp.action("Station details"),onClick={appMenu=false;openTool("Station")}) {HelpText("Station details")}
                    HelpMenuItem(UiHelp.action("Weather reports"),onClick={appMenu=false;openTool("Weather")}) {HelpText("Weather reports")}
                    HelpMenuItem(UiHelp.action("Download maps"),onClick={appMenu=false;openTool("Offline")}) {HelpText("Download maps")}
                    HelpMenuItem(UiHelp.action("All tools…"),onClick={appMenu=false;openTool("Station")}) {HelpText("All tools…")}
                    Divider()
                    HelpMenuItem(UiHelp.action("Radio & GPS ports…"),onClick={appMenu=false;portWorkspace=true}) {HelpText("Radio & GPS ports…")}
                    HelpMenuItem(UiHelp.action("Packet monitor…"),onClick={appMenu=false;packetMonitor=true}) {HelpText("Packet monitor…")}
                    Divider()
                    if(connection==AprsConnectionState.STOPPED) HelpMenuItem(UiHelp.action("Practice with local demo"),onClick={appMenu=false;model.localDemo()}) {HelpText("Practice with local demo")}
                    else HelpMenuItem(UiHelp.action(if(demo) "Stop practice" else "Disconnect APRS-IS"),onClick={appMenu=false;model.disconnect(remember=!demo)}) {HelpText(if(demo) "Stop practice" else "Disconnect APRS-IS")}
                    HelpMenuItem(UiHelp.action("About & license"),onClick={appMenu=false;about=true}) {HelpText("About & license")}
                    }
                }
            }
        }
        val tabs=listOf("Enables","Messages","Screen","View","Configure","Clear","Help")
        var tab by remember {mutableStateOf("Enables")}
        Row(Modifier.fillMaxWidth().background(Panel),verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                tabs.dropLast(1).forEach { label -> RibbonTab(label,tab==label && expanded){tab=label;if(!expanded) saveFlag("display.ribbon_expanded",true)} }
            }
            RibbonTab("Help",tab=="Help" && expanded){tab="Help";if(!expanded) saveFlag("display.ribbon_expanded",true)}
            HelpTextButton("Toggle ribbon commands",onClick={saveFlag("display.ribbon_expanded",!expanded)}){HelpText(if(expanded) "Hide ribbon" else "Show ribbon")}
        }
        if(expanded) key(tab) {
            Row(Modifier.fillMaxWidth().background(if(MaterialTheme.colors.isLight)Color(0xFFDEECE9) else Color(0xFF203940)).horizontalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=4.dp)) {
                when(tab) {
                    "Enables" -> {
                        CommandGroup("APRS-IS") { Command(if(connection==AprsConnectionState.STOPPED) "Connect…" else "Disconnect"){if(connection==AprsConnectionState.STOPPED) settings=true else model.disconnect(remember=!demo)} }
                        CommandGroup("Beaconing"){HelpSwitch("Enable or disable station beaconing",beacon.enabled,{saveFlag("beacon.enabled",it)});HelpText(if(beacon.enabled) "On" else "Off")}
                        CommandGroup("Radio ports") {
                            model.ports.forEachIndexed {index,port ->
                                val enabled=port.getProperty("enabled").toBoolean()
                                Command((if(enabled) "Disable " else "Enable ")+port.getProperty("name")) {
                                    val ports=model.ports.mapIndexed {i,p->if(i!=index) p else java.util.Properties().apply {putAll(p);setProperty("enabled",(!enabled).toString())}}
                                    model.savePreferences(java.util.Properties().apply {putAll(model.settings)},model.entities,ports)
                                }
                            }
                            Command("Manage ports…",icon=AppIcons.Settings){portWorkspace=true}
                        }
                        CommandGroup("Diagnostics"){Command("Packet monitor…",icon=AppIcons.Search){packetMonitor=true}}
                    }
                    "Messages" -> {
                        CommandGroup("Conversation"){Command("Read / send",icon=AppIcons.Send){focusChat++;if(!showChat) saveFlag("display.chat_visible",true)};Command("Send email",icon=AppIcons.Send){openTool("Email")}}
                        CommandGroup("Broadcast"){Command("Groups"){openTool("Groups")};Command("Bulletins"){openTool("Bulletins")}}
                    }
                    "Screen" -> {
                        CommandGroup("Map"){
                            Command("Appearance…",icon=AppIcons.Settings){navigate("settings:Map")}
                            val showCallsigns=remember(settingsRevision){model.settings.getProperty("tactical.labels")=="all"}
                            Command(if(showCallsigns) "Hide callsigns" else "Show callsigns"){
                                model.updateFeature("tactical.labels",if(showCallsigns) "none" else "all")
                            }
                        }
                        CommandGroup("Panes"){Command(if(showStations) "Hide stations" else "Show stations"){saveFlag("display.stations_visible",!showStations)};Command(if(showChat) "Hide messages" else "Show messages"){saveFlag("display.chat_visible",!showChat)}}
                    }
                    "View" -> {
                        CommandGroup("Station filter"){ChoiceMenu("Show",kind,stationKinds,Modifier.width(185.dp)){kind=it;if(it=="all") search=""}}
                        CommandGroup("Order"){ChoiceMenu("Sort by",sort,stationSort,Modifier.width(150.dp)){sort=it}}
                        CommandGroup("Tracking"){Command("Track selected",selected!=null && selected !in tracking && tracking.size<8){selected?.let(tracking::add)}}
                    }
                    "Configure" -> {
                        CommandGroup("Station"){Command("General settings…",icon=AppIcons.Settings){preferences=true};Command("Beaconing…",icon=AppIcons.Send){navigate("settings:Beacon")}}
                        CommandGroup("Connections"){Command("Radio & GPS ports…",icon=AppIcons.Settings){portWorkspace=true};Command("Station login…"){settings=true}}
                    }
                    "Clear" -> CommandGroup("Filters and notices"){
                        var open by remember {mutableStateOf(false)}
                        Box { Command("Choose what to clear…"){open=true};DropdownMenu(open,{open=false}){
                            HelpMenuItem("Reset the visible station search and category for this window. Saved starting filters, subscriptions, stations and chats are preserved.",onClick={open=false;search="";kind="all"}){Text("Reset station search and category")}
                            HelpMenuItem("Dismiss the activity banner and its in-memory list until another event arrives. Station records and messages are preserved.",onClick={open=false;model.activity.dismiss()}){Text("Dismiss displayed activity notices")}
                        } }
                    }
                    "Help" -> {
                        CommandGroup("APRSERJ64"){Command("Version "+AppVersion.value){about=true};Command("License"){about=true}}
                        CommandGroup("Updates"){
                            Command("GitHub"){Desktop.getDesktop().browse(URI(model.settings.getProperty("services.source_url")))}
                            Command(if(checking) updateStatus else "Update APRSERJ64",!checking){
                                checking=true;updateStatus="Checking GitHub"
                                scope.launch {
                                    try {
                                        val worker=UpdateWorker.start(model.directory,model.settings.getProperty("services.release_api_url"))
                                        while(worker.process.isAlive){
                                            if(java.nio.file.Files.exists(worker.restart)){closeForUpdate();break}
                                            val status=worker.restart.resolveSibling("status")
                                            if(java.nio.file.Files.isRegularFile(status))runCatching {java.nio.file.Files.readString(status).trim()}.getOrNull()?.takeIf {it.isNotBlank()}?.let {updateStatus=it}
                                            delay(250)
                                        }
                                    } catch(e:Exception){model.error.value=e.message ?: "Unable to start updater."}
                                    finally {checking=false}
                                }
                            }
                        }
                        CommandGroup("Documentation"){Command("User manual",icon=AppIcons.Menu){Desktop.getDesktop().browse(URI(model.settings.getProperty("services.manual_url")))}}
                    }
                }
            }
        }
        if(error.isNotBlank()) HelpText(error,Modifier.fillMaxWidth().background(MaterialTheme.colors.error.copy(alpha=0.18f)).padding(10.dp))
        if(setupIssue!=null) Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            HelpText(setupIssue!!,Modifier.weight(1f),fontSize=12.sp)
            HelpTextButton("Open your callsign and passcode settings.",onClick={settings=true}) {HelpText("Fix sign-in")}
        }
        if(notices.isNotEmpty()) Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal=12.dp)) {
            HelpText(notices.last().text,Modifier.weight(1f),fontSize=12.sp)
            HelpTextButton(UiHelp.action("View alerts"),onClick={openTool("Alerts")}) {HelpText("View alerts")};HelpTextButton(UiHelp.action("Dismiss"),onClick={model.activity.dismiss()}){HelpText("Dismiss")}
        }
        Row(Modifier.weight(1f)) {
            if(showStations) Column(Modifier.width((model.settings.getProperty("display.stations_width").toIntOrNull() ?: 225).coerceIn(160,600).dp).fillMaxHeight().background(Panel).padding(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    HelpText("STATIONS",Modifier.weight(1f),color=Muted,fontSize=12.sp)
                    Command("Hide"){saveFlag("display.stations_visible",false)}
                    Box {
                        ActionIcon(AppIcons.MoreVert,"Station list options") {listMenu=true}
                        DropdownMenu(expanded=listMenu,onDismissRequest={listMenu=false}) {
                            HelpMenuItem(UiHelp.action("Save as my starting view"),onClick={listMenu=false;val p=java.util.Properties().apply {putAll(model.settings);setProperty("display.search",search);setProperty("display.kind",kind);setProperty("display.sort",sort);setProperty("display.tracking_calls",encodedNames(tracking.toList()))};model.savePreferences(p,model.entities)}) {HelpText("Save as my starting view")}
                            HelpMenuItem(UiHelp.action("Clear filters"),onClick={listMenu=false;search="";kind="all"}) {HelpText("Clear filters")}
                        }
                    }
                }

                ChoiceMenu("Show",kind,stationKinds,Modifier.fillMaxWidth()) {kind=it}
                ChoiceMenu("Sort by",sort,stationSort,Modifier.fillMaxWidth()) {sort=it}
                HelpText("${filtered.size} / ${stations.size}",Modifier.padding(vertical=6.dp),fontSize=16.sp,help="${filtered.size} stations match the current list filters out of ${stations.size} received stations. Search, category and age limits affect this count.")
                if(search.isNotBlank() || kind!="all") HelpTextButton(UiHelp.action("Clear filters"),onClick={search="";kind="all"}) {HelpText("Clear filters")}
                if(account.isBlank()) {
                    HelpText("Start with your callsign and location, or explore without going online.",fontSize=12.sp,color=Muted)
                    HelpOutlinedButton(UiHelp.action("Try practice mode"),onClick={model.localDemo()},modifier=Modifier.fillMaxWidth(),fill=true) {HelpText("Try practice mode")}
                }
                if(stations.isNotEmpty() && filtered.isEmpty()) HelpText("No matching stations. Clear filters to see everyone.",fontSize=12.sp,color=Muted)
                val trackTarget=search.trim().uppercase().takeIf {AprsProtocol.validCallsign(it)} ?: selected
                if(trackTarget!=null) HelpTextButton("Open a map that waits for the station's next position.",enabled=trackTarget !in tracking && tracking.size<8,onClick={tracking.add(trackTarget)}){HelpText("Track "+trackTarget)}

                LazyColumn(Modifier.weight(1f)) { items(filtered,key={it.callsign}) { station ->
                    ContextMenuArea(items={stationMenu(station.callsign)}) {
                    Column(Modifier.fillMaxWidth().clickable { selectStation(station.callsign) }.padding(vertical=10.dp)) {
                        HelpText(TacticalPreferences.aliases(model.settings)[station.callsign]?.let {"$it (${station.callsign})"} ?: station.callsign,color=if(selected==station.callsign) Accent else MaterialTheme.colors.onSurface,fontWeight=FontWeight.Bold)
                        HelpText("%.3f, %.3f".format(station.latitude,station.longitude),fontSize=11.sp,color=Muted)
                    } }; Divider(color=Color(0xFF2B3D46))
                } }
                HelpText(if(beacon.enabled) "Beacon position: ${beacon.source.lowercase()}" else "Beaconing off",fontSize=12.sp,color=Accent)
                HelpText(beaconDetail(beacon.detail),fontSize=11.sp,color=Muted)
                portStatuses.forEach {p->HelpText("${p.name}: ${p.detail} · RX ${p.received} / TX ${p.transmitted}"+(if(p.dropped>0) " / Dropped ${p.dropped}" else ""),fontSize=11.sp,color=Muted)}
                beacon.sentAt?.let {HelpText("Last sent "+DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)),fontSize=11.sp,color=Muted)}
            }
            val own=beacon.position?.takeIf {account.isNotBlank()}?.let {p -> AprsStation(account,p.fix.latitude,p.fix.longitude,
                model.settings.getProperty("beacon.symbol_table").single(),model.settings.getProperty("beacon.symbol").single(),"",p.fix.timeMillis,p.course,p.speedKnots,p.altitudeMeters)}
            val localStations=listOfNotNull(own)+beacon.entities
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if(!showChat || !showStations) Row {
                    if(!showStations) Command("Show stations list"){saveFlag("display.stations_visible",true)}
                    if(!showChat) Command("Show chat bar ("+rows.count {it.unread}+")"){saveFlag("display.chat_visible",true)}
                }
                val mapStations = (localStations + filtered + listOfNotNull(stations.firstOrNull { mainFollow && it.callsign==selected })).distinctBy { it.callsign }
                if(mainFollow && selected!=null && mapStations.none {it.callsign==selected}) HelpText("Waiting for a position from "+selected)
                key(mapConfigurationKey(model)) {MapPanel(mapStations,selected,model,beaconCenter,Modifier.weight(1f).fillMaxHeight(),stationMenu=::stationMenu,request=mapRequest) { selectStation(it) }}
            }
            if(showChat) ChatPanel(account,selected,::selectStation,rows,model,Modifier.width((model.settings.getProperty("display.chat_width").toIntOrNull() ?: 350).coerceIn(260,700).dp).fillMaxHeight(),focusChat,::stationMenu,{saveFlag("display.chat_visible",false)})
        }
    }
    tracking.toList().forEach {call->key(call) {TrackingWindow(model,call,::stationMenu) {tracking.remove(call)}}}
    if(tools) OperatingToolsWindow(model,selected,toolPage,toolRequest,onNavigate=::navigate) {tools=false}
    if(settings) ConnectionDialog(model) {settings=false}
    if(preferences) PreferencesDialog(model,preferencesPage,preferencesRequest,onNavigate=::navigate) {preferences=false}
    if(packetMonitor) PacketMonitorWindow(model,openStream={if(it !in packetStreams) packetStreams.add(it)}) {packetMonitor=false}
    packetStreams.toList().forEachIndexed {index,stream->key(stream) {
        PacketMonitorWindow(model,stream,offset=(index%8+1)*24) {packetStreams.remove(stream)}
    }}
    if(portWorkspace) PortWorkspaceWindow(model) {portWorkspace=false}
    if(about) AlertDialog(onDismissRequest={about=false},title={HelpText("APRSERJ64 ${AppVersion.value}")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState())) {
         HelpText("Version "+AppVersion.value+"\nPortable folder: "+model.directory+"\nSettings: "+model.directory.resolve("settings.toml"))
         HelpText("Copyright 2026 APRSERJ64 contributors.\nAGPL-3.0. No warranty.\n\nInspired by Lynn Deffenbaugh KJ4ERJ's APRSISCE/32. APRS by Bob Bruninga WB4APR. Map data © OpenStreetMap contributors.\n\nContains GPLv3 shared components. Third-party license notices are included in the app folder.")
         listOf("APRSERJ64 manual" to "manual_url", "APRSIS32 manual" to "legacy_manual_url", "Original APRSISCE/32 wiki" to "legacy_wiki_url", "Attributions and licenses" to "credits_url", "APRS Foundation" to "foundation_url", "APRS Foundation GitHub" to "foundation_github_url", "Foundation APRSISCE/32 source" to "foundation_legacy_url", "APRSIS32 GitHub" to "legacy_source_url", "Passcode instructions" to "passcode_help_url").forEach {(label,key)->
          val address=model.settings.getProperty("services.$key")
          androidx.compose.foundation.ContextMenuArea(items={listOf(
           androidx.compose.foundation.ContextMenuItem("Copy link address") {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(address),null)
           },
           androidx.compose.foundation.ContextMenuItem("Open link") {Desktop.getDesktop().browse(URI(address))}
          )}) {HelpTextButton("Open "+address,onClick={Desktop.getDesktop().browse(URI(address))}) {HelpText(label)}}
          HelpText(address,fontSize=12.sp)
         }
        }
    },confirmButton={HelpTextButton(UiHelp.action("Source & license"),onClick={Desktop.getDesktop().browse(URI(model.settings.getProperty("services.source_url")))}) {HelpText("Source & license")}},dismissButton={HelpTextButton(UiHelp.action("Close"),onClick={about=false}) {HelpText("Close")}})
}

private fun stationJson(stations: List<AprsStation>) = buildJsonObject {
    put("type","FeatureCollection")
    putJsonArray("features") { stations.forEach { s -> addJsonObject {
        put("type","Feature"); putJsonObject("properties") {put("callsign",s.callsign)}
        putJsonObject("geometry") {put("type","Point"); putJsonArray("coordinates") {add(s.longitude); add(s.latitude)}}
    } } }
}.toString()

@Composable @MaplibreComposable
private fun MapRasterLayer(id:String,url:String,settings:java.util.Properties,kind:String) {
    val source=rememberRasterTileSource(listOf(url),tileSize=settings.getProperty("map.tile_size").toInt()) {
        minZoom=settings.getProperty("map.minimum_zoom").toDouble().toInt()
        maxZoom=settings.getProperty("map.maximum_zoom").toDouble().toInt()
        attributionHtml=settings.getProperty("map.attribution")
    }
    RasterLayer(id=id,source=source,opacity=const(settings.getProperty("map.${kind}_opacity").toFloat()))
}

internal data class StationMapRequest(val call:String,val action:String,val id:Int)
@Composable internal fun MapPanel(stations: List<AprsStation>, selected: String?, model: AppModel, beaconCenter: AprsFix?, modifier: Modifier, forceFollow:Boolean=false, viewSettings:java.util.Properties=model.settings, stationMenu:(String)->List<ContextMenuItem> = {emptyList()},request:StationMapRequest?=null,select: (String)->Unit) {
    val importedPoints by model.imports.content.collectAsState()
    val orbitPoints by model.satellites.positions.collectAsState()
    val extraPoints=importedPoints.waypoints.map {AprsStation(it.name.take(9),it.fix.latitude,it.fix.longitude,'/','/',it.name,System.currentTimeMillis())}+orbitPoints.map {AprsStation(it.name.take(9),it.latitude,it.longitude,'/','S',it.name,System.currentTimeMillis())}
    var chosenPoint by remember {mutableStateOf<AprsStation?>(null)}
    fun selectPoint(call:String){if(stations.any {it.callsign==call})select(call) else chosenPoint=extraPoints.firstOrNull {it.callsign==call}}
    val data = remember(stations,extraPoints) { GeoJsonData.JsonString(stationJson(stations+extraPoints)) }
    val preferencesRevision by model.settingsRevision.collectAsState()
    val density = LocalDensity.current.density
    val variants = remember(viewSettings,preferencesRevision) { ServicePreferences.projectTileVariants(viewSettings) }
    val detailed = ServicePreferences.preferDetailedTiles(model.settings.getProperty(ServicePreferences.TILE_RESOLUTION_KEY),density)
    val offline = viewSettings.getProperty("offline.enabled").toBoolean()
    val adaptiveBase = remember(variants) {
        variants?.let { model.offline.adaptiveTemplate(it,detailed,offline) }
    }
    DisposableEffect(adaptiveBase) { onDispose { adaptiveBase?.let(model.offline::removeAdaptiveTemplate) } }
    SideEffect { adaptiveBase?.selection=AdaptiveTileSource.Selection(detailed,offline) }
    val mapSettings = remember(adaptiveBase) { mapSettings(model,viewSettings) }
    val style = remember(mapSettings) { BaseStyle.Json(ServicePreferences.style(mapSettings,includeBaseAndOverlay=adaptiveBase==null)) }
    var renderedRaster by remember(adaptiveBase) { mutableStateOf(0L) }
    var requestedRaster by remember(adaptiveBase) { mutableStateOf(0L) }
    LaunchedEffect(adaptiveBase) {
        adaptiveBase?.changes?.collectLatest { revision ->
            if(revision!=0L) {
                delay(250)
                requestedRaster=revision
            }
        }
    }
    LaunchedEffect(adaptiveBase) {
        if(adaptiveBase!=null) while(true) {delay(11_000); adaptiveBase.retry()}
    }
    val historyRevision by model.archive.revision.collectAsState()
    val selectedPath by remember(selected) {model.tactical.paths.map {it[selected]}.distinctUntilChanged()}.collectAsState(initial=null)
    val webWarnings by model.weatherAlerts.alerts.collectAsState()
    val legacyWarnings by model.legacyWeather.alerts.collectAsState()
    val warnings=webWarnings+legacyWarnings
    val weather by model.archive.weather.collectAsState()
    var clock by remember {mutableStateOf(System.currentTimeMillis())}
    var chosenAlert by remember {mutableStateOf<WeatherAlert?>(null)}
    LaunchedEffect(Unit) {while(true) {delay(5000);clock=System.currentTimeMillis()}}
    // The timer triggers expiry updates, but newly received packets can be newer
    // than its last tick. Use the current time when recalculating their age.
    val imported by model.imports.content.collectAsState()
    val localMap by model.imports.localMap.collectAsState()
    val orbits by model.satellites.positions.collectAsState()
    val geometry=remember(stations,selected,selectedPath,warnings,clock,preferencesRevision,imported,orbits) {tacticalGeometry(stations,selected,selectedPath?.let {mapOf(it.call to it)}.orEmpty(),warnings,viewSettings,System.currentTimeMillis())+imported.shapes+model.satellites.geometry(System.currentTimeMillis())}
    var trail by remember(selected) {mutableStateOf<List<AprsFix>>(emptyList())}
    var trackingFollow by remember { mutableStateOf(true) }
    val follow = if(forceFollow) trackingFollow else remember(preferencesRevision) { model.settings.getProperty("display.follow").toBoolean() }
    fun setMapFollow(enabled: Boolean) {
        if(forceFollow) {
            trackingFollow=enabled
        } else {
            val edited=java.util.Properties().apply {
                putAll(model.settings)
                setProperty("display.follow",enabled.toString())
                setProperty("display.follow_call",selected.takeIf {enabled}.orEmpty())
            }
            model.savePreferences(edited,model.entities)
        }
    }
    var locationInput by remember {mutableStateOf("")}
    var coordinateReference by remember {mutableStateOf<MapLocation?>(null)}
    val locationResult=remember(locationInput,coordinateReference) {CoordinateInput.parse(locationInput,coordinateReference)}
    var showCoordinates by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(selected,historyRevision) {trail=selected?.let {model.archive.track(it)} ?: emptyList()}

    val initial=remember {Position(viewSettings.getProperty("longitude").toDouble(),viewSettings.getProperty("latitude").toDouble())}
    val state=rememberMapState(baseStyle=style,initialCameraPosition=CameraPosition(center=if(forceFollow) stations.firstOrNull {it.callsign==selected}?.let {Position(it.longitude,it.latitude)} ?: initial else initial,zoom=viewSettings.getProperty("map.initial_zoom").toDouble())) {
        if(adaptiveBase!=null) {
            listOf(renderedRaster,requestedRaster).distinct().forEach { revision -> key(revision) {
                MapRasterLayer("base-raster-$revision",adaptiveBase.url(revision),mapSettings,"base")
            } }
            if(mapSettings.getProperty("map.overlay_enabled").toBoolean())
                MapRasterLayer("adaptive-overlay",mapSettings.getProperty("map.overlay_url"),mapSettings,"overlay")
        }
        localMap?.let {local->
            val localSource=rememberGeoJsonSource(GeoJsonData.JsonString(local.lines))
            LineLayer(id="local-osm",source=localSource,color=const(Color(0xFF476976)),width=const(1.6.dp))
        }
        TacticalLayers(geometry) {chosenAlert=it}
        if(viewSettings.getProperty("display.trails").toBoolean() && trail.size>1) {
            val trailSource=rememberGeoJsonSource(GeoJsonData.JsonString(trailJson(trail)))
            LineLayer(id="station-trail",source=trailSource,color=const(Color(0xFFB33E20)),width=const(3.dp))
        }
        val source=rememberGeoJsonSource(data)
        CircleLayer(id="aprs-stations",source=source,radius=const(7.dp),color=const(Color(0xFF006D65)),strokeWidth=const(2.dp),strokeColor=const(Color.White),hitPadding=8.dp,
            onClick={features -> features.firstOrNull()?.properties?.get("callsign")?.jsonPrimitive?.content?.let {selectPoint(it)}; ClickResult.Consume})
        if(viewSettings.getProperty("display.symbols").toBoolean()) (stations+extraPoints).groupBy {"${it.symbolTable.code}-${it.symbol.code}"}.forEach {(id,group)->key(id) {
            val symbols=rememberGeoJsonSource(GeoJsonData.JsonString(stationJson(group)))
            SymbolLayer(id="symbol-$id",source=symbols,iconImage=image(StationSymbols.bitmap(group.first().symbolTable,group.first().symbol)),iconAllowOverlap=const(true),
                onClick={features->features.firstOrNull()?.properties?.get("callsign")?.jsonPrimitive?.content?.let {selectPoint(it)};ClickResult.Consume})
        }}
        val labels=when(viewSettings.getProperty("tactical.labels")) {
            "all"->stations.sortedBy {if(it.callsign==selected) 0 else 1}.take(viewSettings.getProperty("tactical.maximum_labels").toInt())
            "selected"->stations.filter {it.callsign==selected}
            else->emptyList()
        }
        (labels+extraPoints).distinctBy {it.callsign}.forEach {station->key("label-${station.callsign}") {
            val source=rememberGeoJsonSource(GeoJsonData.JsonString(stationJson(listOf(station))))
            SymbolLayer(id="label-${station.callsign}",source=source,iconImage=image(StationSymbols.label(stationLabel(station,viewSettings,weather[station.callsign]))),
                iconAllowOverlap=const(false),onClick={selectPoint(station.callsign);ClickResult.Consume})
        }}
    }
    val mapReady=state.style.loadState == StyleLoadState.Ready
    LaunchedEffect(request,mapReady){
        if(request!=null && mapReady){
            if(request.action!="locate"){
                setMapFollow(request.action=="follow")
            }
            if(request.action!="unfollow")(stations+model.stations.value).firstOrNull {it.callsign==request.call}?.let {
                state.animateCamera(CameraUpdate(center=Position(it.longitude,it.latitude)))
            }
        }
    }
    var menuTarget by remember {mutableStateOf<Pair<String,DpOffset>?>(null)}
    var dimensions by remember {mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)}
    val camera=state.cameraPosition
    val viewport=remember {Any()}
    DisposableEffect(viewport){onDispose {model.viewAreas(viewport,emptyList())}}
    LaunchedEffect(mapReady,camera.center,camera.zoom,dimensions,density) {
        if(mapReady) {
            delay(750)
            val areas=visibleAreas(camera.center.latitude,camera.center.longitude,camera.zoom,
                dimensions.width.toDouble()/density,dimensions.height.toDouble()/density)
            model.viewAreas(viewport,areas)
        }
    }
    var mapError by remember {mutableStateOf("")}
    LaunchedEffect(state) {
        if(System.getProperty("aprserj64.probe") == "true") {
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
        MapEvent.Idle -> {
            if(adaptiveBase!=null && state.style.layers["base-raster-$requestedRaster"]!=null) renderedRaster=requestedRaster
            println("MAP idle")
        }
        is MapEvent.StyleLoadFailed -> {mapError="Unable to load the map style: ${event.reason}"; System.err.println("MAP style failed: ${event.reason}")}
        is MapEvent.SourceDataFailed -> {
            mapError="Unable to load map data for ${event.sourceId}"
            System.err.println("MAP source failed: ${event.sourceId}")
        }
        is MapEvent.FrameRendered -> if(event.stats?.frameCount == 1L) println("MAP_FIRST_FRAME ${event.stats}")
        else -> Unit
    } } }
    LaunchedEffect(selected,mapReady) {
        if(mapReady) stations.firstOrNull {it.callsign==selected}?.let {
            state.animateCamera(CameraUpdate(center=Position(it.longitude,it.latitude),bearing=0.0,pitch=0.0))
        }
    }
    val followed=stations.firstOrNull {it.callsign==selected}
    chosenPoint?.let {point->AlertDialog(onDismissRequest={chosenPoint=null},title={HelpText(point.comment)},text={HelpText("${point.latitude}, ${point.longitude}")},confirmButton={HelpTextButton(UiHelp.action("Close"),onClick={chosenPoint=null}){HelpText("Close")}})}
    chosenAlert?.let {alert->AlertDialog(onDismissRequest={chosenAlert=null},title={HelpText("${alert.severity}: ${alert.event}")},
        text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {HelpText(alert.headline);HelpText(alert.description);HelpText("Expires ${alert.expires}")}},
        confirmButton={HelpTextButton(UiHelp.action("Close"),onClick={chosenAlert=null}) {HelpText("Close")}})}
    LaunchedEffect(mapReady,follow,followed?.latitude,followed?.longitude) {if(mapReady && follow && followed!=null) state.animateCamera(CameraUpdate(center=Position(followed.longitude,followed.latitude),bearing=0.0,pitch=0.0))}
    LaunchedEffect(beaconCenter,mapReady) {if(mapReady) beaconCenter?.let {state.animateCamera(CameraUpdate(center=Position(it.longitude,it.latitude),zoom=if(it==localMap?.center) 16.0 else null,bearing=0.0,pitch=0.0))}}
    Box(modifier.onSizeChanged {dimensions=it}.pointerInput(state) {
        awaitPointerEventScope {
            while(true){
                val event=awaitPointerEvent(PointerEventPass.Initial)
                if(event.type==PointerEventType.Press && event.buttons.isSecondaryPressed){
                    val point=event.changes.first().position
                    val anchor=DpOffset(point.x.toDp(),point.y.toDp())
                    scope.launch {
                        val hits=state.queryRenderedFeatures(anchor,layerIds=setOf("aprs-stations"))
                        hits.firstOrNull()?.properties?.get("callsign")?.jsonPrimitive?.content?.let {menuTarget=it to anchor}
                    }
                }
            }
        }
    }) {
        MaplibreMap(state=state,interactions=MapInteractions {camera {rotate {enabled=false}; pitch {enabled=false}}},
            cameraConstraints=CameraConstraints {maxPitch=0.0; minZoom=viewSettings.getProperty("map.minimum_zoom").toDouble(); maxZoom=viewSettings.getProperty("map.maximum_zoom").toDouble()},renderOptions=RenderOptions {maximumFps=viewSettings.getProperty("map.maximum_fps").toInt()}, overlay={})
        Column(Modifier.align(Alignment.TopStart).padding(6.dp).background(Panel.copy(alpha=0.94f)).padding(6.dp)) {
            Row {
                ActionIcon(AppIcons.Add,"Zoom in") {scope.launch {state.animateCamera(CameraUpdate(zoom=(state.cameraPosition.zoom+1).coerceAtMost(viewSettings.getProperty("map.maximum_zoom").toDouble())))}}
                ActionIcon(AppIcons.Remove,"Zoom out") {scope.launch {state.animateCamera(CameraUpdate(zoom=(state.cameraPosition.zoom-1).coerceAtLeast(viewSettings.getProperty("map.minimum_zoom").toDouble())))}}
                HelpTextButton(UiHelp.action("Home"),onClick={scope.launch {state.animateCamera(CameraUpdate(center=initial,bearing=0.0,pitch=0.0))}}){HelpText("Home")}
                HelpTextButton(UiHelp.action(if(follow) "Following" else "Follow station"),onClick={setMapFollow(!follow)},enabled=selected!=null || follow){HelpText(if(follow) "Following" else "Follow station")}
                ActionIcon(AppIcons.Search,"Go to coordinates") {showCoordinates=!showCoordinates}
            }
            if(showCoordinates) Column(Modifier.width(400.dp)) {
                val requests=remember {kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity=1)}
                Row(verticalAlignment=Alignment.CenterVertically) {
                    HelpField(UiHelp.field("Location"),locationInput,{locationInput=it;coordinateReference=MapLocation(state.cameraPosition.center.latitude,state.cameraPosition.center.longitude)},Modifier.weight(1f).submitPlaceOnEnter(requests,locationResult.location==null && locationInput.isNotBlank()),label={HelpText("Location")},placeholder={HelpText("Place, coordinates, map link or Plus Code")},singleLine=true,isError=locationInput.isNotBlank() && locationResult.location==null,fill=true)
                    HelpTextButton(UiHelp.action("Go"),onClick={locationResult.location?.let {p->if(follow)setMapFollow(false);scope.launch {state.animateCamera(CameraUpdate(center=Position(p.longitude,p.latitude),bearing=0.0,pitch=0.0))}}},enabled=locationResult.location!=null){HelpText("Go")}
                }
                if(locationInput.isNotBlank()) {
                    if(locationResult.location==null) PlaceSearch(locationInput,requests) { point -> locationInput=point.latitude.toString()+", "+point.longitude }
                    locationResult.location?.let {HelpText("Go to ${it.display()}",fontSize=12.sp,color=Accent)}
                    if(locationResult.explanation.isNotBlank()) HelpText(locationResult.explanation,fontSize=11.sp,color=Muted)
                    if(locationResult.error.isNotBlank()) HelpText(locationResult.error,fontSize=11.sp,color=MaterialTheme.colors.error)
                }
            }
            if(followed!=null) HelpText(navigationSummary(model,followed),fontSize=11.sp)
        }
        menuTarget?.let {(call,anchor)->
            Box(Modifier.offset(anchor.x,anchor.y)){
                DropdownMenu(expanded=true,onDismissRequest={menuTarget=null}){
                    stationMenu(call).forEach {action->DropdownMenuItem(onClick={menuTarget=null;action.onClick()}){HelpText(action.label)}}
                }
            }
        }
        val attribution=if(localMap!=null) (listOf(viewSettings.getProperty("map.attribution"),"© OpenStreetMap contributors").distinct().joinToString(" · "))
            else viewSettings.getProperty("map.attribution")
        HelpText(attribution,Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.White.copy(alpha=0.88f))
            .clickable {Desktop.getDesktop().browse(URI(viewSettings.getProperty("map.attribution_url")))}.padding(5.dp),color=Color(0xFF263740),fontSize=11.sp)
        if(mapError.isNotEmpty()) HelpText(mapError,Modifier.align(Alignment.TopStart).padding(12.dp).background(Panel).padding(10.dp),color=Accent,fontSize=12.sp)
    }
}

@Composable private fun ChatPanel(account: String, peer: String?, select: (String?)->Unit, all: List<ChatRow>,model: AppModel,modifier: Modifier,focusRequest:Int,stationMenu:(String)->List<ContextMenuItem>,hide:()->Unit) {
    var sms by remember(peer) {mutableStateOf<String?>(null)}
    var groupChat by remember(peer) {mutableStateOf<String?>(null)}
    groupChat?.let {group->Column(modifier.padding(12.dp)){GroupChatPanel(model,account,all,group,stationMenu){groupChat=null}};return}
    sms?.let {initial->Column(modifier.padding(12.dp)){SmsPanel(account,model,all,initial){sms=null}};return}
    val groupServer=model.settings.getProperty("groups.server")
    fun openRow(row:ChatRow){
        val number=smsPeer(row);val group=groupIn(row,groupServer)
        when {number!=null->sms=number;group!=null->groupChat=group;else->select(row.peer)}
    }
    fun label(row:ChatRow)=smsPeer(row)?.let {"SMS @"+it} ?: groupIn(row,groupServer)?.let {"Group "+it} ?: row.peer
    val fieldFocus=remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(focusRequest){if(focusRequest>0) fieldFocus.requestFocus()}
    val scope=rememberCoroutineScope()
    var recipient by remember {mutableStateOf("")}
    val drafts=remember {mutableStateMapOf<Pair<String,String?>,String>()}
    val draftKey=account to peer
    val draft=drafts[draftKey].orEmpty()
    var deliveryOptions by remember(account,peer) {mutableStateOf(false)}
    var sending by remember {mutableStateOf(false)}
    var chatOptions by remember(account,peer) {mutableStateOf(ChatOptions())}
    LaunchedEffect(account,peer) {if(peer!=null)chatOptions=model.database.options(account,peer)}
    val canSubmit=peer!=null && !sending && AprsMessages.validLongText(draft) && AprsProtocol.validCallsign(account) && AprsProtocol.validCallsign(peer) && account!=peer
    fun submitDraft() {
        if(!canSubmit || sending || drafts[draftKey]!=draft)return
        val submitted=draft;val key=draftKey;sending=true
        scope.launch {try {model.send(peer,submitted);if(drafts[key]==submitted) drafts.remove(key)} catch(_:Exception){model.error.value="Unable to save this message."} finally {sending=false}}
    }
    val focusManager=LocalFocusManager.current
    val history=all.filter {it.peer==peer}
    LaunchedEffect(account,peer,history){if(peer!=null)model.database.markDisplayed(account,peer,history.filter {it.unread}.map {it.id})}
    val cards=history.groupBy {if(it.multipartId==0L) -it.id else it.multipartId}.values.toList()
    val list=rememberLazyListState()
    LaunchedEffect(cards.size) {if(cards.isNotEmpty()) list.animateScrollToItem(cards.lastIndex)}
    Column(modifier.background(Panel).padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            val headingColor=if(peer==null)Accent else history.lastOrNull()?.let {conversationColor(history.any {r->r.unread},it.outgoing)} ?: Muted
            HelpText(if(peer==null) "MESSAGES" else peer,if(peer==null)Modifier else Modifier.border(1.dp,headingColor).padding(3.dp),fontSize=18.sp,fontWeight=FontWeight.Bold,color=headingColor)
            Spacer(Modifier.weight(1f)); Command("Hide",action=hide); if(peer!=null) HelpTextButton(UiHelp.action("Chats"),onClick={select(null)}) {HelpText("Chats")}
        }
        val unread=all.filter {it.unread && !it.outgoing}.groupBy(::label)
        if(unread.isNotEmpty()) {
            HelpText("New messages",fontWeight=FontWeight.Bold)
            Column(Modifier.heightIn(max=140.dp).verticalScroll(rememberScrollState())){
                unread.values.sortedByDescending {it.last().time}.forEach {messages->
                    val last=messages.last()
                    HelpTextButton("Read this conversation",onClick={openRow(last)}){HelpText(label(last)+" ("+messages.size+") · "+Instant.ofEpochMilli(last.time).atZone(ZoneId.systemDefault()).toLocalTime().withNano(0)+" · "+last.body.take(32),color=conversationColor(true,false),fontWeight=FontWeight.Bold)}
                }
            }
        }
        if(peer==null) {
            Command("New SMS conversation"){sms=""}
            Command("Group chat"){groupChat="HOTG"}
            Row(Modifier.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                HelpField(UiHelp.field("Callsign-SSID"),recipient,{recipient=it.trim().uppercase()},Modifier.weight(1f).focusRequester(fieldFocus),label={HelpText("Callsign-SSID")},singleLine=true,fill=true)
                HelpTextButton(UiHelp.action("Chat"),onClick={select(recipient)},enabled=AprsProtocol.validCallsign(recipient)&&recipient!=account&&account.isNotBlank()) {HelpText("Chat")}
            }
            val threads=all.groupBy(::label).values.sortedByDescending {it.last().time}
            LazyColumn {items(threads,key={label(it.last())}) {thread ->
                Column(Modifier.fillMaxWidth().clickable {openRow(thread.last())}.padding(vertical=12.dp)) {
                    val last=thread.last();val n=thread.count {it.unread}
                    HelpText(label(last)+" · "+(if(n>0) "$n unread" else if(last.outgoing) "Sent" else "Received"),color=conversationColor(n>0,last.outgoing),fontWeight=FontWeight.Bold)
                    HelpText(thread.last().body,maxLines=2,color=Muted,fontSize=12.sp)
                }; Divider()
            } }
        } else {
            HelpText("As ${account.ifBlank {"—"}}",fontSize=12.sp,color=Muted)
            HelpTextButton(UiHelp.action(if(deliveryOptions) "Hide delivery options" else "Delivery: "+when(chatOptions.route){"rf"->"Radio only";"is"->"Internet only";else->"Automatic"}),onClick={deliveryOptions=!deliveryOptions}) {HelpText(if(deliveryOptions) "Hide delivery options" else "Delivery: "+when(chatOptions.route){"rf"->"Radio only";"is"->"Internet only";else->"Automatic"})}
            if(deliveryOptions) {
                ChoiceMenu("Send via",chatOptions.route,listOf("best" to "Automatic (recommended)","rf" to "Radio only","is" to "Internet only"),Modifier.fillMaxWidth()) {route->scope.launch {val next=chatOptions.copy(route=route);model.database.saveOptions(account,peer,next);chatOptions=next}}
                Row(verticalAlignment=Alignment.CenterVertically) {HelpCheckbox(UiHelp.action("Request acknowledgment"),chatOptions.requestAck,{checked->scope.launch {val next=chatOptions.copy(requestAck=checked);model.database.saveOptions(account,peer,next);chatOptions=next}});HelpText("Request acknowledgment",fontSize=12.sp)}
            }
            LazyColumn(Modifier.weight(1f).padding(vertical=12.dp),state=list,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(cards,key={it.first().id}) {segments ->
                    val parts=if(segments.first().multipartId!=0L)segments.sortedBy {it.body.substringAfter('(').substringBefore('/').toIntOrNull()} else segments
                    val first=parts.first()
                    var details by remember {mutableStateOf(false)}
                    val cardColor=if(MaterialTheme.colors.isLight){if(first.outgoing)Color(0xFFD9F0E6) else Color(0xFFDEEAF4)} else if(first.outgoing) Color(0xFF24473F) else Color(0xFF263740)
                    Column(Modifier.fillMaxWidth().background(cardColor).padding(10.dp)) {
                    if(first.multipartId!=0L) {
                        HelpText(parts.joinToString(""){it.body.substringAfter(") ")})
                        val total=first.body.substringAfter('/').substringBefore(')')
                        HelpText(if(first.outgoing)parts.count {it.status=="ACKNOWLEDGED"}.toString()+"/"+total+" acknowledged" else parts.size.toString()+"/"+total+" received")
                        Command(if(details) "Hide packets" else "Show packets"){details=!details}
                    }
                    if(first.multipartId==0L || details) parts.forEach { row ->
                    if(first.multipartId!=0L) HelpText("Packet ID "+row.wireId)
                    HelpText(if(row.outgoing) "You" else row.peer,color=Accent,fontSize=11.sp)
                    HelpText(row.body,Modifier.padding(vertical=5.dp),fontSize=14.sp)
                    if(row.outgoing && row.status in listOf("NO_ACK","REJECTED")) HelpTextButton(UiHelp.action("Retry as new message"),onClick={scope.launch {model.database.retry(account,row.id)}}) {HelpText("Retry as new message")}
                    val deliveryStatus = when (row.status) {
                        "QUEUED" -> "Queued — waiting for a connection or eligible route"
                        "SENDING" -> "Sending…"
                        "WAITING" -> "Sent — waiting for acknowledgment"
                        "ACKNOWLEDGED" -> "Delivered — acknowledgment received"
                        "SENT" -> "Sent — acknowledgment not requested"
                        "NO_ACK" -> "No acknowledgment — delivery unconfirmed"
                        "REJECTED" -> "Rejected by recipient"
                        "RECEIVED" -> "Received"
                        else -> row.status.replace('_', ' ')
                    }
                    HelpText(deliveryStatus,fontSize=12.sp,color=if(row.status=="ACKNOWLEDGED") Accent else Muted)
                    HelpText(DateTimeFormatter.ofPattern("MMM d HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(row.time))+
                        if(row.outgoing && row.attempts>0) " · ${row.attempts} delivery attempt${if(row.attempts==1) "" else "s"}" else "",
                        fontSize=10.sp,color=Muted)
                } } }
            }
            OutlinedTextField(draft,{drafts[draftKey]=it},Modifier.fillMaxWidth().focusRequester(fieldFocus).onPreviewKeyEvent {event->
                when {
                    event.key==Key.Enter || event.key==Key.NumPadEnter -> {if(event.type==KeyEventType.KeyUp) submitDraft();true}
                    event.type==KeyEventType.KeyDown && event.key==Key.Tab -> {focusManager.moveFocus(if(event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next);true}
                    else -> false
                }
            }.padding(top=8.dp),label={Text("Message")},maxLines=3)
            if(draft.isNotEmpty() && !AprsMessages.validLongText(draft)) HelpText("Use 1–300 printable English characters; no line breaks, {, | or ~.",fontSize=12.sp,color=MaterialTheme.colors.error)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                val parts=if(AprsMessages.validLongText(draft)) AprsMessages.split(draft) else emptyList()
                val used=parts.lastOrNull()?.let {if(parts.size>1) it.substringAfter(") ").length else it.length} ?: 0
                val capacity=if(parts.size>1) 61 else 67
                HelpText(draft.length.toString()+"/300 · $used/$capacity · "+parts.size.coerceAtLeast(1)+" packet(s)",color=Muted,fontSize=10.sp)
                Spacer(Modifier.weight(1f))
                HelpButton(UiHelp.action("Send"),onClick={submitDraft()},enabled=canSubmit) {Icon(AppIcons.Send,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));HelpText("Send")}
            }
        }
    }
}

@Composable private fun ConnectionDialog(model: AppModel, dismiss: ()->Unit) {
    var call by remember {mutableStateOf(model.settings.getProperty("callsign",""))}
    var pass by remember {mutableStateOf(model.settings.getProperty("passcode",""))}
    var manual by remember {mutableStateOf(ConnectionSetup.problem(call,pass)==null)}
    var imported by remember {mutableStateOf(false)}
    var importMessage by remember {mutableStateOf("")}
    var ssidMenu by remember {mutableStateOf(false)}

    var receiveOnly by remember {mutableStateOf(pass=="-1")}
    var showPasscodeHelp by remember {mutableStateOf(false)}
    var validation by remember {mutableStateOf("")}
    val setupIssue by model.connectionSetupIssue.collectAsState()
    var host by remember {mutableStateOf(model.settings.getProperty("endpoint"))}
    val presets=model.servers
    var preset by remember {mutableStateOf(presets.indexOfFirst {it.endpoint==host})}
    var menu by remember {mutableStateOf(false)}
    val connectionScope=rememberCoroutineScope()
    val clipboard=remember {java.awt.Toolkit.getDefaultToolkit().systemClipboard}
    var copyNotice by remember {mutableStateOf("")}
    var copySequence by remember {mutableStateOf(0)}
    LaunchedEffect(copySequence) {if(copySequence>0) {delay(2000);copyNotice=""}}
    fun copyEmailText(text:String) {
        runCatching {clipboard.setContents(java.awt.datatransfer.StringSelection(text),null)}
            .onSuccess {copyNotice="Copied";copySequence++}
            .onFailure {copyNotice="";validation="Unable to copy right now. Select the address and press Ctrl+C, or try again."}
    }
    var testing by remember {mutableStateOf(false)}
    var testResult by remember {mutableStateOf("")}
    var testJob by remember {mutableStateOf<kotlinx.coroutines.Job?>(null)}
    LaunchedEffect(call,pass,receiveOnly,host) {testJob?.cancel();testing=false;testResult=""}
    var lat by remember {mutableStateOf(model.settings.getProperty("latitude","28.536725"))}
    var lon by remember {mutableStateOf(model.settings.getProperty("longitude","-81.242932"))}
    val bounds=remember {windowBounds(680,780)}
    DialogWindow(icon=AppIcon.window,onCloseRequest=dismiss,title="Station setup",state=rememberDialogState(position=bounds.composePosition,width=bounds.width.dp,height=bounds.height.dp)) {
        Surface {Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            HelpText("Station setup",fontSize=22.sp)
            ScrollableForm(Modifier.weight(1f).fillMaxWidth()) {
            HelpButton("Choose the XML saved by APRSIS32",onClick={
                val picker=javax.swing.JFileChooser().apply {fileFilter=javax.swing.filechooser.FileNameExtensionFilter("APRSIS32 settings (*.xml)","xml")}
                if(picker.showOpenDialog(window)==javax.swing.JFileChooser.APPROVE_OPTION) {
                    try {
                        val profile=readLegacyLogin(picker.selectedFile.toPath())
                        call=profile.call;pass=profile.pass;receiveOnly=profile.receiveOnly
                        profile.endpoint?.let {host=it;preset=presets.indexOfFirst {server->server.endpoint==it}}
                        if(profile.latitude!=null && profile.longitude!=null){lat=profile.latitude.toString();lon=profile.longitude.toString()}
                        imported=true;manual=false;validation="";importMessage=""
                    } catch(e:Exception) {importMessage=if(e is IllegalArgumentException && e.message in listOf("Choose an APRSIS32 XML file under 32 MB.","Choose an APRSIS32 configuration XML file.","This XML has no usable station callsign.","The saved APRS-IS passcode is invalid. Use manual setup.")) e.message.orEmpty() else "Unable to import this APRSIS32 configuration. Check the file or use manual setup."}
                }
            }){HelpText("Import APRSIS32 settings…")}
            HelpText("Import your callsign, APRS-IS passcode, server and receive-area coordinates. Radio ports and beacon settings are configured separately.",fontSize=12.sp)
            if(importMessage.isNotBlank()) HelpText(importMessage,color=MaterialTheme.colors.error)
            if(imported && !manual) HelpText("$call · "+(if(receiveOnly) "Receive only" else "Saved passcode loaded")+" · $host")
            if(!manual) HelpTextButton("Enter or edit station settings",onClick={manual=true}){HelpText(if(imported) "Edit imported settings" else "Enter settings manually")}
            if(manual && setupIssue!=null) HelpText(setupIssue!!,color=MaterialTheme.colors.error)
            if(validation.isNotBlank()) HelpText(validation,color=MaterialTheme.colors.error)
            if(manual) {
            Row {
                HelpField(UiHelp.field("Callsign-SSID"),call,{call=it.uppercase()},label={HelpText("Callsign-SSID")},singleLine=true,modifier=Modifier.weight(1f),fill=true)
                Box {
                    HelpTextButton(UiHelp.action("SSID"),onClick={ssidMenu=true}) {HelpText("SSID")}
                    DropdownMenu(expanded=ssidMenu,onDismissRequest={ssidMenu=false}) {(0..15).forEach {ssid->
                        HelpMenuItem(UiHelp.action(if(ssid==0) "0 (no suffix)" else "-$ssid"),onClick={runCatching {call=AprsIdentity.callsign(call.substringBefore('-'),ssid)};ssidMenu=false}) {HelpText(if(ssid==0) "0 (no suffix)" else "-$ssid")}
                    }}
                }
            }
            HelpField(UiHelp.field("APRS-IS passcode"),pass.takeUnless {it=="-1"}.orEmpty(),{pass=it},label={HelpText("APRS-IS passcode")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),enabled=!receiveOnly,singleLine=true,modifier=Modifier.fillMaxWidth(),fill=true)
            Row(verticalAlignment=Alignment.CenterVertically) {
                HelpCheckbox("Receive stations without sending Internet messages or beacons. A passcode is not needed in this mode.",receiveOnly,{receiveOnly=it;if(!it && pass=="-1")pass=""});HelpText("Receive only (no APRS-IS transmissions)")
            }
            Row {
                HelpTextButton("Read APRSISCE/32 wiki guidance for existing and new passcodes.",onClick={showPasscodeHelp=!showPasscodeHelp}){HelpText(if(showPasscodeHelp) "Hide passcode help" else "How do I get a passcode?")}
            }
            if(showPasscodeHelp) {
                HelpText("Already used APRS-IS? Use the same passcode for your callsign; it works across clients and SSIDs. This is not your website password.")
                HelpText("The APRSISCE/32 wiki asks licensed APRSISCE/32 users to email their licensed name and callsign. That service is specifically for APRSISCE/32 users; the draft accurately identifies APRSERJ64. You can choose Receive only while arranging a passcode.")
                val email=model.settings.getProperty("services.passcode_request_email")
                Box {
                    Column {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(email,modifier=Modifier.clickable {copyEmailText(email)},color=MaterialTheme.colors.primary,textDecoration=androidx.compose.ui.text.style.TextDecoration.Underline)
                        }
                        HelpText("Click the address to copy it, then paste it into the To field in your email app or website.",fontSize=12.sp)
                        Row {
                            HelpTextButton("Copy the passcode-request email address.",onClick={copyEmailText(email)}) {HelpText("Copy address")}
                            HelpTextButton("Copy recipient, subject and suggested message; paste them into your own email app or website.",onClick={copyEmailText(PasscodeRequest.copyRequest(email,call))}) {HelpText("Copy request")}
                        }
                        HelpText("Copy request includes your callsign. Replace the name placeholder before sending:")
                        Text("ENTER YOUR FULL LICENSED NAME HERE",fontWeight=FontWeight.Bold)
                    }
                    if(copyNotice.isNotEmpty()) Popup(alignment=Alignment.TopEnd,properties=PopupProperties(focusable=false)) {
                        Surface(color=MaterialTheme.colors.primary,contentColor=MaterialTheme.colors.onPrimary,elevation=8.dp,shape=MaterialTheme.shapes.small) {
                            Text(copyNotice,Modifier.padding(horizontal=16.dp,vertical=10.dp))
                        }
                    }
                }
                HelpTextButton("Open the preserved APRSISCE/32 wiki passcode instructions.",onClick={runCatching {Desktop.getDesktop().browse(URI(model.settings.getProperty("services.passcode_help_url")))}}){HelpText("Read APRSISCE/32 passcode instructions")}
                HelpTextButton("Open the APRSERJ64 project for maintainer support.",onClick={runCatching {Desktop.getDesktop().browse(URI(model.settings.getProperty("services.source_url")))}}){HelpText("APRSERJ64 project & support")}
            }
            Box {
                HelpOutlinedButton(UiHelp.action(if(preset>=0) presets[preset].name else "Custom server"),onClick={menu=true},modifier=Modifier.fillMaxWidth(),fill=true) {HelpText(if(preset>=0) presets[preset].name else "Custom server",Modifier.weight(1f));Icon(AppIcons.ArrowDropDown,null)}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    presets.forEachIndexed { index, server -> HelpMenuItem(UiHelp.action(server.name),onClick={preset=index; host=server.endpoint; menu=false}) {HelpText(server.name)} }
                    HelpMenuItem(UiHelp.action("Custom server"),onClick={preset=-1; menu=false}) {HelpText("Custom server")}
                }
            }
            HelpField(UiHelp.field("Server URL or host:port"),host,{host=it},label={HelpText("Server URL or host:port")},enabled=preset<0,singleLine=true,modifier=Modifier.fillMaxWidth(),fill=true)
            LocationField("Receive-area center",lat,lon) {a,b->lat=a;lon=b}
            HelpText("Save & connect starts your connection and enables reconnect after outages and on restart. Choosing Disconnect keeps it off until you connect again.",fontSize=12.sp)
            HelpOutlinedButton("Check these credentials with APRS-IS without saving them or sending messages or beacons.",enabled=!testing,onClick={
                validation=ConnectionSetup.problem(call,if(receiveOnly) "-1" else pass).orEmpty()
                if(validation.isBlank()) {
                    testing=true;testResult="Testing APRS-IS login…"
                    val testCall=call;val testPass=if(receiveOnly) "-1" else pass;val testHost=host
                    testJob=connectionScope.launch {
                        try {testResult=ConnectionProbe.test(testCall,testPass,testHost,network=model.network)}
                        catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                        catch(_:Exception) {testResult="Connection test failed. Check the server address and Internet connection."}
                        finally {testing=false}
                    }
                }
            }) {HelpText(if(testing) "Testing…" else "Test connection")}
            if(testResult.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer {Text(testResult)}
            HelpText("Your beacon position is separate: check Settings → My position before transmitting.",fontSize=12.sp)
            }
            }
            Row {HelpTextButton(UiHelp.action("Cancel"),onClick=dismiss){HelpText("Cancel")}; Spacer(Modifier.weight(1f)); HelpButton("Save your login and connect now. Reconnect automatically until you explicitly disconnect.",enabled=!testing && (manual || imported),onClick={
                val credential=if(receiveOnly) "-1" else pass.trim()
                validation=ConnectionSetup.problem(call,credential).orEmpty()
                if(validation.isBlank() && runCatching {AprsEndpoint.parse(host)}.isFailure) validation="Enter a valid APRS-IS server hostname and port, or choose a server from the list."
                if(validation.isBlank() && runCatching {MapLocation(lat.toDouble(),lon.toDouble())}.isFailure) validation="Enter a valid receive-area center and check its decoded coordinates."
                if(validation.isBlank()) {
                    if(model.connect(call,credential,host,lat.toDoubleOrNull()?:Double.NaN,lon.toDoubleOrNull()?:Double.NaN,true)) dismiss()
                    else validation=model.error.value
                }
            }){HelpText("Save & connect")}}
        } }
    }
}

private val stationKinds=listOf("all" to "All stations","weather" to "Weather","moving" to "Moving stations","objects" to "Objects & items","rf" to "Heard on radio","message-capable" to "Messaging stations","frequency" to "Repeaters / frequency","frequency-issues" to "Frequency issues","telemetry" to "Telemetry","status" to "Status reports","altitude" to "Altitude reports","direct-rf" to "Direct radio","igates" to "IGates","ambiguous" to "Approximate positions","mic-e" to "Mic-E")

private val stationSort=listOf("callsign" to "Callsign","recent" to "Most recent","distance" to "Nearest first")

@Composable private fun RibbonTab(label:String,selected:Boolean,onClick:()->Unit) {
    Column(Modifier.width(96.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Tab(selected=selected,onClick=onClick,modifier=Modifier.height(36.dp)) { Text(label,maxLines=1,color=if(selected) Accent else MaterialTheme.colors.onSurface) }
        Spacer(Modifier.width(40.dp).height(3.dp).background(if(selected) Accent else Color.Transparent))
    }
}

@Composable private fun CommandGroup(label:String,commands:@Composable RowScope.()->Unit) {
    Column(Modifier.padding(horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.height(52.dp),verticalAlignment=Alignment.CenterVertically,content=commands)
        Text(label,color=Muted,fontSize=11.sp)
    }
    Divider(Modifier.height(66.dp).width(1.dp))
}

@Composable private fun Command(label:String,enabled:Boolean=true,icon:androidx.compose.ui.graphics.vector.ImageVector?=null,action:()->Unit) {
    HelpTextButton(label,onClick=action,enabled=enabled){Column(horizontalAlignment=Alignment.CenterHorizontally){if(icon!=null) Icon(icon,null,Modifier.size(19.dp));HelpText(label)}}
}
