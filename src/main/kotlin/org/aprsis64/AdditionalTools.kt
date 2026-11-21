// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.osmand.shared.aprs.*

@Composable fun AdditionalTools(model:AppModel,tab:String) {
 val scope=rememberCoroutineScope()
 val metadata by model.views.stations.collectAsState()
 val stations by model.stations.collectAsState()
 val revision by model.settingsRevision.collectAsState()
 var email by remember {mutableStateOf("")};var body by remember {mutableStateOf("")}
 var query by remember {mutableStateOf("")};var result by remember {mutableStateOf("")}
 if(tab=="Email") {
  Text("Gateway: ${model.settings.getProperty("messaging.email_gateway")}")
  OutlinedTextField(email,{email=it},label={Text("Email address")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(body,{body=it},label={Text("Email text")},modifier=Modifier.fillMaxWidth())
  val packet="$email $body"
  Text("${packet.length}/67 characters including address")
  Button(onClick={scope.launch {result=runCatching {model.send(model.settings.getProperty("messaging.email_gateway"),packet);"Queued; gateway replies appear in Messages"}.getOrElse {"Unable to queue email"}}},enabled=email.matches(Regex("[^ @]+@[^ @]+\\.[^ @]+")) && body.isNotBlank() && AprsMessages.validText(packet)){Text("Send email")}
 }else {
  OutlinedTextField(query,{query=it},label={Text(if(tab=="Platforms")"Callsign or platform" else "Callsign or frequency")},singleLine=true,modifier=Modifier.fillMaxWidth())
  val live=stations.map {it.callsign}.toSet()
  val rows=metadata.values.filter {it.call in live && (tab=="Platforms" || it.frequency!=null) && (it.call.contains(query,true) || it.platform.contains(query,true) || it.frequency?.mhz.toString().contains(query))}.sortedBy {if(tab=="Platforms")it.platform+it.call else it.frequency?.mhz.toString()+it.call}
  if(rows.isEmpty())Text("No matching received stations")
  rows.forEach {entry->
   Text(entry.call,color=MaterialTheme.colors.primary)
   if(tab=="Platforms")Text("${entry.platform} · ${if(entry.rf)"Heard on RF" else "APRS-IS"} · ${if(entry.messageCapable)"Messaging advertised" else "Messaging not advertised"}")
   entry.frequency?.let {f->Text("${"%.3f".format(java.util.Locale.US,f.mhz)} MHz · offset ${f.offsetMhz?.let {"$it MHz"}?:"unspecified"} · tone ${f.tone?:"unspecified"}"+(f.rangeMiles?.let {" · range $it miles"}?:""))}
   Divider()
  }
 }
 if(result.isNotEmpty())Text(result)
}
