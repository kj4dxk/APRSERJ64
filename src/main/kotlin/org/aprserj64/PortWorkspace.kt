// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

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

private data class WireColors(val receive: Color, val send: Color, val gps: Color, val digipeat: Color)
private val darkWires = WireColors(Color(0xFF6DDAC5), Color(0xFFF1BC72), Color(0xFF83BEFF), Color(0xFFD3A7FF))
private val lightWires = WireColors(Color(0xFF006453), Color(0xFF824700), Color(0xFF155E9A), Color(0xFF794098))
private val wireColors: WireColors @Composable get() = if (MaterialTheme.colors.isLight) lightWires else darkWires
private val WorkspaceBackground: Color @Composable get() = MaterialTheme.colors.background
private val WorkspacePanel: Color @Composable get() = MaterialTheme.colors.surface
private val WorkspaceMuted: Color @Composable get() = MaterialTheme.colors.onSurface.copy(alpha = 0.72f)

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
    val demo by model.demo.collectAsState()
    val port=draft.devices.firstOrNull {it.id==selected}
    val issues=draft.issues()
    LaunchedEffect(port?.id,port?.protocol) {if(port?.protocol !in rawRadioProtocols) wiring=false}
    fun close() {if(draft.dirty) confirm="close" else dismiss()}
    Window(icon=AppIcon.window,onCloseRequest={close()},title="Ports & connections — APRSERJ64",
        state=rememberWindowState(placement=WindowPlacement.Maximized,width=1500.dp,height=940.dp)) {
        MaterialTheme(colors=applicationColors(model)) {
            Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(24.dp,16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column {HelpText("Ports & connections",fontSize=25.sp,fontWeight=FontWeight.Bold)
                        HelpText(if(draft.dirty) "Unsaved changes · connections change when you Apply" else "Saved configuration · live port status",color=WorkspaceMuted,fontSize=12.sp)}
                    Spacer(Modifier.weight(1f))
                    HelpTextButton(UiHelp.action("Undo"),onClick={draft.undo();failure="";wiring=false;if(draft.devices.none {it.id==selected}) selected=draft.devices.firstOrNull()?.id},enabled=draft.canUndo) {HelpText("Undo")}
                    HelpTextButton(UiHelp.action("Redo"),onClick={draft.redo();failure="";wiring=false;if(draft.devices.none {it.id==selected}) selected=draft.devices.firstOrNull()?.id},enabled=draft.canRedo) {HelpText("Redo")}
                    HelpTextButton(UiHelp.action("Reload saved"),onClick={if(draft.dirty) confirm="reload" else {draft=PortWorkspaceDraft(model.settings,model.ports,model.entities);selected=draft.devices.firstOrNull()?.id}}) {HelpText("Reload saved")}
                    Spacer(Modifier.width(12.dp))
                    HelpButton(UiHelp.action("+ Add port"),onClick={wizard=true},enabled=draft.devices.size<32) {HelpText("+ Add port")}
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf("Wiring","Object routes","Radio limits").forEach {tab->
                        HelpTextButton(UiHelp.action(tab),onClick={page=tab;wiring=false}) {HelpText(tab,color=if(page==tab) wireColors.receive else WorkspaceMuted,fontWeight=if(page==tab) FontWeight.Bold else FontWeight.Normal)}
                    }
                    Spacer(Modifier.weight(1f))
                    if(page=="Wiring") {HelpCheckbox(UiHelp.action("Show all wires"),showAll,{showAll=it});HelpText("Show all wires",fontSize=12.sp)}
                }
                Divider()
                if(failure.isNotBlank()) HelpText(failure,Modifier.fillMaxWidth().background(MaterialTheme.colors.error.copy(alpha=0.18f)).padding(12.dp))
                if(issues.isNotEmpty()) HelpText(issues.take(3).joinToString("\n"),Modifier.fillMaxWidth().background(wireColors.send.copy(alpha=0.15f)).padding(12.dp),fontSize=13.sp)
                if(draft.values["position.source"] in listOf("gps_serial","gps_tcp","gpsd")) Row(Modifier.padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                    HelpText("Your GPS currently uses the earlier connection settings.",fontSize=12.sp,color=WorkspaceMuted)
                    HelpTextButton(UiHelp.action("Show it as a port"),onClick={selected=draft.importLegacyGps();page="Wiring"},enabled=draft.devices.size<32) {HelpText("Show it as a port")}
                }
                when(page) {
                    "Wiring"->Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                HelpText(if(wiring) "Choose output ports for ${port?.name}. Click again to remove a wire." else "Select a port to see and edit its connections.",fontSize=13.sp,color=WorkspaceMuted,modifier=Modifier.weight(1f))
                                if(wiring) HelpButton(UiHelp.action("Done wiring"),onClick={wiring=false}) {HelpText("Done wiring")}
                            }
                            if(draft.devices.isEmpty()) Column(Modifier.fillMaxSize().padding(40.dp),verticalArrangement=Arrangement.Center) {
                                HelpText("Connect your first device",fontSize=24.sp)
                                HelpText("Choose a GPS, hardware TNC, or network TNC. The setup guide fills its connections for you.",Modifier.padding(vertical=16.dp),color=WorkspaceMuted)
                                HelpButton(UiHelp.action("Add a port"),onClick={wizard=true}) {HelpText("Add a port")}
                            } else WiringDiagram(draft,port,statuses,(if(demo) "${isState.name} · Local demo" else "${isState.name} · Configured: ${draft.values["endpoint"].orEmpty()}"),showAll,wiring,Modifier.weight(1f),{page="Object routes"}) {target->
                                if(wiring && port!=null && port.protocol in rawRadioProtocols && target.canTransmit && target.protocol in rawRadioProtocols) draft.toggleDigiTarget(port,target)
                                else if(!wiring) selected=target.id
                            }
                            Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                                Legend("Receive",wireColors.receive);Legend("Transmit",wireColors.send);Legend("GPS",wireColors.gps);Legend("Digipeat",wireColors.digipeat)
                            }
                        }
                        Divider(Modifier.fillMaxHeight().width(1.dp))
                        Box(Modifier.width(390.dp).fillMaxHeight().background(WorkspacePanel)) {
                            if(port!=null) PortInspector(draft,port,statuses.firstOrNull {it.name==draft.savedName(port.id)},
                                {selected=draft.duplicate(port)}, {draft.remove(port.id);selected=draft.devices.firstOrNull()?.id;wiring=false},
                                {wiring=!wiring}, {page="Object routes"},model::queryModem) {
                                    if(!draft.dirty && draft.matchesBaseline(model.settings,model.ports,model.entities)) {
                                        draft.set(port.id,"enabled",(!port.enabled).toString())
                                        if(draft.issues().isEmpty() && model.savePreferences(draft.values.properties(),draft.objectProperties(),draft.portProperties())) draft.markSaved()
                                        else {failure=draft.issues().firstOrNull() ?: model.error.value;draft.undo()}
                                    }
                                }
                            else HelpText("Select a port",Modifier.padding(24.dp))
                        }
                    }
                    "Object routes"->ObjectRoutes(draft,Modifier.weight(1f).fillMaxWidth())
                    else->Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        HelpText("IGate locality and duplicate limits",fontSize=20.sp)
                        HelpText("These limits apply to every radio port. Each port keeps its own heard-station history.",color=WorkspaceMuted)
                        PortPreferences.global.forEach {field->WorkspaceField(field,draft.values[field.key].orEmpty()) {value->draft.change {draft.values[field.key]=value}}}
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(20.dp,12.dp),verticalAlignment=Alignment.CenterVertically) {
                    HelpText("${draft.devices.size} ports · ${draft.objects.size} objects",color=WorkspaceMuted,modifier=Modifier.weight(1f))
                    HelpTextButton(UiHelp.action("Close"),onClick={close()}) {HelpText("Close")}
                    HelpButton(UiHelp.action("Apply changes"),onClick={
                        if(!draft.matchesBaseline(model.settings,model.ports,model.entities)) failure="Settings changed in another window. Reload saved settings before applying this draft."
                        else if(model.savePreferences(draft.values.properties(),draft.objectProperties(),draft.portProperties())) {draft.markSaved();failure="";wiring=false}
                        else failure="Could not apply. Check the listed settings and that the application folder is writable."
                    },enabled=draft.dirty && issues.isEmpty()) {HelpText("Apply changes")}
                }
            } }
            if(wizard) AddPortWizard(draft,model.portPresets,MaterialTheme.colors,{wizard=false}) {props,useGps->
                selected=draft.add(props);if(useGps) draft.useGps(draft.devices.first {it.id==selected})
                wizard=false;page="Wiring"
            }
            if(confirm!=null) AlertDialog(onDismissRequest={confirm=null},title={HelpText("Discard unsaved changes?")},
                text={HelpText("Your running connections and saved settings have not changed.")},
                confirmButton={HelpTextButton(UiHelp.action("Discard changes"),onClick={val action=confirm;confirm=null;if(action=="close") dismiss() else {draft=PortWorkspaceDraft(model.settings,model.ports,model.entities);selected=draft.devices.firstOrNull()?.id;failure="";wiring=false}}) {HelpText("Discard changes")}},
                dismissButton={HelpTextButton(UiHelp.action("Keep editing"),onClick={confirm=null}) {HelpText("Keep editing")}})
        }
    }
}

