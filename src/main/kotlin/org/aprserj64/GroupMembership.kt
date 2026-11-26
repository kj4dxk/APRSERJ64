// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.nio.file.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.aprsfoundation.core.*

data class Membership(val group:String,val state:String,val time:Long,val evidence:String)
/** Confirmations come only from addressed replies by the configured server. */
class GroupMembership(private val home:Path,private val setting:(String)->String) {
 val entries=MutableStateFlow<List<Membership>>(emptyList());val replies=MutableStateFlow<List<String>>(emptyList())
 private var listUntil=0L;private var key=""
 @Synchronized fun activate(account:String) {
  val current="$account-${setting("groups.server")}";if(key!=current){key=current;entries.value=load();replies.value=emptyList();listUntil=0}
 }
 @Synchronized fun observe(account:String,direction:String,line:String) {
  val m=AprsMessages.parse(line)?:return;val now=System.currentTimeMillis();val server=setting("groups.server")
  activate(account)
  if(direction=="TX" && m.sender==account && m.recipient==server){
   val p=m.text.split(' ',limit=3);if(p[0]=="L"){
    listUntil=now+60000
    entries.value.filter {it.state=="Confirmed member"}.forEach {put(it.group,"Previously confirmed; awaiting list",it.time,it.evidence)}
   }
   if(p[0] in listOf("J","U","K","CQ") && p.size>1 && (p[0]=="U" || entries.value.none {it.group==p[1].uppercase() && it.state=="Confirmed member"}))put(p[1],if(p[0]=="U")"Leave requested" else "Join requested",now,m.text)
  }
  if(direction!="RX" || m.sender!=server || m.recipient!=account || m.acknowledgement!=null)return
  replies.value=(replies.value+m.text).takeLast(30)
  Regex("(?i)^Now Monitoring ([A-Z0-9_-]{1,20})$").matchEntire(m.text)?.let {put(it.groupValues[1],"Confirmed member",now,m.text)}
  Regex("(?i)^Message has been sent to ([A-Z0-9_-]{1,20})$").matchEntire(m.text)?.let {put(it.groupValues[1],"Confirmed member",now,m.text)}
  Regex("(?i)^Removed from ([A-Z0-9_-]{1,20})$").matchEntire(m.text)?.let {put(it.groupValues[1],"Not a member",now,m.text)}
  Regex("(?i)^([A-Z0-9_-]{1,20}) Removed From Group$").matchEntire(m.text)?.let {put(it.groupValues[1],"Not a member",now,m.text)}
  if(now<listUntil)Regex("(?:^|[ ,:])([A-Za-z0-9_-]{1,20})\\(([0-9]+)\\)").findAll(m.text).forEach {put(it.groupValues[1],"Confirmed member",now,m.text)}
  Regex("(?i)^(?:joined|joining|keeping alive|listening to)\\s+(?:group\\s+)?([A-Z0-9_-]{1,20})").find(m.text)?.let {put(it.groupValues[1],"Confirmed member",now,m.text)}
  Regex("(?i)^(?:unjoined|left|removed from|not a member of)\\s+(?:group\\s+)?([A-Z0-9_-]{1,20})").find(m.text)?.let {put(it.groupValues[1],"Not a member",now,m.text)}
 }
 private fun file()=home.resolve("data/groups-${key.replace(Regex("[^A-Z0-9-]"),"_")}.json")
 private fun load()=runCatching {Json.parseToJsonElement(Files.readString(file())).jsonArray.take(200).map {val o=it.jsonObject;Membership(o["group"]!!.jsonPrimitive.content,o["state"]!!.jsonPrimitive.content,o["time"]!!.jsonPrimitive.long,o["evidence"]!!.jsonPrimitive.content)}}.getOrDefault(emptyList())
 private fun put(group:String,state:String,time:Long,evidence:String){
  val call=group.uppercase();entries.value=(entries.value.filterNot {it.group==call}+Membership(call,state,time,evidence)).takeLast(200)
  runCatching{Files.createDirectories(file().parent);val temp=Files.createTempFile(file().parent,"groups-",".tmp");try{Files.writeString(temp,JsonArray(entries.value.map {buildJsonObject{put("group",it.group);put("state",it.state);put("time",it.time);put("evidence",it.evidence)}}).toString());Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING)}finally{Files.deleteIfExists(temp)}}
 }
}
