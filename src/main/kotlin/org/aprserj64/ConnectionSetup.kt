// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

internal object ConnectionSetup {
 fun normalizedCall(call:String):String {
  val value=call.trim().uppercase()
  val ssid=value.substringAfter('-',"").toIntOrNull()
  return if(ssid==null) value else value.substringBefore('-')+if(ssid==0) "" else "-$ssid"
 }
 fun problem(call:String,passcode:String):String? {
  val identity=call.trim().uppercase()
  val base=identity.substringBefore('-')
  if(identity.isBlank()) return "Enter your amateur-radio callsign and optional SSID, for example N1ABC-13."
  if(!Regex("[A-Z0-9]{3,6}(-[0-9]{1,2})?").matches(identity) ||
     !base.any {it.isLetter()} || !base.any {it.isDigit()} ||
     ('-' in identity && identity.substringAfter('-').toInt() !in 0..15) || base in listOf("N0CALL","NOCALL"))
   return "Enter your own callsign with an optional SSID from 0 to 15, for example N1ABC-13."
  val pass=passcode.trim()
  if(pass.isBlank()) return "Enter your APRS-IS passcode, or explicitly choose Receive only."
  if(pass!="-1" && (!pass.matches(Regex("[0-9]{1,5}")) || pass.toInt() !in 0..32767))
   return "The APRS-IS passcode must be a number from 0 to 32767. It is not your website password."
  return null
 }
 fun rejected(response:String,call:String)=Regex("^#\\s*logresp\\s+${Regex.escape(call)}\\s+unverified\\b",RegexOption.IGNORE_CASE).containsMatchIn(response)
}
