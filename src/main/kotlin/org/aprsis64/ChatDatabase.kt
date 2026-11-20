// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.osmand.shared.aprs.*

data class ChatRow(val id: Long, val peer: String, val body: String, val outgoing: Boolean,
                   val time: Long, val status: String, val unread: Boolean, val attempts: Int)

/** Prototype JDBC adapter for the shared chat contract. Never stores login passcodes. */
class ChatDatabase(path: Path, private val now: () -> Long = System::currentTimeMillis) : ChatRepository, AutoCloseable {
    private val mutex = Mutex()
    private val db: Connection
    val revision = MutableStateFlow(0L)
    init {
        Class.forName("org.sqlite.JDBC")
        path.toAbsolutePath().parent.toFile().mkdirs()
        db = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
        db.createStatement().use { s ->
            s.execute("PRAGMA busy_timeout=5000")
            s.execute("PRAGMA journal_mode=WAL")
            s.execute("CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY, account TEXT NOT NULL, peer TEXT NOT NULL, body TEXT NOT NULL, outgoing INTEGER NOT NULL, wire_id TEXT, time INTEGER NOT NULL, status TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, last_send INTEGER NOT NULL DEFAULT 0, unread INTEGER NOT NULL DEFAULT 0)")
            s.execute("CREATE INDEX IF NOT EXISTS conversation ON messages(account,peer,time,id)")
            s.execute("CREATE INDEX IF NOT EXISTS outbox ON messages(account,outgoing,status)")
            s.execute("PRAGMA user_version=1")
        }
    }
    private suspend fun <T> access(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
    private fun execute(sql: String, vararg args: Any?): Int = db.prepareStatement(sql).use { s ->
        args.forEachIndexed { i, arg -> s.setObject(i + 1, arg) }; s.executeUpdate()
    }
    private fun changed() { revision.value++ }
    suspend fun enqueue(account: String, peer: String, text: String) = access {
        require(AprsProtocol.validCallsign(account) && AprsProtocol.validCallsign(peer) && account != peer)
        require(AprsMessages.validText(text))
        var wire: String
        do {
            wire = UUID.randomUUID().toString().replace("-", "").take(5).uppercase()
            val exists = db.prepareStatement("SELECT 1 FROM messages WHERE account=? AND peer=? AND outgoing=1 AND wire_id=? AND time>?").use { s ->
                s.setString(1, account); s.setString(2, peer); s.setString(3, wire); s.setLong(4, now()-86_400_000)
                s.executeQuery().use { it.next() }
            }
        } while (exists)
        execute("INSERT INTO messages(account,peer,body,outgoing,wire_id,time,status) VALUES(?,?,?,1,?,?,'QUEUED')", account, peer, text, wire, now())
        changed()
    }
    override suspend fun receive(account: String, message: AprsMessage) = access {
        require(message.recipient == account)
        db.autoCommit = false
        try {
            listOfNotNull(message.acknowledgement, message.replyAck).forEach { id ->
                execute("UPDATE messages SET status=? WHERE account=? AND peer=? AND outgoing=1 AND wire_id=? AND time>?",
                    if(message.rejected && id == message.acknowledgement) "REJECTED" else "ACKNOWLEDGED", account, message.sender, id, now()-86_400_000)
            }
            if (message.acknowledgement == null) {
                val duplicate = message.id != null && db.prepareStatement("SELECT 1 FROM messages WHERE account=? AND peer=? AND outgoing=0 AND wire_id=? AND body=? AND time>?").use { s ->
                    s.setString(1,account); s.setString(2,message.sender); s.setString(3,message.id); s.setString(4,message.text); s.setLong(5,now()-86_400_000)
                    s.executeQuery().use { it.next() }
                }
                if (!duplicate) execute("INSERT INTO messages(account,peer,body,outgoing,wire_id,time,status,unread) VALUES(?,?,?,0,?,?,'RECEIVED',1)",
                    account,message.sender,message.text,message.id,now())
            }
            db.commit(); changed()
        } catch (e: Exception) { db.rollback(); throw e } finally { db.autoCommit = true }
    }
    override suspend fun pending(account: String): List<PendingChat> = access {
        val expired = execute("UPDATE messages SET status='NO_ACK' WHERE account=? AND outgoing=1 AND status IN ('QUEUED','SENDING','WAITING') AND (time<? OR (attempts>=5 AND last_send<?))", account, now()-900_000, now()-30_000)
        if(expired > 0) changed()
        db.prepareStatement("SELECT id,peer,body,wire_id FROM messages WHERE account=? AND outgoing=1 AND status IN ('QUEUED','SENDING','WAITING') AND attempts<5 AND last_send<=? ORDER BY id LIMIT 1").use { s ->
            s.setString(1,account); s.setLong(2,now()-30_000)
            s.executeQuery().use { r -> buildList { while(r.next()) add(PendingChat(r.getLong(1),r.getString(2),r.getString(3),r.getString(4))) } }
        }
    }
    override suspend fun attempted(row: Long) = access {
        execute("UPDATE messages SET status='SENDING',attempts=attempts+1,last_send=? WHERE id=? AND status IN ('QUEUED','SENDING','WAITING')",now(),row); changed()
    }
    override suspend fun transmitted(row: Long, success: Boolean) = access {
        execute("UPDATE messages SET status=? WHERE id=? AND status='SENDING'",if(success) "WAITING" else "QUEUED",row); changed()
    }
    suspend fun rows(account: String, peer: String? = null): List<ChatRow> = access {
        db.prepareStatement("SELECT id,peer,body,outgoing,time,status,unread,attempts FROM messages WHERE account=?" + (if(peer != null) " AND peer=?" else "") + " ORDER BY time,id").use { s ->
            s.setString(1,account); if(peer != null) s.setString(2,peer)
            s.executeQuery().use { r -> buildList { while(r.next()) add(ChatRow(r.getLong(1),r.getString(2),r.getString(3),r.getInt(4)==1,r.getLong(5),r.getString(6),r.getInt(7)==1,r.getInt(8))) } }
        }
    }
    suspend fun markRead(account: String, peer: String) = access {
        if(execute("UPDATE messages SET unread=0 WHERE account=? AND peer=? AND unread=1",account,peer)>0) changed()
    }
    override fun close() { db.close() }
}
