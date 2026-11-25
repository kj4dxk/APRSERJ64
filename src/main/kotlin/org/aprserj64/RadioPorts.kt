// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.util.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.aprsfoundation.core.*

/** Routing decisions are serialized; socket/serial readers never wait for their own transmit lock. */
class RadioPorts(val call: String, values: Properties, configs: List<Properties>, parent: CoroutineScope,
    private val traffic: (String,String,String)->Unit = {_,_,_->}, private val network: NetworkRoutes = NetworkRoutes()) : AutoCloseable, AprsMessageTransport {
    private val scope=CoroutineScope(parent.coroutineContext+SupervisorJob(parent.coroutineContext[Job]))
    private val router=RadioRouter(call,PortPreferences.rules(values))
    private val routing=Mutex()
    private val events=Channel<Pair<String,RadioPacket>>(128)
    private val incoming=Channel<AprsMessage>(128)
    override val messages=incoming.receiveAsFlow()
    private val localForChat=java.util.concurrent.ConcurrentHashMap<String,Pair<String,Long>>()
    private val directHeard=java.util.concurrent.ConcurrentHashMap<String,Long>()
    private val localHeard=java.util.concurrent.ConcurrentHashMap<String,Pair<Int,Long>>()
    private val maximumHops=values.getProperty("radio.maximum_hops").toInt()
    private val heardHours=java.util.concurrent.ConcurrentHashMap<String,LongArray>()
    private val heardForChat=java.util.concurrent.ConcurrentHashMap<String,Pair<String,Long>>()
    private val heardMillis=values.getProperty("radio.heard_seconds").toLong()*1000
    @Volatile private var devices: Map<String,DevicePort> = emptyMap()
    private val positions=java.util.concurrent.ConcurrentHashMap<String,AprsFix>()
    private val igates=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val weatherReadings=java.util.concurrent.ConcurrentHashMap<String,AprsWeather>()
    private val fixes=java.util.concurrent.ConcurrentHashMap<String,GpsReading>()
    private val store=AprsStationStore()
    val stations=MutableStateFlow<List<AprsStation>>(emptyList())
    val statuses=MutableStateFlow<List<PortStatus>>(emptyList())
    private var statusObserver: Job?=null
    @Volatile private var client: AprsIsClient?=null
    private var internetJob: Job?=null
    init {
        updatePorts(configs)
        scope.launch {for((name,packet) in events) routing.withLock {
            val now=System.currentTimeMillis();val device=devices[name] ?: return@withLock
            if(!device.config.isRf) {
                localForChat.entries.removeIf {now-it.value.second>heardMillis}
                if(localForChat.size<4096)localForChat[packet.source]=name to now
            }
            traffic(name,"RX",packet.line())
            store.receive(packet.line(),now);stations.value=store.snapshot(now)
            AprsProtocol.parse(packet.line(),now)?.let {positions[it.callsign]=AprsSpatial.fix(it)}
            if(positions.size>10000){positions.entries.removeIf {now-it.value.timeMillis>1800000};while(positions.size>10000)positions.keys.firstOrNull()?.let {positions.remove(it)}}
            if(!device.config.isRf) {
                if(device.config.flag("rf_to_is") && client?.canSend()==true && !router.duplicate("local",packet,now)) {
                    router.toInternet(packet,false)?.let {line->runCatching {client?.sendGated(call,line);traffic("APRS-IS","TX",line);router.transmitted("local",packet,now)}}
                }
                if(device.config.flag("is_to_rf"))for((target,rf) in devices.filter {it.value.config.isRf}) {
                    val action="local:$target"
                    if(rf.config.flag("is_to_rf") && !rf.config.flag("passall") && !router.duplicate(action,packet,now))
                        router.toRadio(target,packet,now,rf.config.path("message_path"),rf.config.list("gate_calls"),rf.config.list("gate_objects"),rf.config.gateFilter.matches(packet,positions[call],positions,igates))?.let {if(rf.transmit(it)){router.transmitted(action,packet,now);router.gatedToRadio(target,packet,now)}}
                }
                AprsMessages.parse(packet.line())?.takeIf {it.recipient==call && it.sender!=call}?.let {incoming.send(it)}
                return@withLock
            }
            router.observeRf(name,packet,now)
            if(!packet.payload.startsWith('}')) {
                if(packet.usedHops<=maximumHops)localHeard[packet.source]=packet.usedHops to now
                localHeard.entries.removeIf {now-it.value.second>heardMillis}
                if(packet.usedHops==0)directHeard[packet.source]=now
                directHeard.entries.removeIf {now-it.value>heardMillis}
                if(heardHours.size<4096 || heardHours.containsKey(packet.source)) {
                    val h=heardHours.getOrPut(packet.source){LongArray(48)};val hour=now/3600000;val index=(hour%24).toInt()*2
                    synchronized(h){if(h[index]!=hour){h[index]=hour;h[index+1]=0};h[index+1]++}
                }
                heardForChat[packet.source]=name to now
                if(heardForChat.size>values.getProperty("radio.maximum_entries").toInt()) heardForChat.entries.minByOrNull {it.value.second}?.let {heardForChat.remove(it.key,it.value)}
            }
            val messagePacket=if(packet.payload.startsWith('}')) RadioPacket.parse(packet.payload.drop(1)) else packet
            messagePacket?.let {AprsMessages.parse(it.line())}?.takeIf {it.recipient==call && it.sender!=call}?.let {incoming.send(it)}
            if(!RadioPacket.address(call)) return@withLock
            val config=device.config
            if(config.flag("digipeater") && !config.flag("passall") && !router.duplicate("digi",packet,now)) {
                router.digipeat(packet,config.list("digi_transforms"))?.let {
                    val outputs=config.list("digi_outputs").ifEmpty {listOf(name)}
                    for(target in outputs) if(devices[target]?.transmit(it)==true) router.transmitted("digi",packet,now)
                }
            }
            val isClient=client
            if(config.flag("rf_to_is") && !(config.flag("no_gate_me") && packet.source==call) && !config.flag("passall") && isClient?.state?.value==AprsConnectionState.VERIFIED && !router.duplicate("igate",packet,now)) {
                router.toInternet(packet,config.canTransmit && config.flag("is_to_rf"))?.let {
                    try {isClient.sendGated(call,it);traffic("APRS-IS","TX",it);router.transmitted("igate",packet,now)}
                    catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) { /* No offline queue. */ }
                }
            }
        } }
        scope.launch {while(isActive) {routing.withLock {stations.value=store.snapshot(System.currentTimeMillis())};delay(1000)}}
    }
    @Synchronized fun updatePorts(configs: List<Properties>) {
        val enabled=configs.filter {it.getProperty("enabled").toBoolean()}.map(::PortConfig)
        val previous=devices
        val unchanged=enabled.filter {previous[it.name]?.config?.values==it.values}.map {it.name}.toSet()
        previous.filterKeys {it !in unchanged}.forEach {(name,port)->
            port.close()
            fixes.remove(name)
            weatherReadings.remove(name)
        }
        devices=enabled.associate {config->
            val name=config.name
            name to (previous[name]?.takeIf {name in unchanged} ?: DevicePort(config,scope,{events.trySend(name to it).isSuccess},{reading->
                fixes[name]=if(!reading.valid && reading.position==null) fixes[name]?.copy(valid=false,detail=reading.detail) ?: reading else reading
            },{packet->traffic(name,"TX",packet.line())},{sample->weatherReadings[name]=sample},network))
        }
        statusObserver?.cancel()
        val current=devices
        statuses.value=current.values.map {it.status.value}
        if(current.isNotEmpty()) statusObserver=scope.launch {
            combine(current.values.map {it.status}) {it.toList()}.collect {latest->
                if(devices===current) statuses.value=latest
            }
        }
    }
    fun attach(value: AprsIsClient?) {
        internetJob?.cancel();client=value
        if(value!=null) internetJob=scope.launch {
            launch {value.messages.collect {incoming.send(it)}}
            value.packets.collect {line->
            traffic("APRS-IS","RX",line)
            val packet=RadioPacket.parse(line) ?: return@collect
            routing.withLock {
                val now=System.currentTimeMillis();router.observeInternet(packet,now)
                AprsProtocol.parse(line,now)?.let {positions[it.callsign]=AprsSpatial.fix(it)}
                if(positions.size>10000){positions.entries.removeIf {now-it.value.timeMillis>1800000};while(positions.size>10000)positions.keys.firstOrNull()?.let {positions.remove(it)}}
                packet.path.indexOfFirst {it in listOf("qAR","qAr","qAo")}.takeIf {it>=0}?.let {index->packet.path.getOrNull(index+1)?.let {if(igates.size<4096)igates.add(it)}}
                if(!RadioPacket.address(call)) return@withLock
                for((name,device) in devices) {
                    val config=device.config;val action="is:$name"
                    if(!config.isRf || !config.flag("is_to_rf") || !config.canTransmit || config.flag("passall") || router.duplicate(action,packet,now)) continue
                    router.toRadio(name,packet,now,config.path("message_path"),config.list("gate_calls"),config.list("gate_objects"),config.gateFilter.matches(packet,positions[call],positions,igates))?.let {
                        if(device.transmit(it)) {router.transmitted(action,packet,now);router.gatedToRadio(name,packet,now)}
                    }
                }
            }
        } }
    }
    suspend fun queryModem(name: String)=devices[name]?.queryModem()==true
    fun isService(name:String)=devices[name]?.config?.protocol in PortPreferences.services
    fun weather(name:String)=weatherReadings[name]
    fun sky()=devices.mapValues {it.value.gpsSky.value}.filterValues {it.time>0}
    fun heardCalls()=heardForChat.filterValues {System.currentTimeMillis()-it.second in 0..heardMillis}.keys.sorted()
    fun positions()=positions.toMap()
    fun localCalls()=localHeard.filterValues {System.currentTimeMillis()-it.second in 0..heardMillis}.toSortedMap().map {(call,h)->if(h.first==0)call else "$call(${h.first})"}
    fun directCalls()=directHeard.filterValues {System.currentTimeMillis()-it in 0..heardMillis}.keys.sorted()
    fun heardReport(call:String):String {
        val h=heardHours[call]?:return "$call not heard on RF"
        val hour=System.currentTimeMillis()/3600000
        return synchronized(h){"$call HEARD: "+(0L..7L).joinToString(" "){ago->val index=((hour-ago)%24).toInt()*2;if(h[index]==hour-ago)h[index+1].toString() else "."}}
    }
    suspend fun answer(port:String,line:String,recipient:String?=null):Boolean {
        if(port=="APRS-IS")return broadcast(line,true,false)
        val device=devices[port]?.takeIf {it.config.flag("messages")}?:return false
        val packet=RadioPacket.parse(line.trimEnd('\r','\n'))?:return false
        if(!device.config.isRf)return recipient?.let {device.replyTo(it,packet)}?:false
        return device.transmit(packet.copy(path=device.config.path("message_path")))
    }
    suspend fun publishServices(port:String,line:String) {
        val packet=RadioPacket.parse(line)?:return
        devices.filter {it.key!=port && it.value.config.protocol in PortPreferences.services && it.value.config.protocol!="CWOP"}.values.forEach {it.transmit(packet)}
    }
    fun gps(name: String)=fixes[name] ?: GpsReading(detail="Waiting for GPS port")
    override fun canSend()=client?.canSend()==true || RadioPacket.address(call) && devices.values.any {it.ready && it.config.canTransmit && it.config.flag("messages")}
    override suspend fun sendMessage(account: String, recipient: String, text: String, id: String) {
        check(account==call);sendChat(recipient,AprsMessages.packet(account,recipient,text,id))
    }
    override suspend fun acknowledge(account: String, recipient: String, id: String) {
        check(account==call);sendChat(recipient,AprsMessages.ack(account,recipient,id))
    }
    override suspend fun sendPending(account:String,message:PendingChat) {
        check(account==call)
        sendChat(message.peer,if(message.requestAck) AprsMessages.packet(account,message.peer,message.body,message.wireId)
            else AprsMessages.unnumbered(account,message.peer,message.body),message.route)
    }
    private suspend fun sendChat(recipient: String, line: String, route:String="best") {
        require(route in listOf("best","rf","is"))
        val local=localForChat[recipient]?.takeIf {System.currentTimeMillis()-it.second in 0..heardMillis}
        if(route=="best" && local!=null) {
            check(devices[local.first]?.replyTo(recipient,requireNotNull(RadioPacket.parse(line.trimEnd('\r','\n'))))==true){"Local client unavailable"}
            return
        }
        val candidates=devices.values.filter {it.config.isRf && route!="is" && it.ready && it.config.canTransmit && it.config.flag("messages")}
        val heard=heardForChat[recipient]?.takeIf {System.currentTimeMillis()-it.second in 0..heardMillis}
        val device=candidates.firstOrNull {it.config.name==heard?.first}
        val packet=requireNotNull(RadioPacket.parse(line.trimEnd('\r','\n')))
        if(device!=null) {check(device.transmit(packet.copy(path=device.config.path("message_path"))));return}
        val connection=client
        if(route!="rf" && connection?.canSend()==true) {
            // Message framing is already validated above, including its APRS destination.
            connection.sendBroadcast(call,line);traffic("APRS-IS","TX",line);return
        }
        val fallback=checkNotNull(candidates.firstOrNull()) {"No messaging output"}
        check(fallback.transmit(packet.copy(path=fallback.config.path("message_path"))))
    }
    fun beaconAvailable(useIs: Boolean, useRf: Boolean)=
        useIs && client?.canSend()==true || useRf && devices.values.any {
            it.ready && it.config.isRf && it.config.canTransmit && it.config.flag("beacon")
        }
    fun available()=client?.state?.value==AprsConnectionState.VERIFIED || devices.values.any {it.ready && it.config.canTransmit}
    /** Immediate directed Internet item reply: never queue a stale object or fall back to RF. */
    suspend fun queryItem(recipient:String,text:String):Boolean {
        val connection=client?.takeIf {it.canSend()} ?: return false
        val line=AprsMessages.unnumbered(call,recipient,text)
        connection.sendBroadcast(call,line);traffic("APRS-IS","TX",line);return true
    }
    /** A report can target IS, selected RF ports, or both. Empty ports means all eligible RF ports. */
    suspend fun broadcast(line: String, useIs: Boolean=true, useRf: Boolean=true, selected: List<String> = emptyList(), rfPath: String?=null): Boolean {
        val packet=RadioPacket.parse(line.trimEnd('\r','\n')) ?: return false
        AprsProtocol.parse(packet.line(),System.currentTimeMillis())?.let {positions[it.callsign]=AprsSpatial.fix(it)}
        var sent=false
        val connection=client
        if(useIs && connection?.state?.value==AprsConnectionState.VERIFIED) {
            try {connection.sendBroadcast(call,line);traffic("APRS-IS","TX",line);sent=true}
            catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) { }
        }
        if(useRf && RadioPacket.address(call)) for((name,device) in devices) {
            val objectReport=packet.payload.firstOrNull() in listOf(';',')')
            if(selected.isNotEmpty() && name !in selected || !device.config.flag(if(objectReport) "objects" else "beacon")) continue
            val path=rfPath?.split(',')?.filter {it.isNotBlank()} ?: device.config.path("beacon_path")
            if(device.transmit(packet.copy(path=path))) sent=true
        }
        return sent
    }
    suspend fun shutdown() {
        try {coroutineScope {devices.values.forEach {port->launch {port.shutdown()}}}}
        finally {close()}
    }
    override fun close() {internetJob?.cancel();devices.values.forEach {it.close()};events.close();incoming.close();scope.cancel()}
}
