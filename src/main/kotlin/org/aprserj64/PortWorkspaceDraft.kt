// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.runtime.*
import java.util.Properties
import java.util.UUID
import kotlinx.serialization.json.*

internal fun Properties.entriesMap()=stringPropertyNames().associateWith {getProperty(it)}
internal fun Map<String,String>.properties()=Properties().also {p->forEach {(k,v)->p.setProperty(k,v)}}
internal fun names(value: String?)=runCatching {Json.parseToJsonElement(value ?: "[]").jsonArray.map {it.jsonPrimitive.content}}.getOrDefault(emptyList())
internal fun encodedNames(value: List<String>)=JsonArray(value.map {JsonPrimitive(it)}).toString()
internal val rawRadioProtocols=setOf("Simply(KISS)","KISS","KWD700(KISS)","AGW","TEXT")

internal class PortDraft(val id: String, initial: Map<String,String>) {
    val values=mutableStateMapOf<String,String>().apply {putAll(initial)}
    val name get()=values["name"].orEmpty()
    val protocol get()=values["protocol"].orEmpty()
    val enabled get()=values["enabled"]=="true"
    val radio get()=protocol !in PortPreferences.services && protocol !in listOf("NMEA","Weather")
    val canTransmit get()=radio && protocol !in PortPreferences.readOnly
}

