// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.runtime.*
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration
import kotlinx.coroutines.*
import org.aprsfoundation.core.AprsEndpoint

@Composable internal fun NetworkPreferences(model: AppModel, values: MutableMap<String,String>) {
    val adapters = remember { runCatching(::networkAdapters).getOrDefault(emptyList()) }
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<Pair<String,List<String>>>()) }
    NetworkResource.entries.forEach { resource ->
        val selection = values[resource.key] ?: AUTO_ADAPTER
        val choices = listOf(AUTO_ADAPTER to "Automatic", SYSTEM_ADAPTER to "System default") +
            adapters.map { it.id to it.label }
        val available = if (choices.any { it.first == selection }) choices else choices + (selection to "Unavailable ($selection)")
        ChoiceMenu("${resource.label} adapter", selection, available,
            help="Automatic tries the system route and then other adapters if needed. Choose an adapter to use it for this resource.") {
            values[resource.key] = it
        }
    }
    HelpButton("Test the configured servers on each network adapter without logging in or transmitting.",
        enabled = !testing && !model.demo.value, onClick = {
            val draft = values.toMap()
            val ports = model.ports.map { it.entriesMap() }
            results = emptyList()
            testing = true
            scope.launch {
                try {
                    val targets = mutableListOf<Pair<String,()->List<String>>>()
                    fun tcp(label: String, key: String?, resource: NetworkResource) {
                        if (key.isNullOrBlank()) return
                        targets += "$label · $key" to { model.network.probeTcp(AprsEndpoint.parse(key), resource) }
                    }
                    tcp("APRS-IS", draft["endpoint"], NetworkResource.APRS_IS)
                    ports.filter { it["enabled"]=="true" && it["transport"]=="tcp" &&
                        (it["protocol"]=="CWOP" || it["protocol"] !in PortPreferences.services) }.forEach {
                        tcp(it["name"].orEmpty(), it["endpoint"], NetworkResource.TCP_DEVICES)
                    }
                    when(draft["position.source"]) {
                        "gps_tcp" -> tcp("GPS",draft["position.gps_endpoint"],NetworkResource.TCP_DEVICES)
                        "gpsd" -> tcp("gpsd",draft["position.gpsd_endpoint"],NetworkResource.TCP_DEVICES)
                    }
                    if (draft["offline.enabled"]!="true") {
                        for ((label,flag,key) in listOf(
                            Triple("Map tiles","map.online_enabled","map.tile_url"),
                            Triple("Map overlay","map.overlay_enabled","map.overlay_url"),
                            Triple("Map underlay","map.underlay_enabled","map.underlay_url"),
                            Triple("Weather radar","weather.radar_enabled","weather.radar_url")
                        )) {
                            val source=draft[key].orEmpty()
                            if(draft[flag]!="true" || source.isBlank()) continue
                            targets += label to {
                                val zoom = draft["map.initial_zoom"]?.toDoubleOrNull()?.toInt()?.coerceIn(0,18) ?: 0
                                val lat=draft["latitude"]?.toDoubleOrNull()?.coerceIn(-85.0,85.0) ?: 0.0
                                val lon=draft["longitude"]?.toDoubleOrNull()?.coerceIn(-180.0,180.0) ?: 0.0
                                val tile=TilePlan.region(lat,lon,lat,lon,zoom,zoom,1).single()
                                model.network.probeTile(model.offline.tileRequest(source,tile))
                            }
                        }
                    }
                    fun web(label: String, url: String?) {
                        if (url.isNullOrBlank()) return
                        targets += label to {
                            val request=HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(5))
                                .header("User-Agent","APRSERJ64/${AppVersion.value}").build()
                            model.network.probeWeb(request)
                        }
                    }
                    if(draft["weather.enabled"]=="true") web("Weather alerts",draft["weather.alerts_url"]
                        ?.replace("{lat}",draft["position.latitude"].orEmpty())
                        ?.replace("{lon}",draft["position.longitude"].orEmpty()))
                    if(draft["satellites.enabled"]=="true") web("Satellite elements",draft["satellites.url"])
                    for ((label, action) in targets) {
                        val result = runInterruptible(Dispatchers.IO) {
                            runCatching(action).getOrElse { listOf("Unable to test this endpoint") }
                        }
                        results = results + (label to result)
                    }
                } finally { testing = false }
            }
        }) { HelpText(if (testing) "Testing adapters…" else "Test network adapters") }
    results.forEach { (label, lines) ->
        HelpText(label)
        lines.forEach { HelpText(it) }
    }
}
