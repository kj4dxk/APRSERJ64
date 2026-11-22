// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import org.aprsfoundation.core.*
import java.util.Properties
import java.util.Locale

internal fun stationLabel(s:AprsStation,p:Properties,weather:AprsWeather?):String {
 val nickname=TacticalPreferences.aliases(p)[s.callsign]
 val title=if(nickname.isNullOrBlank()) s.callsign else "$nickname (${s.callsign})"
 if(s.ambiguityDigits>0)return "$title\nApproximate (${s.ambiguityDigits}-digit ambiguity)"
 val details=AprsSpatial.details(s);val parts=mutableListOf<String>()
 if(p.getProperty("tactical.label_speed").toBoolean())details.speedKnots?.let {parts.add("%.0f km/h".format(Locale.US,it*1.852))}
 if(p.getProperty("tactical.label_altitude").toBoolean())details.altitudeMeters?.let {parts.add("%.0f m MSL".format(Locale.US,it))}
 if(p.getProperty("tactical.label_weather").toBoolean()) weather?.temperatureC?.let {parts.add("%.1f °C".format(Locale.US,it))}
 return title+if(parts.isEmpty()) "" else "\n"+parts.joinToString(" · ")
}
