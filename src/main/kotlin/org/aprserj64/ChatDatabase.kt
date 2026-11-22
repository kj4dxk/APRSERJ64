// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import kotlinx.coroutines.runBlocking
import org.aprsfoundation.core.ChatSql
import org.aprsfoundation.core.ChatSqlRow
import org.aprsfoundation.core.ChatStore
import java.nio.file.Path
import java.sql.DriverManager

/** SQLite JDBC adapter. The core owns the schema and chat delivery transitions. */
class ChatDatabase private constructor(private val storage: JdbcChatSql, now: () -> Long) :
    ChatStore(storage, now), AutoCloseable {
    constructor(path: Path, now: () -> Long = System::currentTimeMillis) : this(JdbcChatSql(path), now)

    override fun close() = runBlocking { closeStorage(storage::close) }
}

private class JdbcChatSql(path: Path) : ChatSql {
    private val db = path.toAbsolutePath().let {
        Class.forName("org.sqlite.JDBC")
        it.parent.toFile().mkdirs()
        DriverManager.getConnection("jdbc:sqlite:$it")
    }

    init {
        db.createStatement().use {
            it.execute("PRAGMA busy_timeout=5000")
            it.execute("PRAGMA journal_mode=WAL")
        }
    }

    override fun execute(sql: String, vararg values: Any?): Int =
        db.prepareStatement(sql).use { statement ->
            values.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
            statement.executeUpdate()
        }

    override fun <T> query(sql: String, values: List<Any?>, read: (ChatSqlRow) -> T): List<T> =
        db.prepareStatement(sql).use { statement ->
            values.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
            statement.executeQuery().use { result ->
                val row = object : ChatSqlRow {
                    override fun text(index: Int) = result.getString(index + 1)
                    override fun number(index: Int) = result.getLong(index + 1)
                }
                buildList { while (result.next()) add(read(row)) }
            }
        }

    override fun <T> transaction(block: () -> T): T {
        db.autoCommit = false
        try {
            val value = block()
            db.commit()
            return value
        } catch (failure: Throwable) {
            db.rollback()
            throw failure
        } finally {
            db.autoCommit = true
        }
    }

    fun close() = db.close()
}
