package org.aprserj64

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.aprsfoundation.core.*

private val smsAddress=Regex("(?:[0-9]{10,12}|[a-zA-Z][a-zA-Z0-9]{0,15})")
internal fun smsPeer(row:ChatRow):String?=row.body.substringBefore(' ').takeIf {
    row.peer=="SMS" && it.startsWith('@') && ' ' in row.body && smsAddress.matches(it.drop(1))
}?.drop(1)
internal fun smsPackets(number:String,body:String):List<String> {
    require(smsAddress.matches(number) && AprsMessages.validLongText(body))
    val prefix="@"+number+" "
    return body.chunked(67-prefix.length).map {prefix+it}
}

@Composable internal fun SmsPanel(account:String,model:AppModel,rows:List<ChatRow>,initial:String,close:()->Unit) {
    var number by remember(initial) {mutableStateOf(initial)}
    var alias by remember(number) {mutableStateOf("")}
    var draft by remember(number) {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val packets=runCatching {smsPackets(number,draft)}.getOrDefault(emptyList())
    val contacts=names(model.settings.getProperty("messaging.sms_contacts"))
    val thread=rows.filter {smsPeer(it)==number}
    val unread=thread.filter {it.unread}.map {it.id}
    LaunchedEffect(account,unread){model.database.markDisplayed(account,"SMS",unread)}
    fun aliasCommand(action:String){
        busy=true
        scope.launch {try {model.database.enqueueParts(account,"SMS",listOf("#alias #"+action+" "+alias+" "+number),internetOnly=true)}
            catch(_:Exception){model.error.value="Unable to queue alias request"} finally {busy=false}}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HelpTextButton("Return to direct APRS conversations",onClick=close){HelpText("Back to messages")}
        HelpText("SMS via APRS")
        HelpText("Phone numbers and text are public on APRS. The recipient must subscribe at aprs.wiki and may reply STOP to unsubscribe. A gateway ACK confirms APRS receipt only.")
        HelpField("US, Puerto Rico and Canada use 10 digits, Australia 11, UK 12. You can also use a registered gateway alias.",number,{number=it.trim()},label={HelpText("Phone number or alias")},singleLine=true)
        contacts.forEach {contact->HelpTextButton("Open this contact",onClick={number=contact}){HelpText(contact)}}
        if(smsAddress.matches(number)) {
            val saved=number in contacts
            HelpTextButton("Edit the saved contact list",onClick={
                model.updateFeature("messaging.sms_contacts",encodedNames(if(saved)contacts-number else contacts+number))
            }){HelpText(if(saved)"Remove contact" else "Save contact")}
        }
        if(number.matches(Regex("(?:[0-9]{10}|[0-9]{12})"))) {
            HelpField("Gateway alias for this phone number",alias,{alias=it.trim()},label={HelpText("Gateway alias")},singleLine=true)
            val canManage=!busy && AprsProtocol.validCallsign(account) && alias.matches(Regex("[A-Za-z][A-Za-z0-9]{0,15}"))
            HelpTextButton("Queue alias registration with the gateway",enabled=canManage,onClick={aliasCommand("add")}){HelpText("Register alias")}
            HelpTextButton("Queue alias removal with the gateway",enabled=canManage,onClick={aliasCommand("remove")}){HelpText("Remove alias")}
        }
        thread.forEach {row->HelpText((if(row.outgoing)"You: " else "Received: ")+row.body.substringAfter(' '));HelpText(row.status)}
        HelpField("SMS message body",draft,{draft=it},label={HelpText("Text message")})
        HelpText(draft.length.toString()+"/300 · "+(65-number.length)+" characters per packet · "+packets.size+" packets")
        HelpTextButton("Send through the SMS gateway",enabled=packets.isNotEmpty() && !busy && AprsProtocol.validCallsign(account),onClick={
            val submitted=draft
            busy=true
            scope.launch {try {model.database.enqueueParts(account,"SMS",packets);if(draft==submitted)draft=""}
                catch(_:Exception){model.error.value="Unable to queue SMS"} finally {busy=false}}
        }){HelpText("Send SMS")}
        HelpTextButton("Ask the gateway for missed messages",enabled=AprsProtocol.validCallsign(account),onClick={scope.launch {model.send("SMS","APRSM")}}){HelpText("Retrieve missed messages")}
    }
}
