// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import com.fazecast.jSerialComm.SerialPort
import java.util.Properties
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

private val WorkspaceBackground=Color(0xFF0E1B22)
private val WorkspacePanel=Color(0xFF182B34)
private val WorkspaceMuted=Color(0xFFA8BCC7)
private val ReceiveColor=Color(0xFF6DDAC5)
private val SendColor=Color(0xFFF1BC72)
private val GpsColor=Color(0xFF83BEFF)
private val DigiColor=Color(0xFFD3A7FF)

@Composable fun PortWorkspaceWindow(model: AppModel, dismiss: ()->Unit) {
    var draft by remember {mutableStateOf(PortWorkspaceDraft(model.settings,model.ports,model.entities))}
    var selected by remember {mutableStateOf(draft.devices.firstOrNull()?.id)}
    var page by remember {mutableStateOf("Wiring")}
    var wiring by remember {mutableStateOf(false)}
    var showAll by remember {mutableStateOf(false)}
    var wizard by remember {mutableStateOf(false)}
    var confirm by remember {mutableStateOf<String?>(null)}
    var failure by remember {mutableStateOf("")}
    val statuses by model.portStatuses.collectAsState()
    val isState by model.state.collectAsState()
    val port=draft.devices.firstOrNull {it.id==selected}
    val issues=draft.issues()
    LaunchedEffect(port?.id,port?.protocol) {if(port?.protocol !in rawRadioProtocols) wiring=false}
    fun close() {if(draft.dirty) confirm="close" else dismiss()}
    Window(onCloseRequest={close()},title="Ports & connections — APRSIS64",
        state=rememberWindowState(placement=WindowPlacement.Maximized,width=1500.dp,height=940.dp)) {
        MaterialTheme(colors=darkColors(primary=ReceiveColor,background=WorkspaceBackground,surface=WorkspacePanel)) {
            Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(24.dp,16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column {Text("Ports & connections",fontSize=25.sp,fontWeight=FontWeight.Bold)
                        Text(if(draft.dirty) "Unsaved changes · connections change when you Apply" else "Saved configuration · live port status",color=WorkspaceMuted,fontSize=12.sp)}
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick={draft.undo();failure="";wiring=false;if(draft.devices.none {it.id==selected}) selected=draft.devices.firstOrNull()?.id},enabled=draft.canUndo) {Text("Undo")}
                    TextButton(onClick={draft.redo();failure="";wiring=false;if(draft.devices.none {it.id==selected}) selected=draft.devices.firstOrNull()?.id},enabled=draft.canRedo) {Text("Redo")}
                    TextButton(onClick={if(draft.dirty) confirm="reload" else {draft=PortWorkspaceDraft(model.settings,model.ports,model.entities);selected=draft.devices.firstOrNull()?.id}}) {Text("Reload saved")}
                    Spacer(Modifier.width(12.dp))
                    Button(onClick={wizard=true},enabled=draft.devices.size<32) {Text("+ Add port")}
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf("Wiring","Object routes","Radio limits").forEach {tab->
                        TextButton(onClick={page=tab;wiring=false}) {Text(tab,color=if(page==tab) ReceiveColor else WorkspaceMuted,fontWeight=if(page==tab) FontWeight.Bold else FontWeight.Normal)}
                    }
                    Spacer(Modifier.weight(1f))
                    if(page=="Wiring") {Checkbox(showAll,{showAll=it});Text("Show all wires",fontSize=12.sp)}
                }
                Divider()
                if(failure.isNotBlank()) Text(failure,Modifier.fillMaxWidth().background(Color(0xFF633E32)).padding(12.dp))
                if(issues.isNotEmpty()) Text(issues.take(3).joinToString("\n"),Modifier.fillMaxWidth().background(Color(0xFF4B3D24)).padding(12.dp),fontSize=13.sp)
                if(draft.values["position.source"] in listOf("gps_serial","gps_tcp","gpsd")) Row(Modifier.padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Your GPS currently uses the earlier connection settings.",fontSize=12.sp,color=WorkspaceMuted)
                    TextButton(onClick={selected=draft.importLegacyGps();page="Wiring"},enabled=draft.devices.size<32) {Text("Show it as a port")}
                }
                when(page) {
                    "Wiring"->Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(wiring) "Choose output ports for ${port?.name}. Click again to remove a wire." else "Select a port to see and edit its connections.",fontSize=13.sp,color=WorkspaceMuted,modifier=Modifier.weight(1f))
                                if(wiring) Button(onClick={wiring=false}) {Text("Done wiring")}
                            }
                            if(draft.devices.isEmpty()) Column(Modifier.fillMaxSize().padding(40.dp),verticalArrangement=Arrangement.Center) {
                                Text("Connect your first device",fontSize=24.sp)
                                Text("Choose a GPS, hardware TNC, or network TNC. The setup guide fills its connections for you.",Modifier.padding(vertical=16.dp),color=WorkspaceMuted)
                                Button(onClick={wizard=true}) {Text("Add a port")}
                            } else WiringDiagram(draft,port,statuses,isState.name,showAll,wiring,Modifier.weight(1f),{page="Object routes"}) {target->
                                if(wiring && port!=null && port.protocol in rawRadioProtocols && target.canTransmit && target.protocol in rawRadioProtocols) draft.toggleDigiTarget(port,target)
                                else if(!wiring) selected=target.id
                            }
                            Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                                Legend("Receive",ReceiveColor);Legend("Transmit",SendColor);Legend("GPS",GpsColor);Legend("Digipeat",DigiColor)
                            }
                        }
                        Divider(Modifier.fillMaxHeight().width(1.dp))
                        Box(Modifier.width(390.dp).fillMaxHeight().background(WorkspacePanel)) {
                            if(port!=null) PortInspector(draft,port,statuses.firstOrNull {it.name==draft.savedName(port.id)},
                                {selected=draft.duplicate(port)}, {draft.remove(port.id);selected=draft.devices.firstOrNull()?.id;wiring=false},
                                {wiring=!wiring}, {page="Object routes"})
                            else Text("Select a port",Modifier.padding(24.dp))
                        }
                    }
                    "Object routes"->ObjectRoutes(draft,Modifier.weight(1f).fillMaxWidth())
                    else->Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("IGate locality and duplicate limits",fontSize=20.sp)
                        Text("These limits apply to every radio port. Each port keeps its own heard-station history.",color=WorkspaceMuted)
                        PortPreferences.global.forEach {field->WorkspaceField(field,draft.values[field.key].orEmpty()) {value->draft.change {draft.values[field.key]=value}}}
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(20.dp,12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("${draft.devices.size} ports · ${draft.objects.size} objects",color=WorkspaceMuted,modifier=Modifier.weight(1f))
                    TextButton(onClick={close()}) {Text("Close")}
                    Button(onClick={
                        if(!draft.matchesBaseline(model.settings,model.ports,model.entities)) failure="Settings changed in another window. Reload saved settings before applying this draft."
                        else if(model.savePreferences(draft.values.properties(),draft.objectProperties(),draft.portProperties())) {draft.markSaved();failure="";wiring=false}
                        else failure="Could not apply. Check the listed settings and that the application folder is writable."
                    },enabled=draft.dirty && issues.isEmpty()) {Text("Apply changes")}
                }
            } }
            if(wizard) AddPortWizard(draft,model.portPresets,{wizard=false}) {props,useGps->
                selected=draft.add(props);if(useGps) draft.useGps(draft.devices.first {it.id==selected})
                wizard=false;page="Wiring"
            }
            if(confirm!=null) AlertDialog(onDismissRequest={confirm=null},title={Text("Discard unsaved changes?")},
                text={Text("Your running connections and saved settings have not changed.")},
                confirmButton={TextButton(onClick={val action=confirm;confirm=null;if(action=="close") dismiss() else {draft=PortWorkspaceDraft(model.settings,model.ports,model.entities);selected=draft.devices.firstOrNull()?.id;failure="";wiring=false}}) {Text("Discard changes")}},
                dismissButton={TextButton(onClick={confirm=null}) {Text("Keep editing")}})
        }
    }
}