@Composable private fun Legend(text: String,color: Color) {Row(verticalAlignment=Alignment.CenterVertically) {Box(Modifier.size(9.dp).background(color,RoundedCornerShape(5.dp)));Spacer(Modifier.width(6.dp));HelpText(text,fontSize=11.sp,color=WorkspaceMuted)}}

private data class DiagramWire(val service: Int,val key: String,val label: String,val configured: Boolean,val effective: Boolean,val reverse: Boolean,val color: Color)
private fun wires(draft: PortWorkspaceDraft,p: PortDraft,palette: WireColors): List<DiagramWire> = buildList {
    val tx=p.enabled && p.canTransmit && p.values["transmit"]=="true"
    val gate=p.values["passall"]!="true"
    if(p.protocol=="NMEA" || p.values["provides_nmea"]=="true") {
        val selected=draft.values["position.source"]=="port" && draft.values["position.port_name"]==p.name
        add(DiagramWire(0,"gps","GPS source",selected,selected && p.enabled,false,palette.gps))
    }
    if(p.radio) {
        add(DiagramWire(1,"rf_to_is","RF → Internet",p.values["rf_to_is"]=="true",p.enabled && gate && p.values["rf_to_is"]=="true",false,palette.receive))
        if(p.canTransmit) add(DiagramWire(1,"is_to_rf","Internet → RF",p.values["is_to_rf"]=="true",tx && gate && p.values["is_to_rf"]=="true",true,palette.send))
        add(DiagramWire(2,"receive","Stations → map",true,p.enabled,false,palette.receive))
        if(p.canTransmit) {
            add(DiagramWire(2,"messages","Chat → radio",p.values["messages"]=="true",tx && p.values["messages"]=="true",true,palette.send))
            val beacon=draft.values["beacon.enabled"]=="true" && draft.values["beacon.radio"]=="true"
            add(DiagramWire(3,"beacon","Beacon → radio",p.values["beacon"]=="true",tx && beacon && p.values["beacon"]=="true",true,palette.send))
            add(DiagramWire(4,"objects","Objects → radio",p.values["objects"]=="true",tx && draft.values["beacon.enabled"]=="true" && p.values["objects"]=="true",true,palette.send))
        }
    }
}

