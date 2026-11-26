// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import com.sun.net.httpserver.HttpServer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.test.*

class NetworkRoutesTest {
    @Test fun scaledDisplaysSelectTheMatchingHighDpiPresetAtTheExistingTileSize() {
        val folder=Files.createTempDirectory("aprserj64-tile-resolution")
        Files.writeString(folder.resolve("settings.toml"),"[map]\ninitial_zoom = 12.5\n")
        val store=SettingsStore(folder)
        val settings=store.values
        val key=ServicePreferences.TILE_RESOLUTION_KEY
        assertEquals("windows",settings.getProperty(key))
        assertEquals("12.5",settings.getProperty("map.initial_zoom"))
        val highDpi=settings.getProperty("map.project_preset_hidpi_url")
        assertFalse(ServicePreferences.preferDetailedTiles(settings.getProperty(key),1f))
        assertTrue(ServicePreferences.preferDetailedTiles(settings.getProperty(key),1.5f))
        assertTrue(ServicePreferences.preferDetailedTiles(settings.getProperty(key),2f))
        assertEquals(highDpi,ServicePreferences.projectTileVariants(settings)?.detailed?.template)
        assertContains(ServicePreferences.style(settings),"\"tileSize\":256")

        settings.setProperty(key,"1x")
        assertFalse(ServicePreferences.preferDetailedTiles(settings.getProperty(key),2f))
        settings.setProperty(key,"2x")
        assertTrue(ServicePreferences.preferDetailedTiles(settings.getProperty(key),1f))
        store.save()
        assertEquals("2x",SettingsStore(folder).values.getProperty(key))

        settings.setProperty("offline.enabled","true")
        assertEquals(highDpi,ServicePreferences.projectTileVariants(settings)?.detailed?.template)
        settings.setProperty("offline.enabled","false")
        settings.setProperty("map.tile_url",settings.getProperty("map.osm_preset_url"))
        assertNull(ServicePreferences.projectTileVariants(settings))

        val custom="https://maps.example/{z}/{x}/{y}.png"
        settings.setProperty("map.project_preset_url",custom)
        settings.setProperty("map.tile_url",custom)
        assertNull(ServicePreferences.projectTileVariants(settings))
        settings.setProperty("map.project_preset_hidpi_url",custom.replace(".png","@2x.png"))
        assertEquals("https://maps.example/{z}/{x}/{y}@2x.png",ServicePreferences.projectTileVariants(settings)?.detailed?.template)
    }

    @Test fun tileRelayForwardsCacheHeadersAndConditionalRequests() {
        val image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lXcAAAAASUVORK5CYII=")
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        upstream.createContext("/3/2/1.png") { exchange ->
            exchange.use {
                exchange.responseHeaders.set("Content-Type","image/png")
                exchange.responseHeaders.set("Cache-Control","max-age=3600")
                exchange.responseHeaders.set("ETag","\"release-map-test\"")
                if (exchange.requestHeaders.getFirst("If-None-Match")=="\"release-map-test\"") {
                    exchange.sendResponseHeaders(304,-1)
                } else {
                    exchange.sendResponseHeaders(200,image.size.toLong())
                    exchange.responseBody.write(image)
                }
            }
        }
        upstream.start()
        val scope=CoroutineScope(kotlinx.coroutines.Job())
        try {
            NetworkRoutes().use { network ->
                OfflineMaps(Files.createTempDirectory("aprserj64-network"),scope,network) { "" }.use { maps ->
                    val source="http://127.0.0.1:${upstream.address.port}/{z}/{x}/{y}.png"
                    val request=maps.tileRequest(maps.onlineTemplate(source),Tile(3,2,1))
                    HttpClient.newHttpClient().use { client ->
                        val response=client.send(request,HttpResponse.BodyHandlers.ofByteArray())
                        assertEquals(200,response.statusCode())
                        assertContentEquals(image,response.body())
                        assertEquals("max-age=3600",response.headers().firstValue("Cache-Control").orElseThrow())
                        val conditional=HttpRequest.newBuilder(request,{_,_->true})
                            .header("If-None-Match","\"release-map-test\"").build()
                        assertEquals(304,client.send(conditional,HttpResponse.BodyHandlers.discarding()).statusCode())
                    }
                }
            }
        } finally {
            scope.cancel()
            upstream.stop(0)
        }
    }