@Composable private fun Legend(text: String,color: Color) {Row(verticalAlignment=Alignment.CenterVertically) {Box(Modifier.size(9.dp).background(color,RoundedCornerShape(5.dp)));Spacer(Modifier.width(6.dp));Text(text,fontSize=11.sp,color=WorkspaceMuted)}}

private data class DiagramWire(val service: Int,val key: String,val label: String,val configured: Boolean,val effective: Boolean,val reverse: Boolean,val color: Color)
private fun wires(draft: PortWorkspaceDraft,p: PortDraft): List<DiagramWire> = buildList {
    val tx=p.enabled && p.canTransmit && p.values["transmit"]=="true"
    val gate=p.values["passall"]!="true"
    if(p.protocol=="NMEA" || p.values["provides_nmea"]=="true") {
        val selected=draft.values["position.source"]=="port" && draft.values["position.port_name"]==p.name
        add(DiagramWire(0,"gps","GPS source",selected,selected && p.enabled,false,GpsColor))
    }
    if(p.radio) {
        add(DiagramWire(1,"rf_to_is","RF → Internet",p.values["rf_to_is"]=="true",p.enabled && gate && p.values["rf_to_is"]=="true",false,ReceiveColor))
        if(p.canTransmit) add(DiagramWire(1,"is_to_rf","Internet → RF",p.values["is_to_rf"]=="true",tx && gate && p.values["is_to_rf"]=="true",true,SendColor))
        add(DiagramWire(2,"receive","Stations → map",true,p.enabled,false,ReceiveColor))
        if(p.canTransmit) {
            add(DiagramWire(2,"messages","Chat → radio",p.values["messages"]=="true",tx && p.values["messages"]=="true",true,SendColor))
            val beacon=draft.values["beacon.enabled"]=="true" && draft.values["beacon.radio"]=="true"
            add(DiagramWire(3,"beacon","Beacon → radio",p.values["beacon"]=="true",tx && beacon && p.values["beacon"]=="true",true,SendColor))
            add(DiagramWire(4,"objects","Objects → radio",p.values["objects"]=="true",tx && draft.values["beacon.enabled"]=="true" && p.values["objects"]=="true",true,SendColor))
        }
    }
}