@Composable private fun WiringDiagram(draft: PortWorkspaceDraft,selected: PortDraft?,statuses: List<PortStatus>,isState: String,all: Boolean,wiring: Boolean,modifier: Modifier,objects: ()->Unit,select: (PortDraft)->Unit) {
    val palette = wireColors
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
                        wires(draft,p,palette).filter {it.configured}.forEach {wire->
                            val offset=if(wire.reverse) 9 else -9
                            curve(Offset(270.dp.toPx(),(76+index*116+offset).dp.toPx()),Offset(490.dp.toPx(),(serviceTop+50+wire.service*122+offset).dp.toPx()),
                                wire.color.copy(alpha=if(p.id==selected?.id) 1f else .25f),wire.effective,wire.reverse)
                        }
                        if(p.values["digipeater"]=="true") names(p.values["digi_outputs"]).ifEmpty {listOf(p.name)}.forEach {target->
                            val targetIndex=draft.devices.indexOfFirst {it.name==target}
                            if(targetIndex>=0) curve(Offset(270.dp.toPx(),(94+index*116).dp.toPx()),Offset(270.dp.toPx(),(58+targetIndex*116).dp.toPx()),
                                palette.digipeat.copy(alpha=if(p.id==selected?.id) 1f else .25f),p.enabled && p.values["passall"]!="true" && names(p.values["digi_transforms"]).isNotEmpty() && draft.devices[targetIndex].enabled && draft.devices[targetIndex].values["transmit"]=="true",bend=400.dp.toPx(),endLeft=true)
                        }
                    }
                }
                // Internal application routes are visible too, even with no selected RF output.
                fun servicePoint(index: Int,offset: Int=0)=Offset(770.dp.toPx(),(serviceTop+50+index*122+offset).dp.toPx())
                val beaconOn=draft.values["beacon.enabled"]=="true"
                curve(servicePoint(0),servicePoint(3,-8),palette.gps,beaconOn,bend=820.dp.toPx(),endLeft=true)
                if(draft.values["beacon.internet"]=="true") curve(servicePoint(3,8),servicePoint(1,-8),palette.send,beaconOn,bend=855.dp.toPx(),endLeft=true)
                if(draft.objects.any {it["internet"]=="true" && it["enabled"]=="true"}) curve(servicePoint(4),servicePoint(1,8),palette.send,beaconOn,bend=890.dp.toPx(),endLeft=true)
                curve(servicePoint(1,18),servicePoint(2,-8),palette.receive,true,bend=810.dp.toPx(),endLeft=true)
                curve(servicePoint(2,8),servicePoint(1,28),palette.send,true,bend=835.dp.toPx(),endLeft=true)
            }
            draft.devices.forEachIndexed {index,p->
                val status=statuses.firstOrNull {it.name==draft.savedName(p.id)}
                val target=wiring && p.canTransmit && p.protocol in rawRadioProtocols
                Surface(Modifier.offset(20.dp,(26+index*116).dp).size(250.dp,100.dp).clickable(enabled=!wiring || target) {select(p)},
                    shape=RoundedCornerShape(12.dp),color=if(p.id==selected?.id) wireColors.receive.copy(alpha=0.16f) else WorkspacePanel,
                    border=BorderStroke(if(p.id==selected?.id || target) 2.dp else 1.dp,if(target) palette.digipeat else if(p.id==selected?.id) palette.receive else Color(0xFF314650))) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        HelpText(p.name,fontWeight=FontWeight.Bold,maxLines=1)
                        HelpText("${p.protocol} · ${if(p.values["transport"]!="tcp") p.values["device"].orEmpty().ifBlank {"Choose device"} else p.values["endpoint"].orEmpty().ifBlank {"Enter address"}}",fontSize=11.sp,color=WorkspaceMuted,maxLines=1)
                        HelpText(if(draft.portChanged(p.id)) "Pending Apply · live: ${status?.detail ?: "not running"}" else if(!p.enabled) "Disabled" else status?.detail ?: "Not running",fontSize=11.sp,color=if(p.enabled && status?.detail=="Connected") palette.receive else WorkspaceMuted,maxLines=1)
                        if(status!=null) HelpText("RX ${status.received} · TX ${status.transmitted}",fontSize=10.sp,color=WorkspaceMuted)
                    }
                }
            }
            services.forEachIndexed {index,title->
                Surface(Modifier.offset(490.dp,(serviceTop+index*122).dp).size(280.dp,110.dp),shape=RoundedCornerShape(12.dp),color=WorkspacePanel) {
                    Column(Modifier.padding(12.dp)) {
                        HelpText(title,fontWeight=FontWeight.Bold)
                        val detail=when(index) {
                            0->if(draft.values["position.source"]=="port") draft.values["position.port_name"].orEmpty().ifBlank {"Choose GPS"} else if(draft.values["position.source"]=="fixed") "Fixed position" else "Legacy GPS connection"
                            1->isState
                            2->"Received stations and saved conversations"
                            3->if(draft.values["beacon.enabled"]=="true") "Beaconing enabled" else "Master beacon switch off"
                            else->"${draft.objects.size} configured · individual routes in Object routes"
                        }
                        HelpText(detail,fontSize=10.sp,color=WorkspaceMuted,maxLines=2)
                        val links=selected?.let {wires(draft,it,palette).filter {w->w.service==index && w.key!="receive"}}.orEmpty()
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {links.forEach {link->
                            HelpTextButton(UiHelp.action(if(link.key=="gps") (if(selected?.enabled!=true) "Enable GPS" else if(link.configured) "Selected GPS" else "Use this GPS") else (if(link.configured) "● " else "○ ")+link.label),onClick={if(link.key=="gps") draft.useGps(selected!!,enable=true) else draft.set(selected!!.id,link.key,(!link.configured).toString())},enabled=link.key!="gps" || !link.configured || selected?.enabled!=true,contentPadding=PaddingValues(4.dp)) {
                                HelpText(if(link.key=="gps") (if(selected?.enabled!=true) "Enable GPS" else if(link.configured) "Selected GPS" else "Use this GPS") else (if(link.configured) "● " else "○ ")+link.label,fontSize=10.sp,color=if(link.configured) link.color else WorkspaceMuted)
                            }
                        }
                            if(index==3) HelpTextButton(UiHelp.action((if(draft.values["beacon.internet"]=="true") "● " else "○ ")+"Internet"),onClick={draft.change {draft.values["beacon.internet"]=(draft.values["beacon.internet"]!="true").toString()}},contentPadding=PaddingValues(4.dp)) {
                                HelpText((if(draft.values["beacon.internet"]=="true") "● " else "○ ")+"Internet",fontSize=10.sp,color=palette.send)
                            }
                            if(index==4) HelpTextButton(UiHelp.action("Routes"),onClick=objects,contentPadding=PaddingValues(4.dp)) {HelpText("Routes",fontSize=10.sp)}
                        }
                    }
                }
            }
            HelpText("Solid: eligible in this configuration · Dashed: blocked by a switch or missing rules\nLive device status is shown on each port; a wire does not prove packet delivery.",
                Modifier.offset(490.dp,(serviceTop+614).dp).width(280.dp),fontSize=11.sp,color=WorkspaceMuted)
        }
      }
      VerticalScrollbar(rememberScrollbarAdapter(vertical),Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom=12.dp))
      HorizontalScrollbar(rememberScrollbarAdapter(horizontal),Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end=12.dp))
    }
}

@Composable private fun RouteSwitch(title: String,description: String,checked: Boolean,enabled: Boolean=true,change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {HelpText(title,fontSize=13.sp);HelpText(description,fontSize=11.sp,color=WorkspaceMuted)}
        HelpSwitch(UiHelp.action(title),checked,change,modifier=Modifier.semantics {contentDescription=title},enabled=enabled)
    }
}

