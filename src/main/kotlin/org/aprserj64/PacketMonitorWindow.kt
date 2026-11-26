// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PacketStream(val port: String, val direction: String) {
    val title get()=if(direction=="RX") "$port received" else "$port sent"
    fun matches(entry: TrafficEntry)=entry.port==port && entry.direction==direction
}

@Composable fun PacketMonitorWindow(model: AppModel,stream: PacketStream?=null,offset: Int=0,
    openStream: ((PacketStream)->Unit)?=null,dismiss: ()->Unit) {
    val entries by model.monitor.entries.collectAsState()
    val feed by model.monitor.feedStatus.collectAsState()
    val serverMessages by model.serverMessages.collectAsState()
    var showServerMessages by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    var query by remember {mutableStateOf("")}
    var satellites by remember {mutableStateOf(false)}
    var category by remember {mutableStateOf("All")}
    var paused by remember {mutableStateOf<List<TrafficEntry>?>(null)}
    var result by remember {mutableStateOf("")}
    val ports by model.portStatuses.collectAsState()
    val displayed=(paused ?: entries).filter {(stream==null || stream.matches(it)) && (!satellites || it.satellite) && (it.packet.contains(query,true)||it.port.contains(query,true)) && when(category){"RF"->it.port!="APRS-IS";"Duplicates"->it.duplicate;"Invalid"->it.invalid;else->true}}
    val bounds=remember {windowBounds(if(stream==null) 1100 else 760,if(stream==null) 760 else 520,offset)}
    Window(icon=AppIcon.window,onCloseRequest=dismiss,title="${stream?.title ?: "Packet monitor"} — APRSERJ64",
        state=rememberWindowState(position=bounds.composePosition,width=bounds.width.dp,height=bounds.height.dp)) {
        MaterialTheme(colors=applicationColors(model)) {Surface {Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row {
                HelpField(UiHelp.field("Callsign, path or port"),query,{query=it},label={HelpText("Callsign, path or port")},modifier=Modifier.weight(1f),singleLine=true,fill=true)
                HelpTextButton(UiHelp.action(if(paused==null) "Pause display" else "Resume"),onClick={paused=if(paused==null) entries else null}) {HelpText(if(paused==null) "Pause display" else "Resume")}
                HelpButton(UiHelp.action("Export TNC2"),onClick={val snapshot=displayed.toList();scope.launch {result=runCatching {"Saved ${model.monitor.export(snapshot)}"}.getOrElse {"Export failed"}}}) {HelpText("Export TNC2")}
            }
            if(openStream!=null) {
                var menu by remember {mutableStateOf(false)}
                val savedPorts=(model.ports.map {it.getProperty("name")}+ports.map {it.name}).distinct()
                Box {
                    HelpTextButton(UiHelp.action("Open packet stream"),onClick={menu=true}) {HelpText("Open packet stream")}
                    DropdownMenu(menu,{menu=false}) {
                        (listOf("APRS-IS")+savedPorts).distinct().forEach {port->
                            listOf("RX","TX").forEach {direction->
                                val selected=PacketStream(port,direction)
                                HelpMenuItem(UiHelp.action(selected.title),onClick={menu=false;openStream(selected)}) {HelpText(selected.title)}
                            }
                        }
                    }
                }
            }
            Row {HelpCheckbox(UiHelp.action("Satellite paths only"),satellites,{satellites=it});HelpText("Satellite paths only",Modifier.padding(top=12.dp))}
            Row {listOf("All","RF","Duplicates","Invalid").forEach {name->HelpTextButton(UiHelp.action(if(name==category)"[$name]" else name),onClick={category=name}){HelpText(if(name==category)"[$name]" else name)}}}
            if(satellites) HelpTextButton(UiHelp.action("Current ISS radio status"),onClick={java.awt.Desktop.getDesktop().browse(java.net.URI(model.settings.getProperty("services.satellite_status_url")))}) {HelpText("Current ISS radio status")}
            HelpText("$feed · ${displayed.size} packets",fontSize=12.sp)
            if(stream==null || stream.port=="APRS-IS") HelpTextButton("Show the latest APRS-IS server login responses and diagnostic comments. These are separate from station packets.",onClick={showServerMessages=!showServerMessages}) {HelpText(if(showServerMessages) "Hide server responses" else "Show server responses")}
            if(showServerMessages) Column(Modifier.heightIn(max=160.dp).verticalScroll(rememberScrollState())) {
                if(serverMessages.isEmpty()) HelpText("No APRS-IS server responses yet.")
                serverMessages.asReversed().forEach {SelectionContainerCompat(it)}
            }
            if(result.isNotBlank()) HelpText(result,fontSize=12.sp)
            key(category,satellites,query) {LazyColumn(Modifier.weight(1f).fillMaxWidth()) {items(displayed.asReversed(),key={it.sequence}) {entry ->
                Column(Modifier.padding(vertical=6.dp)) {
                    HelpText("${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.time))}  ${entry.direction}  ${entry.port}"+(if(entry.satellite) "  Satellite" else ""),color=MaterialTheme.colors.primary,fontSize=11.sp)
                    entry.issue?.let {HelpText(it,color=MaterialTheme.colors.error,fontSize=12.sp)}
                    SelectionContainerCompat(entry.packet)
                };Divider()
            }}}
        }}}
    }
}
@Composable private fun SelectionContainerCompat(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {HelpText(text,fontFamily=FontFamily.Monospace,fontSize=12.sp)}
}