@Composable private fun WiringDiagram(draft: PortWorkspaceDraft,selected: PortDraft?,statuses: List<PortStatus>,isState: String,all: Boolean,wiring: Boolean,modifier: Modifier,objects: ()->Unit,select: (PortDraft)->Unit) {
    val selectedIndex=draft.devices.indexOfFirst {it.id==selected?.id}.coerceAtLeast(0)
    val serviceTop=maxOf(26,26+selectedIndex*116-244)
    val height=maxOf(720,60+draft.devices.size*116,serviceTop+700)
    val services=listOf("Position source","APRS-IS","Map & messages","Station beacon","Static objects")
    val vertical=rememberScrollState();val horizontal=rememberScrollState()
    Box(modifier.fillMaxWidth()) {
      Box(Modifier.fillMaxSize().padding(end=12.dp,bottom=12.dp).verticalScroll(vertical).horizontalScroll(horizontal)) {
        Box(Modifier.requiredSize(900.dp,height.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                fun curve(start: Offset,end: Offset,color: Color,active: Boolean,reverse: Boolean=false,bend: Float?=null,endLeft: Boolean=false) {
                    val control=bend ?: (start.x+end.x)/2
                    val path=Path().apply {moveTo(start.x,start.y);cubicTo(control,start.y,control,end.y,end.x,end.y)}
                    drawPath(path,color,style=Stroke(width=if(active) 2.2.dp.toPx() else 1.4.dp.toPx(),pathEffect=if(active) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),5.dp.toPx()))))
                    val tip=if(reverse) start else end;val dx=if(reverse || endLeft) 7.dp.toPx() else -7.dp.toPx()
                    drawLine(color,tip,tip+Offset(dx,-4.dp.toPx()),2.dp.toPx());drawLine(color,tip,tip+Offset(dx,4.dp.toPx()),2.dp.toPx())
                }
                draft.devices.forEachIndexed {index,p->
                    if(all || p.id==selected?.id) {
                        wires(draft,p).filter {it.configured}.forEach {wire->
                            val offset=if(wire.reverse) 9 else -9
                            curve(Offset(270.dp.toPx(),(76+index*116+offset).dp.toPx()),Offset(490.dp.toPx(),(serviceTop+50+wire.service*122+offset).dp.toPx()),
                                wire.color.copy(alpha=if(p.id==selected?.id) 1f else .25f),wire.effective,wire.reverse)
                        }
                        if(p.values["digipeater"]=="true") names(p.values["digi_outputs"]).ifEmpty {listOf(p.name)}.forEach {target->
                            val targetIndex=draft.devices.indexOfFirst {it.name==target}
                            if(targetIndex>=0) curve(Offset(270.dp.toPx(),(94+index*116).dp.toPx()),Offset(270.dp.toPx(),(58+targetIndex*116).dp.toPx()),
                                DigiColor.copy(alpha=if(p.id==selected?.id) 1f else .25f),p.enabled && p.values["passall"]!="true" && names(p.values["digi_transforms"]).isNotEmpty() && draft.devices[targetIndex].enabled && draft.devices[targetIndex].values["transmit"]=="true",bend=400.dp.toPx(),endLeft=true)
                        }
                    }
                }
                // Internal application routes are visible too, even with no selected RF output.
                fun servicePoint(index: Int,offset: Int=0)=Offset(770.dp.toPx(),(serviceTop+50+index*122+offset).dp.toPx())
                val beaconOn=draft.values["beacon.enabled"]=="true"
                curve(servicePoint(0),servicePoint(3,-8),GpsColor,beaconOn,bend=820.dp.toPx(),endLeft=true)
                if(draft.values["beacon.internet"]=="true") curve(servicePoint(3,8),servicePoint(1,-8),SendColor,beaconOn,bend=855.dp.toPx(),endLeft=true)
                if(draft.objects.any {it["internet"]=="true" && it["enabled"]=="true"}) curve(servicePoint(4),servicePoint(1,8),SendColor,beaconOn,bend=890.dp.toPx(),endLeft=true)
                curve(servicePoint(1,18),servicePoint(2,-8),ReceiveColor,true,bend=810.dp.toPx(),endLeft=true)
                curve(servicePoint(2,8),servicePoint(1,28),SendColor,true,bend=835.dp.toPx(),endLeft=true)
            }
            draft.devices.forEachIndexed {index,p->
                val status=statuses.firstOrNull {it.name==draft.savedName(p.id)}
                val target=wiring && p.canTransmit && p.protocol in rawRadioProtocols
                Surface(Modifier.offset(20.dp,(26+index*116).dp).size(250.dp,100.dp).clickable(enabled=!wiring || target) {select(p)},
                    shape=RoundedCornerShape(12.dp),color=if(p.id==selected?.id) Color(0xFF24444D) else WorkspacePanel,
                    border=BorderStroke(if(p.id==selected?.id || target) 2.dp else 1.dp,if(target) DigiColor else if(p.id==selected?.id) ReceiveColor else Color(0xFF314650))) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(p.name,fontWeight=FontWeight.Bold,maxLines=1)
                        Text("${p.protocol} · ${if(p.values["transport"]=="serial") p.values["device"].orEmpty().ifBlank {"Choose device"} else p.values["endpoint"].orEmpty().ifBlank {"Enter address"}}",fontSize=11.sp,color=WorkspaceMuted,maxLines=1)
                        Text(if(draft.portChanged(p.id)) "Pending Apply · live: ${status?.detail ?: "not running"}" else if(!p.enabled) "Disabled" else status?.detail ?: "Not running",fontSize=11.sp,color=if(p.enabled && status?.detail=="Connected") ReceiveColor else WorkspaceMuted,maxLines=1)
                        if(status!=null) Text("RX ${status.received} · TX ${status.transmitted}",fontSize=10.sp,color=WorkspaceMuted)
                    }
                }
            }
            services.forEachIndexed {index,title->
                Surface(Modifier.offset(490.dp,(serviceTop+index*122).dp).size(280.dp,110.dp),shape=RoundedCornerShape(12.dp),color=WorkspacePanel) {
                    Column(Modifier.padding(12.dp)) {
                        Text(title,fontWeight=FontWeight.Bold)
                        val detail=when(index) {
                            0->if(draft.values["position.source"]=="port") draft.values["position.port_name"].orEmpty().ifBlank {"Choose GPS"} else if(draft.values["position.source"]=="fixed") "Fixed position" else "Legacy GPS connection"
                            1->"$isState · ${draft.values["endpoint"].orEmpty()}"
                            2->"Received stations and saved conversations"
                            3->if(draft.values["beacon.enabled"]=="true") "Beaconing enabled" else "Master beacon switch off"
                            else->"${draft.objects.size} configured · individual routes in Object routes"
                        }
                        Text(detail,fontSize=10.sp,color=WorkspaceMuted,maxLines=2)
                        val links=selected?.let {wires(draft,it).filter {w->w.service==index && w.key!="receive"}}.orEmpty()
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {links.forEach {link->
                            TextButton(onClick={if(link.key=="gps") draft.useGps(selected!!,enable=true) else draft.set(selected!!.id,link.key,(!link.configured).toString())},enabled=link.key!="gps" || !link.configured || selected?.enabled!=true,contentPadding=PaddingValues(4.dp)) {
                                Text(if(link.key=="gps") (if(selected?.enabled!=true) "Enable GPS" else if(link.configured) "Selected GPS" else "Use this GPS") else (if(link.configured) "● " else "○ ")+link.label,fontSize=10.sp,color=if(link.configured) link.color else WorkspaceMuted)
                            }
                        }
                            if(index==3) TextButton(onClick={draft.change {draft.values["beacon.internet"]=(draft.values["beacon.internet"]!="true").toString()}},contentPadding=PaddingValues(4.dp)) {
                                Text((if(draft.values["beacon.internet"]=="true") "● " else "○ ")+"Internet",fontSize=10.sp,color=SendColor)
                            }
                            if(index==4) TextButton(onClick=objects,contentPadding=PaddingValues(4.dp)) {Text("Routes",fontSize=10.sp)}
                        }
                    }
                }
            }
            Text("Solid: eligible in this configuration · Dashed: blocked by a switch or missing rules\nLive device status is shown on each port; a wire does not prove packet delivery.",
                Modifier.offset(490.dp,(serviceTop+614).dp).width(280.dp),fontSize=11.sp,color=WorkspaceMuted)
        }
      }
      VerticalScrollbar(rememberScrollbarAdapter(vertical),Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom=12.dp))
      HorizontalScrollbar(rememberScrollbarAdapter(horizontal),Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end=12.dp))
    }
}

