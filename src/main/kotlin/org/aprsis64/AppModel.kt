// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.shared.aprs.*

class AppModel(val directory: Path, demoMode: Boolean = false) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Default)
    val database = ChatDatabase(directory.resolve("data/chats.sqlite"))
    val account = MutableStateFlow("")
    val state = MutableStateFlow(AprsConnectionState.STOPPED)
    val stations = MutableStateFlow<List<AprsStation>>(emptyList())
    val error = MutableStateFlow("")
    val demo = MutableStateFlow(demoMode)
    private var client: AprsIsClient? = null
    private var chat: ChatSession? = null
    private var observing: Job? = null
    private var server: DemoServer? = null
    private var broadcaster: BeaconController? = null
    private var beaconObserver: Job? = null
    private var radio: RadioPorts?=null
    private var radioObserver: Job?=null
    private val internetStations=MutableStateFlow<List<AprsStation>>(emptyList())
    val portStatuses=MutableStateFlow<List<PortStatus>>(emptyList())
    @Volatile private var stress = false
    private val store = SettingsStore(directory)
    val settings = store.values
    val servers get() = store.servers
    val entities get() = store.entities
    val ports get() = store.ports
    val portPresets get() = store.portPresets
    val settingsRevision=MutableStateFlow(0L)
    val beaconState=MutableStateFlow(BeaconState(settings.getProperty("beacon.enabled").toBoolean()))
    val offline=OfflineMaps(directory,scope) {settings.getProperty(it)}
    val weatherAlerts=WeatherAlerts({settings.getProperty(it)},scope)
    val activity=ActivityAlerts {settings.getProperty(it)}
    private val kioskReplies=java.util.concurrent.ConcurrentHashMap<String,Long>()
    val archive=StationArchive(directory,scope) {settings.getProperty(it)}
    val monitor=PacketMonitor(directory,scope)
    val beaconCenter=MutableStateFlow<AprsFix?>(null)
    init { account.value = settings.getProperty("callsign","");startPorts() }
    private fun startPorts() {
        chat?.close();chat=null
        beaconObserver?.cancel();broadcaster?.close();broadcaster=null
        radioObserver?.cancel();radio?.close()
        monitor.configure(settings,demo.value)
        weatherAlerts.configure(demo.value)
        activity.configure()
        val current=RadioPorts(account.value,Properties().apply {putAll(settings)},if(demo.value) emptyList() else store.ports,scope,::recordTraffic)
        radio=current;current.attach(client)
        if(AprsProtocol.validCallsign(account.value)) chat=ChatSession(account.value,current,database,scope) {error.value=it}
        radioObserver=scope.launch {
            launch {combine(current.stations,internetStations) {rf,inet->rf+inet.filter {s->rf.none {it.callsign==s.callsign}}}.collect {if(!stress) stations.value=it}}
            launch {current.statuses.collect {portStatuses.value=it}}
        }
        if(AprsProtocol.validCallsign(account.value)) startBeacons(current,account.value)
    }
    fun connect(call: String, password: String, endpoint: String, latitude: Double, longitude: Double, autoConnect: Boolean = true): Boolean {
        try {
            val address = AprsEndpoint.parse(endpoint)
            val config = AprsIsConfig(call.trim().uppercase(),password.trim().ifBlank { "-1" },address.host,address.port,
                latitude,longitude,radiusKm=settings.getProperty("radiusKm").toInt(),application="APRSIS64",
                filterMode=settings.getProperty("operating.filter_mode"),customFilter=settings.getProperty("operating.custom_filter"),
                fallbackServers=if(settings.getProperty("connectivity.failover").toBoolean()) servers.take(16).map {AprsEndpoint(it.host,it.port)} else emptyList())
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
        if(radio?.call==config.callsign && !demo.value) radio?.attach(connection) else startPorts()
        observing = scope.launch {
            launch { connection.state.collect { state.value=it; println("APRS_CONNECTION ${config.callsign} $it ${connection.activeEndpoint.value?.host}:${connection.activeEndpoint.value?.port}") } }
            launch { connection.stations.collect { internetStations.value=it; if(it.size == 1 || it.size % 50 == 0) println("APRS_STATIONS ${it.size}") } }
        }
        connection.start(config)
    }
    fun disconnect(remember: Boolean = false) {
        if(remember) {
            settings.setProperty("autoConnect", "false")
            try { store.save() } catch (_: Exception) { error.value="Unable to save settings." }
        }
        observing?.cancel(); observing=null
        radio?.attach(null)
        client?.stop(); client=null; server?.close(); server=null
        state.value=AprsConnectionState.STOPPED; demo.value=false; stress=false; internetStations.value=emptyList()
    }
    private fun startBeacons(connection: RadioPorts, call: String) {
        beaconObserver?.cancel(); broadcaster?.close()
        val snapshot=Properties().apply {putAll(settings)}
        if(demo.value) snapshot.setProperty("position.source","fixed")
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
    fun savePreferences(values: Properties, entities: List<Properties>, ports: List<Properties> = store.ports): Boolean {
        val previous=Properties().apply {putAll(settings)}
        val previousEntities=store.entities
        val previousPorts=store.ports
        try {
            BeaconConfiguration(values,entities); ServicePreferences.validate(values); OperatingPreferences.validate(values); FeaturePreferences.validate(values)
            PortPreferences.validateAll(values,ports,entities)
            settings.clear(); settings.putAll(values); store.entities=entities;store.ports=ports
            store.save(); settingsRevision.value++
            if(client!=null && !demo.value) {
                val config=AprsIsConfig(account.value,settings.getProperty("passcode").ifBlank {"-1"},
                    AprsEndpoint.parse(settings.getProperty("endpoint")).host,AprsEndpoint.parse(settings.getProperty("endpoint")).port,
                    settings.getProperty("latitude").toDouble(),settings.getProperty("longitude").toDouble(),
                    radiusKm=settings.getProperty("radiusKm").toInt(),application="APRSIS64",
                    filterMode=settings.getProperty("operating.filter_mode"),customFilter=settings.getProperty("operating.custom_filter"),
                fallbackServers=if(settings.getProperty("connectivity.failover").toBoolean()) servers.take(16).map {AprsEndpoint(it.host,it.port)} else emptyList())
                disconnect();startPorts();start(config)
            } else startPorts()
            beaconState.value=beaconState.value.copy(enabled=settings.getProperty("beacon.enabled").toBoolean())
            error.value=""; return true
        } catch(_: Exception) {settings.clear();settings.putAll(previous);store.entities=previousEntities;store.ports=previousPorts;error.value="Check the settings values and folder write access.";return false}
    }
    private fun recordTraffic(port:String,direction:String,line:String) {
        val packet=line.trimEnd();monitor.record(port,direction,packet)
        if(direction=="RX" || port=="APRS-IS") archive.offer(packet)
        if(direction=="RX") {
            activity.incoming(packet,account.value)
            val message=AprsMessages.parse(packet)
            if(message!=null && message.recipient==account.value && message.acknowledgement==null && settings.getProperty("kiosk.enabled").toBoolean() && !demo.value) {
                val entries=names(settings.getProperty("kiosk.entries")).associate {it.substringBefore('=') to it.substringAfter('=')}
                val query=message.text.trim().uppercase()
                val reply=if(query=="INFO") entries.keys.joinToString(" ").take(67) else entries[query]
                if(!reply.isNullOrBlank()) {
                    val now=System.currentTimeMillis();val last=kioskReplies[message.sender] ?: 0
                    if(now-last>=settings.getProperty("kiosk.minimum_seconds").toLong()*1000) {
                        if(kioskReplies.size>1000)kioskReplies.clear()
                        kioskReplies[message.sender]=now
                        scope.launch {runCatching {send(message.sender,reply)}}
                    }
                }
            }
        }
    }
    private val groupRefresh=scope.launch {
        while(isActive) {
            delay(settings.getProperty("groups.keepalive_minutes").toLong()*60000)
            if(!demo.value && state.value==AprsConnectionState.VERIFIED && settings.getProperty("groups.keepalive").toBoolean())
                names(settings.getProperty("groups.names")).forEach {group->runCatching {send(settings.getProperty("groups.server"),AprsServices.group("K",group))}}
        }
    }
    suspend fun sendBulletin(slot:String,group:String,text:String):Boolean =
        radio?.broadcast(AprsServices.bulletin(account.value,slot,group,text)) ?: false
    fun updateFeature(key:String,value:String):Boolean {
        val values=Properties().apply {putAll(settings);setProperty(key,value)}
        return savePreferences(values,entities)
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
        broadcaster?.close();radioObserver?.cancel()
        runBlocking {try {withTimeoutOrNull(3000) {radio?.shutdown()}} finally {radio?.close()}}
        monitor.close();offline.close();weatherAlerts.close();activity.close()
        runBlocking {archive.finish()}
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        database.close()
    }
}
