// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

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

@Composable fun PacketMonitorWindow(model: AppModel,dismiss: ()->Unit) {
    val entries by model.monitor.entries.collectAsState()
    val feed by model.monitor.feedStatus.collectAsState()
    val scope=rememberCoroutineScope()
    var query by remember {mutableStateOf("")}
    var satellites by remember {mutableStateOf(false)}
    var category by remember {mutableStateOf("All")}
    var paused by remember {mutableStateOf<List<TrafficEntry>?>(null)}
    var result by remember {mutableStateOf("")}
    val displayed=(paused ?: entries).filter {(!satellites || it.satellite) && (it.packet.contains(query,true)||it.port.contains(query,true)) && when(category){"RF"->it.port!="APRS-IS";"Duplicates"->it.duplicate;"Invalid"->it.invalid;else->true}}
    Window(onCloseRequest=dismiss,title="Packets — APRSIS64",state=rememberWindowState(width=1100.dp,height=760.dp)) {
        MaterialTheme(colors=darkColors()) {Surface {Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row {
                OutlinedTextField(query,{query=it},label={Text("Callsign, path or port")},modifier=Modifier.weight(1f),singleLine=true)
                TextButton(onClick={paused=if(paused==null) entries else null}) {Text(if(paused==null) "Pause display" else "Resume")}
                Button(onClick={val snapshot=displayed.toList();scope.launch {result=runCatching {"Saved ${model.monitor.export(snapshot)}"}.getOrElse {"Export failed"}}}) {Text("Export TNC2")}
            }
            Row {Checkbox(satellites,{satellites=it});Text("Satellite paths only",Modifier.padding(top=12.dp))}
            Row {listOf("All","RF","Duplicates","Invalid").forEach {name->TextButton(onClick={category=name}){Text(if(name==category)"[$name]" else name)}}}
            if(satellites) TextButton(onClick={java.awt.Desktop.getDesktop().browse(java.net.URI(model.settings.getProperty("services.satellite_status_url")))}) {Text("Current ISS radio status")}
            Text("$feed · ${displayed.size} packets",fontSize=12.sp)
            if(result.isNotBlank()) Text(result,fontSize=12.sp)
            key(category,satellites,query) {LazyColumn(Modifier.weight(1f).fillMaxWidth()) {items(displayed.asReversed(),key={it.sequence}) {entry ->
                Column(Modifier.padding(vertical=6.dp)) {
                    Text("${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.time))}  ${entry.direction}  ${entry.port}"+(if(entry.satellite) "  Satellite" else ""),color=MaterialTheme.colors.primary,fontSize=11.sp)
                    SelectionContainerCompat(entry.packet)
                };Divider()
            }}}
        }}}
    }
}
@Composable private fun SelectionContainerCompat(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {Text(text,fontFamily=FontFamily.Monospace,fontSize=12.sp)}
}