@Composable internal fun WorkspaceField(field: BeaconField,value: String,change: (String)->Unit) {
    if(field.kind==SettingKind.BOOLEAN) RouteSwitch(field.label,"",value.toBoolean()) {change(it.toString())}
    else if(field.choices.isNotEmpty()) ChoiceField(field.label,value,field.choices,change)
    else HelpField(UiHelp.setting(field),value,change,label={HelpText(field.label)},modifier=Modifier.fillMaxWidth(),singleLine=field.kind!=SettingKind.STRINGS,maxLines=if(field.kind==SettingKind.STRINGS) 5 else 1,fill=true)
}
@Composable private fun ChoiceField(label: String,value: String,choices: List<String>,change: (String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {HelpOutlinedButton(UiHelp.action("$label: $value"),onClick={expanded=true},modifier=Modifier.fillMaxWidth(),fill=true) {HelpText("$label: $value")}
        DropdownMenu(expanded,{expanded=false}) {choices.forEach {choice->HelpMenuItem(UiHelp.action(choice),onClick={change(choice);expanded=false}) {HelpText(choice)}}}}
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
        catch(_: Exception) {scanError=true}
        catch(_: LinkageError) {scanError=true} finally {scanning=false}
    }}
    val transport=values["transport"].orEmpty()
    LaunchedEffect(transport) {if(transport in PortPreferences.comTransports) refresh()}
    if(values["protocol"]=="AGW" || values["protocol"] in PortPreferences.services) HelpText("TCP connection",color=WorkspaceMuted)
    else {
        val labels=linkedMapOf("serial" to "USB / COM serial","spp" to "Bluetooth Classic SPP (COM)","tcp" to "TCP")
        if(values["protocol"]=="Simply(KISS)") labels["ble"]="Bluetooth LE KISS"
        ChoiceField("Connection",labels[transport].orEmpty(),labels.values.toList()) {label->
            set("transport",labels.entries.first {it.value==label}.key)
        }
    }
    if(transport in PortPreferences.comTransports) {
        if(transport=="spp") HelpText("Pair the TNC in Windows and select its outgoing COM port.",color=WorkspaceMuted)
        var menu by remember {mutableStateOf(false)}
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                HelpOutlinedButton(UiHelp.action(if(scanning) "Finding devices…" else "Choose detected device"),onClick={menu=true},modifier=Modifier.fillMaxWidth(),fill=true) {HelpText(if(scanning) "Finding devices…" else "Choose detected device")}
                DropdownMenu(menu,{menu=false}) {
                    if(devices.isEmpty()) HelpMenuItem(UiHelp.action("No serial devices detected"),onClick={},enabled=false) {HelpText("No serial devices detected")}
                    devices.forEach {(name,description)->HelpMenuItem(UiHelp.action("$name · $description"),onClick={set("device",name);menu=false}) {HelpText("$name · $description")}}
                }
            }
            HelpTextButton(UiHelp.action("Refresh"),onClick={refresh()},enabled=!scanning) {HelpText("Refresh")}
        }
        HelpField(UiHelp.field("Serial device"),values["device"].orEmpty(),{set("device",it.trim())},label={HelpText("Serial device")},placeholder={HelpText("COM3")},modifier=Modifier.fillMaxWidth(),singleLine=true,fill=true)
        if(scanError) HelpText("Device discovery unavailable. Enter the COM name manually.",fontSize=11.sp,color=WorkspaceMuted)
        ChoiceField("Data bits",values["serial_data_bits"] ?: "8",listOf("5","6","7","8")) {set("serial_data_bits",it)}
        ChoiceField("Parity",values["serial_parity"] ?: "none",listOf("none","odd","even","mark","space")) {set("serial_parity",it)}
        ChoiceField("Stop bits",values["serial_stop_bits"] ?: "1",listOf("1","1.5","2")) {set("serial_stop_bits",it)}
        HelpField(UiHelp.field("Serial baud"),values["baud"].orEmpty(),{set("baud",it)},label={HelpText("Serial baud")},modifier=Modifier.fillMaxWidth(),singleLine=true,fill=true)
    } else if(transport=="ble") {
        HelpField(UiHelp.field("Bluetooth LE address"),values["device"].orEmpty(),{set("device",it.trim())},
            label={HelpText("Bluetooth LE address")},placeholder={HelpText("AA:BB:CC:DD:EE:FF")},modifier=Modifier.fillMaxWidth(),singleLine=true,fill=true)
        AdvertisedTncPicker("Find Bluetooth LE devices","No Bluetooth LE devices found",
            {WindowsBleKiss.discover().map {it.name.ifBlank {"Bluetooth LE device"} to it.address}}) {set("device",it)}
    } else HelpField(UiHelp.field("Host:port"),values["endpoint"].orEmpty(),{set("endpoint",it.trim())},label={HelpText("Host:port")},placeholder={HelpText("Enter the TNC or GPS address")},modifier=Modifier.fillMaxWidth(),singleLine=true,fill=true)
    if(transport=="tcp" && values["protocol"] in PortPreferences.kissProtocols)
        AdvertisedTncPicker("Find advertised KISS TNCs","No KISS TNC advertisements found",
            {discoverNetworkTncs().map {it.name to it.endpoint}}) {set("endpoint",it)}
    if(values["protocol"]=="CWOP")HelpField(UiHelp.field("CWOP station identifier"),values["service_call"].orEmpty(),{set("service_call",it.uppercase())},label={HelpText("CWOP station identifier")},modifier=Modifier.fillMaxWidth(),singleLine=true,fill=true)
    if(values["protocol"] in rawRadioProtocols && values["protocol"]!="TEXT")
        HelpField(UiHelp.field(if(values["protocol"]=="AGW") "AGW radio number (starts at 0)" else "KISS channel (usually 0)"),values["channel"].orEmpty(),{set("channel",it)},label={HelpText(if(values["protocol"]=="AGW") "AGW radio number (starts at 0)" else "KISS channel (usually 0)")},modifier=Modifier.fillMaxWidth(),singleLine=true,fill=true)
}

@Composable private fun AdvertisedTncPicker(label: String, empty: String, discover: suspend ()->List<Pair<String,String>>, select: (String)->Unit) {
    val scope=rememberCoroutineScope()
    var results by remember {mutableStateOf<List<Pair<String,String>>>(emptyList())}
    var searching by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf("")}
    var menu by remember {mutableStateOf(false)}
    Box {
        HelpOutlinedButton(UiHelp.action(label),enabled=!searching,onClick={scope.launch {
            searching=true;message=""
            try {
                results=discover()
                if(results.isEmpty()) message=empty else menu=true
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {message="Device discovery unavailable"}
            finally {searching=false}
        }}) {HelpText(if(searching) "Searching…" else label)}
        DropdownMenu(menu,{menu=false}) {
            results.forEach {(name,endpoint)->
                HelpMenuItem(UiHelp.action(name),onClick={select(endpoint);menu=false}) {
                    HelpText("$name · $endpoint")
                }
            }
        }
    }
    if(message.isNotEmpty()) HelpText(message,fontSize=11.sp,color=WorkspaceMuted)
}

