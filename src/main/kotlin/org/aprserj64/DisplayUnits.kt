// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.util.Locale
import java.util.Properties

internal enum class DisplayUnits(
    val distanceUnit: String,
    private val metersPerDistanceUnit: Double,
    private val altitudeUnit: String,
    private val metersPerAltitudeUnit: Double,
    private val speedUnit: String
) {
    IMPERIAL("mi", 1609.344, "ft", 0.3048, "mph"),
    METRIC("km", 1000.0, "m", 1.0, "km/h");

    fun distance(meters: Double, decimals: Int = 1) =
        format(meters / metersPerDistanceUnit, distanceUnit, decimals)

    fun altitude(meters: Double) = format(meters / metersPerAltitudeUnit, altitudeUnit, 0)
    fun speed(knots: Double) = format(knots * 1852 / metersPerDistanceUnit, speedUnit, 0)

    private fun format(value: Double, unit: String, decimals: Int) =
        "%.${decimals}f %s".format(Locale.US, value, unit)

    companion object {
        fun from(settings: Properties) =
            if (settings.getProperty("display.units") == "metric") METRIC else IMPERIAL
    }
}
