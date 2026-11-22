// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.osmand.shared.aprs.*
import java.net.URI
import java.net.URLEncoder

/** A separate login-only session: never saves settings, resumes a connection or sends beacons. */
internal object ConnectionProbe {
 suspend fun test(call:String,passcode:String,endpoint:String,timeoutMillis:Long=15000):String {
  ConnectionSetup.problem(call,passcode)?.let {return it}
  val address=runCatching {AprsEndpoint.parse(endpoint)}.getOrNull() ?: return "Invalid server address. Choose a server or enter host:port."
  val identity=ConnectionSetup.normalizedCall(call)
  val client=AprsIsClient()
  try {
   client.start(AprsIsConfig(identity,passcode.trim(),address.host,address.port,0.0,0.0,radiusKm=1,
    application="APRSIS64",applicationVersion=AppVersion.value))
   val pattern=Regex("^#\\s*logresp\\s+${Regex.escape(identity)}\\s+",RegexOption.IGNORE_CASE)
   val replies=withTimeoutOrNull(timeoutMillis) {client.serverMessages.first {lines->lines.any {pattern.containsMatchIn(it)}}}
    ?: return "No login result from the server. Check the server address and Internet connection, then try again. Credentials were not verified."
   val reply=replies.last {pattern.containsMatchIn(it)}
   return when {
    AprsProtocol.verifiedLogin(reply,identity)->"Authenticated: APRS-IS verified this callsign and passcode."
    passcode.trim()=="-1"->"Connected in receive-only mode. This login is not authenticated for transmission."
    else->"Not authenticated: APRS-IS rejected this callsign/passcode combination."
   }
  } finally {client.stop()}
 }
}

internal object PasscodeRequest {
 fun formattedDraft(email:String,call:String):String {
  mailto(email,call) // Validate recipient before placing it in a mail header.
  fun b64(text:String)=java.util.Base64.getMimeEncoder(76,"\r\n".toByteArray()).encodeToString(text.toByteArray(Charsets.UTF_8))
  val plain=body(call)
  val html="<html><body>"+plain.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
   .replace("[ENTER YOUR FULL LICENSED NAME HERE]","<strong>[ENTER YOUR FULL LICENSED NAME HERE]</strong>")
   .replace("\n","<br>\r\n")+"</body></html>"
  val encodedSubject=java.util.Base64.getEncoder().encodeToString(subject(call).toByteArray(Charsets.UTF_8))
  return "X-Unsent: 1\r\nTo: $email\r\nSubject: =?UTF-8?B?$encodedSubject?=\r\nMIME-Version: 1.0\r\nContent-Type: multipart/alternative; boundary=\"APRSIS64_REQUEST\"\r\n\r\n"+
   "--APRSIS64_REQUEST\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n${b64(plain)}\r\n"+
   "--APRSIS64_REQUEST\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n${b64(html)}\r\n--APRSIS64_REQUEST--\r\n"
 }
 fun subject(call:String)="APRS-IS passcode request — ${ConnectionSetup.normalizedCall(call).ifBlank {"[YOUR CALLSIGN]"}}"
 fun body(call:String)="""Hello,

My licensed name is: [ENTER YOUR FULL LICENSED NAME HERE]
My callsign is: ${ConnectionSetup.normalizedCall(call).ifBlank {"[YOUR CALLSIGN]"}}

I am using APRSIS64 and would like assistance obtaining my APRS-IS passcode.
I found your address in the APRSISCE/32 wiki. I understand that your passcode service supports APRSISCE/32 users; if my request is outside that scope, please direct me to the appropriate support.

Thank you."""
 fun mailto(email:String,call:String):URI {
  require(Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matches(email))
  fun encode(text:String)=URLEncoder.encode(text,Charsets.UTF_8).replace("+","%20")
  return URI("mailto:$email?subject=${encode(subject(call))}&body=${encode(body(call))}")
 }
}
