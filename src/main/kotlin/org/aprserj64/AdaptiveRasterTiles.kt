// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.net.http.HttpRequest
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class AdaptiveTileSource(val url: String, val variants: TileVariants, detailed: Boolean, offline: Boolean) {
    data class Selection(val detailed: Boolean, val offline: Boolean)
    val changes=MutableStateFlow(0L)
    private val retryNeeded=AtomicBoolean()
    @Volatile var selection=Selection(detailed,offline)
        set(value) {
            if (field==value) return
            field=value
            changed()
        }

    fun changed() {changes.update { it+1 }}
    fun retryLater() {retryNeeded.set(true)}
    fun retry() {if(retryNeeded.getAndSet(false) && !selection.offline) changed()}
    fun url(revision:Long) = if(revision==0L) url else "$url?revision=$revision"
}

internal class AdaptiveRasterTiles(
    directory: Path,
    budget: () -> Long,
    private val request: (String,Tile) -> HttpRequest,
    private val download: (HttpRequest) -> MapTile,
    private val offlineTile: (String,Tile) -> ByteArray?,
    private val now: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private data class FetchKey(val variant: TileVariant, val tile: Tile)
    private val cache=RasterTileCache(directory,budget)
    private val pending=ConcurrentHashMap<FetchKey,CompletableFuture<CachedRaster?>>()
    private val retryAfter=object : LinkedHashMap<FetchKey,Long>(64,0.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FetchKey,Long>?)=size>1024
    }
    private val workers=ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(24),
        { task -> Thread(task,"raster-resolution").apply { isDaemon=true } })

    fun get(source: AdaptiveTileSource, tile: Tile): CachedRaster? {
        val variants=source.variants
        val selection=source.selection
        val primary=if(selection.detailed) variants.detailed else variants.standard
        val secondary=if(selection.detailed) variants.standard else variants.detailed
        fun cached(variant: TileVariant): CachedRaster? {
            cache.find(variants,variant,tile)?.let { return it }
            if(variant!=variants.standard) return null
            val bytes=offlineTile(variant.template,tile) ?: return null
            return rasterType(bytes,variant.pixels)?.let { CachedRaster(bytes,it,0) }
        }
        val preferred=cached(primary)
        if(preferred!=null && preferred.expires>now()) return preferred
        val available=preferred ?: cached(secondary)
        if(selection.offline) return available
        val refresh=fetch(primary,tile,preferred)
        if(available!=null) {
            if(refresh==null) source.retryLater()
            else refresh.thenAccept { result ->
                if(source.selection==selection) {
                    if(result==null) source.retryLater()
                    else if(!result.bytes.contentEquals(available.bytes)) source.changed()
                }
            }
            return available
        }
        val result=await(refresh)
        if(result!=null) return result
        source.retryLater()
        return await(fetch(secondary,tile,null))
    }

    private fun await(future: CompletableFuture<CachedRaster?>?): CachedRaster? =
        try {future?.get(15,TimeUnit.SECONDS)} catch (_: TimeoutException) {null}

    private fun fetch(variant: TileVariant, tile: Tile, previous: CachedRaster?): CompletableFuture<CachedRaster?>? {
        val key=FetchKey(variant,tile)
        pending[key]?.let { return it }
        if (synchronized(retryAfter) { (retryAfter[key] ?: 0)>now() }) return null
        val result=CompletableFuture<CachedRaster?>()
        pending.putIfAbsent(key,result)?.let { return it }
        try {
            workers.execute {
                val tileData=try { load(variant,tile,previous) } catch (_: Exception) { null }
                if(tileData==null) synchronized(retryAfter) { retryAfter[key]=now()+10_000 }
                result.complete(tileData)
                pending.remove(key,result)
            }
        } catch (_: RejectedExecutionException) {
            result.complete(null)
            pending.remove(key,result)
        }
        return result
    }

    private fun load(variant: TileVariant,tile:Tile,previous:CachedRaster?): CachedRaster? {
        val builder=HttpRequest.newBuilder(request(variant.template,tile),{_,_->true})
        previous?.upstreamTag?.let { builder.header("If-None-Match",it) }
        previous?.modified?.let { builder.header("If-Modified-Since",it) }
        val response=download(builder.build())
        val expires=tileExpiry(response.headers,now())
        val raster=if(response.status==304 && previous!=null) previous.copy(expires=expires)
        else {
            if(!response.valid) return null
            val type=rasterType(response.bytes,variant.pixels) ?: return null
            CachedRaster(response.bytes,type,expires,response.headers["ETag"],response.headers["Last-Modified"])
        }
        if(response.headers["Cache-Control"]?.contains("no-store",true)!=true) cache.put(variant,tile,raster)
        return raster
    }

    override fun close() {
        workers.shutdownNow()
        pending.values.forEach { it.complete(null) }
        cache.close()
    }
}
