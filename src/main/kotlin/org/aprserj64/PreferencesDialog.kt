// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import java.util.Properties
import kotlinx.serialization.json.*

@Composable fun PreferencesDialog(model: AppModel, initialSection:String="Position", requestId:Int=0,onNavigate:(String)->Unit={}, dismiss: ()->Unit) {
    val values=remember {mutableStateMapOf<String,String>().apply {model.settings.stringPropertyNames().forEach {put(it,model.settings.getProperty(it))}}}
    val objects=remember {mutableStateListOf<MutableMap<String,String>>().apply {
        model.entities.forEach {props -> add(mutableStateMapOf<String,String>().apply {props.stringPropertyNames().forEach {put(it,props.getProperty(it))}})}
    }}
    val baselineSettings=remember {model.settings.entriesMap()}
    val baselinePorts=remember {model.ports.map {it.entriesMap()}}
    val baselineObjects=remember {model.entities.map {it.entriesMap()}}
    var selected by remember {mutableStateOf(initialSection)}
    LaunchedEffect(initialSection,requestId) {selected=initialSection}
    var entity by remember {mutableStateOf(0)}
    var failure by remember {mutableStateOf("")}
    fun properties(values: Map<String,String>)=Properties().apply {values.forEach {(key,value)->setProperty(key,value)}}
    val bounds=remember {windowBounds(1000,790)}
    DialogWindow(icon=AppIcon.window,onCloseRequest=dismiss,title="Settings",state=rememberDialogState(position=bounds.composePosition,width=bounds.width.dp,height=bounds.height.dp)) {
        LaunchedEffect(initialSection,requestId) {window.toFront();window.requestFocus()}
        MaterialTheme(colors=MaterialTheme.colors) {Surface {Column(Modifier.fillMaxSize().padding(18.dp)) {
            HelpText("Settings",style=MaterialTheme.typography.h5)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f)) {
                SectionNavigator(settingsSections,selected,Modifier.width(245.dp).fillMaxHeight(),model.settings.getProperty("callsign",""),onNavigate={if(it.startsWith("settings:")) selected=it.substringAfter(':') else onNavigate(it)}) {selected=it}
                Divider(Modifier.fillMaxHeight().width(1.dp))
                Column(Modifier.weight(1f).padding(start=18.dp)) {
                    HelpText(settingsSections.first {it.id==selected}.title,style=MaterialTheme.typography.h6)
            if(failure.isNotEmpty()) HelpText(failure,color=MaterialTheme.colors.error)
            key(selected,entity) {ScrollableForm(Modifier.weight(1f).fillMaxWidth()) {
                if(selected=="Objects") {
                    Row {
                        HelpButton(UiHelp.action("New"),onClick={objects.add(mutableStateMapOf<String,String>().apply {BeaconPreferences.entityFields.forEach {put(it.key,it.default)}});entity=objects.lastIndex},enabled=objects.size<32) {HelpText("New")}
                        Spacer(Modifier.width(12.dp))
                        HelpTextButton(UiHelp.action("Remove from settings"),onClick={if(objects.isNotEmpty()) {objects.removeAt(entity);entity=(entity-1).coerceAtLeast(0)}},enabled=objects.isNotEmpty()) {HelpText("Remove from settings")}
                    }
                    objects.forEachIndexed {index,obj -> HelpTextButton(UiHelp.action(obj["name"].orEmpty().ifBlank {"New object"}),onClick={entity=index}) {HelpText(obj["name"].orEmpty().ifBlank {"New object"})} }
                    objects.getOrNull(entity)?.let {objectValues -> BeaconPreferences.entityFields.forEach {SettingField(it,objectValues)} }
                } else {
                    if(selected=="Map") {
                        HelpText("Map presets change the base map only. Routing and search keep their own services.")
                        Row {
                            listOf("pdeffenb" to "project_preset_url", "OpenStreetMap" to "osm_preset_url").forEach { (name, key) ->
                                HelpOutlinedButton("Select $name tiles, then Save to apply. Keeps your other service settings.", onClick={
                                    values["map.source_name"]=name
                                    values["map.tile_url"]=values["map.$key"].orEmpty()
                                    values["map.tile_size"]="256"
                                    values["map.minimum_zoom"]="0"
                                    values["map.maximum_zoom"]=if(name=="OpenStreetMap") "19" else "18"
                                    values["map.initial_zoom"]=(values["map.initial_zoom"]?.toDoubleOrNull() ?: 12.0).coerceIn(0.0,18.0).toString()
                                    values["map.online_enabled"]="true"
                                    values["map.overlay_enabled"]="false"
                                    values["map.underlay_enabled"]="false"
                                }) {HelpText(name)}
                            }
                        }
                        HelpText("Public OpenStreetMap is for normal interactive viewing. Do not use offline bulk downloads with that preset.")
                    }
                    if(selected=="Operating") {
                        SettingField(BeaconField("radiusKm","APRS-IS radius (km)","100",SettingKind.INTEGER),values)
                        HelpText("Range uses the connection coordinates; moving follows your last APRS-IS position. Custom uses only your filter terms plus delivery to your callsign. Example: b/N1CALL d/RS0ISS*")
                    }
                    val section=selected.lowercase()
                    if(selected=="Connectivity") NetworkPreferences(model,values)
                    val schema=when(selected) {"Display","History","Offline","Weather","Groups","Kiosk","Alerts","Connectivity","Tactical","Messaging","Bulletins","Telemetry","Logging","Legacyweather","Qru","Queries","Satellites","Imports","Platforms","Weatherstation","Multitrack","Digital"->FeaturePreferences.fields;"Operating"->OperatingPreferences.fields;"Map","Services"->ServicePreferences.fields;"Radio"->PortPreferences.global;else->BeaconPreferences.fields}
                    if(selected=="Beacon") {
                        val mode=when {
                            values["beacon.time_only"]=="true" -> "time"
                            values["beacon.smart"]=="true" -> "smart"
                            else -> "genius"
                        }
                        ChoiceMenu("Beacon timing",mode,listOf("genius" to "GeniusBeaconing",
                            "smart" to "SmartBeaconing","time" to "Time only"),
                            help="Select the automatic position reporting policy.") {
                            values["beacon.time_only"]=(it=="time").toString()
                            values["beacon.smart"]=(it=="smart").toString()
                        }
                    }
                    schema.filter {it.key.startsWith("$section.") || selected=="Beacon" && it.key.startsWith("odometer.")}
                        .filter {it.key !in setOf("beacon.smart","beacon.time_only")}
                        .filter {field->NetworkResource.entries.none {it.key==field.key}}
                        .filter {selected!="Position" || positionFieldVisible(it.key,values["position.source"].orEmpty())}
                        .forEach {field -> SettingField(field,values)}
                    if(selected=="Beacon") {
                        Row {HelpTextButton(UiHelp.action("Report odometer"),onClick={model.reportOdometer()}) {HelpText("Report odometer")}; HelpTextButton(UiHelp.action("Report and reset"),onClick={model.reportOdometer(true)}) {HelpText("Report and reset")}}
                    }
                    if(selected=="Status") HelpTextButton(UiHelp.action("Send status"),onClick={model.transmitStatus()}) {HelpText("Send status")}
                }
            } }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                HelpTextButton(UiHelp.action("Cancel"),onClick=dismiss) {HelpText("Cancel")}
                HelpButton(UiHelp.action("Save"),onClick={
                    try {
                        val props=properties(values); val entityProps=objects.map {properties(it)}
                        BeaconConfiguration(props,entityProps); ServicePreferences.validate(props)
                        if(model.settings.entriesMap()!=baselineSettings || model.ports.map {it.entriesMap()}!=baselinePorts || model.entities.map {it.entriesMap()}!=baselineObjects)
                            failure="Settings changed in another window. Close and reopen this editor before saving."
                        else if(model.savePreferences(props,entityProps)) dismiss() else failure="Check settings and folder write access."
                    } catch(error: Exception) {failure=error.message ?: "Check the selected options, numbers, coordinates and object names."}
                }) {HelpText("Save")}
            }
        } } }
    }
}

