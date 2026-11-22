// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.util.Properties
import net.osmand.shared.aprs.AprsFilters
import org.tomlj.Toml

object OperatingPreferences {
    fun filterTerms(values:Properties):String=listOf(values.getProperty("operating.custom_filter"),
        if(values.getProperty("legacyweather.enabled","false").toBoolean()) values.getProperty("legacyweather.filter","g/NWS*") else "",
        if(values.getProperty("tactical.qru_enabled","false").toBoolean()) "g/"+values.getProperty("tactical.qru_address") else "").filter {it.isNotBlank()}.joinToString(" ")
    val fields=listOf(
        BeaconField("operating.filter_mode","APRS-IS filter mode","range",choices=listOf("range","moving","custom")),
        BeaconField("operating.custom_filter","Additional / custom server filter",""),
        BeaconField("operating.monitor_capacity","Packets kept in monitor","1000",SettingKind.INTEGER),
        BeaconField("operating.feed_enabled","Share received packets with local apps","false",SettingKind.BOOLEAN),
        BeaconField("operating.feed_port","Local receive-only TNC2 TCP feed port","14581",SettingKind.INTEGER),
        BeaconField("operating.satellite_aliases","Satellite callsign prefixes (one per line)","[\"RS0ISS\",\"NA1SS\",\"ARISS\",\"APRSAT\"]",SettingKind.STRINGS)
    )
    fun validate(values: Properties) {
        AprsFilters.compose("N0CALL-13",values.getProperty("latitude").toDouble(),values.getProperty("longitude").toDouble(),
            values.getProperty("radiusKm").toInt(),values.getProperty("operating.filter_mode"),filterTerms(values))
        require(values.getProperty("operating.monitor_capacity").toInt() in 100..10000)
        require(values.getProperty("operating.feed_port").toInt() in 1024..65535)
        require(names(values.getProperty("operating.satellite_aliases")).all {it.matches(Regex("[A-Z0-9-]{1,9}"))})
    }
}
object PortPresets {
    fun defaults(): List<Properties> {
        val doc=Toml.parse(checkNotNull(javaClass.getResourceAsStream("/port_presets.toml")).bufferedReader().use {it.readText()})
        val entries=doc.getArray("port_presets")!!
        return (0 until entries.size()).map {PortPreferences.load(entries.getTable(it))}
    }
}
