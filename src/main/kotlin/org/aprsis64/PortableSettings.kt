// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.AtomicMoveNotSupportedException
import java.util.Properties
import kotlinx.serialization.json.*

/** Everything belongs to the EXE's folder, even when a shortcut has another working directory. */
object PortableHome {
    fun resolve(): Path = Path.of(System.getenv("APRSIS64_HOME")
        ?: System.getProperty("aprsis64.home") ?: System.getProperty("user.dir")).toAbsolutePath().normalize()
    fun prepare(root: Path) {
        listOf("data", "cache", "cache/tmp", "logs").forEach { Files.createDirectories(root.resolve(it)) }
    }
}

class SettingsStore(private val root: Path) {
    val values = Properties().apply {
        val path = root.resolve("settings.properties")
        if (Files.exists(path)) Files.newInputStream(path).use { load(it) }
    }
    init {
        val defaults = mapOf("callsign" to "", "passcode" to "", "endpoint" to AprsServers.defaultEndpoint,
            "latitude" to "28.536725", "longitude" to "-81.242932", "autoConnect" to "true")
        defaults.forEach { (key, value) -> if(!values.containsKey(key)) values.setProperty(key, value) }
        if(!Files.exists(root.resolve("settings.properties"))) save()
    }
    fun save() {
        Files.createDirectories(root)
        val temp = Files.createTempFile(root, ".settings-", ".tmp")
        try {
            Files.newOutputStream(temp).use { values.store(it, "APRSIS64 portable settings") }
            try { Files.move(temp, root.resolve("settings.properties"), ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp, root.resolve("settings.properties"), REPLACE_EXISTING) }
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