    @Test fun adaptiveRelayKeepsLegacyPixelsWhileLoadingAndReusesBothResolutionsOffline() {
        fun image(size:Int)=ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(size,size,BufferedImage.TYPE_INT_RGB),"png",output)
            output.toByteArray()
        }
        val standard=image(256)
        val detailed=image(512)
        val started=CountDownLatch(1)
        val release=CountDownLatch(1)
        val upstream=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        upstream.createContext("/3/2/1@2x.png") { exchange -> exchange.use {
            started.countDown()
            if(release.await(5,TimeUnit.SECONDS)) {
                exchange.responseHeaders.set("Content-Type","image/png")
                exchange.sendResponseHeaders(200,detailed.size.toLong())
                exchange.responseBody.write(detailed)
            } else exchange.sendResponseHeaders(503,-1)
        } }
        val home=Files.createTempDirectory("aprserj64-adaptive")
        val cache=Files.createDirectory(home.resolve("cache"))
        val template="http://127.0.0.1:${upstream.address.port}/{z}/{x}/{y}.png"
        val variants=TileVariants(TileVariant(template,256),TileVariant(template.replace(".png","@2x.png"),512))
        val originalTemplate="http://127.0.0.1:12345/old-session/${tileSourceKey(template)}/{z}/{x}/{y}"
        DriverManager.getConnection("jdbc:sqlite:${cache.resolve("tiles.db")}").use { db ->
            db.createStatement().use { it.execute("CREATE TABLE tiles(url_template, pixel_ratio, z, x, y, data, compressed, expires, accessed)") }
            db.prepareStatement("INSERT INTO tiles VALUES(?,1,3,2,1,?,1,0,0)").use { insert ->
                insert.setString(1,originalTemplate)
                insert.setBytes(2,ByteArrayOutputStream().use { output ->
                    DeflaterOutputStream(output).use { it.write(standard) }
                    output.toByteArray()
                })
                insert.executeUpdate()
            }
        }
        upstream.start()
        val scope=CoroutineScope(kotlinx.coroutines.Job())
        try {
            NetworkRoutes().use { network -> HttpClient.newHttpClient().use { client ->
                val tile=Tile(3,2,1)
                fun get(maps:OfflineMaps,source:AdaptiveTileSource) =
                    client.send(maps.tileRequest(source.url,tile),HttpResponse.BodyHandlers.ofByteArray())
                OfflineMaps(home,scope,network) { "" }.use { maps ->
                    val source=maps.adaptiveTemplate(variants,detailed=true,offline=false)
                    assertContentEquals(standard,get(maps,source).body())
                    assertTrue(started.await(5,TimeUnit.SECONDS))
                    val outline=get(maps,source)
                    assertContentEquals(standard,outline.body())
                    assertEquals("max-age=2",outline.headers().firstValue("Cache-Control").orElseThrow())
                    release.countDown()
                    val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                    var response=get(maps,source)
                    while(!detailed.contentEquals(response.body()) && System.nanoTime()<deadline) {
                        Thread.sleep(20)
                        response=get(maps,source)
                    }
                    assertContentEquals(detailed,response.body())
                    assertNotEquals(outline.headers().firstValue("ETag"),response.headers().firstValue("ETag"))
                }
                upstream.stop(0)
                OfflineMaps(home,scope,network) { "" }.use { maps ->
                    val source=maps.adaptiveTemplate(variants,detailed=false,offline=true)
                    assertContentEquals(standard,get(maps,source).body())
                    source.selection=AdaptiveTileSource.Selection(detailed=true,offline=true)
                    assertContentEquals(detailed,get(maps,source).body())
                }
            } }
        } finally {
            release.countDown()
            scope.cancel()
            upstream.stop(0)
        }
    }

    @Test fun releaseMachineCanLoadTileViaAutomaticAdapter() {
        val url=System.getenv("APRSERJ64_NETWORK_TEST_TILE").orEmpty()
        org.junit.Assume.assumeTrue(url.isNotBlank())
        val request=HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(5))
            .header("User-Agent","APRSERJ64/${AppVersion.value}").build()
        NetworkRoutes().use { network ->
            network.probeTile(request).forEach(::println)
            assertTrue(network.getTile(request).valid)
        }
    }
}
