// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.aprsfoundation.core.*



/** A separate login-only session: never saves settings, resumes a connection or sends beacons. */
internal object ConnectionProbe {
 suspend fun test(call:String,passcode:String,endpoint:String,timeoutMillis:Long=15000,network:NetworkRoutes=NetworkRoutes()):String {
  ConnectionSetup.problem(call,passcode)?.let {return it}
  val address=runCatching {AprsEndpoint.parse(endpoint)}.getOrNull() ?: return "Invalid server address. Choose a server or enter host:port."
  val identity=ConnectionSetup.normalizedCall(call)
  val client=network.aprsClient()
  try {
   client.start(AprsIsConfig(identity,passcode.trim(),address.host,address.port,0.0,0.0,radiusKm=1,
    application="APRSERJ64",applicationVersion=AppVersion.value))
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
 fun subject(call:String)="APRS-IS passcode request — ${ConnectionSetup.normalizedCall(call).ifBlank {"[YOUR CALLSIGN]"}}"
 fun body(call:String)="""Hello,

My licensed name is: [ENTER YOUR FULL LICENSED NAME HERE]
My callsign is: ${ConnectionSetup.normalizedCall(call).ifBlank {"[YOUR CALLSIGN]"}}

I am using APRSERJ64 and would like assistance obtaining my APRS-IS passcode.
I found your address in the APRSISCE/32 wiki. I understand that your passcode service supports APRSISCE/32 users; if my request is outside that scope, please direct me to the appropriate support.

Thank you."""
 fun copyRequest(email:String,call:String)="To: $email\nSubject: ${subject(call)}\n\n${body(call)}"
}
