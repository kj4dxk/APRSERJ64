// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.util.Locale
import java.util.Properties
import org.aprsfoundation.core.AprsWeather

internal enum class WeatherDisplay(val temperatureUnit: String, val windUnit: String, val pressureUnit: String, val rainUnit: String) {
    METRIC("°C","km/h","hPa","mm"),
    US("°F","mph","inHg","in");

    private fun number(value: Double?, decimals: Int, scale: Double=1.0, offset: Double=0.0) =
        value?.let {String.format(Locale.getDefault(),"%.${decimals}f",it*scale+offset)} ?: "—"

    fun temperature(celsius: Double?)=if(this==US) number(celsius,1,1.8,32.0) else number(celsius,1)
    fun wind(kph: Double?)=number(kph,1,if(this==US) 1/1.609344 else 1.0)
    fun pressure(hpa: Double?)=if(this==US) number(hpa,2,0.029529983) else number(hpa,1)
    fun rain(mm: Double?)=if(this==US) number(mm,2,1/25.4) else number(mm,1)

    fun report(w: AprsWeather) =
        "${temperature(w.temperatureC)} $temperatureUnit · wind ${w.windDegrees ?: "—"}° ${wind(w.windKph)} $windUnit · " +
        "gust ${wind(w.gustKph)} $windUnit · RH ${w.humidity ?: "—"}% · ${pressure(w.pressureHpa)} $pressureUnit · " +
        "rain 1h ${rain(w.rainHourMm)} $rainUnit / 24h ${rain(w.rainDayMm)} $rainUnit"

    companion object {
        fun from(settings: Properties) = when (settings.getProperty("weather.units")) {
            "metric" -> METRIC
            "us" -> US
            else -> if (DisplayUnits.from(settings) == DisplayUnits.IMPERIAL) US else METRIC
        }
    }
}

internal fun weatherUnitLabel(value: String) = when (value) {
    "metric" -> "Metric"
    "us" -> "Imperial"
    else -> "Follow display units"
}
