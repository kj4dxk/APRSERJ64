// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import org.aprsfoundation.core.AprsConnectionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val readableTime=DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss z").withZone(ZoneId.systemDefault())
internal fun displayTime(epochMillis:Long)=readableTime.format(Instant.ofEpochMilli(epochMillis))

internal fun connectionLabel(state:AprsConnectionState,practice:Boolean=false):String = if(practice && state!=AprsConnectionState.STOPPED)
 "Practice connected — local traffic only" else when(state) {
 AprsConnectionState.VERIFIED->"APRS-IS connected and verified"
 AprsConnectionState.RECEIVING->"APRS-IS connected — receive only"
 AprsConnectionState.STOPPED->"APRS-IS not connected"
 AprsConnectionState.CONNECTING->"Connecting to APRS-IS…"
 AprsConnectionState.RECONNECTING->"Reconnecting to APRS-IS…"
}
internal fun beaconDetail(detail:String)=when(detail) {
 "Off"->"Automatic beaconing disabled"
 "Fixed"->"Fixed position available"
 "GPS fix"->"Current GPS position available"
 "Waiting for GPS fix"->"Waiting for a valid, current GPS position"
 "Waiting for connection","Waiting for an output port"->"Waiting for verified APRS-IS or a radio output"
 "Sent"->"Beacon accepted by an output"
 else->detail
}
