// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.util.Properties
import kotlinx.serialization.json.*
import org.aprsfoundation.core.*
import org.tomlj.TomlTable

object PortPreferences {
    const val PK88 = "AEA PK-88 (KISS)"
    val protocols=listOf("Weather","NMEA","Simply(KISS)","KISS",PK88,"AGW","KWD700(KISS)","KWD700(RO+GPS)","KWD710(APRS)","KWD710(Pkt)","CONVerse","TEXT","FTM350v1.3(RO)","CWOP","Dummy","IS-Server","Local-Server","UDP-Listen","Syslog-UDP")
    val kissProtocols=setOf("Simply(KISS)","KISS",PK88,"KWD700(KISS)")
    val rawRadioProtocols=kissProtocols+setOf("AGW","TEXT")
    val comTransports=setOf("serial","spp")
    val transports=listOf("serial","spp","ble","tcp")
    val services=setOf("CWOP","Dummy","IS-Server","Local-Server","UDP-Listen","Syslog-UDP")
    val readOnly=setOf("Weather","NMEA","KWD700(RO+GPS)","FTM350v1.3(RO)")
    val nmeaProtocols=setOf("NMEA","TEXT","CONVerse","KWD700(RO+GPS)","KWD710(APRS)","KWD710(Pkt)","FTM350v1.3(RO)")
    private val b=SettingKind.BOOLEAN; private val i=SettingKind.INTEGER
    val fields=listOf(
        BeaconField("name","Port name","New port"),
        BeaconField("notes","Setup notes",""),
        BeaconField("frequency_mhz","Radio frequency reminder (MHz)",""),
        BeaconField("enabled","Enabled","true",b),
        BeaconField("protocol","Protocol","Simply(KISS)",choices=protocols),
        BeaconField("transport","Connection","serial",choices=transports),
        BeaconField("device","Serial device (e.g. COM3)",""),
        BeaconField("endpoint","TCP host:port",""),
        BeaconField("baud","Serial baud","9600",i),
        BeaconField("channel","KISS channel / AGW radio (zero based)","0",i),
        BeaconField("provides_nmea","Also provides NMEA","false",b),
        BeaconField("transmit","Transmit enabled","true",b),
        BeaconField("rf_to_is","Gate RF to Internet","true",b),
        BeaconField("no_gate_me","Do not IGate my own callsign","false",b),
        BeaconField("is_to_rf","Gate messages to local RF stations","true",b),
        BeaconField("beacon","Station beacon on this port","true",b),
        BeaconField("beacon_path","Beacon path","WIDE1-1,WIDE2-1"),
        BeaconField("message_path","IGate message path","WIDE1-1"),
        BeaconField("messages","Local messaging on this port","true",b),
        BeaconField("objects","Object beacons on this port","true",b),
        BeaconField("digipeater","Digipeater enabled","false",b),
        BeaconField("digi_outputs","Digipeater output ports (empty: this port)","[]",SettingKind.STRINGS),
        BeaconField("digi_transforms","Digipeater transforms (one per line)","[\"WIDE1-1=\"]",SettingKind.STRINGS),
        BeaconField("gate_calls","Additional Internet source calls to RF","[]",SettingKind.STRINGS),
        BeaconField("gate_objects","Additional Internet object names to RF","[]",SettingKind.STRINGS),
        BeaconField("gate_filter","Additional IS-to-RF filter (e.g. r/28/-81/30 -b/TEST*)",""),
        BeaconField("service_input","Accept local service input","false",b),
        BeaconField("service_peers","Allowed UDP source IPs (loopback is always allowed)","[]",SettingKind.STRINGS),
        BeaconField("service_heartbeat_seconds","Local server heartbeat (seconds)","20",i),
        BeaconField("service_max_clients","Maximum local server clients","8",i),
        BeaconField("service_call","CWOP station identifier",""),
        BeaconField("agw_user","AGW username (empty: no authentication)",""),
        BeaconField("agw_password","AGW password",""),
        BeaconField("passall","TNC PASSALL is enabled (blocks IGating)","false",b),
        BeaconField("open_commands","Open commands (JSON strings; command|response)","[]",SettingKind.STRINGS),
        BeaconField("close_commands","Close commands (JSON strings)","[]",SettingKind.STRINGS),
        BeaconField("command_prompt","TNC command prompt","cmd:"),
        BeaconField("command_timeout_ms","Command response timeout (ms)","3000",i),
        BeaconField("command_delay_ms","Command pacing (ms)","100",i),
        BeaconField("reconnect_seconds","Reconnect delay (seconds)","3",i),
        BeaconField("transmit_per_minute","Maximum transmissions per minute","20",i),
        BeaconField("minimum_transmit_ms","Minimum transmission spacing (ms)","1000",i),
        BeaconField("kiss_txdelay","KISS TX delay (10 ms units; -1 leaves TNC unchanged)","-1",i),
        BeaconField("kiss_persistence","KISS persistence (-1 leaves unchanged)","-1",i),
        BeaconField("kiss_slot","KISS slot time (10 ms units)","-1",i),
        BeaconField("kiss_tail","KISS TX tail (10 ms units)","-1",i),
        BeaconField("kiss_duplex","KISS duplex (0/1; -1 leaves unchanged)","-1",i)
    )
    val global=listOf(
        BeaconField("radio.heard_seconds","Local heard window (seconds)","1800",i),
        BeaconField("radio.maximum_hops","Maximum local RF digipeater hops","2",i),
        BeaconField("radio.duplicate_seconds","Duplicate suppression (seconds)","30",i),
        BeaconField("radio.internet_seconds","Internet presence window (seconds)","1800",i),
        BeaconField("radio.maximum_entries","Maximum heard/dedup entries","4096",i)
    )
    fun defaults(protocol: String="Simply(KISS)")=Properties().apply {
        fields.forEach {setProperty(it.key,it.default)};setProperty("protocol",protocol)
        if(protocol in readOnly) {setProperty("transmit","false");setProperty("is_to_rf","false")}
        if(protocol !in rawRadioProtocols) setProperty("digipeater","false")
        if(protocol=="AGW" || protocol in services) setProperty("transport","tcp")
        if(protocol in services) {
            setProperty("rf_to_is","false");setProperty("is_to_rf","false");setProperty("digipeater","false")
            setProperty("endpoint",if(protocol=="CWOP")"cwop.aprs.net:14580" else "127.0.0.1:14582")
            setProperty("beacon_path","");setProperty("message_path","")
            if(protocol=="CWOP")setProperty("messages","false")
        }
        if(protocol=="Weather") {setProperty("baud","2400");listOf("rf_to_is","is_to_rf","beacon","messages","objects").forEach {setProperty(it,"false")}}
        if(protocol=="NMEA") {
            setProperty("baud","4800");setProperty("provides_nmea","true")
            listOf("rf_to_is","is_to_rf","beacon","messages","objects").forEach {setProperty(it,"false")}
        }
        if(protocol in listOf("KWD700(RO+GPS)","KWD710(APRS)")) setProperty("provides_nmea","true")
        val commands=when(protocol) {
            "KISS"->listOf("\r|cmd:","XFLOW OFF\r|cmd:","KISS ON\r|cmd:","RESTART\r")
            PK88->listOf("\u0003\u0003\u0003","\r|cmd:","KISS ON\r")
            "KWD700(KISS)"->listOf("TNC 2\r","\r|cmd:","KISS ON\r|cmd:","RESTART\r")
            "KWD710(APRS)"->listOf("TC 1\r")
            "KWD710(Pkt)","CONVerse"->listOf("\u0003|cmd:","MON ON\r|cmd:","MCOM OFF\r|cmd:","MCON OFF\r|cmd:")
            else->emptyList()
        }
        setProperty("open_commands",JsonArray(commands.map {JsonPrimitive(it)}).toString())
    }
    fun load(table: TomlTable): Properties {
        val protocol=table.getString("protocol") ?: "Simply(KISS)"
        require(protocol in protocols)
        val seed=defaults(protocol)
        return Properties().also {BeaconPreferences.loadFields(it,table,fields.map {field->field.copy(default=seed.getProperty(field.key))})}
    }
    fun rules(values: Properties)=RadioRules(values.getProperty("radio.heard_seconds").toInt(),values.getProperty("radio.maximum_hops").toInt(),
        values.getProperty("radio.duplicate_seconds").toInt(),values.getProperty("radio.internet_seconds").toInt(),values.getProperty("radio.maximum_entries").toInt())
    fun validate(ports: List<Properties>) {
        require(ports.size<=32 && ports.map {it.getProperty("name")}.distinct().size==ports.size)
        ports.forEach {PortConfig(it)}
        val names=ports.map {it.getProperty("name")}.toSet()
        ports.forEach {require(PortConfig(it).list("digi_outputs").all {name->name in names})}
        val serial=ports.filter {it.getProperty("enabled").toBoolean() && it.getProperty("transport") in comTransports}.map {it.getProperty("device").uppercase()}
        require(serial.distinct().size==serial.size) {"A serial device can belong to only one enabled port"}
    }
    fun validateAll(values: Properties, ports: List<Properties>, objects: List<Properties>) {
        validate(ports);rules(values)
        if(values.getProperty("position.source")=="port") require(ports.any {
            it.getProperty("name")==values.getProperty("position.port_name") && (it.getProperty("protocol")=="NMEA" || it.getProperty("provides_nmea").toBoolean())
        }) {"Select a named NMEA-capable port"}
        if(values.getProperty("position.source")=="gps_serial") require(ports.none {
            it.getProperty("enabled").toBoolean() && it.getProperty("transport") in comTransports &&
                (values.getProperty("position.serial_port").isBlank() || it.getProperty("device").equals(values.getProperty("position.serial_port"),ignoreCase=true))
        }) {"Use a named GPS port instead of opening the serial device twice"}
        val names=ports.map {it.getProperty("name")}.toSet()
        objects.forEach {obj->require(Json.parseToJsonElement(obj.getProperty("ports")).jsonArray.all {it.jsonPrimitive.content in names})}
        require(Json.parseToJsonElement(values.getProperty("digital.ports","[]")).jsonArray.all {it.jsonPrimitive.content in names}) {"Select existing digital object ports"}
    }
}

