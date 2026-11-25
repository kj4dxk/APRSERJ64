// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.aprsfoundation.core.*

class AppModel(val directory: Path, demoMode: Boolean = false) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Default)
    val database = ChatDatabase(directory.resolve("data/chats.sqlite"))
    val account = MutableStateFlow("")
    val state = MutableStateFlow(AprsConnectionState.STOPPED)
    val stations = MutableStateFlow<List<AprsStation>>(emptyList())
    val error = MutableStateFlow("")
    val serverMessages=MutableStateFlow<List<String>>(emptyList())
    val connectionSetupIssue=MutableStateFlow<String?>(null)
    val demo = MutableStateFlow(demoMode)
    private var client: AprsIsClient? = null
    private var chat: ChatSession? = null
    private var observing: Job? = null
    private var server: DemoServer? = null
    private var broadcaster: BeaconController? = null
    private var beaconObserver: Job? = null
    private var radio: RadioPorts?=null
    private var radioObserver: Job?=null
    val digitalStations=MutableStateFlow<List<AprsStation>>(emptyList())
    private val internetStations=MutableStateFlow<List<AprsStation>>(emptyList())
    val portStatuses=MutableStateFlow<List<PortStatus>>(emptyList())
    @Volatile private var stress = false
    private val store = SettingsStore(directory)
    val settings = store.values
    internal val network=NetworkRoutes(settings::getProperty)
    val servers get() = store.servers
    val entities get() = store.entities
    val ports get() = store.ports
    val portPresets get() = store.portPresets
    val settingsRevision=MutableStateFlow(0L)
    val beaconState=MutableStateFlow(BeaconState(settings.getProperty("beacon.enabled").toBoolean()))
    val offline=OfflineMaps(directory,scope,network) {settings.getProperty(it)}
    val weatherAlerts=WeatherAlerts({settings.getProperty(it)},scope,network)
    val activity=ActivityAlerts(directory) {settings.getProperty(it)}
    val groups=GroupMembership(directory) {settings.getProperty(it)}
    private val kioskReplies=java.util.concurrent.ConcurrentHashMap<String,Long>()
    val archive=StationArchive(directory,scope) {settings.getProperty(it)}
    val tactical=TacticalHistory(directory,scope) {settings.getProperty(it)}
    private val queries=QueryResponder(this,scope) {radio}
    val monitor=PacketMonitor(directory,scope)
    val beaconCenter=MutableStateFlow<AprsFix?>(null)
    val views=StationViews(directory) {settings.getProperty(it)}
    val imports=MapImports(this,scope)
    val satellites=SatelliteTracking(this,scope)
    val legacyWeather=LegacyWeather(directory,scope) {settings.getProperty(it)}
    private val automation=OperatingAutomation(this,scope) {radio}
    init { account.value = settings.getProperty("callsign","");groups.activate(account.value);startPorts();imports.reload();satellites.reload() }
    private fun startPorts() {
        chat?.close();chat=null
        beaconObserver?.cancel();broadcaster?.close();broadcaster=null
        radioObserver?.cancel();radio?.close()
        monitor.configure(settings,demo.value)
        weatherAlerts.configure(demo.value)
        activity.configure()
        val current=RadioPorts(account.value,Properties().apply {putAll(settings)},if(demo.value) emptyList() else store.ports,scope,::recordTraffic,network)
        radio=current;current.attach(client)
        if(AprsProtocol.validCallsign(account.value)) chat=ChatSession(account.value,current,database,scope) {error.value=it}
        radioObserver=scope.launch {
            launch {combine(current.stations,internetStations,digitalStations) {rf,inet,digital->(rf+inet+digital).distinctBy {it.callsign}}.collect {if(!stress) stations.value=it}}
            launch {current.statuses.collect {portStatuses.value=it}}
        }
        startBeacons(current,account.value)
    }
    fun connect(call: String, password: String, endpoint: String, latitude: Double, longitude: Double, autoConnect: Boolean = true): Boolean {
        try {
            val address = AprsEndpoint.parse(endpoint)
            val config = AprsIsConfig(ConnectionSetup.normalizedCall(call),password.trim().ifBlank { "-1" },address.host,address.port,
                latitude,longitude,radiusKm=settings.getProperty("radiusKm").toInt(),application="APRSERJ64",applicationVersion=AppVersion.value,
                filterMode=settings.getProperty("operating.filter_mode"),customFilter=OperatingPreferences.filterTerms(settings),
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
        if(!settings.getProperty("autoConnect", "true").toBoolean()) return
        connectionSetupIssue.value=ConnectionSetup.problem(settings.getProperty("callsign",""),settings.getProperty("passcode",""))
        if(connectionSetupIssue.value!=null) return
        if(settings.getProperty("autoConnect", "true").toBoolean())
            connect(settings.getProperty("callsign"), settings.getProperty("passcode", "-1"),
                settings.getProperty("endpoint"),
                settings.getProperty("latitude", "28.536725").toDoubleOrNull() ?: Double.NaN,
                settings.getProperty("longitude", "-81.242932").toDoubleOrNull() ?: Double.NaN)
    }
    fun localDemo() {
        disconnect()
        server = DemoServer()
        demo.value = true
        start(AprsIsConfig("N0CALL-12","12345","127.0.0.1",server!!.port,28.536725,-81.242932,application="APRSERJ64",applicationVersion=AppVersion.value))
    }
    private fun start(config: AprsIsConfig) {
        connectionSetupIssue.value=null
        account.value = config.callsign;groups.activate(account.value); error.value = ""
        val connection = network.aprsClient(); client = connection
        if(radio?.call==config.callsign && !demo.value) radio?.attach(connection) else startPorts()
        observing = scope.launch {
            launch {connection.serverMessages.collect {messages->
                serverMessages.value=messages
                val login=messages.lastOrNull {it.startsWith("# logresp",ignoreCase=true)}
                if(!demo.value && config.passcode!="-1" && login!=null && ConnectionSetup.rejected(login,config.callsign))
                    connectionSetupIssue.value="The APRS-IS server did not verify your login. Check your callsign and passcode. You can receive, but messages and beacons cannot be sent through APRS-IS."
                else if(login!=null && AprsProtocol.verifiedLogin(login,config.callsign)) connectionSetupIssue.value=null
            }}
            launch { connection.state.collect { if(state.value!=it)activity.event("connection",it.name,"Connection: $it");state.value=it; println("APRS_CONNECTION ${config.callsign} $it ${connection.activeEndpoint.value?.host}:${connection.activeEndpoint.value?.port}") } }
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
        if(demo.value) {
            snapshot.setProperty("position.source","fixed")
            snapshot.setProperty("position.fixed_confirmed","true")
        }
        val entities=store.entities.map {Properties().apply {putAll(it)}}
        val odometer=directory.resolve("data/odometer-$call.txt")
        val distance=runCatching {Files.readString(odometer).trim().toDouble().takeIf {it.isFinite() && it>=0}}.getOrNull() ?: 0.0
        val controller=BeaconController(call,connection,BeaconConfiguration(snapshot,entities),scope,distance,{meters ->
            val temp=Files.createTempFile(directory.resolve("data"),"odometer-",".tmp")
            try {Files.writeString(temp,meters.toString());Files.move(temp,odometer,StandardCopyOption.REPLACE_EXISTING)}
            finally {Files.deleteIfExists(temp)}
        },BeaconJournal(directory.resolve("data/kills-$call")),network) {beaconCenter.value=it.fix}
        broadcaster=controller
        controller.extraStatus={if(settings.getProperty("tactical.dx_status").toBoolean()) tactical.summary() else null}
        beaconObserver=scope.launch {
            var recordedTime:Long?=null
            controller.state.collect {value->
                beaconState.value=value
                val position=value.position
                if(snapshot.getProperty("position.source")!="fixed" && position?.current==true && position.fix.timeMillis!=recordedTime) {
                    recordedTime=position.fix.timeMillis
                    if(AprsProtocol.validCallsign(call)) archive.offer(BeaconPackets.position(call,position,BeaconFormat()).trimEnd('\r','\n'),position.fix.timeMillis)
                }
            }
        }
    }
    fun savePreferences(values: Properties, entities: List<Properties>, ports: List<Properties> = store.ports): Boolean {
        val previous=Properties().apply {putAll(settings)}
        val previousEntities=store.entities
        val previousPorts=store.ports
        try {
            BeaconConfiguration(values,entities); ServicePreferences.validate(values); OperatingPreferences.validate(values); FeaturePreferences.validate(values)
            PortPreferences.validateAll(values,ports,entities)
            settings.clear(); settings.putAll(values); store.entities=entities;store.ports=ports
            store.save(); settingsRevision.value++;imports.reload();satellites.reload()
            val changed=(previous.stringPropertyNames()+values.stringPropertyNames()).filter {previous.getProperty(it)!=values.getProperty(it)}
            val radioChanged=changed.any {it.startsWith("radio.") || it==NetworkResource.TCP_DEVICES.key}
            val beaconChanged=previousEntities!=entities || changed.any {key->
                listOf("position.","beacon.","status.","odometer.").any(key::startsWith)
            }
            val internetChanged=changed.any {it in setOf("endpoint","passcode","latitude","longitude","radiusKm","connectivity.failover",NetworkResource.APRS_IS.key) || it.startsWith("operating.filter")}
            if(radioChanged) startPorts()
            else radio?.let {current->
                if(previousPorts!=ports && !demo.value) current.updatePorts(ports)
                if(beaconChanged) startBeacons(current,account.value)
            }
            monitor.configure(settings,demo.value)
            weatherAlerts.configure(demo.value)
            activity.configure()
            if(client!=null && !demo.value && internetChanged) {
                val config=AprsIsConfig(account.value,settings.getProperty("passcode").ifBlank {"-1"},
                    AprsEndpoint.parse(settings.getProperty("endpoint")).host,AprsEndpoint.parse(settings.getProperty("endpoint")).port,
                    settings.getProperty("latitude").toDouble(),settings.getProperty("longitude").toDouble(),
                    radiusKm=settings.getProperty("radiusKm").toInt(),application="APRSERJ64",applicationVersion=AppVersion.value,
                    filterMode=settings.getProperty("operating.filter_mode"),customFilter=OperatingPreferences.filterTerms(settings),
                fallbackServers=if(settings.getProperty("connectivity.failover").toBoolean()) servers.take(16).map {AprsEndpoint(it.host,it.port)} else emptyList())
                disconnect();start(config)
            }
            beaconState.value=beaconState.value.copy(enabled=settings.getProperty("beacon.enabled").toBoolean())
            error.value=""; return true
        } catch(_: Exception) {settings.clear();settings.putAll(previous);store.entities=previousEntities;store.ports=previousPorts;error.value="Check the settings values and folder write access.";return false}
    }
    private fun recordTraffic(port:String,direction:String,line:String) {
        val packet=line.trimEnd('\r','\n');monitor.record(port,direction,packet)
        if(direction=="RX" || port=="APRS-IS")scope.launch {radio?.publishServices(port,packet)}
        automation.observe(direction,packet)
        groups.observe(account.value,direction,packet)
        if(direction=="RX" || port=="APRS-IS") archive.offer(packet)
        if(direction=="RX") {
            val rf=ports.any {it.getProperty("name")==port && it.getProperty("protocol") !in PortPreferences.services && it.getProperty("protocol") !in listOf("NMEA","Weather")}
            views.receive(port,packet,rf=rf);legacyWeather.offer(packet)
            if(rf || port=="APRS-IS")tactical.offer(port,packet,beaconState.value.position?.takeIf {it.current}?.fix)
            queries.receive(port,packet)
            activity.incoming(packet,account.value,beaconState.value.position?.takeIf {it.current}?.fix)
            val message=AprsMessages.parse(packet)
            if(message!=null && message.recipient==account.value && message.acknowledgement==null && settings.getProperty("kiosk.enabled").toBoolean() && !demo.value &&
                !(settings.getProperty("tactical.qru_enabled").toBoolean() && (message.text.trim().uppercase().substringBefore(' ')=="INFO" || entities.any {it.getProperty("qru_group","")==message.text.trim().uppercase().substringBefore(' ')} ))) {
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
    private val notificationClock=scope.launch {
        val warned=mutableSetOf<String>()
        while(isActive){delay(10000);activity.tick()
            (weatherAlerts.alerts.value+legacyWeather.alerts.value).filter {it.severity in listOf("Severe","Extreme")}.forEach {alert->
                if(warned.add(alert.id))activity.event("weather",alert.id,"${alert.severity}: ${alert.event}")
            }
            if(warned.size>1000)warned.clear()
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
    fun localWeather(name:String)=radio?.weather(name)
    fun gpsSky()=radio?.sky().orEmpty() + listOfNotNull(broadcaster?.gpsSky()?.let {"Position" to it}).toMap()
    suspend fun queryModem(name: String)=radio?.queryModem(name)==true
    fun transmitBeacon() {broadcaster?.transmit()}
    suspend fun reportDx():Boolean {
        if(!settings.getProperty("beacon.enabled").toBoolean())return false
        val summary=tactical.summary() ?: return false
        return radio?.broadcast(BeaconPackets.status(account.value,summary,System.currentTimeMillis()),
            settings.getProperty("beacon.internet").toBoolean(),settings.getProperty("beacon.radio").toBoolean()) ?: false
    }
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
        automation.close();legacyWeather.close();monitor.close();offline.close();weatherAlerts.close();network.close();activity.close()
        runBlocking {archive.finish();tactical.finish()}
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        database.close()
    }
}
