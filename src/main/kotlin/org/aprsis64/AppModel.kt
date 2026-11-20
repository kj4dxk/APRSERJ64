// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.nio.file.Path
import java.util.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.shared.aprs.*

class AppModel(val directory: Path) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Default)
    val database = ChatDatabase(directory.resolve("chats.sqlite"))
    val account = MutableStateFlow("")
    val state = MutableStateFlow(AprsConnectionState.STOPPED)
    val stations = MutableStateFlow<List<AprsStation>>(emptyList())
    val error = MutableStateFlow("")
    val demo = MutableStateFlow(false)
    private var client: AprsIsClient? = null
    private var chat: ChatSession? = null
    private var observing: Job? = null
    private var server: DemoServer? = null
    @Volatile private var stress = false
    private val settingsFile = directory.resolve("settings.properties").toFile()
    val settings = Properties().apply { if(settingsFile.exists()) settingsFile.inputStream().use { load(it) } }
    init { account.value = settings.getProperty("callsign","") }
    fun connect(call: String, password: String, endpoint: String, latitude: Double, longitude: Double) {
        try {
            val address = AprsEndpoint.parse(endpoint)
            val config = AprsIsConfig(call.trim().uppercase(),password.ifBlank { "-1" },address.host,address.port,
                latitude,longitude,application="APRSIS64")
            disconnect()
            start(config)
            settings.setProperty("callsign", config.callsign); settings.setProperty("endpoint",endpoint)
            settings.setProperty("latitude",latitude.toString()); settings.setProperty("longitude",longitude.toString())
            settingsFile.outputStream().use { settings.store(it,"APRSIS64 settings. Passcodes are never saved.") }
        } catch (_: Exception) { error.value = "Check callsign/SSID, passcode, server and coordinates." }
    }
    fun localDemo() {
        disconnect()
        server = DemoServer()
        demo.value = true
        start(AprsIsConfig("N0CALL-12","12345","127.0.0.1",server!!.port,28.536725,-81.242932,application="APRSIS64"))
    }
    private fun start(config: AprsIsConfig) {
        account.value = config.callsign; error.value = ""
        val connection = AprsIsClient(); client = connection
        chat = ChatSession(config.callsign,connection,database,scope) { error.value=it }
        observing = scope.launch {
            launch { connection.state.collect { state.value=it } }
            launch { connection.stations.collect { if(!stress) stations.value=it } }
        }
        connection.start(config)
    }
    fun disconnect() {
        observing?.cancel(); observing=null; chat?.close(); chat=null
        client?.stop(); client=null; server?.close(); server=null
        state.value=AprsConnectionState.STOPPED; demo.value=false; stress=false; stations.value=emptyList()
    }
    fun stressStations(count: Int) {
        if(!demo.value) return
        stress=true
        stations.value = (0 until count).map { i ->
            AprsStation("S$i",28.536725 + ((i/100)%100-50)*0.0005,
                -81.242932+(i%100-50)*0.0005,'/','>',"Synthetic render test",System.currentTimeMillis())
        }
    }
    suspend fun send(peer: String, text: String) { database.enqueue(account.value,peer,text) }
    override fun close() {
        disconnect()
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        database.close()
    }
}
