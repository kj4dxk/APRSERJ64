// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.AtomicMoveNotSupportedException
import java.util.Properties
import kotlinx.serialization.json.*
import net.osmand.shared.aprs.AprsEndpoint
import org.tomlj.Toml

/** Everything belongs to the EXE's folder, even when a shortcut has another working directory. */
object PortableHome {
    fun resolve(): Path = Path.of(System.getenv("APRSERJ64_HOME")
        ?: System.getProperty("aprserj64.home") ?: System.getProperty("user.dir")).toAbsolutePath().normalize()
    fun prepare(root: Path) {
        listOf("data", "cache", "cache/tmp", "logs").forEach { Files.createDirectories(root.resolve(it)) }
    }
}

class SettingsStore(private val root: Path) {
    private val path = root.resolve("settings.toml")
    private val document = if(Files.exists(path)) Toml.parse(path).also {
        require(!it.hasErrors()) { "Invalid settings.toml; check TOML syntax." }
    } else null
    val values = Properties().apply {
        if (document != null) {
            // Do not include file contents or parser error excerpts: they may contain the passcode.
            require(!document.hasErrors()) { "Invalid settings.toml; check TOML syntax." }
            fun text(key: String, default: String): String {
                val value = document.get(key) ?: return default
                require(value is String) { "$key must be a quoted string." }
                return value
            }
            fun coordinate(key: String, default: Double, range: ClosedFloatingPointRange<Double>): String {
                val value = document.get(key) ?: default
                require(value is Number && value.toDouble().isFinite() && value.toDouble() in range) { "$key must be a valid coordinate." }
                return value.toDouble().toString()
            }
            val port = document.get("aprs.port") ?: 14580L
            require(port is Long && port in 1L..65535L) { "aprs.port must be an integer from 1 to 65535." }
            val server = text("aprs.server", AprsServers.presets.first().host)
            val address = AprsEndpoint.parse(server, port.toInt())
            setProperty("endpoint", if(':' in address.host) "[${address.host}]:${address.port}" else "${address.host}:${address.port}")
            setProperty("callsign", text("aprs.callsign", ""))
            setProperty("passcode", text("aprs.passcode", ""))
            val connect = document.get("aprs.connect_on_startup") ?: true
            require(connect is Boolean) { "aprs.connect_on_startup must be true or false." }
            setProperty("autoConnect", connect.toString())
            val radius=document.get("aprs.radius_km") ?: 100L
            require(radius is Long && radius in 1L..500L)
            setProperty("radiusKm",radius.toString())
            setProperty("latitude", coordinate("map.latitude", 28.536725, -90.0..90.0))
            setProperty("longitude", coordinate("map.longitude", -81.242932, -180.0..180.0))
        } else {
            // One-time import. Leave the original intact for older EXEs and rollback.
            val legacy = root.resolve("settings.properties")
            if(Files.exists(legacy)) Files.newInputStream(legacy).use { load(it) }
        }
    }
    var entities: List<Properties> = document?.getArray("objects")?.let { array ->
        (0 until array.size()).map { index -> Properties().also { BeaconPreferences.loadFields(it,array.getTable(index),BeaconPreferences.entityFields) } }
    } ?: emptyList()
    var ports: List<Properties> = document?.getArray("ports")?.let {array ->
        (0 until array.size()).map {index->PortPreferences.load(array.getTable(index))}
    } ?: emptyList()
    var portPresets: List<Properties> = document?.getArray("port_presets")?.let {array ->
        (0 until array.size()).map {PortPreferences.load(array.getTable(it))}
    } ?: PortPresets.defaults()
    var servers: List<ServerPreset> = document?.getArray("aprs.servers")?.let { array ->
        (0 until array.size()).map { index -> val entry=array.getTable(index)
            val port=requireNotNull(entry.getLong("port")).also {require(it in 1L..65535L)}.toInt()
            ServerPreset(requireNotNull(entry.getString("name")),requireNotNull(entry.getString("host")),port).also {
                require(it.name.isNotBlank()); AprsEndpoint.parse(it.host,it.port)
            }
        }
    } ?: AprsServers.presets
    init {
        val defaults = mapOf("callsign" to "", "passcode" to "", "endpoint" to AprsServers.defaultEndpoint,
            "latitude" to "28.536725", "longitude" to "-81.242932", "autoConnect" to "true", "radiusKm" to "100")
        defaults.forEach { (key, value) -> if(!values.containsKey(key)) values.setProperty(key, value) }
        BeaconPreferences.load(values,document)
        ServicePreferences.load(values,document)
        BeaconPreferences.loadFields(values,document,PortPreferences.global)
        BeaconPreferences.loadFields(values,document,OperatingPreferences.fields)
        BeaconPreferences.loadFields(values,document,FeaturePreferences.fields)
        FeaturePreferences.validate(values)
        OperatingPreferences.validate(values)
        PortPreferences.validateAll(values,ports,entities)
        ServicePreferences.validate(values)
        BeaconConfiguration(values,entities)
        if(!Files.exists(path) || document?.get("aprs.servers")==null || document?.get("port_presets")==null ||
            (BeaconPreferences.fields+ServicePreferences.fields+PortPreferences.global+OperatingPreferences.fields+FeaturePreferences.fields).any {document?.get(it.key)==null}) save()
    }
    fun save() {
        FeaturePreferences.validate(values)
        OperatingPreferences.validate(values)
        PortPreferences.validateAll(values,ports,entities)
        BeaconConfiguration(values,entities)
        ServicePreferences.validate(values)
        Files.createDirectories(root)
        val address = AprsEndpoint.parse(values.getProperty("endpoint"))
        val latitude = values.getProperty("latitude").toDouble()
        val longitude = values.getProperty("longitude").toDouble()
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        val connect = values.getProperty("autoConnect").toBooleanStrict()
        // JSON's basic string escapes used here are also valid TOML basic-string escapes.
        fun quoted(value: String) = JsonPrimitive(value).toString()
        val contents = """
            # APRSERJ64 portable settings
            [aprs]
            callsign = ${quoted(values.getProperty("callsign"))}
            passcode = ${quoted(values.getProperty("passcode"))}
            server = ${quoted(address.host)}
            port = ${address.port}
            connect_on_startup = $connect
            radius_km = ${values.getProperty("radiusKm").toInt().also {require(it in 1..500)}}
            ${if(servers.isEmpty()) "servers = []" else ""}

            [map]
            latitude = $latitude
            longitude = $longitude
        """.trimIndent() + "\n" + ServicePreferences.writeMap(values) + ServicePreferences.writeServices(values) +
            servers.joinToString("") { "\n[[aprs.servers]]\nname = ${quoted(it.name)}\nhost = ${quoted(it.host)}\nport = ${it.port}\n" } +
            BeaconPreferences.writeMain(values) + entities.joinToString("") {
            "\n[[objects]]\n"+BeaconPreferences.write(it,BeaconPreferences.entityFields,sections=false)
        } + BeaconPreferences.write(values,PortPreferences.global) + ports.joinToString("") {
            "\n[[ports]]\n"+BeaconPreferences.write(it,PortPreferences.fields,sections=false)
        }
        require(portPresets.size<=64);portPresets.forEach {PortConfig(it)}
        val completeContents=(if(portPresets.isEmpty()) "port_presets = []\n" else "")+contents+
            BeaconPreferences.write(values,OperatingPreferences.fields)+BeaconPreferences.write(values,FeaturePreferences.fields.sortedBy {it.key.substringBefore('.')})+portPresets.joinToString("") {
                "\n[[port_presets]]\n"+BeaconPreferences.write(it,PortPreferences.fields,sections=false)
            }
        val temp = Files.createTempFile(root, ".settings-", ".tmp")
        try {
            Files.writeString(temp, completeContents)
            try { Files.move(temp, path, ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp, path, REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temp) }
    }
}

data class ServerPreset(val name: String, val host: String, val port: Int) {
    val endpoint: String get() = if(':' in host && !host.startsWith('[')) "[$host]:$port" else "$host:$port"
}

object AprsServers {
    // Exact ordering and defaults from APRSERJa's aprs_servers.json, not a popularity claim.
    val presets: List<ServerPreset> = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream("/aprs_servers.json")).bufferedReader().use { it.readText() }
    ).jsonObject.getValue("servers").jsonArray.map {
        val item = it.jsonObject
        ServerPreset(item.getValue("name").jsonPrimitive.content, item.getValue("host").jsonPrimitive.content,
            item.getValue("port").jsonPrimitive.int)
    }
    val defaultEndpoint get() = presets.first().endpoint
}
