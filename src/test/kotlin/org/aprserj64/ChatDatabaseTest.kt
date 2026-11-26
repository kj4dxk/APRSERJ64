// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*
import org.aprsfoundation.core.*

class ChatDatabaseTest {
    @Test fun inboundMultipartOrderingAndConflicts():Unit=runBlocking {
        ChatDatabase(Files.createTempDirectory("multipart-rx").resolve("chats.sqlite")).use {db->
            suspend fun part(index:Int,body:String){db.receive("N0CALL",AprsMessage("N1CALL","N0CALL","("+index+"/3) "+body,"J2XY"+index))}
            part(3,"C");part(1,"A");part(3,"C");part(2,"B")
            val rows=db.rows("N0CALL")
            assertEquals(3,rows.size)
            assertTrue(rows.first().multipartId>0 && rows.all {it.multipartId==rows.first().multipartId})
            assertEquals("ABC",rows.sortedBy {it.wireId}.joinToString(""){it.body.substringAfter(") ")})
            part(2,"other")
            assertTrue(db.rows("N0CALL").all {it.multipartId==0L})
        }
    }
    @Test fun gatewayAnnouncementsOpenTheNamedGroup() {
        val row=ChatRow(1,"N1CALL-7","N:HOTG Orlando check-in",false,0,"RECEIVED",true,0)
        assertEquals("HOTG",groupIn(row,"ANSRVR"))
        assertEquals("HOTG",groupIn(row.copy(body="CQ HOTG Check-in"),"CQSRVR"))
        assertNull(groupIn(row.copy(body="N: bad group"),"ANSRVR"))
        assertNull(groupIn(row.copy(outgoing=true),"ANSRVR"))
        assertNull(groupIn(row.copy(body="CQ HOTG Check-in",outgoing=true),"ANSRVR"))
        assertEquals("HOTG",groupIn(row.copy(body="CQ HOTG Check-in",peer="ANSRVR",outgoing=true),"ANSRVR"))
    }
    @Test fun smsRepeatsDestinationAndGroupQueuePreservesRfPreferences():Unit=runBlocking {
        val number="2025550100"
        val body="X".repeat(300)
        val packets=smsPackets(number,body)
        assertEquals(6,packets.size)
        assertTrue(packets.all {it.startsWith("@"+number+" ") && it.length<=67})
        assertEquals(body,packets.joinToString(""){it.substringAfter(' ')})
        ChatDatabase(Files.createTempDirectory("group-route").resolve("chat.sqlite")).use {db->
            db.saveOptions("N0CALL","ANSRVR",ChatOptions("rf",true))
            db.enqueueParts("N0CALL","ANSRVR",listOf("CQ HOTG TEST"),internetOnly=true)
            assertEquals("is",db.pending("N0CALL").single().route)
            assertEquals("rf",db.options("N0CALL","ANSRVR").route)
        }
    }
    @Test fun multipartGroupsSurviveRestartWithoutJoiningSimultaneousMessages():Unit=runBlocking {
        val path=Files.createTempDirectory("multipart").resolve("chat.sqlite")
        val text="1234567890".repeat(18)
        ChatDatabase(path,{1234L}).use { db ->
            repeat(2){db.enqueue("N0CALL","N1CALL",text)}
            db.receive("N0CALL",AprsMessage("N1CALL","N0CALL","(1/3) ambiguous","A1"))
        }
        ChatDatabase(path).use { db ->
            val rows=db.rows("N0CALL")
            assertEquals(0L,rows.single {!it.outgoing}.multipartId)
            val groups=rows.filter {it.outgoing}.groupBy {it.multipartId}
            assertEquals(2,groups.size)
            groups.values.forEach {parts ->
                assertEquals(text,parts.joinToString(""){it.body.substringAfter(") ")})
                assertEquals(3,parts.map {it.wireId}.distinct().size)
            }
        }
    }
    @Test fun persistenceDedupAndAccounts(): Unit = runBlocking {
        val path=Files.createTempDirectory("aprserj64-test").resolve("chats.sqlite")
        ChatDatabase(path).use { db ->
            val incoming=AprsMessage("N1CALL-7","N0CALL-12","Hello","A71")
            db.receive("N0CALL-12",incoming); db.receive("N0CALL-12",incoming)
            db.receive("N0CALL-12",incoming.copy(sender="N1CALL-8"))
            db.receive("N0CALL-13",incoming.copy(recipient="N0CALL-13"))
            assertEquals(2,db.rows("N0CALL-12").size)
            db.markRead("N0CALL-12","N1CALL-7")
        }
        ChatDatabase(path).use { db ->
            assertFalse(db.rows("N0CALL-12","N1CALL-7").single().unread)
            assertTrue(db.rows("N0CALL-12","N1CALL-8").single().unread)
            assertEquals(1,db.rows("N0CALL-13").size)
        }
    }
    @Test fun ackRaceRetryAndExpiry(): Unit = runBlocking {
        var now=1_000_000L
        ChatDatabase(Files.createTempDirectory("aprserj64-test").resolve("chats.sqlite"),{now}).use { db ->
            db.enqueue("N0CALL-12","N1CALL-7","Hello")
            val p=db.pending("N0CALL-12").single(); db.attempted(p.row)
            db.receive("N0CALL-12",AprsMessage("N1CALL-7","N0CALL-12","",acknowledgement=p.wireId))
            db.transmitted(p.row,true)
            assertEquals("ACKNOWLEDGED",db.rows("N0CALL-12").single().status)
            db.enqueue("N0CALL-12","N1CALL-8","No ack")
            repeat(5) {
                val retry=db.pending("N0CALL-12").single(); db.attempted(retry.row); db.transmitted(retry.row,true)
                assertTrue(db.pending("N0CALL-12").isEmpty()); now+=30_001
            }
            assertTrue(db.pending("N0CALL-12").isEmpty())
            assertEquals("NO_ACK",db.rows("N0CALL-12","N1CALL-8").single().status)
            db.enqueue("N0CALL-12","N2CALL","Old queued")
            now+=900_001; assertTrue(db.pending("N0CALL-12").isEmpty())
            assertEquals("NO_ACK",db.rows("N0CALL-12","N2CALL").single().status)
        }
    }
    @Test fun newMessagesDoNotWaitForOlderAcknowledgments(): Unit = runBlocking {
        var now=1_000_000L
        val account="N0CALL-12"; val peer="N1CALL-7"
        ChatDatabase(Files.createTempDirectory("aprserj64-independent-chat").resolve("chats.sqlite"),{now}).use { db ->
            db.enqueue(account,peer,"First")
            val first=db.pending(account).single()
            db.attempted(first.row); db.transmitted(first.row,true)
            db.enqueue(account,peer,"Second")
            val second=db.pending(account).single()
            assertEquals("Second",second.body)
            db.attempted(second.row); db.transmitted(second.row,true)
            assertEquals(listOf("WAITING","WAITING"),db.rows(account).map {it.status})
            now+=30_001 // Both retries are due, but fresh text goes first.
            db.enqueue(account,peer,"Third")
            val third=db.pending(account).single()
            assertEquals("Third",third.body)
            db.attempted(third.row); db.transmitted(third.row,true)
            db.receive(account,AprsMessage(peer,account,"",acknowledgement=second.wireId))
            assertEquals(listOf("WAITING","ACKNOWLEDGED","WAITING"),db.rows(account).map {it.status})
            assertEquals(first.row,db.pending(account).single().row)
            assertEquals(3,setOf(first.wireId,second.wireId,third.wireId).size)
        }
    }
    @Test fun localTransportChatRoundTrip(): Unit = runBlocking {
        val path=Files.createTempDirectory("aprserj64-wire-test").resolve("chats.sqlite")
        val parent=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val client=AprsIsClient()
        ChatDatabase(path).use { db -> DemoServer().use { server ->
            val chat=ChatSession("N0CALL-12",client,db,parent)
            try {
                client.start(AprsIsConfig("N0CALL-12","12345","127.0.0.1",server.port,28.536725,-81.242932,application="APRSERJ64"))
                withTimeout(10_000) {while(db.rows("N0CALL-12").size<2) delay(30)}
                assertEquals(2,db.rows("N0CALL-12").size)
                db.enqueue("N0CALL-12","N1CALL-7","Windows test")
                withTimeout(10_000) {while(db.rows("N0CALL-12").none {it.outgoing&&it.status=="ACKNOWLEDGED"}) delay(30)}
                assertEquals(1,db.rows("N0CALL-12").count {it.outgoing})
                assertEquals(1,db.rows("N0CALL-12").single {it.outgoing}.attempts)
            } finally { chat.close(); client.stop(); parent.coroutineContext[Job]?.cancelAndJoin() }
        } }
        ChatDatabase(path).use { db -> assertEquals("ACKNOWLEDGED",db.rows("N0CALL-12").single {it.outgoing}.status) }
    }
}
