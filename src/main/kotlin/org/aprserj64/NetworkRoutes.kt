// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.channels.SocketChannel
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.aprsfoundation.core.AprsEndpoint
import org.aprsfoundation.core.AprsIsClient
import org.aprsfoundation.core.AprsIsConnection

internal const val AUTO_ADAPTER = "auto"
internal const val SYSTEM_ADAPTER = "system"

internal enum class NetworkResource(val key: String, val label: String) {
    APRS_IS("connectivity.aprs_is_adapter", "APRS-IS"),
    TCP_DEVICES("connectivity.tcp_device_adapter", "TCP devices"),
    MAP_TILES("connectivity.map_tile_adapter", "Map tiles"),
    WEB_SERVICES("connectivity.web_service_adapter", "Web services")
}

internal data class NetworkAdapter(val id: String, val label: String, val addresses: List<InetAddress>)

internal fun networkAdapters(): List<NetworkAdapter> =
    NetworkInterface.networkInterfaces().toList().mapNotNull { adapter ->
        if (!adapter.isUp || adapter.isLoopback) return@mapNotNull null
        val addresses = adapter.inetAddresses().toList().filterNot {
            it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress
        }
        if (addresses.isEmpty()) null else NetworkAdapter("nic:${adapter.name}", adapter.displayName, addresses)
    }.sortedBy { it.label }

class NetworkRoutes(private val setting: (String) -> String? = { null }) : AutoCloseable {
    private data class Target(val resource: NetworkResource, val host: String, val port: Int)
    private data class Candidate(val address: InetAddress?, val label: String)
    private val successful = ConcurrentHashMap<Target, Candidate>()
    private val defaultCandidate = Candidate(null, "System default")
    private val clients = ConcurrentHashMap<Candidate, HttpClient>()

    private fun candidates(target: Target, selection: String): List<Candidate> {
        if (selection == SYSTEM_ADAPTER) return listOf(defaultCandidate)
        val destinations = InetAddress.getAllByName(target.host)
        if (destinations.all { it.isLoopbackAddress }) return listOf(defaultCandidate)
        val eligible = networkAdapters().filter { selection == AUTO_ADAPTER || it.id == selection }
            .flatMap { adapter ->
                adapter.addresses.filter { local -> destinations.any { it.javaClass == local.javaClass } }
                    .map { Candidate(it, adapter.label) }
            }
        if (selection != AUTO_ADAPTER) {
            if (eligible.isEmpty()) throw IOException("Selected adapter is unavailable for ${target.host}")
            return eligible
        }
        val available = listOf(defaultCandidate) + eligible
        return (listOfNotNull(successful[target]?.takeIf { it in available }) + available).distinct()
    }

    private fun <T> useRoute(target: Target, attempt: (Candidate) -> T): T {
        val selection = setting(target.resource.key) ?: AUTO_ADAPTER
        var failure: IOException? = null
        for (candidate in candidates(target, selection)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            try {
                return attempt(candidate).also {
                    if (selection == AUTO_ADAPTER) successful[target] = candidate
                }
            } catch (error: IOException) {
                successful.remove(target, candidate)
                failure = error
            }
        }
        throw IOException("Unable to reach ${target.host}:${target.port}", failure)
    }

