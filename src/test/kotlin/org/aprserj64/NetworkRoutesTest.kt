// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.test.*

class NetworkRoutesTest {
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