@Composable private fun RouteSwitch(title: String,description: String,checked: Boolean,enabled: Boolean=true,change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Text(title,fontSize=13.sp);Text(description,fontSize=11.sp,color=WorkspaceMuted)}
        Switch(checked,change,modifier=Modifier.semantics {contentDescription=title},enabled=enabled)
    }
}

@Composable internal fun WorkspaceField(field: BeaconField,value: String,change: (String)->Unit) {
    if(field.kind==SettingKind.BOOLEAN) RouteSwitch(field.label,"",value.toBoolean()) {change(it.toString())}
    else if(field.choices.isNotEmpty()) ChoiceField(field.label,value,field.choices,change)
    else OutlinedTextField(value,change,label={Text(field.label)},modifier=Modifier.fillMaxWidth(),singleLine=field.kind!=SettingKind.STRINGS,maxLines=if(field.kind==SettingKind.STRINGS) 5 else 1)
}
@Composable private fun ChoiceField(label: String,value: String,choices: List<String>,change: (String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {Text("$label: $value")}
        DropdownMenu(expanded,{expanded=false}) {choices.forEach {choice->DropdownMenuItem(onClick={change(choice);expanded=false}) {Text(choice)}}}}
}

@Composable private fun DeviceConnectionFields(values: Map<String,String>,set: (String,String)->Unit) {
    val scope=rememberCoroutineScope()
    var devices by remember {mutableStateOf<List<Pair<String,String>>>(emptyList())}
    var scanning by remember {mutableStateOf(false)}
    var scanError by remember {mutableStateOf(false)}
    fun refresh() {scope.launch {
        scanning=true;scanError=false
        try {devices=withContext(Dispatchers.IO) {SerialPort.getCommPorts().map {it.systemPortName to it.descriptivePortName}}}
        catch(cancelled: CancellationException) {throw cancelled}
        catch(_: Exception) {scanError=true} finally {scanning=false}
    }}
    LaunchedEffect(Unit) {refresh()}
    if(values["protocol"]=="AGW") Text("TCP connection",color=WorkspaceMuted)
    else ChoiceField("Connection",values["transport"].orEmpty(),listOf("serial","tcp")) {set("transport",it)}
    if(values["transport"]=="serial") {
        var menu by remember {mutableStateOf(false)}
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()) {Text(if(scanning) "Finding devices…" else "Choose detected device")}
                DropdownMenu(menu,{menu=false}) {
                    if(devices.isEmpty()) DropdownMenuItem(onClick={},enabled=false) {Text("No serial devices detected")}
                    devices.forEach {(name,description)->DropdownMenuItem(onClick={set("device",name);menu=false}) {Text("$name · $description")}}
                }
            }
            TextButton(onClick={refresh()},enabled=!scanning) {Text("Refresh")}
        }
        OutlinedTextField(values["device"].orEmpty(),{set("device",it.trim())},label={Text("Serial device")},placeholder={Text("COM3")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        if(scanError) Text("Device discovery unavailable. Enter the COM name manually.",fontSize=11.sp,color=WorkspaceMuted)
        OutlinedTextField(values["baud"].orEmpty(),{set("baud",it)},label={Text("Serial baud")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    } else OutlinedTextField(values["endpoint"].orEmpty(),{set("endpoint",it.trim())},label={Text("Host:port")},placeholder={Text("Enter the TNC or GPS address")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    if(values["protocol"] in rawRadioProtocols && values["protocol"]!="TEXT")
        OutlinedTextField(values["channel"].orEmpty(),{set("channel",it)},label={Text(if(values["protocol"]=="AGW") "AGW radio number (starts at 0)" else "KISS channel (usually 0)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
}

@Composable private fun PortInspector(draft: PortWorkspaceDraft,port: PortDraft,status: PortStatus?,duplicate: ()->Unit,remove: ()->Unit,wire: ()->Unit,objects: ()->Unit) {
    var rename by remember(port.id) {mutableStateOf(false)}
    var name by remember(port.id) {mutableStateOf(port.name)}
    var delete by remember(port.id) {mutableStateOf(false)}
    var advanced by remember(port.id) {mutableStateOf(false)}
    var rules by remember(port.id) {mutableStateOf(false)}
    var profileChange by remember(port.id) {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {Text(port.name,Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold);TextButton(onClick={name=port.name;rename=true}) {Text("Rename")}}
        ChoiceField("Profile",port.protocol,PortPreferences.protocols) {if(it!=port.protocol) profileChange=it}
        if(status!=null) Text("Live: ${status.detail} · RX ${status.received} · TX ${status.transmitted}"+(if(status.dropped>0) " · Dropped ${status.dropped}" else ""),fontSize=11.sp,color=WorkspaceMuted)
        RouteSwitch("Port enabled","Applied changes open or close this connection.",port.enabled) {draft.set(port.id,"enabled",it.toString())}
        DeviceConnectionFields(port.values) {key,value->draft.set(port.id,key,value)}
        Divider(Modifier.padding(vertical=6.dp))
        Text("Connections",fontWeight=FontWeight.Bold,fontSize=17.sp)
        if(port.protocol=="NMEA" || port.values["provides_nmea"]=="true") {
            val active=draft.values["position.source"]=="port" && draft.values["position.port_name"]==port.name
            Text(if(active) "This port supplies your position." else "GPS is available on this port.",color=GpsColor,fontSize=12.sp)
            if(!active || !port.enabled) Button(onClick={draft.useGps(port,enable=true)}) {Text(if(port.enabled) "Use this GPS" else "Select & enable GPS")}
            else OutlinedButton(onClick={draft.fixedPosition()}) {Text("Use fixed position instead")}
        }
        if(port.radio) {
            Text("Received stations appear on the map automatically.",color=WorkspaceMuted,fontSize=12.sp)
            RouteSwitch("RF → APRS-IS","Upload eligible packets heard by this radio.",port.values["rf_to_is"]=="true") {draft.set(port.id,"rf_to_is",it.toString())}
            if(port.canTransmit) {
                RouteSwitch("Radio transmit","Master switch for this port's RF output.",port.values["transmit"]=="true") {draft.set(port.id,"transmit",it.toString())}
                RouteSwitch("APRS-IS → RF","Messages for local stations and associated positions.",port.values["is_to_rf"]=="true") {draft.set(port.id,"is_to_rf",it.toString())}
                RouteSwitch("My messages → RF","Prefer this radio for stations heard here.",port.values["messages"]=="true") {draft.set(port.id,"messages",it.toString())}
                RouteSwitch("My beacon → RF","Send your position on this radio.",port.values["beacon"]=="true") {draft.set(port.id,"beacon",it.toString())}
                RouteSwitch("Objects → RF","Allow objects assigned to this radio.",port.values["objects"]=="true") {draft.set(port.id,"objects",it.toString())}
                TextButton(onClick=objects) {Text("Choose which objects use this port →")}
                if(draft.values["beacon.enabled"]!="true") TextButton(onClick={draft.change {draft.values["beacon.enabled"]="true"}}) {Text("Enable the master beacon switch")}
                if(draft.values["beacon.radio"]!="true") TextButton(onClick={draft.change {draft.values["beacon.radio"]="true"}}) {Text("Enable station beacon output to RF")}
                PortPreferences.fields.filter {it.key in listOf("beacon_path","message_path")}.forEach {f->WorkspaceField(f,port.values[f.key].orEmpty()) {draft.set(port.id,f.key,it)}}
                Text("Blank path sends directly. The prefilled paths allow nearby digipeaters to relay your packets.",fontSize=11.sp,color=WorkspaceMuted)
            } else Text("This profile receives only; RF transmit routes are unavailable.",fontSize=12.sp,color=WorkspaceMuted)
            if(port.protocol in rawRadioProtocols) {
                Divider(Modifier.padding(vertical=6.dp))
                RouteSwitch("Digipeater","Repeat packets matching the rules below.",port.values["digipeater"]=="true") {draft.set(port.id,"digipeater",it.toString())}
                val targets=names(port.values["digi_outputs"]).ifEmpty {listOf(port.name)}
                Text("Outputs: ${targets.joinToString()} · ${names(port.values["digi_transforms"]).size} rules",fontSize=12.sp,color=DigiColor)
                OutlinedButton(onClick=wire,modifier=Modifier.fillMaxWidth()) {Text("Wire digipeater outputs on diagram")}
                TextButton(onClick={rules=!rules}) {Text(if(rules) "Hide digipeater rules" else "Edit digipeater rules")}
                if(rules) DigiRules(port) {draft.set(port.id,"digi_transforms",encodedNames(it))}
                if(port.values["digipeater"]=="true" && names(port.values["digi_transforms"]).isEmpty()) Text("Add a rule to activate repeating.",color=SendColor,fontSize=12.sp)
            }
        }
        if(port.values["passall"]=="true") Text("PASSALL is selected: IGating and digipeating are blocked on this port.",color=SendColor,fontSize=12.sp)
        Divider(Modifier.padding(vertical=6.dp))
        TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "Hide advanced settings" else "Advanced device settings")}
        if(advanced) {
            PortPreferences.fields.filter {it.key in listOf("provides_nmea","no_gate_me","passall","gate_calls","gate_objects","open_commands","close_commands","command_prompt","command_timeout_ms","command_delay_ms","reconnect_seconds","transmit_per_minute","minimum_transmit_ms","kiss_txdelay","kiss_persistence","kiss_slot","kiss_tail","kiss_duplex")}
                .filter {port.radio || it.key in listOf("open_commands","close_commands","command_prompt","command_timeout_ms","command_delay_ms","reconnect_seconds")}
                .filter {it.key!="provides_nmea" || port.protocol in PortPreferences.nmeaProtocols}
                .forEach {f->WorkspaceField(f,port.values[f.key].orEmpty()) {draft.set(port.id,f.key,it)}}
        }
        Row {TextButton(onClick=duplicate,enabled=draft.devices.size<32) {Text("Duplicate")};TextButton(onClick={delete=true}) {Text("Remove port")}}
    }
    if(rename) AlertDialog(onDismissRequest={rename=false},title={Text("Rename port")},text={Column {
        OutlinedTextField(name,{name=it},label={Text("Name")},singleLine=true)
        Text("GPS, object and digipeater references follow the new name.",Modifier.padding(top=12.dp),fontSize=12.sp)
    }},confirmButton={TextButton(onClick={draft.rename(port.id,name.trim());rename=false},enabled=name.trim().isNotEmpty() && name.trim().length<=64 && draft.devices.none {it.id!=port.id && it.name==name.trim()}) {Text("Rename")}},dismissButton={TextButton(onClick={rename=false}) {Text("Cancel")}})
    if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("Remove ${port.name}?")},text={Text("Its wires will be removed. Routes with no remaining output are disabled. If this is your GPS source, choose a replacement before applying. You can undo this change.")},confirmButton={TextButton(onClick={delete=false;remove()}) {Text("Remove")}},dismissButton={TextButton(onClick={delete=false}) {Text("Keep port")}})
    profileChange?.let {type->AlertDialog(onDismissRequest={profileChange=null},title={Text("Use $type?")},
        text={Text("This restores the selected profile's commands and routing defaults. The port name and connection details stay in place; review the baud and address for the new device. Nothing changes on the device until you Apply.")},
        confirmButton={TextButton(onClick={draft.profile(port,type);profileChange=null}) {Text("Use profile")}},dismissButton={TextButton(onClick={profileChange=null}) {Text("Cancel")}})}
}

@Composable private fun DigiRules(port: PortDraft,change: (List<String>)->Unit) {
    val rows=names(port.values["digi_transforms"])
    fun replace(index: Int,value: String)=change(rows.toMutableList().apply {set(index,value)})
    Text("Match the first unused alias, insert our callsign, then use the remaining path.",fontSize=12.sp,color=WorkspaceMuted)
    rows.forEachIndexed {index,rule->
        val alias=rule.substringBefore('=');val rest=rule.substringAfter('=',"");val flags=rest.takeWhile {it in "!@"};val remaining=rest.drop(flags.length)
        Surface(shape=RoundedCornerShape(8.dp),color=WorkspaceBackground) {Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(alias,{replace(index,it.uppercase()+"="+rest)},label={Text("Incoming alias")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(remaining,{replace(index,alias+"="+flags+it.uppercase())},label={Text("Remaining path (blank: finished)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            RouteSwitch("Include my callsign","Identify this digipeater in the path.",'!' !in flags) {on->replace(index,alias+"="+(if(on) "" else "!")+(if('@' in flags) "@" else "")+remaining)}
            RouteSwitch("Discard earlier used hops","APRSIS32 UIFLOOD-style replacement.",'@' in flags) {on->replace(index,alias+"="+(if('!' in flags) "!" else "")+(if(on) "@" else "")+remaining)}
            TextButton(onClick={change(rows.filterIndexed {i,_->i!=index})}) {Text("Remove rule")}
        }}
    }
    TextButton(onClick={change(rows+"ALIAS=")},enabled=rows.size<64) {Text("+ Add rule")}
    Row {TextButton(onClick={change(listOf("WIDE1-1="))}) {Text("WIDE1 fill-in")};TextButton(onClick={change(listOf("WIDE2-2=WIDE2-1","WIDE2-1="))}) {Text("WIDE2 trace")}}
}

@Composable private fun ObjectRoutes(draft: PortWorkspaceDraft,modifier: Modifier) {
    val radios=draft.devices.filter {it.canTransmit}
    val horizontal=rememberScrollState()
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Choose each object's destinations",fontSize=22.sp,fontWeight=FontWeight.Bold)
        Text("All RF ports includes future ports. Selecting individual destinations keeps that object's route explicit.",color=WorkspaceMuted)
        HorizontalScrollbar(rememberScrollbarAdapter(horizontal),Modifier.fillMaxWidth())
        if(draft.objects.isEmpty()) Text("No objects yet. Create a static object in Settings → Objects, then choose its destinations here.",color=WorkspaceMuted)
        else Box(Modifier.horizontalScroll(horizontal)) {Column(Modifier.requiredWidth((440+radios.size*140).dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Object",Modifier.width(220.dp),fontWeight=FontWeight.Bold)
                Text("Internet",Modifier.width(100.dp));Text("All RF ports",Modifier.width(120.dp))
                radios.forEach {p->Column(Modifier.width(140.dp)) {Text(p.name,fontWeight=FontWeight.Bold);Text(if(!p.enabled) "Port disabled" else if(p.values["objects"]!="true") "Object output off" else "",fontSize=10.sp,color=SendColor)}}
            }
            Divider(Modifier.padding(vertical=12.dp))
            draft.objects.forEachIndexed {index,obj->
                Row(Modifier.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.width(220.dp)) {Text(obj["name"].orEmpty(),fontWeight=FontWeight.Bold)
                        Text(if(obj["enabled"]!="true") "Object disabled" else if(obj["alive"]!="true") "Kill report" else "${obj["source"]} · ${obj["interval_seconds"]} s",fontSize=11.sp,color=WorkspaceMuted)}
                    Box(Modifier.width(100.dp)) {Checkbox(obj["internet"]=="true",{value->draft.change {obj["internet"]=value.toString()}})}
                    Box(Modifier.width(120.dp)) {Checkbox(obj["radio"]=="true" && names(obj["ports"]).isEmpty(),{value->draft.change {obj["radio"]=value.toString();obj["ports"]="[]"}})}
                    radios.forEach {p->Box(Modifier.width(140.dp)) {Checkbox(obj["radio"]=="true" && (names(obj["ports"]).isEmpty() || p.name in names(obj["ports"])),{draft.setObjectPort(index,p.name,it)})}}
                }
                OutlinedTextField(obj["rf_path"].orEmpty(),{value->draft.change {obj["rf_path"]=value}},label={Text("${obj["name"]} RF path (blank: use each port's beacon path)")},modifier=Modifier.width(430.dp),singleLine=true)
                Divider(Modifier.padding(vertical=12.dp))
            }
        }}
    }
}

private fun profileDescription(protocol: String)=when(protocol) {
    "NMEA"->"A GPS receiver providing NMEA position sentences."
    "Simply(KISS)"->"A hardware or software TNC already operating in KISS mode."
    "KISS"->"A command-mode TNC that needs KISS initialization."
    "AGW"->"An AGWPE-compatible packet engine over TCP."
    "KWD700(KISS)"->"Kenwood D700 using KISS framing."
    "KWD700(RO+GPS)"->"Kenwood D700 text monitor and GPS, receive only."
    "KWD710(APRS)"->"Kenwood D710 APRS monitor with command-mode transmission."
    "KWD710(Pkt)"->"Kenwood D710 packet mode."
    "FTM350v1.3(RO)"->"Yaesu FTM-350 two-line monitor, receive only."
    "CONVerse"->"A TNC using MYCALL, UNPROTO and CONV commands."
    else->"A device or network service exchanging TNC2 text packets."
}

@Composable private fun AddPortWizard(draft: PortWorkspaceDraft,presets: List<Properties>,dismiss: ()->Unit,add: (Properties,Boolean)->Unit) {
    var step by remember {mutableStateOf(0)}
    val values=remember {mutableStateMapOf<String,String>().apply {putAll(PortPreferences.defaults().entriesMap());put("name",draft.uniqueName("VHF"))}}
    var useGps by remember {mutableStateOf(false)}
    val protocol=values["protocol"].orEmpty();val receiveOnly=protocol in PortPreferences.readOnly
    val validName=values["name"].orEmpty().let {it.isNotBlank() && it==it.trim() && it.length<=64 && draft.devices.none {p->p.name==it}}
    val valid=validName && runCatching {PortConfig(values.properties())}.isSuccess
    DialogWindow(onCloseRequest=dismiss,title="Add a port",state=rememberDialogState(width=650.dp,height=820.dp)) {
        MaterialTheme(colors=darkColors(primary=ReceiveColor,surface=WorkspacePanel,background=WorkspaceBackground)) {Surface {Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(listOf("1 · Choose your device","2 · Connect your device","3 · Review its connections")[step],fontSize=23.sp,fontWeight=FontWeight.Bold)
            LinearProgressIndicator((step+1)/3f,Modifier.fillMaxWidth().padding(vertical=16.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when(step) {
                    0->{
                    presets.forEach {preset->OutlinedButton(onClick={
                        values.clear();values.putAll(preset.entriesMap());values["name"]=draft.uniqueName(preset.getProperty("name"));values["enabled"]="true"
                        useGps=values["protocol"]=="NMEA";step=1
                    },modifier=Modifier.fillMaxWidth()) {Text(preset.getProperty("name"))}}
                    Text("Other device protocols",fontWeight=FontWeight.Bold)
                    PortPreferences.protocols.forEach {type->Surface(Modifier.fillMaxWidth().clickable {if(type!=protocol) {
                        values.clear();values.putAll(PortPreferences.defaults(type).entriesMap());values["name"]=draft.uniqueName(if(type=="NMEA") "GPS" else if(type=="AGW") "AGW" else "VHF");useGps=type=="NMEA"
                    }},shape=RoundedCornerShape(10.dp),border=BorderStroke(if(protocol==type) 2.dp else 1.dp,if(protocol==type) ReceiveColor else Color(0xFF38505D))) {
                        Column(Modifier.padding(14.dp)) {Text(type,fontWeight=FontWeight.Bold);Text(profileDescription(type),fontSize=12.sp,color=WorkspaceMuted)}
                    }}
                    }
                    1->{
                        if(!values["notes"].isNullOrBlank()) Text(values["notes"].orEmpty(),color=WorkspaceMuted)
                        if(!values["frequency_mhz"].isNullOrBlank()) Text("Radio: ${values["frequency_mhz"]} MHz",color=SendColor)
                        Text(protocol,fontWeight=FontWeight.Bold)
                        OutlinedTextField(values["name"].orEmpty(),{values["name"]=it},label={Text("A name you'll recognize")},isError=!validName,modifier=Modifier.fillMaxWidth(),singleLine=true)
                        if(!validName) Text("Choose a unique port name.",color=SendColor,fontSize=12.sp)
                        DeviceConnectionFields(values) {key,value->values[key]=value}
                    }
                    else->{
                        Text("${values["name"]} · $protocol",fontSize=19.sp,fontWeight=FontWeight.Bold)
                        Text(if(values["transport"]=="serial") "${values["device"].orEmpty().ifBlank {"No serial device selected"}} · ${values["baud"]} baud" else values["endpoint"].orEmpty().ifBlank {"No TCP endpoint entered"},color=WorkspaceMuted)
                        RouteSwitch("Enable this port","The connection opens when you Apply the workspace.",values["enabled"]=="true") {values["enabled"]=it.toString()}
                        if(protocol=="NMEA" || values["provides_nmea"]=="true") RouteSwitch("Use as my GPS source","Select this device for position beacons.",useGps) {useGps=it}
                        if(protocol!="NMEA") {
                            RouteSwitch("RF → APRS-IS","Upload eligible packets heard by this radio.",values["rf_to_is"]=="true") {values["rf_to_is"]=it.toString()}
                            if(!receiveOnly) {
                                RouteSwitch("Radio transmit","Enable outgoing packets on this port.",values["transmit"]=="true") {values["transmit"]=it.toString()}
                                RouteSwitch("APRS-IS → RF","Deliver messages to recently heard stations.",values["is_to_rf"]=="true") {values["is_to_rf"]=it.toString()}
                                Text("Station beacons, messages and assigned objects are connected by default.",fontSize=12.sp,color=WorkspaceMuted)
                                Text("Beacon path: ${values["beacon_path"]}\nMessage path: ${values["message_path"]}",fontSize=12.sp)
                                if(protocol in rawRadioProtocols) RouteSwitch("WIDE1 fill-in digipeater","Repeat matching WIDE1-1 packets on this radio.",values["digipeater"]=="true") {values["digipeater"]=it.toString()}
                            }
                        }
                        if(!valid) Text("Go back and finish the name and connection, or leave this port disabled for later setup.",color=SendColor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.End) {
                TextButton(onClick=dismiss) {Text("Cancel")}
                if(step>0) TextButton(onClick={step--}) {Text("Back")}
                if(step<2) Button(onClick={step++},enabled=step==0 || validName) {Text("Next")}
                else Button(onClick={add(values.properties(),useGps)},enabled=valid) {Text("Add to diagram")}
            }
        }}}
    }
}