    private fun openSocket(target: Target, candidate: Candidate, timeout: Int): Socket {
        val destinations = InetAddress.getAllByName(target.host).filter { candidate.address == null || it.javaClass == candidate.address.javaClass }
        var failure: IOException? = null
        for (address in destinations) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val socket = if (target.resource == NetworkResource.APRS_IS) SocketChannel.open().socket() else Socket()
            try {
                candidate.address?.let { socket.bind(InetSocketAddress(it, 0)) }
                socket.connect(InetSocketAddress(address, target.port), timeout)
                return socket
            } catch (error: Throwable) {
                runCatching { socket.close() }
                if (error !is IOException) throw error
                failure = error
            }
        }
        throw IOException("TCP connection failed", failure)
    }

    internal fun connect(endpoint: AprsEndpoint, resource: NetworkResource = NetworkResource.TCP_DEVICES): Socket {
        val target = Target(resource, endpoint.host, endpoint.port)
        return useRoute(target) { openSocket(target, it, 2000) }
    }

    fun aprsClient(): AprsIsClient = AprsIsClient { endpoint ->
        var opened: Socket? = null
        try {
            runInterruptible(Dispatchers.IO) {
                val socket = connect(endpoint, NetworkResource.APRS_IS)
                opened = socket
                socket.tcpNoDelay = true
                val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val output = socket.getOutputStream()
                object : AprsIsConnection {
                    override suspend fun readLine(limit: Int): String? = runInterruptible(Dispatchers.IO) {
                        val line = StringBuilder()
                        while (true) {
                            val next = input.read()
                            if (next < 0 && line.isEmpty()) return@runInterruptible null
                            if (next < 0 || next == '\n'.code) break
                            if (line.length >= limit) throw IOException("APRS-IS line exceeds its limit")
                            line.append(next.toChar())
                        }
                        line.toString().trimEnd('\r')
                    }
                    override suspend fun write(text: String) = runInterruptible(Dispatchers.IO) {
                        output.write(text.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                    override fun close() = socket.close()
                }
            }
        } catch (failure: Throwable) {
            runCatching { opened?.close() }
            throw failure
        }
    }

    private fun httpTarget(uri: URI, resource: NetworkResource) = Target(resource, requireNotNull(uri.host),
        if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80)

    internal fun getTile(request: HttpRequest): MapTile = useRoute(httpTarget(request.uri(),NetworkResource.MAP_TILES)) { candidate ->
        fetchTile(request, candidate)
    }

    private fun send(request: HttpRequest, candidate: Candidate, limit: Int,
                     keepBody: (HttpResponse.ResponseInfo) -> Boolean): HttpResponse<ByteArray> {
        val client = clients.computeIfAbsent(candidate) {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .apply { candidate.address?.let { localAddress(it) } }.build()
        }
        val response=client.sendAsync(request) { info ->
            if (keepBody(info)) HttpResponse.BodySubscribers.limiting(HttpResponse.BodySubscribers.ofByteArray(),limit.toLong())
            else HttpResponse.BodySubscribers.replacing(byteArrayOf())
        }
        try {
            return response.get(request.timeout().orElse(Duration.ofSeconds(5)).toMillis(), TimeUnit.MILLISECONDS)
        } catch (error: ExecutionException) {
            throw IOException("HTTP request failed",error.cause)
        } catch (error: TimeoutException) {
            throw IOException("HTTP request timed out",error)
        } finally {
            if (!response.isDone) response.cancel(true)
        }
    }

    internal fun getWeb(request: HttpRequest, limit: Int): ByteArray =
        useRoute(httpTarget(request.uri(),NetworkResource.WEB_SERVICES)) { candidate ->
            val response=send(request,candidate,limit) { it.statusCode()==200 }
            require(response.statusCode()==200) { "Web service returned HTTP ${response.statusCode()}" }
            response.body()
        }

    private fun fetchTile(request: HttpRequest, candidate: Candidate): MapTile {
        val response = send(request,candidate,2_000_000) { info ->
            info.statusCode()==200 && info.headers().firstValue("Content-Type").orElse("").startsWith("image/",true)
        }
        val type = response.headers().firstValue("Content-Type").orElse("")
        val headers = listOf("Cache-Control","Expires","ETag","Last-Modified","Retry-After").mapNotNull { name ->
            response.headers().firstValue(name).orElse(null)?.let { name to it }
        }.toMap()
        return MapTile(response.statusCode(), type, response.body(), headers)
    }

    internal fun probeTcp(endpoint: AprsEndpoint, resource: NetworkResource): List<String> {
        val target = Target(resource, endpoint.host, endpoint.port)
        return probe(target) { openSocket(target, it, 1500).use { "TCP connected" } }
    }

    fun probeTile(request: HttpRequest): List<String> = probe(httpTarget(request.uri(),NetworkResource.MAP_TILES)) {
        val response = fetchTile(request, it)
        if (response.valid) "Tile received" else "HTTP ${response.status}, no tile"
    }

    internal fun probeWeb(request: HttpRequest): List<String> = probe(httpTarget(request.uri(),NetworkResource.WEB_SERVICES)) {
        val response = send(request,it,0) { false }
        "HTTP ${response.statusCode()}"
    }

    private fun probe(target: Target, test: (Candidate) -> String): List<String> =
        candidates(target, AUTO_ADAPTER).map { candidate ->
            val result = try { test(candidate) } catch (_: IOException) { "Unreachable" }
            val source = candidate.address?.let { " (${it.hostAddress})" }.orEmpty()
            "${candidate.label}$source · $result"
        }

    override fun close() { clients.values.forEach { it.shutdownNow() } }
}

internal data class MapTile(val status: Int, val type: String, val bytes: ByteArray, val headers: Map<String,String>) {
    val valid get() = status == 200 && bytes.size in 1..2_000_000
}
