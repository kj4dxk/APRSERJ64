// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ActionIcon(icon:ImageVector,label:String,enabled:Boolean=true,onClick:()->Unit) {
 HelpArea(UiHelp.action(label)) {
  IconButton(onClick=onClick,enabled=enabled) {Icon(icon,contentDescription=label)}
 }
}

@Composable internal fun ChoiceMenu(label:String,value:String,choices:List<Pair<String,String>>,modifier:Modifier=Modifier,help:String=UiHelp.field(label),onSelect:(String)->Unit) {
 var expanded by remember {mutableStateOf(false)}
 Box(modifier) {
  HelpOutlinedButton(help,onClick={expanded=true},modifier=Modifier.fillMaxWidth(),fill=true) {
   Column(Modifier.weight(1f)) {HelpText(label,fontSize=10.sp,color=MaterialTheme.colors.onSurface.copy(alpha=.65f));HelpText(choices.firstOrNull {it.first==value}?.second?:value,fontSize=13.sp)}
   Icon(AppIcons.ArrowDropDown,contentDescription=null)
  }
  DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=380.dp)) {
   choices.forEach {(key,title)->HelpMenuItem("Choose $title for ${label.lowercase()}. $help",onClick={onSelect(key);expanded=false}) {HelpText(title,fontWeight=if(key==value)FontWeight.Bold else FontWeight.Normal)}}
  }
 }
}

internal data class NavigationSection(val id:String,val title:String,val group:String,val keywords:String="")
internal val settingsSections=listOf(
 NavigationSection("Position","My position","Everyday","GPS location coordinates"),NavigationSection("Beacon","Beaconing","Everyday","transmit symbol timing speed"),
 NavigationSection("Map","Map appearance","Everyday","tiles provider overlay"),NavigationSection("Display","Station display","Everyday","list filters trails"),NavigationSection("Messaging","Messages","Everyday","chats acknowledgment"),
 NavigationSection("Operating","Receive area & filters","Everyday","radius range APRS-IS"),NavigationSection("Offline","Offline maps","Everyday","download cache"),
 NavigationSection("Status","Station status","Station & objects"),NavigationSection("Objects","Objects & items","Station & objects"),NavigationSection("Multitrack","Tracking windows","Station & objects"),
 NavigationSection("Weather","Weather alerts","Weather & data"),NavigationSection("Weatherstation","My weather station","Weather & data"),NavigationSection("Telemetry","Telemetry","Weather & data"),NavigationSection("Legacyweather","APRS weather","Weather & data"),NavigationSection("Digital","Digital-mode imports","Weather & data","FT8 JS8 JT65"),
 NavigationSection("History","History storage","Files & services"),NavigationSection("Logging","Track logging","Files & services","GPX CSV"),NavigationSection("Imports","GPX imports","Files & services"),NavigationSection("Services","Service addresses","Files & services","URLs pdeffenb"),NavigationSection("Connectivity","Connection recovery","Files & services"),
 NavigationSection("Groups","Group messages","Advanced"),NavigationSection("Bulletins","Bulletins","Advanced"),NavigationSection("Alerts","Notifications","Advanced"),NavigationSection("Tactical","Tactical overlays","Advanced"),NavigationSection("Qru","QRU server","Advanced"),NavigationSection("Queries","Query replies","Advanced"),NavigationSection("Satellites","Satellites","Advanced"),NavigationSection("Platforms","Device identification","Advanced"),NavigationSection("Kiosk","Unattended operation","Advanced")
)
internal val toolSections=listOf(
 NavigationSection("Station","Selected station","Everyday"),NavigationSection("Weather","Weather reports","Everyday"),NavigationSection("Tracks","Export a track","Everyday","GPX CSV"),NavigationSection("Offline","Download maps","Everyday","tiles offline"),NavigationSection("Alerts","Notifications","Everyday"),
 NavigationSection("Groups","Group messages","Messages & objects"),NavigationSection("QRU","Find nearby objects","Messages & objects"),NavigationSection("Bulletins","Bulletins","Messages & objects"),NavigationSection("Email","Send APRS email","Messages & objects"),NavigationSection("Frequency","Repeater objects","Messages & objects"),
 NavigationSection("Telemetry","Telemetry charts","Measurements"),NavigationSection("GPS sky","GPS reception","Measurements"),NavigationSection("Local sensors","Local sensors","Measurements"),
 NavigationSection("GPX import","Import GPX","Advanced"),NavigationSection("Satellites","Satellite passes","Advanced"),NavigationSection("Weather boundaries","Weather boundaries","Advanced"),NavigationSection("Radio DX","Radio reach","Advanced"),NavigationSection("Direction finding","Direction finding","Advanced"),NavigationSection("Platforms","Device identification","Advanced")
)