@Composable private fun PortDiagnostics(status: PortStatus, queryModem: suspend (String)->Boolean) {
    val scope=rememberCoroutineScope()
    var queryState by remember(status.name,status.connected) {mutableStateOf("")}
    HelpText(status.connection,fontSize=12.sp,color=WorkspaceMuted)
    HelpText("${status.detail} · RX packets ${status.received} · TX writes ${status.transmitted}"+
        (if(status.dropped>0) " · Dropped ${status.dropped}" else ""),fontSize=12.sp,color=WorkspaceMuted)
    if(status.startupTrace.isNotEmpty()) {
        HelpTextButton("Copy the connection and startup diagnostics",onClick={
            val report=(listOf(status.connection,status.detail+" · RX "+status.received+" · TX writes "+status.transmitted)+status.startupTrace).joinToString("\n")
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(report),null)
        }) {HelpText("Copy port diagnostics")}
        HelpText(status.startupTrace.takeLast(12).joinToString("\n"),fontSize=11.sp,color=WorkspaceMuted)
    }
    if(status.canQueryModem) {
        HelpOutlinedButton(UiHelp.action("Read Mobilinkd audio and PTT settings"),onClick={scope.launch {
            queryState=if(queryModem(status.name)) "Query written to modem · waiting for a setting report" else "Query could not be written"
        }}) {HelpText("Read Mobilinkd audio and PTT settings")}
        if(status.modemSettings.isNotEmpty()) HelpText("Modem reports · "+status.modemSettings.joinToString(" · "),fontSize=12.sp)
        else if(queryState.isNotEmpty()) HelpText(queryState,fontSize=12.sp)
        HelpText("An APRS message ACK is shown in Messages when received.",fontSize=11.sp,color=WorkspaceMuted)
    }
}

