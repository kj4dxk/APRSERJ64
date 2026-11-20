// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.shared.aprs.*

class AppModel(val directory: Path) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Default)
    val database = ChatDatabase(directory.resolve("data/chats.sqlite"))
    val account = MutableStateFlow("")
    val state = MutableStateFlow(AprsConnectionState.STOPPED)
    val stations = MutableStateFlow<List<AprsStation>>(emptyList())
    val error = MutableStateFlow("")
    val demo = MutableStateFlow(false)
    private var client: AprsIsClient? = null
    private var chat: ChatSession? = null
    private var observing: Job? = null
    private var server: DemoServer? = null
    private var broadcaster: BeaconController? = null
    private var beaconObserver: Job? = null
    @Volatile private var stress = false
    private val store = SettingsStore(directory)
    val settings = store.values
    val servers get() = store.servers
    val entities get() = store.entities
    val settingsRevision=MutableStateFlow(0L)
    val beaconState=MutableStateFlow(BeaconState(settings.getProperty("beacon.enabled").toBoolean()))
    val beaconCenter=MutableStateFlow<AprsFix?>(null)
    init { account.value = settings.getProperty("callsign","") }
    fun connect(call: String, password: String, endpoint: String, latitude: Double, longitude: Double, autoConnect: Boolean = true): Boolean {
        try {
            val address = AprsEndpoint.parse(endpoint)
            val config = AprsIsConfig(call.trim().uppercase(),password.trim().ifBlank { "-1" },address.host,address.port,
                latitude,longitude,radiusKm=settings.getProperty("radiusKm").toInt(),application="APRSIS64")
            settings.setProperty("callsign", config.callsign); settings.setProperty("endpoint",endpoint)
            settings.setProperty("passcode", config.passcode)
            settings.setProperty("autoConnect", autoConnect.toString())
            settings.setProperty("latitude",latitude.toString()); settings.setProperty("longitude",longitude.toString())
            store.save()
            disconnect()
            start(config)
            return true
        } catch (_: Exception) { error.value = "Check connection settings and that this folder is writable."; return false }
    }
    fun connectSaved() {
        if(settings.getProperty("autoConnect", "true").toBoolean() && settings.getProperty("callsign", "").isNotBlank())
            connect(settings.getProperty("callsign"), settings.getProperty("passcode", "-1"),
                settings.getProperty("endpoint"),
                settings.getProperty("latitude", "28.536725").toDoubleOrNull() ?: Double.NaN,
                settings.getProperty("longitude", "-81.242932").toDoubleOrNull() ?: Double.NaN)
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
        startBeacons(connection,config.callsign)
        observing = scope.launch {
            launch { connection.state.collect { state.value=it; println("APRS_CONNECTION ${config.callsign} $it ${config.host}:${config.port}") } }
            launch { connection.stations.collect { if(!stress) stations.value=it; if(it.size == 1 || it.size % 50 == 0) println("APRS_STATIONS ${it.size}") } }
        }
        connection.start(config)
    }
    fun disconnect(remember: Boolean = false) {
        if(remember) {
            settings.setProperty("autoConnect", "false")
            try { store.save() } catch (_: Exception) { error.value="Unable to save settings." }
        }
        observing?.cancel(); observing=null; chat?.close(); chat=null
        beaconObserver?.cancel(); beaconObserver=null; broadcaster?.close(); broadcaster=null
        client?.stop(); client=null; server?.close(); server=null
        state.value=AprsConnectionState.STOPPED; demo.value=false; stress=false; stations.value=emptyList()
        beaconState.value=beaconState.value.copy(detail="Disconnected")
    }
    private fun startBeacons(connection: AprsIsClient, call: String) {
        beaconObserver?.cancel(); broadcaster?.close()
        val snapshot=Properties().apply {putAll(settings)}
        val entities=store.entities.map {Properties().apply {putAll(it)}}
        val odometer=directory.resolve("data/odometer-$call.txt")
        val distance=runCatching {Files.readString(odometer).trim().toDouble().takeIf {it.isFinite() && it>=0}}.getOrNull() ?: 0.0
        val controller=BeaconController(call,connection,BeaconConfiguration(snapshot,entities),scope,distance,{meters ->
            val temp=Files.createTempFile(directory.resolve("data"),"odometer-",".tmp")
            try {Files.writeString(temp,meters.toString());Files.move(temp,odometer,StandardCopyOption.REPLACE_EXISTING)}
            finally {Files.deleteIfExists(temp)}
        }) {beaconCenter.value=it.fix}
        broadcaster=controller
        beaconObserver=scope.launch {controller.state.collect {beaconState.value=it}}
    }
    fun savePreferences(values: Properties, entities: List<Properties>): Boolean {
        val previous=Properties().apply {putAll(settings)}
        val previousEntities=store.entities
        try {
            BeaconConfiguration(values,entities); ServicePreferences.validate(values)
            settings.clear(); settings.putAll(values); store.entities=entities
            store.save(); settingsRevision.value++
            client?.let {startBeacons(it,account.value)}
            beaconState.value=beaconState.value.copy(enabled=settings.getProperty("beacon.enabled").toBoolean())
            error.value=""; return true
        } catch(_: Exception) {settings.clear();settings.putAll(previous);store.entities=previousEntities;error.value="Check the settings values and folder write access.";return false}
    }
    fun transmitBeacon() {broadcaster?.transmit()}
    fun transmitStatus() {broadcaster?.transmitStatus()}
    fun reportOdometer(reset: Boolean=false) {broadcaster?.reportOdometer(reset)}
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
        runBlocking {withTimeoutOrNull(3000) {runCatching {broadcaster?.beforeExit()}}}
        disconnect()
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        database.close()
    }
}