/** Isolated edit transaction. Names are references on disk; stable IDs are only UI identities. */
internal class PortWorkspaceDraft(settings: Properties, ports: List<Properties>, objects: List<Properties>) {
    val values=mutableStateMapOf<String,String>().apply {putAll(settings.entriesMap())}
    val devices=mutableStateListOf<PortDraft>().apply {ports.forEach {add(PortDraft(UUID.randomUUID().toString(),it.entriesMap()))}}
    val objects=mutableStateListOf<MutableMap<String,String>>().apply {objects.forEach {add(mutableStateMapOf<String,String>().apply {putAll(it.entriesMap())})}}
    private data class Snapshot(val values: Map<String,String>,val ports: List<Pair<String,Map<String,String>>>,val objects: List<Map<String,String>>)
    private fun snapshot()=Snapshot(values.toMap(),devices.map {it.id to it.values.toMap()},objects.map {it.toMap()})
    private var baseline by mutableStateOf(snapshot())
    private val undo=mutableStateListOf<Snapshot>();private val redo=mutableStateListOf<Snapshot>()
    val dirty get()=snapshot()!=baseline
    val canUndo get()=undo.isNotEmpty()
    val canRedo get()=redo.isNotEmpty()
    fun savedName(id: String)=baseline.ports.firstOrNull {it.first==id}?.second?.get("name")
    fun portChanged(id: String)=devices.firstOrNull {it.id==id}?.values?.toMap()!=baseline.ports.firstOrNull {it.first==id}?.second
    fun change(action: ()->Unit) {
        val before=snapshot();action()
        if(snapshot()!=before) {undo.add(before);if(undo.size>60) undo.removeAt(0);redo.clear()}
    }
    private fun restore(state: Snapshot) {
        values.clear();values.putAll(state.values)
        devices.clear();devices.addAll(state.ports.map {PortDraft(it.first,it.second)})
        objects.clear();objects.addAll(state.objects.map {mutableStateMapOf<String,String>().apply {putAll(it)}})
    }
    fun undo() {if(undo.isNotEmpty()) {redo.add(snapshot());restore(undo.removeAt(undo.lastIndex))}}
    fun redo() {if(redo.isNotEmpty()) {undo.add(snapshot());restore(redo.removeAt(redo.lastIndex))}}
    fun markSaved() {baseline=snapshot();undo.clear();redo.clear()}
    fun matchesBaseline(settings: Properties, ports: List<Properties>, objects: List<Properties>)=
        settings.entriesMap()==baseline.values && ports.map {it.entriesMap()}==baseline.ports.map {it.second} && objects.map {it.entriesMap()}==baseline.objects
    fun uniqueName(base: String): String {
        if(devices.none {it.name==base}) return base
        var suffix=2;while(devices.any {it.name=="$base $suffix"}) suffix++
        return "$base $suffix"
    }
    fun add(properties: Properties): String {
        val port=PortDraft(UUID.randomUUID().toString(),properties.entriesMap());change {devices.add(port)};return port.id
    }
    fun duplicate(port: PortDraft): String {
        val props=port.values.properties();props.setProperty("name",uniqueName(port.name+" copy"));props.setProperty("enabled","false")
        return add(props)
    }
    fun rename(id: String, newName: String) {
        val port=devices.first {it.id==id};val old=port.name
        require(newName.isNotBlank() && newName==newName.trim() && newName.length<=64 && devices.none {it.id!=id && it.name==newName})
        change {
            port.values["name"]=newName
            values["digital.ports"]=encodedNames(names(values["digital.ports"]).map {if(it==old)newName else it})
            if(values["weatherstation.port"]==old)values["weatherstation.port"]=newName
            if(values["position.source"]=="port" && values["position.port_name"]==old) values["position.port_name"]=newName
            devices.forEach {p->p.values["digi_outputs"]=encodedNames(names(p.values["digi_outputs"]).map {if(it==old) newName else it})}
            objects.forEach {obj->obj["ports"]=encodedNames(names(obj["ports"]).map {if(it==old) newName else it})}
        }
    }
    fun remove(id: String) {
        val port=devices.first {it.id==id}
        change {
            devices.remove(port)
            // Do not fall back silently to an unrelated static location or broaden an RF route.
            if(values["position.source"]=="port" && values["position.port_name"]==port.name) values["position.port_name"]=""
            devices.forEach {p->val before=names(p.values["digi_outputs"])
                if(port.name in before) {val after=before-port.name;p.values["digi_outputs"]=encodedNames(after);if(after.isEmpty()) p.values["digipeater"]="false"}}
            val digitalBefore=names(values["digital.ports"])
            if(port.name in digitalBefore){val after=digitalBefore-port.name;values["digital.ports"]=encodedNames(after);if(after.isEmpty())values["digital.transmit"]="false"}
            if(values["weatherstation.port"]==port.name){values["weatherstation.port"]="";values["weatherstation.enabled"]="false"}
            objects.forEach {obj->val before=names(obj["ports"])
                if(port.name in before) {val after=before-port.name;obj["ports"]=encodedNames(after);if(after.isEmpty()) obj["radio"]="false"}}
        }
    }
    fun set(id: String, key: String, value: String)=change {devices.first {it.id==id}.values[key]=value}
    fun profile(port: PortDraft, protocol: String)=change {
        val seed=PortPreferences.defaults(protocol).entriesMap()
        val keep=setOf("name","enabled","device","endpoint","baud","transport","notes","frequency_mhz",
            "transmit","beacon_path","message_path")
        seed.filterKeys {it !in keep}.forEach {(key,value)->port.values[key]=value}
        if(protocol=="AGW" || protocol in PortPreferences.services) port.values["transport"]="tcp"
        else if(protocol!="Simply(KISS)" && port.values["transport"]=="ble") port.values["transport"]="serial"
    }
    fun useGps(port: PortDraft, enable: Boolean=false)=change {
        port.values["provides_nmea"]="true";if(enable) port.values["enabled"]="true"
        values["position.source"]="port";values["position.port_name"]=port.name
    }
    fun fixedPosition()=change {values["position.source"]="fixed";values["position.port_name"]=""}
    fun importLegacyGps(): String {
        val source=values["position.source"];require(source in listOf("gps_serial","gps_tcp","gpsd"))
        val sourceValues=values.toMap()
        val props=PortPreferences.defaults("NMEA").apply {
            setProperty("name",uniqueName("GPS"))
            if(source=="gps_serial") {setProperty("device",sourceValues["position.serial_port"].orEmpty());setProperty("baud",sourceValues["position.serial_baud"].orEmpty())}
            else {
                setProperty("transport","tcp");setProperty("endpoint",sourceValues[if(source=="gpsd") "position.gpsd_endpoint" else "position.gps_endpoint"].orEmpty())
                if(source=="gpsd") setProperty("open_commands",encodedNames(listOf("?WATCH={\"enable\":true,\"json\":false,\"nmea\":true};\n")))
            }
        }
        val port=PortDraft(UUID.randomUUID().toString(),props.entriesMap())
        change {devices.add(port);values["position.source"]="port";values["position.port_name"]=port.name}
        return port.id
    }
    fun toggleDigiTarget(source: PortDraft, target: PortDraft) = change {
        val current=(if(source.values["digipeater"]=="true") names(source.values["digi_outputs"]).ifEmpty {listOf(source.name)} else emptyList()).toMutableList()
        if(source.values["digipeater"]=="true" && target.name in current) current.remove(target.name) else if(target.name !in current) current.add(target.name)
        source.values["digi_outputs"]=encodedNames(current)
        source.values["digipeater"]=current.isNotEmpty().toString()
    }
    fun setObjectPort(index: Int, portName: String, enabled: Boolean)=change {
        val obj=objects[index];val current=(if(obj["radio"]=="true") names(obj["ports"]).ifEmpty {devices.filter {it.canTransmit}.map {it.name}} else emptyList()).toMutableList()
        if(enabled) {if(portName !in current) current.add(portName)} else current.remove(portName)
        obj["ports"]=encodedNames(current);obj["radio"]=current.isNotEmpty().toString()
    }
    fun issues(): List<String> = buildList {
        if(devices.map {it.name}.distinct().size!=devices.size) add("Give each port a unique name.")
        devices.forEach {p->
            if(p.enabled && p.values["transport"] in PortPreferences.comTransports && p.values["device"].isNullOrBlank()) add("${p.name}: choose a serial device.")
            if(p.enabled && p.values["transport"]=="ble" && p.values["device"].isNullOrBlank()) add("${p.name}: enter a Bluetooth LE address.")
            if(p.enabled && p.values["transport"]=="tcp" && p.values["endpoint"].isNullOrBlank()) add("${p.name}: enter a TCP host and port.")
            try {PortConfig(p.values.properties())} catch(_: Exception) {if(none {it.startsWith(p.name+":")}) add("${p.name}: check the connection, paths, commands and numeric settings.")}
        }
        devices.filter {it.enabled && it.values["transport"] in PortPreferences.comTransports && !it.values["device"].isNullOrBlank()}
            .groupBy {it.values["device"]!!.uppercase()}.filterValues {it.size>1}.forEach {(device,ports)->add("$device is assigned to ${ports.joinToString {it.name}}. Use one owner per device.")}
        if(values["position.source"]=="port" && devices.none {it.name==values["position.port_name"] && (it.protocol=="NMEA" || it.values["provides_nmea"]=="true")})
            add("Choose a GPS source, or explicitly select fixed position.")
        devices.forEach {p->names(p.values["digi_outputs"]).forEach {target->
            if(p.values["digipeater"]=="true" && devices.any {it.name==target && (!it.canTransmit || it.protocol !in rawRadioProtocols)}) add("${p.name}: $target cannot transmit raw digipeater frames. Rewire that output.")}}
        objects.forEach {obj->if(obj["radio"]=="true") names(obj["ports"]).forEach {target->
            if(devices.any {it.name==target && !it.canTransmit}) add("${obj["name"]}: $target is receive-only. Choose another object destination.")}}
        try {PortPreferences.validateAll(values.properties(),devices.map {it.values.properties()},objects.map {it.properties()})}
        catch(_: Exception) {if(isEmpty()) add("Check GPS, object and digipeater port references and the Radio limits.")}
    }
    fun portProperties()=devices.map {it.values.properties()}
    fun objectProperties()=objects.map {it.properties()}
}