class PortConfig(val values: Properties) {
    fun text(key: String)=values.getProperty(key)
    fun flag(key: String)=text(key).toBooleanStrict()
    fun number(key: String)=text(key).toInt()
    fun list(key: String)=Json.parseToJsonElement(text(key)).jsonArray.map {it.jsonPrimitive.content}
    val name=text("name");val protocol=text("protocol")
    val connection: String
        get() {
            val transport=text("transport")
            val label=when(transport) {
                "serial" -> "Serial"
                "spp" -> "Bluetooth Classic SPP"
                "ble" -> "Bluetooth LE"
                else -> "TCP"
            }
            val endpoint=text(if(transport=="tcp") "endpoint" else "device")
            val channel=if(protocol in PortPreferences.kissProtocols) " · KISS channel ${number("channel")}"
                else if(protocol=="AGW") " · AGW radio ${number("channel")}" else ""
            return "$label · $endpoint · $protocol$channel"
        }
    val gateFilter=PacketFilter(text("gate_filter"))
    val isRf=protocol !in PortPreferences.services && protocol !in listOf("NMEA","Weather")
    val canTransmit=flag("transmit") && protocol !in PortPreferences.readOnly
    fun path(key: String)=text(key).split(',').map {it.trim()}.filter {it.isNotEmpty()}
    init {
        require(name.isNotBlank() && name.length<=64 && protocol in PortPreferences.protocols)
        require(text("transport") in PortPreferences.transports)
        if(flag("enabled")) {
            if(text("transport") in PortPreferences.comTransports) require(text("device").isNotBlank())
            else if(text("transport")=="ble") require(text("device").matches(Regex("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}|[0-9a-f]{12}"))) {"Enter the BLE device address"}
            else {require(text("endpoint").contains(':'));AprsEndpoint.parse(text("endpoint"))}
        }
        require(protocol!="AGW" && protocol !in PortPreferences.services || text("transport")=="tcp")
        require(text("transport")!="ble" || protocol=="Simply(KISS)") {"Bluetooth LE uses Simply(KISS)"}
        require(number("service_max_clients") in 1..32 && number("service_heartbeat_seconds") in 5..120)
        require(text("agw_user").length<=255 && text("agw_password").length<=255)
        if(protocol=="CWOP" && flag("enabled"))require(text("service_call").matches(Regex("[A-Z0-9]{3,9}")))
        require(list("service_peers").all {it.matches(Regex("[0-9a-fA-F:.]{2,45}"))})
        require(!flag("provides_nmea") || protocol in PortPreferences.nmeaProtocols) {"This protocol cannot carry standalone NMEA sentences"}
        require(!flag("digipeater") || protocol in PortPreferences.rawRadioProtocols) {"Digipeating requires a raw-frame TNC"}
        require(number("baud") in 1200..921600 && number("channel") in 0..(if(protocol=="AGW") 255 else 15))
        require(number("command_timeout_ms") in 100..30000 && number("command_delay_ms") in 0..5000)
        require(number("reconnect_seconds") in 1..300 && number("transmit_per_minute") in 1..600 && number("minimum_transmit_ms") in 100..60000)
        listOf("beacon_path","message_path").forEach {key->require(path(key).size<=8 && path(key).all {RadioPacket.address(it)})}
        listOf("kiss_txdelay","kiss_persistence","kiss_slot","kiss_tail").forEach {require(number(it) in -1..255)}
        require(number("kiss_duplex") in -1..1)
        require(list("digi_transforms").size<=64)
        list("digi_transforms").forEach {t->require('=' in t && RadioPacket.address(t.substringBefore('=')))
            val after=t.substringAfter('=').dropWhile {it in "!@"}
            require(after.isEmpty() || after.split(',').all {RadioPacket.address(it.trimEnd('*'))})}
        require(list("gate_calls").all {RadioPacket.address(it)} && list("gate_objects").all {it.length in 3..9})
        listOf("open_commands","close_commands").forEach {key->require(list(key).size<=64 && list(key).all {it.length<=512 && it.all {c->c.code in 1..127}})}
    }
}
