// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*

class ChatDatabaseTest {
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
