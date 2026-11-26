package org.aprserj64

import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.*

class PeerGroupTest {
    @Test fun missedHeartbeatsExpireThePrimaryLease() {
        val seats=listOf("N1AAA","N2BBB")
        val sockets=seats.map {DatagramSocket(InetSocketAddress("127.0.0.1",0))}
        val endpoints=seats.zip(sockets.map {InetSocketAddress("127.0.0.1",it.localPort)}).toMap()
        sockets.forEach {it.close()}
        val key=ByteArray(32){42}
        val root=Files.createTempDirectory("peer-timeout")
        val frozen=java.util.concurrent.atomic.AtomicLong()
        PeerGroup(PeerGroupConfig("LOCAL",seats[0],key,endpoints,true),root.resolve("one"),200,{frozen.get().takeIf {it!=0L} ?: System.nanoTime()}).use {
            PeerGroup(PeerGroupConfig("LOCAL",seats[1],key,endpoints,true),root.resolve("two"),200).use {second->
                fun waitFor(primary:String){
                    val until=System.nanoTime()+5_000_000_000
                    while(second.view.value.primary!=primary && System.nanoTime()<until)Thread.sleep(20)
                    assertEquals(primary,second.view.value.primary)
                }
                waitFor(seats[0])
                frozen.set(System.nanoTime())
                waitFor(seats[1])
                frozen.set(0)
                waitFor(seats[0])
            }
        }
    }
    @Test fun electionHandoverAndPartitionHistoryConvergeOverUdp(){
        val calls=listOf("N1AAA","N2BBB","N3CCC")
        val reserved=calls.map {DatagramSocket(InetSocketAddress("127.0.0.1",0))}
        val addresses=calls.zip(reserved.map {InetSocketAddress("127.0.0.1",it.localPort)}).toMap()
        reserved.forEach {it.close()}
        val root=Files.createTempDirectory("peer-group")
        val live=mutableMapOf<String,PeerGroup>()
        fun start(call:String):PeerGroup {
            val service=PeerGroup(PeerGroupConfig("LOCAL",call,ByteArray(32){it.toByte()},addresses,true),root.resolve(call+".sqlite"),150)
            live[call]=service
            return service
        }
        fun await(label:String,done:()->Boolean){
            val until=System.nanoTime()+10_000_000_000L
            while(!done() && System.nanoTime()<until)Thread.sleep(25)
            assertTrue(done(),label+" "+live.mapValues {it.value.view.value to it.value.issue.value})
        }
        try {
            start(calls[0]).apply {append("join");append("text","first partition")}
            live.remove(calls[0])!!.close()
            val b=start(calls[1]);b.append("join");b.append("text","second partition")
            val c=start(calls[2])
            await("Initial relay") {c.view.value.primary==calls[1]}
            start(calls[0])
            await("Union after healing") {live.values.all {it.view.value.primary==calls[0] && it.view.value.history.size==2 && it.view.value.members.size==2}}
            assertEquals(2,c.view.value.history.distinctBy {it.author to it.sequence}.size)
            live.remove(calls[0])!!.close()
            await("Handover") {c.view.value.primary==calls[1]}
            c.append("join");c.append("text","after handover")
            await("Delivery through successor") {b.view.value.history.size==3}
            start(calls[0])
            await("Restart catch-up") {live.values.all {it.view.value.history==c.view.value.history && it.view.value.members.size==3}}
        } finally {live.values.forEach {it.close()}}
    }
}