@Composable private fun PortInspector(draft: PortWorkspaceDraft,port: PortDraft,status: PortStatus?,duplicate: ()->Unit,remove: ()->Unit,wire: ()->Unit,objects: ()->Unit,queryModem: suspend (String)->Boolean,toggleConnection: ()->Unit) {
    var rename by remember(port.id) {mutableStateOf(false)}
    var name by remember(port.id) {mutableStateOf(port.name)}
    var delete by remember(port.id) {mutableStateOf(false)}
    var advanced by remember(port.id) {mutableStateOf(false)}
    var rules by remember(port.id) {mutableStateOf(false)}
    var previewPk88 by remember(port.id) {mutableStateOf(false)}
    var profileChange by remember(port.id) {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {HelpText(port.name,Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold);HelpTextButton(UiHelp.action("Rename"),onClick={name=port.name;rename=true}) {HelpText("Rename")}}
        ChoiceField("Profile",port.protocol,PortPreferences.protocols) {if(it!=port.protocol) profileChange=it}
        if(status!=null) PortDiagnostics(status,queryModem)
        if(port.values["transport"] in PortPreferences.comTransports && port.protocol in PortPreferences.kissProtocols) {
            val preset=PortPreferences.defaults(PortPreferences.PK88)
            val keys=listOf("open_commands","close_commands")
            val matches=keys.all {port.values[it]==preset.getProperty(it)}
            HelpText(if(matches) "Bundled PK-88 startup commands configured" else "Startup commands differ from the bundled PK-88 preset",fontSize=12.sp)
            HelpTextButton("Preview bundled AEA PK-88 startup commands",onClick={previewPk88=true}) {HelpText("Preview PK-88 startup commands")}
            if(previewPk88) AlertDialog(onDismissRequest={previewPk88=false},title={HelpText("AEA PK-88 startup commands")},
                text={HelpText("Open: "+preset.getProperty("open_commands")+"\nClose: "+preset.getProperty("close_commands")+"\nApply in Ports to use this command sequence.")},
                confirmButton={HelpTextButton("Use these startup commands",onClick={
                    draft.change {keys.forEach {port.values[it]=preset.getProperty(it)}}
                    previewPk88=false
                }) {HelpText("Use commands")}},
                dismissButton={HelpTextButton("Close the preview",onClick={previewPk88=false}) {HelpText("Cancel")}})
        }
        if(!draft.dirty && (port.enabled || port.values["device"].orEmpty().isNotBlank() || port.values["endpoint"].orEmpty().isNotBlank())) {
            val action=if(port.enabled) "Disconnect port" else "Connect port"
            HelpOutlinedButton(UiHelp.action(action),onClick=toggleConnection) {HelpText(action)}
        }
        RouteSwitch("Port enabled","Applied changes open or close this connection.",port.enabled) {draft.set(port.id,"enabled",it.toString())}
        DeviceConnectionFields(port.values) {key,value->draft.set(port.id,key,value)}
        Divider(Modifier.padding(vertical=6.dp))
        HelpText("Connections",fontWeight=FontWeight.Bold,fontSize=17.sp)
        if(port.protocol=="NMEA" || port.values["provides_nmea"]=="true") {
            val active=draft.values["position.source"]=="port" && draft.values["position.port_name"]==port.name
            HelpText(if(active) "This port supplies your position." else "GPS is available on this port.",color=wireColors.gps,fontSize=12.sp)
            if(!active || !port.enabled) HelpButton(UiHelp.action(if(port.enabled) "Use this GPS" else "Select & enable GPS"),onClick={draft.useGps(port,enable=true)}) {HelpText(if(port.enabled) "Use this GPS" else "Select & enable GPS")}
            else HelpOutlinedButton(UiHelp.action("Use fixed position instead"),onClick={draft.fixedPosition()}) {HelpText("Use fixed position instead")}
        }
        if(port.protocol in PortPreferences.services) {
            RouteSwitch("Service output","Publish packets to this endpoint.",port.values["transmit"]=="true") {draft.set(port.id,"transmit",it.toString())}
            RouteSwitch("Accept client input","Allow input from verified TCP clients or permitted UDP peers.",port.values["service_input"]=="true") {draft.set(port.id,"service_input",it.toString())}
            RouteSwitch("Client input → Internet","Forward accepted client packets through your APRS-IS connection.",port.values["rf_to_is"]=="true") {draft.set(port.id,"rf_to_is",it.toString())}
            RouteSwitch("Client input → RF","Apply each radio's local-message and additional filter rules.",port.values["is_to_rf"]=="true") {draft.set(port.id,"is_to_rf",it.toString())}
            if(port.protocol=="CWOP")WorkspaceField(PortPreferences.fields.first {it.key=="service_call"},port.values["service_call"].orEmpty()){draft.set(port.id,"service_call",it)}
        } else if(port.radio) {
            HelpText("Received stations appear on the map automatically.",color=WorkspaceMuted,fontSize=12.sp)
            RouteSwitch("RF → APRS-IS","Upload eligible packets heard by this radio.",port.values["rf_to_is"]=="true") {draft.set(port.id,"rf_to_is",it.toString())}
            if(port.canTransmit) {
                RouteSwitch("Radio transmit","Master switch for this port's RF output.",port.values["transmit"]=="true") {draft.set(port.id,"transmit",it.toString())}
                RouteSwitch("APRS-IS → RF","Messages for local stations and associated positions.",port.values["is_to_rf"]=="true") {draft.set(port.id,"is_to_rf",it.toString())}
                RouteSwitch("My messages → RF","Prefer this radio for stations heard here.",port.values["messages"]=="true") {draft.set(port.id,"messages",it.toString())}
                RouteSwitch("My beacon → RF","Send your position on this radio.",port.values["beacon"]=="true") {draft.set(port.id,"beacon",it.toString())}
                RouteSwitch("Objects → RF","Allow objects assigned to this radio.",port.values["objects"]=="true") {draft.set(port.id,"objects",it.toString())}
                HelpTextButton(UiHelp.action("Choose which objects use this port →"),onClick=objects) {HelpText("Choose which objects use this port →")}
                if(draft.values["beacon.enabled"]!="true") HelpTextButton(UiHelp.action("Enable the master beacon switch"),onClick={draft.change {draft.values["beacon.enabled"]="true"}}) {HelpText("Enable the master beacon switch")}
                if(draft.values["beacon.radio"]!="true") HelpTextButton(UiHelp.action("Enable station beacon output to RF"),onClick={draft.change {draft.values["beacon.radio"]="true"}}) {HelpText("Enable station beacon output to RF")}
                PortPreferences.fields.filter {it.key in listOf("beacon_path","message_path")}.forEach {f->WorkspaceField(f,port.values[f.key].orEmpty()) {draft.set(port.id,f.key,it)}}
                HelpText("Blank path sends directly. The prefilled paths allow nearby digipeaters to relay your packets.",fontSize=11.sp,color=WorkspaceMuted)
            } else HelpText("This profile receives only; RF transmit routes are unavailable.",fontSize=12.sp,color=WorkspaceMuted)
            if(port.protocol in rawRadioProtocols) {
                Divider(Modifier.padding(vertical=6.dp))
                RouteSwitch("Digipeater","Repeat packets matching the rules below.",port.values["digipeater"]=="true") {draft.set(port.id,"digipeater",it.toString())}
                val targets=names(port.values["digi_outputs"]).ifEmpty {listOf(port.name)}
                HelpText("Outputs: ${targets.joinToString()} · ${names(port.values["digi_transforms"]).size} rules",fontSize=12.sp,color=wireColors.digipeat)
                HelpOutlinedButton(UiHelp.action("Wire digipeater outputs on diagram"),onClick=wire,modifier=Modifier.fillMaxWidth(),fill=true) {HelpText("Wire digipeater outputs on diagram")}
                HelpTextButton(UiHelp.action(if(rules) "Hide digipeater rules" else "Edit digipeater rules"),onClick={rules=!rules}) {HelpText(if(rules) "Hide digipeater rules" else "Edit digipeater rules")}
                if(rules) DigiRules(port) {draft.set(port.id,"digi_transforms",encodedNames(it))}
                if(port.values["digipeater"]=="true" && names(port.values["digi_transforms"]).isEmpty()) HelpText("Add a rule to activate repeating.",color=wireColors.send,fontSize=12.sp)
            }
        }
        if(port.values["passall"]=="true") HelpText("PASSALL is selected: IGating and digipeating are blocked on this port.",color=wireColors.send,fontSize=12.sp)
        Divider(Modifier.padding(vertical=6.dp))
        HelpTextButton(UiHelp.action(if(advanced) "Hide advanced settings" else "Advanced device settings"),onClick={advanced=!advanced}) {HelpText(if(advanced) "Hide advanced settings" else "Advanced device settings")}
        if(advanced) {
            PortPreferences.fields.filter {it.key in listOf("provides_nmea","no_gate_me","passall","gate_calls","gate_objects","gate_filter","service_input","service_peers","service_max_clients","service_call","agw_user","agw_password","open_commands","close_commands","command_prompt","command_timeout_ms","command_delay_ms","transmit_per_minute","minimum_transmit_ms","kiss_txdelay","kiss_persistence","kiss_slot","kiss_tail","kiss_duplex")}
                .filter {port.radio || it.key in listOf("open_commands","close_commands","command_prompt","command_timeout_ms","command_delay_ms")}
                .filter {it.key!="provides_nmea" || port.protocol in PortPreferences.nmeaProtocols}
                .forEach {f->WorkspaceField(f,port.values[f.key].orEmpty()) {draft.set(port.id,f.key,it)}}
        }
        Row {HelpTextButton(UiHelp.action("Duplicate"),onClick=duplicate,enabled=draft.devices.size<32) {HelpText("Duplicate")};HelpTextButton(UiHelp.action("Remove port"),onClick={delete=true}) {HelpText("Remove port")}}
    }
    if(rename) AlertDialog(onDismissRequest={rename=false},title={HelpText("Rename port")},text={Column {
        HelpField(UiHelp.field("Name"),name,{name=it},label={HelpText("Name")},singleLine=true)
        HelpText("GPS, object and digipeater references follow the new name.",Modifier.padding(top=12.dp),fontSize=12.sp)
    }},confirmButton={HelpTextButton(UiHelp.action("Rename"),onClick={draft.rename(port.id,name.trim());rename=false},enabled=name.trim().isNotEmpty() && name.trim().length<=64 && draft.devices.none {it.id!=port.id && it.name==name.trim()}) {HelpText("Rename")}},dismissButton={HelpTextButton(UiHelp.action("Cancel"),onClick={rename=false}) {HelpText("Cancel")}})
    if(delete) AlertDialog(onDismissRequest={delete=false},title={HelpText("Remove ${port.name}?")},text={HelpText("Its wires will be removed. Routes with no remaining output are disabled. If this is your GPS source, choose a replacement before applying. You can undo this change.")},confirmButton={HelpTextButton(UiHelp.action("Remove"),onClick={delete=false;remove()}) {HelpText("Remove")}},dismissButton={HelpTextButton(UiHelp.action("Keep port"),onClick={delete=false}) {HelpText("Keep port")}})
    profileChange?.let {type->AlertDialog(onDismissRequest={profileChange=null},title={HelpText("Use $type?")},
        text={HelpText("This loads the profile commands and route switches. Your port name, device address, transmit permission and RF paths are saved. Review the connection settings, then Apply.")},
        confirmButton={HelpTextButton(UiHelp.action("Use profile"),onClick={draft.profile(port,type);profileChange=null}) {HelpText("Use profile")}},dismissButton={HelpTextButton(UiHelp.action("Cancel"),onClick={profileChange=null}) {HelpText("Cancel")}})}
}