@Composable private fun SettingField(field: BeaconField, values: MutableMap<String,String>) {
    if(field.key in listOf("longitude","position.longitude")) return
    if(field.key in listOf("latitude","position.latitude")) {
        val lonKey=field.key.replace("latitude","longitude")
        LocationField(if(field.key=="latitude") "Object location" else "Fixed location",values[field.key]?:field.default,values[lonKey].orEmpty()) {a,b->values[field.key]=a;values[lonKey]=b;if(field.key=="position.latitude") values["position.fixed_confirmed"]="false"}
        return
    }
    val value=values[field.key] ?: field.default
    if(field.key in listOf("beacon.symbol","symbol")) {
        var picker by remember {mutableStateOf(false)}
        val table=values[if(field.key=="symbol") "symbol_table" else "beacon.symbol_table"]?.singleOrNull() ?: '/'
        val previewPixels=with(LocalDensity.current) {32.dp.roundToPx().coerceAtLeast(1)}
        val pickerPixels=with(LocalDensity.current) {26.dp.roundToPx().coerceAtLeast(1)}
        Row {Image(StationSymbols.bitmap(table,value.singleOrNull() ?: '?',previewPixels),"Selected APRS symbol",Modifier.size(32.dp));HelpTextButton(UiHelp.action("Choose symbol"),onClick={picker=true}){HelpText("Choose symbol")}}
        if(picker) Column(Modifier.height(250.dp).verticalScroll(rememberScrollState())) {
            (33..126).toList().chunked(12).forEach {row->Row {row.forEach {code->
                HelpArea("Select APRS symbol $table${code.toChar()} (${if(table=='/') "primary" else "alternate/overlay"} table). Save settings to use this symbol.") {
                    Box(Modifier.size(38.dp).clickable {values[field.key]=code.toChar().toString();picker=false}.padding(6.dp)) {Image(StationSymbols.bitmap(table,code.toChar(),pickerPixels),"APRS symbol ${code.toChar()}")}
                }
            }}}
        }
    }
    when {
        field.key=="groups.peer_key" -> {
            HelpField("Use the same group key at every configured seat.",value,{values[field.key]=it},modifier=Modifier.fillMaxWidth(),label={HelpText(field.label)},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),singleLine=true,fill=true)
            if(value.isBlank()) HelpTextButton("Create a random key for this new group",onClick={values[field.key]=java.util.Base64.getEncoder().encodeToString(ByteArray(32).also(java.security.SecureRandom()::nextBytes))}) {HelpText("Generate group key")}
            else HelpTextButton("Copy the group key for a configured peer",onClick={java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(value),null)}){HelpText("Copy group key")}
        }
        field.key in listOf("open_commands","close_commands") -> HelpField(UiHelp.setting(field),value,{values[field.key]=it},label={HelpText(field.label)},modifier=Modifier.fillMaxWidth(),maxLines=6,fill=true)
        field.kind==SettingKind.BOOLEAN -> Row(verticalAlignment=Alignment.CenterVertically) {
            HelpCheckbox(UiHelp.setting(field),value.toBoolean(),{values[field.key]=it.toString()}); HelpText(field.label,help=UiHelp.setting(field))
        }
        field.choices.isNotEmpty() -> {
            ChoiceMenu(field.label,value,field.choices.map {it to when(field.key) {
                "position.source"->positionSourceLabel(it)
                "display.theme"->if(it=="system") "Follow operating system" else it.replaceFirstChar(Char::uppercaseChar)
                "display.units"->it.replaceFirstChar(Char::uppercaseChar)
                "weather.units"->weatherUnitLabel(it)
                ServicePreferences.TILE_RESOLUTION_KEY->ServicePreferences.tileResolutionOptions.getValue(it)
                else->it.ifBlank {"None"}
            }},help=UiHelp.setting(field)) {values[field.key]=it}
            if(field.key=="beacon.status_tag") HelpField(UiHelp.field("Custom status tag"),value,{values[field.key]=it},label={HelpText("Custom status tag")},modifier=Modifier.fillMaxWidth(),fill=true)
        }
        field.kind==SettingKind.STRINGS -> {
            val text=Json.parseToJsonElement(value).jsonArray.joinToString("\n") {it.jsonPrimitive.content}
            HelpField(UiHelp.setting(field),text,{values[field.key]=JsonArray(it.split('\n').map {line->JsonPrimitive(line)}).toString()},label={HelpText(field.label)},modifier=Modifier.fillMaxWidth(),maxLines=6,fill=true)
            if(field.key=="vertices") {
                val reference=runCatching {MapLocation(values["latitude"]!!.toDouble(),values["longitude"]!!.toDouble())}.getOrNull()
                text.lines().filter {it.isNotBlank()}.forEachIndexed {index,line->
                    val parsed=CoordinateInput.parse(line,reference)
                    HelpText("${index+1}: ${parsed.location?.display() ?: parsed.error}",color=if(parsed.location==null) MaterialTheme.colors.error else MaterialTheme.colors.onSurface)
                    if(parsed.explanation.isNotBlank()) HelpText(parsed.explanation)
                }
            }
        }
        else -> HelpField(UiHelp.setting(field),value,{values[field.key]=it},label={HelpText(field.label)},modifier=Modifier.fillMaxWidth(),singleLine=true,fill=true)
    }
}

private fun positionSourceLabel(source:String)=when(source) {
    "fixed"->"Fixed location"; "port"->"GPS from a configured port"; "gps_serial"->"Serial GPS"; "gps_tcp"->"Network GPS (NMEA)"; "gpsd"->"GPSd service"; else->source
}

private fun positionFieldVisible(key:String,source:String)=when(key) {
    "position.port_name"->source=="port"
    "position.latitude","position.longitude","position.altitude_meters","position.fixed_confirmed"->source=="fixed"
    "position.gps_endpoint"->source=="gps_tcp"
    "position.gpsd_endpoint"->source=="gpsd"
    "position.serial_port","position.serial_baud"->source=="gps_serial"
    "position.maximum_age_seconds","position.maximum_accuracy_meters"->source!="fixed"
    else->true
}
