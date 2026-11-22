// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*

internal class StandardQueries(private val model:AppModel,private val scope:CoroutineScope,private val radio:()->RadioPorts?) {
 private val last=linkedMapOf<String,Long>()
 private val objectSent=mutableMapOf<String,Long>()
 fun receive(port:String,line:String):Boolean {
  val packet=RadioPacket.parse(line)?.content()?:return false
  val generic=packet.payload=="?APRS?"
  val message=if(generic)AprsMessage(packet.source,model.account.value,"?APRSP") else AprsMessages.parse(packet.line())?:return false
  if(message.recipient!=model.account.value || message.acknowledgement!=null)return false
  val text=message.text.trim();val query=text.substringBefore(' ').uppercase();val argument=text.substringAfter(' ',"").trim()
  if(query !in setOf("?APRSP","?APRSS","?APRST","?PING?","?ABOUT","?APRSV","?VER","?APRSD","?APRSL","?APRSH","?APRSM","?APRSO","?IGATE","?CPU","?APRS?"))return false
  if(model.demo.value || !model.settings.getProperty("queries.enabled").toBoolean() || message.sender==model.account.value)return true
  val now=System.currentTimeMillis()
  synchronized(last){last.entries.removeAll {now-it.value>3600000};if(last.size>=1000 || now-(last[message.sender]?:0)<model.settings.getProperty("queries.minimum_seconds").toLong()*1000)return true;last[message.sender]=now}
  scope.launch {
   val output=radio()?:return@launch
   suspend fun send(value:String)=output.answer(port,value,message.sender)
   suspend fun reply(value:String){AprsMessages.split(value.take(300)).forEach {send(AprsMessages.unnumbered(model.account.value,message.sender,it))}}
   try {
    val max=model.settings.getProperty("queries.maximum_results").toInt()
    val config=BeaconConfiguration(model.settings,model.entities)
    when(query) {
     "?APRSP"->{val p=model.beaconState.value.position?.takeIf {it.current};if(p==null){if(!generic)reply("No current position")}else send(BeaconPackets.position(model.account.value,p,config.format))}
     "?APRSS"->{send(BeaconPackets.status(model.account.value,model.settings.getProperty("status.text","APRSERJ64").ifBlank {"APRSERJ64"},now,model.settings.getProperty("status.timestamp").toBoolean()));reply("Ports: "+model.portStatuses.value.take(max).joinToString("; "){it.name+" "+it.detail}.ifBlank {"none"})}
     "?APRST","?PING?"->reply("Received via $port: "+line.substringBefore(':'))
     "?ABOUT","?APRSV","?VER"->reply("APRSERJ64 ${AppVersion.value}")
     "?APRSD"->reply("Directs: "+output.directCalls().take(max).joinToString(" ").ifBlank {"none"})
     "?APRSL"->reply("Locals: "+output.localCalls().take(max).joinToString(" ").ifBlank {"none"})
     "?APRSH"->if(argument=="*")reply("Heard: "+output.heardCalls().take(max).joinToString(" ").ifBlank {"none"}) else reply(output.heardReport(argument.ifBlank {message.sender}.uppercase()))
     "?APRSM"->reply("${model.database.requeuePending(model.account.value,message.sender)} Messages Requeued")
     "?APRSO"->{
      var sent=0
      for(e in model.entities.filter {it.getProperty("enabled").toBoolean() && it.getProperty("alive").toBoolean() && (argument.isEmpty() || it.getProperty("name")==argument)}.take(max)) {
       val name=e.getProperty("name")
       if(now-(objectSent[name]?:0)<60000)continue
       val service=output.isService(port)
       val isInternet=port=="APRS-IS" || service
       if(isInternet && !e.getProperty("internet").toBoolean() || !isInternet && !e.getProperty("radio").toBoolean())continue
       val selected=names(e.getProperty("ports"));if(!isInternet && selected.isNotEmpty() && port !in selected)continue
       val position=if(e.getProperty("source")=="follow")model.beaconState.value.position?.takeIf {it.current}?:continue else config.entityPosition(e,now)
       val value=BeaconPackets.entity(model.account.value,name,e.getProperty("kind")=="item",true,position,config.entityFormat(e),ObjectGeometry.comment(e))
       if(if(service)send(value) else output.broadcast(value,isInternet,!isInternet,if(isInternet)emptyList() else listOf(port),e.getProperty("rf_path").ifBlank {null})){sent++;objectSent[name]=now;delay(model.settings.getProperty("tactical.qru_spacing_seconds").toLong()*1000)}
      }
      reply("$sent Objects Transmitted")
     }
     "?IGATE"->reply("IGate RF heard=${output.heardCalls().size} ports=${model.portStatuses.value.size} IS=${model.state.value}")
     "?CPU"->reply("Java ${Runtime.version().feature()} CPUs=${Runtime.getRuntime().availableProcessors()} memoryMB=${(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576}")
     else->reply("?APRSP ?APRSS ?APRST ?PING? ?VER ?APRSD ?APRSL ?APRSH ?APRSM ?APRSO ?IGATE ?CPU ?DX")
    }
   }catch(c:CancellationException){throw c}catch(_:Exception){model.error.value="Unable to answer APRS query"}
  }
  return true
 }
}
