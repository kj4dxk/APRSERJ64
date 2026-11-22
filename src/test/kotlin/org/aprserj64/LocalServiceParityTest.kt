// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.net.*
import java.nio.file.Files
import kotlinx.coroutines.*
import org.aprsfoundation.core.*
import kotlin.test.*
class LocalServiceParityTest {
 @Test fun localQueriesAndChatAckReturnToClientDespiteExcludingFilter():Unit=runBlocking {
  val home=Files.createTempDirectory("local-query-test");PortableHome.prepare(home)
  val endpoint=ServerSocket(0,5,InetAddress.getLoopbackAddress()).use {"127.0.0.1:${it.localPort}"}
  val settings=SettingsStore(home);settings.values.setProperty("callsign","N0CALL");settings.values.setProperty("autoConnect","false");settings.values.setProperty("beacon.enabled","false")
  settings.ports=listOf(PortPreferences.defaults("IS-Server").apply {setProperty("name","Local");setProperty("endpoint",endpoint);setProperty("service_input","true");setProperty("messages","true")});settings.entities=listOf(java.util.Properties().apply {BeaconPreferences.entityFields.forEach {setProperty(it.key,it.default)};setProperty("name","TESTOBJ")})
  settings.save()
  AppModel(home).use {model->
   model.settings.setProperty("queries.minimum_seconds","0")
   val socket=withTimeout(5000){var value:Socket?=null;while(value==null){try {value=Socket("127.0.0.1",endpoint.substringAfter(':').toInt())}catch(_:Exception){delay(20)}};value}
   socket.use {client->
    client.soTimeout=6000;val reader=client.getInputStream().bufferedReader();val output=client.getOutputStream()
    fun write(s:String){output.write((s+"\r\n").toByteArray());output.flush()}
    assertTrue(reader.readLine().startsWith("#"))
    write("user N1CALL pass ${ServicePort.passcode("N1CALL")} vers test 1 filter b/OTHER -b/N0CALL")
    assertTrue(reader.readLine().contains("verified"))
    write("N1CALL>APRS::N0CALL   :?APRSS")
    val status=reader.readLine();assertTrue(status.startsWith("N0CALL>APRS"),status);assertTrue(status.substringAfter(':').startsWith('>'),status)
    val ports=reader.readLine();assertTrue(ports.contains("Ports:"),ports)
    write("N1CALL>APRS::N0CALL   :hello{77")
    var ack="";repeat(3){if(!ack.contains("ack77"))ack=reader.readLine()}
    assertTrue(ack.contains("ack77"),ack)
    withTimeout(3000){while(model.database.rows("N0CALL","N1CALL").none {it.body=="hello"})delay(20)}
    write("N1CALL>APRS::N0CALL   :?APRSO TESTOBJ")
    val objectPacket=reader.readLine();assertTrue(objectPacket.contains(";TESTOBJ  *"),objectPacket)
    assertTrue(reader.readLine().contains("1 Objects Transmitted"))
    write("N1CALL>APRS::N0CALL   :?APRSH N2CALL")
    assertTrue(reader.readLine().contains("N2CALL not heard on RF"))
    write("N1CALL>APRS::N0CALL   :?APRSM")
    assertTrue(reader.readLine().contains("0 Messages Requeued"))
   }
  }
 }
 @Test fun serverDeliversAddressedMessagesAndAssociatedPositionsOutsideSubscription():Unit=runBlocking {
  val parent=CoroutineScope(SupervisorJob()+Dispatchers.IO)
  val endpoint=ServerSocket(0,5,InetAddress.getLoopbackAddress()).use {"127.0.0.1:${it.localPort}"}
  val config=PortConfig(PortPreferences.defaults("IS-Server").apply {setProperty("endpoint",endpoint);setProperty("service_input","true")})
  val received=kotlinx.coroutines.channels.Channel<RadioPacket>(4)
  val service=ServicePort(config,parent,{received.send(it)},{})
  val job=parent.launch {service.run()}
  try {
   val socket=withTimeout(5000){var s:Socket?=null;while(s==null){try{s=Socket("127.0.0.1",endpoint.substringAfter(':').toInt())}catch(_:Exception){delay(20)}};s}
   socket.use {client->
    client.soTimeout=3000;val reader=client.getInputStream().bufferedReader();reader.readLine()
    client.getOutputStream().write("user N1CALL pass ${ServicePort.passcode("N1CALL")} filter b/OTHER -b/N0CALL\r\nN2CALL>APRS:!2830.00N/08112.00W>\r\n".toByteArray());client.getOutputStream().flush()
    assertTrue(reader.readLine().contains("verified"));assertEquals("N2CALL",withTimeout(3000){received.receive()}.source)
    assertTrue(service.send(RadioPacket.parse("N0CALL>APRS::N2CALL   :hello{1")!!));assertTrue(reader.readLine().contains("hello"))
    assertTrue(service.send(RadioPacket.parse("N0CALL>APRS:!2830.00N/08112.00W>")!!));assertTrue(reader.readLine().contains("2830.00"))
   }
  }finally{service.close();job.cancelAndJoin();parent.cancel()}
 }
}
