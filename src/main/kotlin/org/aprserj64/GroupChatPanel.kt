package org.aprserj64

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.aprsfoundation.core.*

private val announcementGroup = Regex("[A-Z0-9_-]{1,20}")

internal fun groupIn(row:ChatRow,server:String):String? {
    if(row.outgoing && row.peer!=server) return null
    val group = when {
        !row.outgoing && row.body.startsWith("N:") -> row.body.substringAfter("N:").substringBefore(' ')
        row.body.startsWith("CQ ",ignoreCase=true) -> row.body.split(' ',limit=3).takeIf {it.size==3}?.get(1)
        else -> null
    }
    return group?.uppercase()?.takeIf(announcementGroup::matches)
}
@Composable internal fun GroupChatPanel(model:AppModel,account:String,rows:List<ChatRow>,initialGroup:String,stationMenu:(String)->List<ContextMenuItem>,close:()->Unit) {
    val server=model.settings.getProperty("groups.server")
    var group by remember(initialGroup) {mutableStateOf(initialGroup)}
    var body by remember(group){mutableStateOf("")}
    val entries by model.groups.entries.collectAsState()
    val independent by model.peerGroup.collectAsState()
    var useIndependent by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    val validGroup=group.matches(Regex("[A-Z0-9_-]{1,20}"))
    fun send(command:String,leaveAfter:Boolean=false) {
        if(busy)return
        busy=true
        scope.launch {try {
            val commands=if(leaveAfter) listOf(command,"U "+group) else listOf(command)
            model.database.enqueueParts(account,server,commands,internetOnly=true)
            if(command.startsWith("CQ "))body=""
            val saved=names(model.settings.getProperty("groups.names"))
            if(command.startsWith("J ") && group !in saved)model.updateFeature("groups.names",encodedNames(saved+group))
            if(leaveAfter || command.startsWith("U "))model.updateFeature("groups.names",encodedNames(saved-group))
        }
            catch(_:Exception){model.error.value="Unable to queue the group command"} finally {busy=false}}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HelpTextButton("Return to messages",onClick=close){HelpText("Back to messages")}
        independent?.let {service->
            HelpTextButton("Switch group service",onClick={useIndependent=!useIndependent}){HelpText(if(useIndependent) "Open "+server else "Open "+model.settings.getProperty("groups.peer_name"))}
            if(useIndependent){
                val state by service.view.collectAsState()
                val issue by service.issue.collectAsState()
                HelpText(model.settings.getProperty("groups.peer_name")+" · relay "+(state.primary ?: "waiting"))
                HelpText("Members: "+state.members.sorted().joinToString(", "))
                val member=account in state.members
                HelpTextButton("Change your group membership",onClick={service.append(if(member)"leave" else "join")}){HelpText(if(member)"Leave" else "Join")}
                state.history.forEach {event->ContextMenuArea(items={stationMenu(event.author)}){HelpText(event.author+" · "+event.body)}}
                HelpField("Independent group message",body,{body=it},label={HelpText("Message")})
                HelpTextButton("Send to independent group",enabled=member && body.isNotBlank() && body.length<=300,onClick={runCatching {service.append("text",body);body=""}.onFailure {model.error.value="Unable to submit group message"}}){HelpText("Send to group")}
                if(issue.isNotEmpty())HelpText(issue)
                return@Column
            }
        }
        HelpText("Group chat · "+server+" · Internet only")
        HelpField("Announcement group name",group,{group=it.uppercase()},label={HelpText("Group")})
        HelpTextButton("APRS Thursday group",onClick={group="HOTG"}){HelpText("APRS Thursday · HOTG")}
        val membership=entries.firstOrNull {it.group==group}
        val now by produceState(System.currentTimeMillis()) {while(true){value=System.currentTimeMillis();kotlinx.coroutines.delay(60_000)}}
        if(membership?.state=="Confirmed member") {
            val minutesLeft=(membership.time+12*60*60_000L-now)/60_000
            HelpText(if(minutesLeft>0) "Confirmed member · estimated expiry in "+minutesLeft/60+"h "+minutesLeft%60+"m" else "Confirmation expired · request the membership list")
        } else HelpText(membership?.state ?: "Membership unconfirmed")
        if(server=="CQSRVR") HelpText("CQSRVR expires membership after 12 hours of inactivity. CQ announcements are limited to one per group every 30 minutes.")
        val ready=!busy && AprsProtocol.validCallsign(account)
        for((action,label) in listOf("?" to "Discover groups","L" to "Membership list","J" to "Join","U" to "Leave")) {
            val named=action in listOf("J","U")
            HelpTextButton(label,enabled=ready && (!named || validGroup),onClick={send(AprsServices.group(action,if(named)group else ""))}){HelpText(label)}
        }
        val messages=rows.filter {groupIn(it,server)==group || it.peer==server && groupIn(it,server)==null}.takeLast(100)
        val unread=messages.filter {it.unread}
        LaunchedEffect(account,unread){unread.groupBy {it.peer}.forEach {(peer,seen)->model.database.markDisplayed(account,peer,seen.map {it.id})}}
        messages.forEach {row->
            val sender=if(row.outgoing)account else row.peer
            ContextMenuArea(items={stationMenu(sender).map {action->ContextMenuItem(action.label){close();action.onClick()}}}){
                HelpText(sender+" · "+row.body,color=conversationColor(row.unread,row.outgoing))
            }
        }
        HelpField("Group message",body,{body=it},label={HelpText("Message")})
        HelpText(body.length.toString()+"/"+(63-group.length))
        val command=runCatching {AprsServices.group("CQ",group,body)}.getOrNull()
        val allowed=server!="CQSRVR" || rows.none {it.outgoing && groupIn(it,server)==group && now-it.time<1_800_000}
        val canSend=ready && validGroup && command!=null && body.isNotBlank() && allowed
        HelpTextButton("Send a group message",enabled=canSend,onClick={command?.let {send(it)}}){HelpText("Send to group")}
        HelpTextButton("Queue the announcement followed by a leave request",enabled=canSend,onClick={command?.let {send(it,true)}}){HelpText("Check in and leave")}
    }
}