internal val navigationActions=listOf(
 NavigationSection("action:connection","My callsign & passcode","Connection","my call callsign call sign ssid password pass code login account change edit server connect"),
 NavigationSection("action:ports","Radio & GPS ports","Menu","serial TNC KISS wiring"),
 NavigationSection("action:packets","Packet monitor","Menu","diagnostics raw traffic server responses"),
 NavigationSection("action:about","About & license","Menu","version credits attribution manual help"),
 NavigationSection("action:alltools","All tools","Menu","station details"),
 NavigationSection("action:practice","Practice with local demo","Menu","sample simulated offline"),
 NavigationSection("action:disconnect","Disconnect APRS-IS / Stop practice","Menu","disconnect stop connection")
)

internal fun navigationResults(query:String,ownCall:String=""):List<NavigationSection> {
 val words=query.lowercase().trim().split(Regex("\\s+")).filter {it !in listOf("", "my", "the", "where", "to", "set", "change", "edit")}
 val entries=navigationActions+settingsSections.map {it.copy(id="settings:${it.id}",group="Settings")}+toolSections.map {it.copy(id="tools:${it.id}",group="Menu & tools")}
 return entries.filter {entry->
  val fields=if(entry.id.startsWith("settings:")) {
   val section=entry.id.substringAfter(':').lowercase()
   (BeaconPreferences.fields+BeaconPreferences.entityFields+FeaturePreferences.fields+OperatingPreferences.fields+ServicePreferences.fields+PortPreferences.global)
    .filter {it.key.startsWith("$section.") || section=="objects" && it in BeaconPreferences.entityFields}.joinToString(" ") {it.key+" "+it.label}
  } else ""
  val text="${entry.title} ${entry.keywords} $fields ${if(entry.id=="action:connection") ownCall else ""}".lowercase()
  words.all {it in text}
 }
}

@Composable internal fun SectionNavigator(sections:List<NavigationSection>,selected:String,modifier:Modifier=Modifier,ownCall:String="",onNavigate:(String)->Unit={},onSelect:(String)->Unit) {
 var query by remember {mutableStateOf("")}
 Column(modifier.padding(end=14.dp)) {
  HelpField("Search all settings, menu items and tools, including your callsign and passcode.",query,{query=it},label={HelpText("Search settings & tools")},singleLine=true,modifier=Modifier.fillMaxWidth(),fill=true)
  val matches=if(query.isBlank()) sections else navigationResults(query,ownCall)
  ScrollableForm(Modifier.weight(1f).padding(top=8.dp)) {
   if(matches.isEmpty()) HelpText("No matching section. Try position, map or messages.",fontSize=12.sp)
   matches.groupBy {it.group}.forEach {(group,entries)->
    HelpText(group,Modifier.padding(top=12.dp,bottom=4.dp),fontSize=11.sp,color=MaterialTheme.colors.onSurface.copy(alpha=.6f))
    entries.forEach {entry->
     val active=entry.id==selected
     Surface(color=if(active)MaterialTheme.colors.primary.copy(alpha=.18f) else Color.Transparent,shape=RoundedCornerShape(6.dp)) {
      HelpText(entry.title,Modifier.fillMaxWidth().clickable {if(query.isBlank()) onSelect(entry.id) else onNavigate(entry.id)}.padding(horizontal=10.dp,vertical=11.dp),fontSize=13.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,color=if(active)MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,help=UiHelp.section(entry.title,entry.keywords))
     }
    }
   }
  }
 }
}
