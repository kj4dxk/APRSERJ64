// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.net.URI
import java.util.Properties
import kotlinx.serialization.json.*
import org.tomlj.Toml
import org.tomlj.TomlTable

object ServicePreferences {
    internal const val TILE_RESOLUTION_KEY = "map.tile_resolution"
    internal val tileResolutionOptions = mapOf(
        "windows" to "Follow Windows",
        "1x" to "Standard (1×)",
        "2x" to "High resolution (2×)"
    )
    private val seed=Toml.parse(checkNotNull(javaClass.getResourceAsStream("/service_defaults.toml")).bufferedReader().use {it.readText()})
    fun defaultText(path: String)=requireNotNull(seed.getString(path))
    val fields=listOf("map","services").flatMap { section -> seed.getTable(section)!!.keySet().map { key ->
        val path="$section.$key"; val value=seed.get(path)!!
        if(path==TILE_RESOLUTION_KEY) {
            return@map BeaconField(path,"Tile resolution",value.toString(),choices=tileResolutionOptions.keys.toList())
        }
        BeaconField(path,key.replace('_',' ')+(if(key=="cache_megabytes") " (restart)" else ""),value.toString(),when(value) {
            is Boolean -> SettingKind.BOOLEAN; is Long -> SettingKind.INTEGER; is Double -> SettingKind.DECIMAL
            else -> SettingKind.TEXT
        })
    } }
    fun load(values: Properties, document: TomlTable?) = BeaconPreferences.loadFields(values,document,fields)
    fun writeMap(values: Properties)=BeaconPreferences.write(values,fields.filter {it.key.startsWith("map.")},false)
    fun writeServices(values: Properties)="\n[services]\n"+BeaconPreferences.write(values,fields.filter {it.key.startsWith("services.")},false)
    fun validate(values: Properties) {
        fun decimal(key: String)=values.getProperty("map.$key").toDouble()
        require(decimal("minimum_zoom") in 0.0..24.0 && decimal("maximum_zoom") in decimal("minimum_zoom")..24.0)
        require(decimal("initial_zoom") in decimal("minimum_zoom")..decimal("maximum_zoom"))
        require(values.getProperty("map.tile_size").toInt() in listOf(256,512))
        require(values.getProperty(TILE_RESOLUTION_KEY,"windows") in tileResolutionOptions) {"Choose a valid tile resolution"}
        require(values.getProperty("map.cache_megabytes").toInt() in 1..4096)
        require(values.getProperty("map.maximum_fps").toInt() in 1..60)
        require(decimal("base_opacity") in 0.0..1.0 && decimal("overlay_opacity") in 0.0..1.0)
        fun url(value: String) { val uri=URI(value.replace("{z}","0").replace("{x}","0").replace("{y}","0")); require(uri.scheme in listOf("http","https") && uri.host!=null) }
        fields.filter {it.key.endsWith("url")}.forEach {field ->
            val value=values.getProperty(field.key)
            if(value.isNotBlank()) url(value)
        }
        require(values.getProperty("map.attribution").isNotBlank() && values.getProperty("map.attribution_url").isNotBlank())
        listOf("tile_url","overlay_url","underlay_url").forEach {key ->
            val enabled=when(key) {"tile_url" -> "online_enabled"; "overlay_url" -> "overlay_enabled"; else -> "underlay_enabled"}
            if(values.getProperty("map.$enabled").toBooleanStrict()) {
                val template=values.getProperty("map.$key")
                require(listOf("{x}","{y}","{z}").all {it in template}) {"map.$key must contain {z}, {x} and {y}"}
            }
        }
        val highDpi=values.getProperty("map.project_preset_hidpi_url","")
        require(highDpi.isBlank() || listOf("{z}","{x}","{y}").all {it in highDpi}) {
            "map.project_preset_hidpi_url must contain {z}, {x} and {y}"
        }
    }

    internal fun preferDetailedTiles(resolution: String, density: Float) = when (resolution) {
        "1x" -> false
        "2x" -> true
        else -> density > 1f
    }

    internal fun projectTileVariants(values: Properties): TileVariants? {
        if (!values.getProperty("map.online_enabled").toBoolean() && !values.getProperty("offline.enabled").toBoolean()) return null
        val standard = values.getProperty("map.tile_url")
        if (standard != values.getProperty("map.project_preset_url")) return null
        val highDpi = values.getProperty("map.project_preset_hidpi_url").orEmpty()
        if (highDpi.isBlank() || highDpi == standard) return null
        // A replaced preset needs its own explicit high-density endpoint.
        if (standard != defaultText("map.project_preset_url") &&
            highDpi == defaultText("map.project_preset_hidpi_url")) return null
        return TileVariants(TileVariant(standard,256),TileVariant(highDpi,512))
    }
    fun style(values: Properties, includeBaseAndOverlay: Boolean = true): String {
        fun p(key: String)=values.getProperty("map.$key")
        val sourceNames=buildList {
            if(p("underlay_enabled").toBoolean()) add("underlay")
            if(includeBaseAndOverlay && p("online_enabled").toBoolean()) add("base")
            if(includeBaseAndOverlay && p("overlay_enabled").toBoolean()) add("overlay")
        }
        return buildJsonObject {
            put("version",8); put("name",p("source_name"))
            putJsonObject("sources") {sourceNames.forEach { name -> putJsonObject(name) {
                put("type","raster"); putJsonArray("tiles") {add(p(if(name=="base") "tile_url" else "${name}_url"))}
                put("tileSize",p("tile_size").toInt()); put("minzoom",p("minimum_zoom").toInt()); put("maxzoom",p("maximum_zoom").toInt())
                put("attribution",p("attribution"))
            } } }
            putJsonArray("layers") {
                addJsonObject {put("id","background");put("type","background");putJsonObject("paint") {put("background-color","#d9e3df")}}
                sourceNames.forEach {name -> addJsonObject {put("id",name);put("type","raster");put("source",name);putJsonObject("paint") {
                    put("raster-opacity",if(name=="underlay") 1.0 else p("${name}_opacity").toDouble())
                }} }
            }
        }.toString()
    }
}
