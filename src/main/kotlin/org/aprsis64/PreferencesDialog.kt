// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import java.util.Properties
import kotlinx.serialization.json.*

@Composable fun PreferencesDialog(model: AppModel, dismiss: ()->Unit) {
    val values=remember {mutableStateMapOf<String,String>().apply {model.settings.stringPropertyNames().forEach {put(it,model.settings.getProperty(it))}}}
    val objects=remember {mutableStateListOf<MutableMap<String,String>>().apply {
        model.entities.forEach {props -> add(mutableStateMapOf<String,String>().apply {props.stringPropertyNames().forEach {put(it,props.getProperty(it))}})}
    }}
    var selected by remember {mutableStateOf("Beacon")}
    var entity by remember {mutableStateOf(0)}
    var failure by remember {mutableStateOf("")}
    fun properties(values: Map<String,String>)=Properties().apply {values.forEach {(key,value)->setProperty(key,value)}}
    DialogWindow(onCloseRequest=dismiss,title="Settings",state=rememberDialogState(width=780.dp,height=790.dp)) {
        MaterialTheme(colors=darkColors()) {Surface {Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("Beacon","Position","Status","Objects","Map","Services").forEach {tab ->
                    TextButton(onClick={selected=tab}) {Text(if(selected==tab) "[$tab]" else tab)}
                }
            }
            if(failure.isNotEmpty()) Text(failure,color=MaterialTheme.colors.error)
            key(selected,entity) {Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(selected=="Objects") {
                    Row {
                        Button(onClick={objects.add(mutableStateMapOf<String,String>().apply {BeaconPreferences.entityFields.forEach {put(it.key,it.default)}});entity=objects.lastIndex},enabled=objects.size<32) {Text("New")}
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick={if(objects.isNotEmpty()) {objects.removeAt(entity);entity=(entity-1).coerceAtLeast(0)}},enabled=objects.isNotEmpty()) {Text("Remove from settings")}
                    }
                    objects.forEachIndexed {index,obj -> TextButton(onClick={entity=index}) {Text(obj["name"].orEmpty().ifBlank {"New object"})} }
                    objects.getOrNull(entity)?.let {objectValues -> BeaconPreferences.entityFields.forEach {SettingField(it,objectValues)} }
                } else {
                    val section=selected.lowercase()
                    val schema=if(selected in listOf("Map","Services")) ServicePreferences.fields else BeaconPreferences.fields
                    schema.filter {it.key.startsWith("$section.") || selected=="Beacon" && it.key.startsWith("odometer.")}.forEach {field -> SettingField(field,values)}
                    if(selected=="Beacon") {
                        Row {TextButton(onClick={model.reportOdometer()}) {Text("Report odometer")}; TextButton(onClick={model.reportOdometer(true)}) {Text("Report and reset")}}
                    }
                    if(selected=="Status") TextButton(onClick={model.transmitStatus()}) {Text("Send status")}
                }
            } }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick=dismiss) {Text("Cancel")}
                Button(onClick={
                    try {
                        val props=properties(values); val entityProps=objects.map {properties(it)}
                        BeaconConfiguration(props,entityProps); ServicePreferences.validate(props)
                        if(model.savePreferences(props,entityProps)) dismiss() else failure="Unable to save settings. Check folder write access."
                    } catch(_: Exception) {failure="Check the selected options, numbers, coordinates and object names."}
                }) {Text("Save")}
            }
        } } }
    }
}

@Composable private fun SettingField(field: BeaconField, values: MutableMap<String,String>) {
    val value=values[field.key] ?: field.default
    when {
        field.kind==SettingKind.BOOLEAN -> Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(value.toBoolean(),{values[field.key]=it.toString()}); Text(field.label)
        }
        field.choices.isNotEmpty() -> {
            var menu by remember {mutableStateOf(false)}
            Box {
                OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()) {Text("${field.label}: ${value.ifBlank {"None"}}")}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {field.choices.forEach {choice ->
                    DropdownMenuItem(onClick={values[field.key]=choice;menu=false}) {Text(choice.ifBlank {"None"})}
                } }
            }
            if(field.key=="beacon.status_tag") OutlinedTextField(value,{values[field.key]=it},label={Text("Custom status tag")},modifier=Modifier.fillMaxWidth())
        }
        field.kind==SettingKind.STRINGS -> {
            val text=Json.parseToJsonElement(value).jsonArray.joinToString("\n") {it.jsonPrimitive.content}
            OutlinedTextField(text,{values[field.key]=JsonArray(it.split('\n').map {line->JsonPrimitive(line)}).toString()},label={Text(field.label)},modifier=Modifier.fillMaxWidth(),maxLines=6)
        }
        else -> OutlinedTextField(value,{values[field.key]=it},label={Text(field.label)},modifier=Modifier.fillMaxWidth(),singleLine=true)
    }
}
