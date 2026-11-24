// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import com.sun.net.httpserver.HttpServer
import java.net.*
import java.net.http.*
import java.nio.file.*
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class Tile(val z:Int,val x:Int,val y:Int)
object TilePlan {
 fun region(south:Double,west:Double,north:Double,east:Double,minZoom:Int,maxZoom:Int,limit:Int):List<Tile> {
  require(listOf(south,west,north,east).all {it.isFinite()} && south in -85.0..85.0 && north in south..85.0 && west in -180.0..180.0 && east in west..180.0)
  require(minZoom in 0..18 && maxZoom in minZoom..18)
  val result=mutableListOf<Tile>()
  for(z in minZoom..maxZoom) {
   val n=1 shl z
   fun x(lon:Double)=floor((lon+180)/360*n).toInt().coerceIn(0,n-1)
   fun y(lat:Double)=floor((1-asinh(tan(lat*PI/180))/PI)/2*n).toInt().coerceIn(0,n-1)
   val xa=x(west);val xb=x(east);val ya=y(north);val yb=y(south)
   require(result.size.toLong()+(xb-xa+1).toLong()*(yb-ya+1)<=limit) {"Area exceeds $limit tiles; reduce its size or zoom range"}
   for(xx in xa..xb) for(yy in ya..yb) result.add(Tile(z,xx,yy))
  }
  return result
 }
}
class OfflineMaps(private val home:Path,private val scope:CoroutineScope,private val setting:(String)->String):AutoCloseable {
 val status=MutableStateFlow("Choose an area to download")
 val running=MutableStateFlow(false)
 private var job:Job?=null
 private val pool=Executors.newFixedThreadPool(2) {r->Thread(r,"offline-map").apply {isDaemon=true}}
 private val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
 private val client=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()
 private fun key(url:String)=MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString("") {"%02x".format(it)}
 private val rasterCache=object:LinkedHashMap<Pair<String,Tile>,ByteArray>(64,0.75f,true) {
  override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Pair<String,Tile>,ByteArray>?)=size>64
 }
 private fun path(url:String,t:Tile)=home.resolve("cache/offline/${key(url)}/${t.z}/${t.x}/${t.y}.tile")
 fun template()="http://127.0.0.1:${server.address.port}/${key(setting("map.tile_url"))}/{z}/{x}/{y}"
 init {
  server.executor=pool
  server.createContext("/") {exchange->try {
   val parts=exchange.requestURI.path.trim('/').split('/')
   require(parts.size==4 && parts[0]==key(setting("map.tile_url")))
   val t=Tile(parts[1].toInt(),parts[2].toInt(),parts[3].toInt());require(t.z in 0..18 && t.x in 0 until (1 shl t.z) && t.y in 0 until (1 shl t.z))
   val file=path(setting("map.tile_url"),t)
   if(!Files.exists(file)) exchange.sendResponseHeaders(404,-1)
   else {val bytes=Files.readAllBytes(file);exchange.responseHeaders.set("Content-Type","image/png");exchange.sendResponseHeaders(200,bytes.size.toLong());exchange.responseBody.use {it.write(bytes)}}
  }catch(_:Exception){runCatching {exchange.sendResponseHeaders(404,-1)}}finally{exchange.close()}}
  server.start()
 }
 internal suspend fun visibleTile(template:String,t:Tile,offline:Boolean):ByteArray?=runInterruptible(Dispatchers.IO) {
  val disk=path(template,t)
  if(Files.isRegularFile(disk)) return@runInterruptible Files.readAllBytes(disk)
  val key=template to t
  synchronized(rasterCache) {rasterCache[key]}?.let {return@runInterruptible it}
  if(offline) return@runInterruptible null
  val url=template.replace("{z}",t.z.toString()).replace("{x}",t.x.toString()).replace("{y}",t.y.toString())
  val request=HttpRequest.newBuilder(URI(url)).timeout(java.time.Duration.ofSeconds(4))
   .header("User-Agent","APRSERJ64/${AppVersion.value} (+${setting("services.source_url")})").build()
  val response=client.send(request,HttpResponse.BodyHandlers.ofInputStream())
  val bytes=response.body().use {it.readNBytes(2_000_001)}
  check(response.statusCode()==200) {"Map server returned HTTP ${response.statusCode()}"}
  check(bytes.size in 1..2_000_000 && response.headers().firstValue("content-type").orElse("").startsWith("image/")) {"Map server returned an invalid tile"}
  synchronized(rasterCache) {rasterCache[key]=bytes}
  bytes
 }
 fun download(tiles:List<Tile>) {
  if(running.value)return
  require(tiles.size<=setting("offline.maximum_tiles").toInt())
  val url=setting("map.tile_url");val budget=setting("offline.maximum_megabytes").toLong()*1024*1024
  running.value=true
  job=scope.launch(Dispatchers.IO) {try {
   val folder=home.resolve("cache/offline");Files.createDirectories(folder)
   var bytesOnDisk=Files.walk(folder).use {stream->stream.filter {Files.isRegularFile(it)}.mapToLong {Files.size(it)}.sum()}
   var failed=0
   tiles.forEachIndexed {i,t->
    ensureActive();val target=path(url,t)
    if(!Files.exists(target)) {
     try {
      val address=url.replace("{z}",t.z.toString()).replace("{x}",t.x.toString()).replace("{y}",t.y.toString())
      val request=HttpRequest.newBuilder(URI(address)).timeout(java.time.Duration.ofSeconds(10)).header("User-Agent","APRSERJ64/0.5").build()
      val response=client.send(request,HttpResponse.BodyHandlers.ofInputStream())
      val bytes=response.body().use {it.readNBytes(2_000_001)}
      require(response.statusCode()==200 && bytes.size in 1..2_000_000 && response.headers().firstValue("content-type").orElse("").startsWith("image/"))
      check(bytesOnDisk+bytes.size<=budget) {"Storage budget reached"}
      Files.createDirectories(target.parent);val temp=Files.createTempFile(target.parent,"tile-",".tmp")
      try {Files.write(temp,bytes);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING)}finally{Files.deleteIfExists(temp)}
      bytesOnDisk+=bytes.size
     }catch(cancelled:CancellationException){throw cancelled}catch(e:IllegalStateException){throw e}catch(_:Exception){failed++}
     delay(150)
    }
    status.value="${i+1}/${tiles.size} tiles · $failed failed"
   }
   status.value="Download complete: ${tiles.size-failed}/${tiles.size} available"
  }catch(_:CancellationException){status.value="Download cancelled; completed tiles kept"}
   catch(e:Exception){status.value=e.message ?: "Download failed"}
   finally{running.value=false}
  }
 }
 fun cancel(){job?.cancel()}
 override fun close(){cancel();server.stop(0);pool.shutdownNow();client.close()}
}
