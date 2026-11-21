// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*

internal class StandardQueries(private val model:AppModel,private val scope:CoroutineScope,private val radio:()->RadioPorts?) {
 private val last=linkedMapOf<String,Long>()
 fun receive(port:String,line:String):Boolean {
  val message=AprsMessages.parse(line)?:return false
  if(message.recipient!=model.account.value || message.acknowledgement!=null)return false
  val query=message.text.trim().uppercase()
  if(query !in setOf("?APRSP","?APRSS","?APRST","?PING?","?ABOUT","?APRSV","?VER","?APRSD","?APRSL","?APRSH","?APRSM","?APRSO","?IGATE","?CPU","?APRS?"))return false
  if(model.demo.value || !model.settings.getProperty("queries.enabled").toBoolean() || message.sender==model.account.value)return true
  val now=System.currentTimeMillis()
  synchronized(last){last.entries.removeAll {now-it.value>3600000};if(last.size>=1000 || now-(last[message.sender]?:0)<model.settings.getProperty("queries.minimum_seconds").toLong()*1000)return true;last[message.sender]=now}
  scope.launch {
   val output=radio()?:return@launch
   suspend fun reply(text:String){AprsMessages.split(text.take(300)).forEach {output.answer(port,AprsMessages.unnumbered(model.account.value,message.sender,it))}}
   runCatching {
    val max=model.settings.getProperty("queries.maximum_results").toInt()
    when(query) {
     "?APRSP"->{val p=model.beaconState.value.position?.takeIf {it.current};if(p==null)reply("No current position") else output.answer(port,BeaconPackets.position(model.account.value,p,BeaconFormat()))}
     "?APRSS"->reply(model.settings.getProperty("status.text","APRSIS64").ifBlank {"APRSIS64"})
     "?APRST","?PING?"->reply("Received via $port: "+line.substringBefore(':'))
     "?ABOUT","?APRSV","?VER"->reply("APRSIS64 0.7.0")
     "?APRSD","?APRSL","?APRSH"->reply("Heard RF: "+output.heardCalls().take(max).joinToString(" ").ifBlank {"none"})
     "?APRSM"->reply("Message-capable: "+model.views.stations.value.values.filter {it.messageCapable}.take(max).joinToString(" "){it.call}.ifBlank {"none"})
     "?APRSO"->{val entities=model.entities.filter {it.getProperty("enabled").toBoolean() && it.getProperty("alive").toBoolean()}.take(max)
      reply("Objects: "+entities.joinToString(" "){it.getProperty("name")}.ifBlank {"none"})}
     "?IGATE"->reply("IGate RF heard=${output.heardCalls().size} ports=${model.portStatuses.value.size} IS=${model.state.value}")
     "?CPU"->reply("Java ${Runtime.version().feature()} CPUs=${Runtime.getRuntime().availableProcessors()} memoryMB=${(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576}")
     else->reply("?APRSP ?APRSS ?APRST ?PING? ?VER ?APRSD ?APRSL ?APRSH ?APRSM ?APRSO ?IGATE ?CPU ?DX")
    }
   }.onFailure {model.error.value="Unable to answer APRS query"}
  }
  return true
 }
}