@Composable private fun DigiRules(port: PortDraft,change: (List<String>)->Unit) {
    val rows=names(port.values["digi_transforms"])
    fun replace(index: Int,value: String)=change(rows.toMutableList().apply {set(index,value)})
    HelpText("Match the first unused alias, insert our callsign, then use the remaining path.",fontSize=12.sp,color=WorkspaceMuted)
    rows.forEachIndexed {index,rule->
        val alias=rule.substringBefore('=');val rest=rule.substringAfter('=',"");val flags=rest.takeWhile {it in "!@"};val remaining=rest.drop(flags.length)
        Surface(shape=RoundedCornerShape(8.dp),color=WorkspaceBackground) {Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            HelpField(UiHelp.field("Incoming alias"),alias,{replace(index,it.uppercase()+"="+rest)},label={HelpText("Incoming alias")},singleLine=true,modifier=Modifier.fillMaxWidth(),fill=true)
            HelpField(UiHelp.field("Remaining path (blank: finished)"),remaining,{replace(index,alias+"="+flags+it.uppercase())},label={HelpText("Remaining path (blank: finished)")},singleLine=true,modifier=Modifier.fillMaxWidth(),fill=true)
            RouteSwitch("Include my callsign","Identify this digipeater in the path.",'!' !in flags) {on->replace(index,alias+"="+(if(on) "" else "!")+(if('@' in flags) "@" else "")+remaining)}
            RouteSwitch("Discard earlier used hops","APRSIS32 UIFLOOD-style replacement.",'@' in flags) {on->replace(index,alias+"="+(if('!' in flags) "!" else "")+(if(on) "@" else "")+remaining)}
            HelpTextButton(UiHelp.action("Remove rule"),onClick={change(rows.filterIndexed {i,_->i!=index})}) {HelpText("Remove rule")}
        }}
    }
    HelpTextButton(UiHelp.action("+ Add rule"),onClick={change(rows+"ALIAS=")},enabled=rows.size<64) {HelpText("+ Add rule")}
    Row {HelpTextButton(UiHelp.action("WIDE1 fill-in"),onClick={change(listOf("WIDE1-1="))}) {HelpText("WIDE1 fill-in")};HelpTextButton(UiHelp.action("WIDE2 trace"),onClick={change(listOf("WIDE2-2=WIDE2-1","WIDE2-1="))}) {HelpText("WIDE2 trace")}}
}

@Composable private fun ObjectRoutes(draft: PortWorkspaceDraft,modifier: Modifier) {
    val radios=draft.devices.filter {it.canTransmit}
    val horizontal=rememberScrollState()
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        HelpText("Choose each object's destinations",fontSize=22.sp,fontWeight=FontWeight.Bold)
        HelpText("All RF ports includes future ports. Selecting individual destinations keeps that object's route explicit.",color=WorkspaceMuted)
        HorizontalScrollbar(rememberScrollbarAdapter(horizontal),Modifier.fillMaxWidth())
        if(draft.objects.isEmpty()) HelpText("No objects yet. Create a static object in Settings → Objects, then choose its destinations here.",color=WorkspaceMuted)
        else Box(Modifier.horizontalScroll(horizontal)) {Column(Modifier.requiredWidth((440+radios.size*140).dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                HelpText("Object",Modifier.width(220.dp),fontWeight=FontWeight.Bold)
                HelpText("Internet",Modifier.width(100.dp));HelpText("All RF ports",Modifier.width(120.dp))
                radios.forEach {p->Column(Modifier.width(140.dp)) {HelpText(p.name,fontWeight=FontWeight.Bold);HelpText(if(!p.enabled) "Port disabled" else if(p.values["objects"]!="true") "Object output off" else "",fontSize=10.sp,color=wireColors.send)}}
            }
            Divider(Modifier.padding(vertical=12.dp))
            draft.objects.forEachIndexed {index,obj->
                Row(Modifier.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.width(220.dp)) {HelpText(obj["name"].orEmpty(),fontWeight=FontWeight.Bold)
                        HelpText(if(obj["enabled"]!="true") "Object disabled" else if(obj["alive"]!="true") "Kill report" else "${obj["source"]} · ${obj["interval_seconds"]} s",fontSize=11.sp,color=WorkspaceMuted)}
                    Box(Modifier.width(100.dp)) {HelpCheckbox("Allow this object's reports through verified APRS-IS. Apply changes to activate this draft route.",obj["internet"]=="true",{value->draft.change {obj["internet"]=value.toString()}})}
                    Box(Modifier.width(120.dp)) {HelpCheckbox("Allow this object's reports on all eligible radio ports, including future ports. Apply changes to activate the draft.",obj["radio"]=="true" && names(obj["ports"]).isEmpty(),{value->draft.change {obj["radio"]=value.toString();obj["ports"]="[]"}})}
                    radios.forEach {p->Box(Modifier.width(140.dp)) {HelpCheckbox("Allow this object's reports through ${p.name}. That port must also be enabled and allow object transmission. Apply changes to activate the draft.",obj["radio"]=="true" && (names(obj["ports"]).isEmpty() || p.name in names(obj["ports"])),{draft.setObjectPort(index,p.name,it)})}}
                }
                HelpField(UiHelp.field("${obj["name"]} RF path (blank: use each port's beacon path)"),obj["rf_path"].orEmpty(),{value->draft.change {obj["rf_path"]=value}},label={HelpText("${obj["name"]} RF path (blank: use each port's beacon path)")},modifier=Modifier.width(430.dp),singleLine=true,fill=true)
                Divider(Modifier.padding(vertical=12.dp))
            }
        }}
    }
}

