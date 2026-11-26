// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.InflaterInputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

private fun rasterKey(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
    .digest(bytes).take(12).joinToString("") { "%02x".format(it) }
internal fun tileSourceKey(value: String) = rasterKey(value.toByteArray())

internal data class TileVariant(val template: String, val pixels: Int)
internal data class TileVariants(val standard: TileVariant, val detailed: TileVariant)
internal data class CachedRaster(val bytes: ByteArray, val type: String, val expires: Long,
    val upstreamTag: String? = null, val modified: String? = null) {
    val tag by lazy { "\"${rasterKey(bytes)}\"" }
}

/** Stores both resolutions under their upstream identity, independent of the loopback port. */
internal class RasterTileCache(private val directory: Path, private val budget: () -> Long) : AutoCloseable {
    private var db: Connection? = null
    private var legacy: Connection? = null
    private var closed=false
    private val legacyTemplates=mutableMapOf<TileVariants,List<String>>()
    private var usedBytes = 0L

    private fun database(): Connection = db ?: DriverManager.getConnection(
        "jdbc:sqlite:${Files.createDirectories(directory).resolve("raster-resolutions.sqlite")}"
    ).also { connection ->
        connection.createStatement().use {
            it.execute("PRAGMA busy_timeout=1000")
            it.execute("""CREATE TABLE IF NOT EXISTS rasters (
                source TEXT, z INTEGER, x INTEGER, y INTEGER, content BLOB NOT NULL,
                mime TEXT, expires INTEGER, etag TEXT, modified TEXT, accessed INTEGER,
                PRIMARY KEY (source,z,x,y))""")
            it.execute("CREATE INDEX IF NOT EXISTS rasters_accessed ON rasters(accessed)")
            it.executeQuery("SELECT coalesce(sum(length(content)),0) FROM rasters").use { rows ->
                if (rows.next()) usedBytes = rows.getLong(1)
            }
        }
        db=connection
    }

    @Synchronized fun find(variants: TileVariants, variant: TileVariant, tile: Tile): CachedRaster? {
        if(closed) return null
        val saved = try { findSaved(variant,tile) } catch (_: SQLException) { null }
        if (saved != null) return saved
        val imported = runCatching { findLegacy(variants,variant,tile) }.getOrNull() ?: return null
        put(variant,tile,imported)
        return imported
    }

    private fun findSaved(variant: TileVariant, tile: Tile): CachedRaster? {
        val connection=database()
        val cached=connection.prepareStatement(
            "SELECT content,mime,expires,etag,modified FROM rasters WHERE source=? AND z=? AND x=? AND y=?"
        ).use { query ->
            query.setString(1,variant.template); query.setInt(2,tile.z); query.setInt(3,tile.x); query.setInt(4,tile.y)
            query.executeQuery().use { rows ->
                if (rows.next()) CachedRaster(rows.getBytes(1),rows.getString(2),rows.getLong(3),rows.getString(4),rows.getString(5))
                else null
            }
        }
        // Timestamp at minute granularity so renderer revalidation does not continuously write the DB.
        if (cached != null) connection.prepareStatement(
            "UPDATE rasters SET accessed=? WHERE source=? AND z=? AND x=? AND y=? AND accessed<?"
        ).use { update ->
            val minute=System.currentTimeMillis()/60000
            update.setLong(1,minute); update.setString(2,variant.template); update.setInt(3,tile.z)
            update.setInt(4,tile.x); update.setInt(5,tile.y); update.setLong(6,minute)
            update.executeUpdate()
        }
        return cached
    }

    private fun findLegacy(variants: TileVariants, variant: TileVariant, tile: Tile): CachedRaster? {
        val path=directory.resolve("tiles.db")
        val connection=legacy ?: if (Files.isRegularFile(path)) {
            DriverManager.getConnection("jdbc:sqlite:${path.toUri()}?mode=ro").also { legacy=it }
        } else return null
        val standard=variants.standard.template
        val detailed=variants.detailed.template
        val templates=legacyTemplates.getOrPut(variants) {
            val suffixes=listOf(standard,detailed,"$detailed\n$standard").map { "/${tileSourceKey(it)}/{z}/{x}/{y}" }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT DISTINCT url_template FROM tiles").use { rows ->
                    buildList {
                        while(rows.next()) {
                            val url=rows.getString(1)
                            if(url==standard || url==detailed ||
                                (url.startsWith("http://127.0.0.1:") && suffixes.any(url::endsWith))) add(url)
                        }
                    }
                }
            }
        }
        if(templates.isEmpty()) return null
        return connection.prepareStatement("""
            SELECT data,compressed,expires FROM tiles WHERE z=? AND x=? AND y=? AND data IS NOT NULL
            AND url_template IN (${templates.joinToString(",") { "?" }}) AND pixel_ratio IN (1,2)
            ORDER BY accessed DESC
        """.trimIndent()).use { query ->
            query.setInt(1,tile.z); query.setInt(2,tile.x); query.setInt(3,tile.y)
            templates.forEachIndexed { index, template -> query.setString(4+index,template) }
            query.executeQuery().use result@{ rows ->
                while (rows.next()) {
                    val encoded=rows.getBytes(1)
                    val bytes=if (rows.getInt(2)==0) encoded else InflaterInputStream(ByteArrayInputStream(encoded)).use {
                        it.readNBytes(2_000_001)
                    }
                    val type=rasterType(bytes,variant.pixels) ?: continue
                    return@result CachedRaster(bytes,type,rows.getLong(3)*1000)
                }
                null
            }
        }
    }

    @Synchronized fun put(variant: TileVariant, tile: Tile, raster: CachedRaster) {
        if(closed) return
        val limit=budget()
        if (raster.bytes.size > limit) return
        try {
            val connection=database()
            val previous=connection.prepareStatement("SELECT length(content) FROM rasters WHERE source=? AND z=? AND x=? AND y=?").use {
                it.setString(1,variant.template); it.setInt(2,tile.z); it.setInt(3,tile.x); it.setInt(4,tile.y)
                it.executeQuery().use { rows -> if(rows.next()) rows.getLong(1) else 0L }
            }
            connection.prepareStatement("INSERT OR REPLACE INTO rasters VALUES(?,?,?,?,?,?,?,?,?,?)").use {
                it.setString(1,variant.template); it.setInt(2,tile.z); it.setInt(3,tile.x); it.setInt(4,tile.y)
                it.setBytes(5,raster.bytes); it.setString(6,raster.type); it.setLong(7,raster.expires)
                it.setString(8,raster.upstreamTag); it.setString(9,raster.modified)
                it.setLong(10,System.currentTimeMillis()/60000); it.executeUpdate()
            }
            usedBytes += raster.bytes.size-previous
            if (usedBytes > limit) {
                connection.prepareStatement("""DELETE FROM rasters WHERE rowid IN (
                    SELECT rowid FROM (SELECT rowid,
                    coalesce(sum(length(content)) OVER (ORDER BY accessed DESC,rowid DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW),0)
                    AS running_bytes FROM rasters) WHERE running_bytes > ?)""").use {
                    it.setLong(1,limit); it.executeUpdate()
                }
                connection.createStatement().use {
                    it.executeQuery("SELECT coalesce(sum(length(content)),0) FROM rasters").use { rows ->
                        if (rows.next()) usedBytes=rows.getLong(1)
                    }
                }
            }
        } catch (_: SQLException) {
            // Cache errors leave the online renderer available.
        }
    }

    @Synchronized override fun close() {
        closed=true
        runCatching { db?.close() }
        runCatching { legacy?.close() }
    }
}

internal fun rasterType(bytes: ByteArray, size: Int): String? {
    if (bytes.size !in 1..2_000_000) return null
    return runCatching {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader=ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.input=input
                if(reader.getWidth(0)==size && reader.getHeight(0)==size) "image/${reader.formatName.lowercase()}"
                else null
            } finally { reader.dispose() }
        }
    }.getOrNull()
}

internal fun tileExpiry(headers: Map<String,String>, now: Long): Long {
    val cacheControl=headers["Cache-Control"].orEmpty()
    if(cacheControl.split(',').any { it.trim().equals("no-cache",true) }) return now
    val maxAge=Regex("(?:^|,)\\s*max-age=(\\d+)",RegexOption.IGNORE_CASE).find(cacheControl)
        ?.groupValues?.get(1)?.toLongOrNull()
    return maxAge?.let { now+it.coerceAtMost(31_536_000)*1000 }
        ?: headers["Expires"]?.let { runCatching { ZonedDateTime.parse(it,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
        ?: (now+7*24*60*60*1000L)
}
