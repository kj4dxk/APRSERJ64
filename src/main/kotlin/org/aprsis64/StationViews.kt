// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import kotlinx.coroutines.flow.MutableStateFlow
import net.osmand.shared.aprs.*

data class StationMetadata(val call:String,val time:Long,val rf:Boolean,val messageCapable:Boolean,val platform:String,val frequency:AprsFrequency?)
class StationViews {
 val stations=MutableStateFlow<Map<String,StationMetadata>>(emptyMap())
 @Synchronized fun receive(port:String,line:String,time:Long=System.currentTimeMillis()) {
  val p=RadioPacket.parse(line)?:return
  val s=AprsProtocol.parse(line,time)
  val call=s?.callsign?:p.source
  val previous=stations.value[call]
  val metadata=StationMetadata(call,time,port!="APRS-IS" || previous?.rf==true,
   StationCapabilities.messageCapable(p.payload) || previous?.messageCapable==true,
   if(p.payload.firstOrNull() in listOf('`','\'')) "Mic-E" else StationCapabilities.platform(p.destination),
   s?.let {StationCapabilities.frequency(it.comment)}?:previous?.frequency)
  stations.value=(stations.value.filterValues {time-it.time<86400000}+ (call to metadata)).entries.toList().takeLast(5000).associate {it.toPair()}
 }
}