private fun profileDescription(protocol: String)=when(protocol) {
    "NMEA"->"A GPS receiver providing NMEA position sentences."
    "Simply(KISS)"->"A hardware or software TNC already operating in KISS mode."
    PortPreferences.PK88->"AEA PK-88 firmware that enters KISS directly with KISS ON."
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

@Composable private fun AddPortWizard(draft: PortWorkspaceDraft,presets: List<Properties>,colors: Colors,dismiss: ()->Unit,add: (Properties,Boolean)->Unit) {
    var step by remember {mutableStateOf(0)}
    val values=remember {mutableStateMapOf<String,String>().apply {putAll(PortPreferences.defaults().entriesMap());put("name",draft.uniqueName("VHF"))}}
    var useGps by remember {mutableStateOf(false)}
    val protocol=values["protocol"].orEmpty();val receiveOnly=protocol in PortPreferences.readOnly
    val validName=values["name"].orEmpty().let {it.isNotBlank() && it==it.trim() && it.length<=64 && draft.devices.none {p->p.name==it}}
    val valid=validName && runCatching {PortConfig(values.properties())}.isSuccess
    DialogWindow(icon=AppIcon.window,onCloseRequest=dismiss,title="Add a port",state=rememberDialogState(width=650.dp,height=820.dp)) {
        MaterialTheme(colors=colors) {Surface {Column(Modifier.fillMaxSize().padding(24.dp)) {
            HelpText(listOf("1 · Choose your device","2 · Connect your device","3 · Review its connections")[step],fontSize=23.sp,fontWeight=FontWeight.Bold)
            LinearProgressIndicator((step+1)/3f,Modifier.fillMaxWidth().padding(vertical=16.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when(step) {
                    0->{
                    presets.forEach {preset->HelpOutlinedButton(UiHelp.action(preset.getProperty("name")),onClick={
                        values.clear();values.putAll(preset.entriesMap());values["name"]=draft.uniqueName(preset.getProperty("name"));values["enabled"]="true"
                        useGps=values["protocol"]=="NMEA";step=1
                    },modifier=Modifier.fillMaxWidth(),fill=true) {HelpText(preset.getProperty("name"))}}
                    HelpText("Other device protocols",fontWeight=FontWeight.Bold)
                    PortPreferences.protocols.forEach {type->Surface(Modifier.fillMaxWidth().clickable {if(type!=protocol) {
                        values.clear();values.putAll(PortPreferences.defaults(type).entriesMap());values["name"]=draft.uniqueName(if(type=="NMEA") "GPS" else if(type=="AGW") "AGW" else "VHF");useGps=type=="NMEA"
                    }},shape=RoundedCornerShape(10.dp),border=BorderStroke(if(protocol==type) 2.dp else 1.dp,if(protocol==type) wireColors.receive else Color(0xFF38505D))) {
                        Column(Modifier.padding(14.dp)) {HelpText(type,fontWeight=FontWeight.Bold);HelpText(profileDescription(type),fontSize=12.sp,color=WorkspaceMuted)}
                    }}
                    }
                    1->{
                        if(!values["notes"].isNullOrBlank()) HelpText(values["notes"].orEmpty(),color=WorkspaceMuted)
                        if(!values["frequency_mhz"].isNullOrBlank()) HelpText("Radio: ${values["frequency_mhz"]} MHz",color=wireColors.send)
                        HelpText(protocol,fontWeight=FontWeight.Bold)
                        HelpField(UiHelp.field("A name you'll recognize"),values["name"].orEmpty(),{values["name"]=it},label={HelpText("A name you'll recognize")},isError=!validName,modifier=Modifier.fillMaxWidth(),singleLine=true,fill=true)
                        if(!validName) HelpText("Choose a unique port name.",color=wireColors.send,fontSize=12.sp)
                        DeviceConnectionFields(values) {key,value->values[key]=value}
                    }
                    else->{
                        HelpText("${values["name"]} · $protocol",fontSize=19.sp,fontWeight=FontWeight.Bold)
                        HelpText(if(values["transport"]=="serial") "${values["device"].orEmpty().ifBlank {"No serial device selected"}} · ${values["baud"]} baud" else values["endpoint"].orEmpty().ifBlank {"No TCP endpoint entered"},color=WorkspaceMuted)
                        RouteSwitch("Enable this port","The connection opens when you Apply the workspace.",values["enabled"]=="true") {values["enabled"]=it.toString()}
                        if(protocol=="NMEA" || values["provides_nmea"]=="true") RouteSwitch("Use as my GPS source","Select this device for position beacons.",useGps) {useGps=it}
                        if(protocol!="NMEA") {
                            RouteSwitch("RF → APRS-IS","Upload eligible packets heard by this radio.",values["rf_to_is"]=="true") {values["rf_to_is"]=it.toString()}
                            if(!receiveOnly) {
                                RouteSwitch("Radio transmit","Enable outgoing packets on this port.",values["transmit"]=="true") {values["transmit"]=it.toString()}
                                RouteSwitch("APRS-IS → RF","Deliver messages to recently heard stations.",values["is_to_rf"]=="true") {values["is_to_rf"]=it.toString()}
                                HelpText("Station beacons, messages and assigned objects are connected by default.",fontSize=12.sp,color=WorkspaceMuted)
                                HelpText("Beacon path: ${values["beacon_path"]}\nMessage path: ${values["message_path"]}",fontSize=12.sp)
                                if(protocol in rawRadioProtocols) RouteSwitch("WIDE1 fill-in digipeater","Repeat matching WIDE1-1 packets on this radio.",values["digipeater"]=="true") {values["digipeater"]=it.toString()}
                            }
                        }
                        if(!valid) HelpText("Go back and finish the name and connection, or leave this port disabled for later setup.",color=wireColors.send)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.End) {
                HelpTextButton(UiHelp.action("Cancel"),onClick=dismiss) {HelpText("Cancel")}
                if(step>0) HelpTextButton(UiHelp.action("Back"),onClick={step--}) {HelpText("Back")}
                if(step<2) HelpButton(UiHelp.action("Next"),onClick={step++},enabled=step==0 || validName) {HelpText("Next")}
                else HelpButton(UiHelp.action("Add to diagram"),onClick={add(values.properties(),useGps)},enabled=valid) {HelpText("Add to diagram")}
            }
        }}}
    }
}
