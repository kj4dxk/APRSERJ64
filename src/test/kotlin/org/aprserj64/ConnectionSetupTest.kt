// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.Files
import java.net.ServerSocket
import java.net.InetAddress
import kotlinx.coroutines.*
import kotlin.test.*

class ConnectionSetupTest {
 @Test fun missingAndMalformedCredentialsRequireCorrection() {
  for(call in listOf("","NOCALL","N0CALL-1","ABC","1234","N1ABC-16","N1ABC-A")) assertNotNull(ConnectionSetup.problem(call,"12345"),call)
  for(pass in listOf("","password","40000","-2","12 34")) assertNotNull(ConnectionSetup.problem("N1ABC-13",pass))
  assertNull(ConnectionSetup.problem("n1abc-13","12345"))
  assertNull(ConnectionSetup.problem("N1ABC","-1"))
  assertEquals("N1ABC",ConnectionSetup.normalizedCall(" n1abc-0 "))
  assertEquals("N1ABC-1",ConnectionSetup.normalizedCall("n1abc-01"))
 }
 @Test fun firstRunPromptsWithoutConnecting() {
  val home=Files.createTempDirectory("aprs-startup-empty");PortableHome.prepare(home)
  AppModel(home).use {model->
   assertEquals("true",model.settings.getProperty("autoConnect"))
   model.connectSaved()
   assertNotNull(model.connectionSetupIssue.value)
   assertEquals(org.aprsfoundation.core.AprsConnectionState.STOPPED,model.state.value)
  }
 }
 @Test fun serverRejectionPromptsAndDisconnectPreservesStartupPreference():Unit=runBlocking {
  val home=Files.createTempDirectory("aprs-startup-rejected");PortableHome.prepare(home)
  ServerSocket(0,1,InetAddress.getLoopbackAddress()).use {server->
   val serving=launch(Dispatchers.IO) {server.accept().use {socket->
    socket.soTimeout=5000
    val writer=socket.getOutputStream().bufferedWriter()
    writer.write("# test server\r\n");writer.flush()
    socket.getInputStream().bufferedReader().readLine()
    writer.write("# logresp N1ABC-13 unverified, server TEST\r\n");writer.flush()
    delay(1500)
   }}
   AppModel(home).use {model->
    assertTrue(model.connect("N1ABC-13","12345","127.0.0.1:${server.localPort}",35.0,-77.0))
    withTimeout(5000) {while(model.connectionSetupIssue.value==null) delay(20)}
    assertTrue(model.connectionSetupIssue.value!!.contains("did not verify"))
    model.disconnect()
    assertEquals("true",model.settings.getProperty("autoConnect"))
   }
   serving.join()
  }
 }
 @Test fun rejectionMatchesOnlyTheCurrentLogin() {
  assertTrue(ConnectionSetup.rejected("# logresp N1ABC unverified, server TEST","N1ABC"))
  assertFalse(ConnectionSetup.rejected("# logresp N1ABC verified, server TEST","N1ABC"))
  assertFalse(ConnectionSetup.rejected("# logresp N2ABC unverified, server TEST","N1ABC"))
 }
}
