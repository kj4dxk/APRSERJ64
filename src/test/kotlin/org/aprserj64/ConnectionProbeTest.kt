// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.net.ServerSocket
import java.net.InetAddress


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
 @Test fun copiedRequestContainsRecipientSubjectAndEditableName() {
  val request=PasscodeRequest.copyRequest("APRSISCE+owner@groups.io","n1abc-13")
  assertTrue(request.startsWith("To: APRSISCE+owner@groups.io\nSubject: APRS-IS passcode request"))
  assertTrue(request.contains("N1ABC-13"))
  assertTrue(request.contains("[ENTER YOUR FULL LICENSED NAME HERE]"))
  assertTrue(request.contains("I am using APRSERJ64"))
  assertFalse(request.contains("mailto:"))
  assertTrue(PasscodeRequest.copyRequest("support@example.org","").contains("[YOUR CALLSIGN]"))
 }
}