// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import org.aprsfoundation.core.*
import java.util.Properties

internal fun stationLabel(s:AprsStation,p:Properties,weather:AprsWeather?):String {
 val nickname=TacticalPreferences.aliases(p)[s.callsign]
 val title=if(nickname.isNullOrBlank()) s.callsign else "$nickname (${s.callsign})"
 if(s.ambiguityDigits>0)return "$title\nApproximate (${s.ambiguityDigits}-digit ambiguity)"
 val details=AprsSpatial.details(s);val parts=mutableListOf<String>()
 val display=DisplayUnits.from(p)
 if(p.getProperty("tactical.label_speed").toBoolean())details.speedKnots?.let {parts.add(display.speed(it))}
 if(p.getProperty("tactical.label_altitude").toBoolean())details.altitudeMeters?.let {parts.add("${display.altitude(it)} MSL")}
 if(p.getProperty("tactical.label_weather").toBoolean()) weather?.temperatureC?.let {val units=WeatherDisplay.from(p);parts.add("${units.temperature(it)} ${units.temperatureUnit}")}
 return title+if(parts.isEmpty()) "" else "\n"+parts.joinToString(" · ")
}
