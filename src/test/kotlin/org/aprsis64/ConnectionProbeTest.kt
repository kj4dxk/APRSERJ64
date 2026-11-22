// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.net.ServerSocket
import java.net.InetAddress
import java.net.URLDecoder
import java.util.Base64
import kotlinx.coroutines.*
import kotlin.test.*

class ConnectionProbeTest {
 @Test fun probeReportsAuthenticationAndOnlySendsLogin():Unit=runBlocking {
  for(verified in listOf(true,false)) ServerSocket(0,1,InetAddress.getLoopbackAddress()).use {server->
   val received=java.util.concurrent.CopyOnWriteArrayList<String>()
   val service=launch(Dispatchers.IO) {server.accept().use {socket->
    socket.soTimeout=3000
    val writer=socket.getOutputStream().bufferedWriter();val reader=socket.getInputStream().bufferedReader()
    writer.write("# test\r\n");writer.flush()
    received.add(reader.readLine())
    writer.write("# logresp N1ABC-13 ${if(verified) "verified" else "unverified"}, server TEST\r\n");writer.flush()
    runCatching {while(true) received.add(reader.readLine()?:break)}
   }}
   val result=ConnectionProbe.test("N1ABC-13","12345","127.0.0.1:${server.localPort}",5000)
   assertTrue(result.startsWith(if(verified) "Authenticated:" else "Not authenticated:"),result)
   service.join()
   assertEquals(1,received.size)
   assertTrue(received.single().startsWith("user N1ABC-13 "))
  }
 }
 @Test fun noServerResponseIsNotReportedAsBadCredentials():Unit=runBlocking {
  ServerSocket(0,1,InetAddress.getLoopbackAddress()).use {server->
   val service=launch(Dispatchers.IO) {server.accept().use {delay(400)}}
   val result=ConnectionProbe.test("N1ABC-13","12345","127.0.0.1:${server.localPort}",200)
   assertTrue(result.startsWith("No login result"))
   service.join()
  }
 }
 @Test fun emailDraftPreservesRecipientPlusAndEscapesFields() {
  val address="APRSISCE+owner@groups.io"
  val uri=PasscodeRequest.mailto(address,"n1abc-13")
  assertTrue(uri.toString().startsWith("mailto:$address?"))
  val decoded=URLDecoder.decode(uri.rawSchemeSpecificPart.substringAfter('?'),Charsets.UTF_8)
  assertTrue(decoded.contains("N1ABC-13"));assertTrue(decoded.contains("FULL LICENSED NAME"))
  assertTrue(decoded.contains("APRSIS64"))
  assertFailsWith<IllegalArgumentException> {PasscodeRequest.mailto("bad\r\nBcc: other@example.org","N1ABC")}
  val eml=PasscodeRequest.formattedDraft(address,"N1ABC-13")
  assertTrue(eml.startsWith("X-Unsent: 1\r\n"))
  val html64=eml.substringAfter("Content-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n").substringBefore("\r\n--APRSIS64_REQUEST")
  val html=String(Base64.getMimeDecoder().decode(html64),Charsets.UTF_8)
  assertTrue(html.contains("<strong>[ENTER YOUR FULL LICENSED NAME HERE]</strong>"))
  assertTrue(html.contains("N1ABC-13"))
 }
}
