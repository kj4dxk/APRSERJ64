// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

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
    fun resolve(): Path = Path.of(System.getenv("APRSIS64_HOME")
        ?: System.getProperty("aprsis64.home") ?: System.getProperty("user.dir")).toAbsolutePath().normalize()
    fun prepare(root: Path) {
        listOf("data", "cache", "cache/tmp", "logs").forEach { Files.createDirectories(root.resolve(it)) }
    }
}

class SettingsStore(private val root: Path) {
    private val path = root.resolve("settings.toml")
    val values = Properties().apply {
        if (Files.exists(path)) {
            val document = Toml.parse(path)
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
            setProperty("latitude", coordinate("map.latitude", 28.536725, -90.0..90.0))
            setProperty("longitude", coordinate("map.longitude", -81.242932, -180.0..180.0))
        } else {
            // One-time import. Leave the original intact for older EXEs and rollback.
            val legacy = root.resolve("settings.properties")
            if(Files.exists(legacy)) Files.newInputStream(legacy).use { load(it) }
        }
    }
    init {
        val defaults = mapOf("callsign" to "", "passcode" to "", "endpoint" to AprsServers.defaultEndpoint,
            "latitude" to "28.536725", "longitude" to "-81.242932", "autoConnect" to "true")
        defaults.forEach { (key, value) -> if(!values.containsKey(key)) values.setProperty(key, value) }
        if(!Files.exists(path)) save()
    }
    fun save() {
        Files.createDirectories(root)
        val address = AprsEndpoint.parse(values.getProperty("endpoint"))
        val latitude = values.getProperty("latitude").toDouble()
        val longitude = values.getProperty("longitude").toDouble()
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        val connect = values.getProperty("autoConnect").toBooleanStrict()
        // JSON's basic string escapes used here are also valid TOML basic-string escapes.
        fun quoted(value: String) = JsonPrimitive(value).toString()
        val contents = """
            # APRSIS64 portable settings
            [aprs]
            callsign = ${quoted(values.getProperty("callsign"))}
            passcode = ${quoted(values.getProperty("passcode"))}
            server = ${quoted(address.host)}
            port = ${address.port}
            connect_on_startup = $connect

            [map]
            latitude = $latitude
            longitude = $longitude
        """.trimIndent() + "\n"
        val temp = Files.createTempFile(root, ".settings-", ".tmp")
        try {
            Files.writeString(temp, contents)
            try { Files.move(temp, path, ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp, path, REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temp) }
    }
}

data class ServerPreset(val name: String, val host: String, val port: Int) {
    val endpoint: String get() = "$host:$port"
}

object AprsServers {
    // Exact ordering and defaults from APRSISOSM's aprs_servers.json, not a popularity claim.
    val presets: List<ServerPreset> = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream("/aprs_servers.json")).bufferedReader().use { it.readText() }
    ).jsonObject.getValue("servers").jsonArray.map {
        val item = it.jsonObject
        ServerPreset(item.getValue("name").jsonPrimitive.content, item.getValue("host").jsonPrimitive.content,
            item.getValue("port").jsonPrimitive.int)
    }
    val defaultEndpoint get() = presets.first().endpoint
}
