// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import net.osmand.shared.aprs.*

/** A real loopback APRS-IS session. Demo packets can never reach an Internet server. */
class DemoServer : AutoCloseable {
    private val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    private val sockets = CopyOnWriteArrayList<Socket>()
    val port: Int get() = server.localPort
    init { thread(isDaemon=true, name="aprsis64-local-demo") {
        try { while(!server.isClosed) {
            val socket = server.accept(); sockets.add(socket)
            thread(isDaemon=true) { runCatching {
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    val writer = it.getOutputStream().bufferedWriter()
                    fun send(text: String) { writer.write(text); writer.flush() }
                    send("# APRSIS64 loopback demo\r\n")
                    val login = reader.readLine() ?: return@use
                    val account = login.split(' ').getOrNull(1) ?: return@use
                    send("# logresp $account verified, server LOCAL-DEMO\r\n")
                    send(AprsProtocol.beacon("N1CALL-7",AprsFix(28.536725,-81.242932,0),"Local demo station"))
                    send(AprsProtocol.beacon("N1CALL-8",AprsFix(28.55,-81.26,0),"Different SSID"))
                    send(AprsProtocol.beacon("N2CALL",AprsFix(28.52,-81.22,0),"Local demo station"))
                    send("N1CALL-8>APRS,TCPIP*:_11201000c180s010g015t075r001p010P020h65b10132\r\n")
                    send("N1CALL-7>APRS,TCPIP*::N1CALL-7 :PARM.Battery,Temperature,Voltage,Current,Signal\r\n")
                    send("N1CALL-7>APRS,TCPIP*::N1CALL-7 :UNIT.percent,C,V,A,dB\r\n")
                    for(sample in 1..8) send("N1CALL-7>APRS,TCPIP*:T#${sample.toString().padStart(3,'0')},${80+sample},025,120,010,090,10101010\r\n")
                    send("N1CALL-8>APRS,TCPIP*::BLN1     :Local demonstration bulletin\r\n")
                    val hello = AprsMessages.packet("N1CALL-7",account,"Hello! This chat stays on this PC.","D001")
                    send(hello); send(hello)
                    send(AprsMessages.packet("N1CALL-8",account,"A separate conversation for SSID 8.","D002"))
                    while(true) {
                        val line = reader.readLine() ?: break
                        val msg = AprsMessages.parse(line) ?: continue
                        val id=msg.id
                        if(msg.acknowledgement == null && id != null) {
                            send(AprsMessages.ack(msg.recipient,account,id))
                            send(AprsMessages.packet(msg.recipient,account,"Received: ${msg.text}".take(67),"R${id.take(4)}"))
                        }
                    }
                }
            }; sockets.remove(socket) }
        } } catch (_: Exception) { /* Expected when closed. */ }
    } }
    override fun close() { server.close(); sockets.forEach { runCatching { it.close() } } }
}
