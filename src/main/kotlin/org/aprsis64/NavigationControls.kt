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

@Composable internal fun SectionNavigator(sections:List<NavigationSection>,selected:String,modifier:Modifier=Modifier,onSelect:(String)->Unit) {
 var query by remember {mutableStateOf("")}
 Column(modifier.padding(end=14.dp)) {
  HelpField(UiHelp.field("Find a section"),query,{query=it},label={HelpText("Find a section")},singleLine=true,modifier=Modifier.fillMaxWidth(),fill=true)
  val matches=sections.filter {query.isBlank() || (it.title+" "+it.id+" "+it.keywords).contains(query,true)}
  ScrollableForm(Modifier.weight(1f).padding(top=8.dp)) {
   if(matches.isEmpty()) HelpText("No matching section. Try position, map or messages.",fontSize=12.sp)
   matches.groupBy {it.group}.forEach {(group,entries)->
    HelpText(group,Modifier.padding(top=12.dp,bottom=4.dp),fontSize=11.sp,color=MaterialTheme.colors.onSurface.copy(alpha=.6f))
    entries.forEach {entry->
     val active=entry.id==selected
     Surface(color=if(active)MaterialTheme.colors.primary.copy(alpha=.18f) else Color.Transparent,shape=RoundedCornerShape(6.dp)) {
      HelpText(entry.title,Modifier.fillMaxWidth().clickable {onSelect(entry.id)}.padding(horizontal=10.dp,vertical=11.dp),fontSize=13.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,color=if(active)MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,help=UiHelp.section(entry.title,entry.keywords))
     }
    }
   }
  }
 }
}
