// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.util.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.osmand.shared.aprs.*

/** Routing decisions are serialized; socket/serial readers never wait for their own transmit lock. */
class RadioPorts(val call: String, values: Properties, configs: List<Properties>, parent: CoroutineScope, private val traffic: (String,String,String)->Unit = {_,_,_->}) : AutoCloseable, AprsMessageTransport {
    private val scope=CoroutineScope(parent.coroutineContext+SupervisorJob(parent.coroutineContext[Job]))
    private val router=RadioRouter(call,PortPreferences.rules(values))
    private val routing=Mutex()
    private val events=Channel<Pair<String,RadioPacket>>(128)
    private val incoming=Channel<AprsMessage>(128)
    override val messages=incoming.receiveAsFlow()
    private val heardForChat=java.util.concurrent.ConcurrentHashMap<String,Pair<String,Long>>()
    private val heardMillis=values.getProperty("radio.heard_seconds").toLong()*1000
    private val devices=linkedMapOf<String,DevicePort>()
    private val fixes=java.util.concurrent.ConcurrentHashMap<String,GpsReading>()
    private val store=AprsStationStore()
    val stations=MutableStateFlow<List<AprsStation>>(emptyList())
    val statuses=MutableStateFlow<List<PortStatus>>(emptyList())
    @Volatile private var client: AprsIsClient?=null
    private var internetJob: Job?=null
    init {
        configs.filter {it.getProperty("enabled").toBoolean()}.forEach {props->
            val config=PortConfig(props)
            devices[config.name]=DevicePort(config,scope,{events.trySend(config.name to it).isSuccess},{reading->
                fixes[config.name]=if(!reading.valid && reading.position==null) fixes[config.name]?.copy(valid=false,detail=reading.detail) ?: reading else reading
            },{packet->traffic(config.name,"TX",packet.line())})
        }
        scope.launch {for((name,packet) in events) routing.withLock {
            val now=System.currentTimeMillis();val device=devices[name] ?: return@withLock
            traffic(name,"RX",packet.line())
            store.receive(packet.line(),now);stations.value=store.snapshot(now)
            router.observeRf(name,packet,now)
            if(!packet.payload.startsWith('}')) {
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
                router.toInternet(packet,config.canTransmit && config.flag("is_to_rf") && router.messageReachable(name,packet.source,now))?.let {
                    try {isClient.sendGated(call,it);traffic("APRS-IS","TX",it);router.transmitted("igate",packet,now)}
                    catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) { /* No offline queue. */ }
                }
            }
        } }
        scope.launch {while(isActive) {statuses.value=devices.values.map {it.status.value};routing.withLock {stations.value=store.snapshot(System.currentTimeMillis())};delay(1000)}}
    }
    fun attach(value: AprsIsClient?) {
        internetJob?.cancel();client=value
        if(value!=null) internetJob=scope.launch {
            launch {value.messages.collect {incoming.send(it)}}
            value.packets.collect {line->
            val packet=RadioPacket.parse(line) ?: return@collect
            traffic("APRS-IS","RX",line)
            routing.withLock {
                val now=System.currentTimeMillis();router.observeInternet(packet,now)
                if(!RadioPacket.address(call)) return@withLock
                for((name,device) in devices) {
                    val config=device.config;val action="is:$name"
                    if(!config.flag("is_to_rf") || !config.canTransmit || config.flag("passall") || router.duplicate(action,packet,now)) continue
                    router.toRadio(name,packet,now,config.path("message_path"),config.list("gate_calls"),config.list("gate_objects"))?.let {
                        if(device.transmit(it)) {router.transmitted(action,packet,now);router.gatedToRadio(name,packet,now)}
                    }
                }
            }
        } }
    }
    fun gps(name: String)=fixes[name] ?: GpsReading(detail="Waiting for GPS port")
    override fun canSend()=client?.canSend()==true || RadioPacket.address(call) && devices.values.any {it.ready && it.config.canTransmit && it.config.flag("messages")}
    override suspend fun sendMessage(account: String, recipient: String, text: String, id: String) {
        check(account==call);sendChat(recipient,AprsMessages.packet(account,recipient,text,id))
    }
    override suspend fun acknowledge(account: String, recipient: String, id: String) {
        check(account==call);sendChat(recipient,AprsMessages.ack(account,recipient,id))
    }
    private suspend fun sendChat(recipient: String, line: String) {
        val candidates=devices.values.filter {it.ready && it.config.canTransmit && it.config.flag("messages")}
        val heard=heardForChat[recipient]?.takeIf {System.currentTimeMillis()-it.second in 0..heardMillis}
        val device=candidates.firstOrNull {it.config.name==heard?.first}
        val packet=requireNotNull(RadioPacket.parse(line.trimEnd('\r','\n')))
        if(device!=null) {check(device.transmit(packet.copy(path=device.config.path("message_path"))));return}
        val connection=client
        if(connection?.canSend()==true) {
            // Message framing is already validated above, including its APRS destination.
            connection.sendBroadcast(call,line);traffic("APRS-IS","TX",line);return
        }
        val fallback=checkNotNull(candidates.firstOrNull()) {"No messaging output"}
        check(fallback.transmit(packet.copy(path=fallback.config.path("message_path"))))
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
    suspend fun shutdown() {devices.values.forEach {it.shutdown()};close()}
    override fun close() {internetJob?.cancel();devices.values.forEach {it.close()};events.close();incoming.close();scope.cancel()}
}
