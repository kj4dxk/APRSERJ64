// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.shared.aprs.*

data class BeaconState(val enabled: Boolean = true, val source: String = "Fixed", val detail: String = "Waiting for connection",
    val sentAt: Long? = null, val reason: String = "", val position: BeaconPosition? = null, val odometerMeters: Double = 0.0,
    val entities: List<AprsStation> = emptyList())

/** Session-owned broadcaster. Never queues old positions for a later connection. */
class BeaconController(private val account: String, private val client: AprsIsClient,
    private val config: BeaconConfiguration, parent: CoroutineScope, initialDistanceMeters: Double,
    private val saveDistance: (Double)->Unit, private val recenter: (BeaconPosition)->Unit) : AutoCloseable {
    private val scope=CoroutineScope(parent.coroutineContext+SupervisorJob(parent.coroutineContext[Job]))
    private val gps=if(config.text("position.source")!="fixed") GpsReceiver(config,scope) else null
    val state=MutableStateFlow(BeaconState(config.bool("beacon.enabled"),if(gps==null) "Fixed" else "GPS"))
    private val policy=AdaptiveBeaconPolicy(config.timing,initialDistanceMeters)
    private val manual=AtomicBoolean(false)
    private val statusNow=AtomicBoolean(false)
    private val odometerNow=AtomicBoolean(false)
    private val resetOdometer=AtomicBoolean(false)
    private var lastStatus: Long? = null
    private var lastComment: Long? = null
    private var commentIndex=if(config.comments.isEmpty()) 0 else config.integer("beacon.selected_comment")%config.comments.size
    private var connected=false
    private var reconnect=false
    private var gpsValid=false
    private var gpsEvent: String? = null
    private class Entity(val properties: Properties, timing: BeaconTiming) {
        val policy=AdaptiveBeaconPolicy(timing)
        var kills=0; var killedAt=0L
    }
    private val entities=config.entities.map { Entity(it,config.entityTiming(it)) }
    init {
        scope.launch {
            while(isActive) {
                try {tick(System.currentTimeMillis())}
                catch(cancelled: CancellationException) {throw cancelled}
                catch(_: Exception) {state.value=state.value.copy(detail="Beacon send failed; retrying")}
                delay(1000)
            }
        }
    }
    fun transmit() {manual.set(true)}
    fun transmitStatus() {statusNow.set(true)}
    fun reportOdometer(reset: Boolean=false) {odometerNow.set(true); resetOdometer.set(reset)}
    private fun position(now: Long): BeaconPosition? {
        if(gps==null) return config.fixed(now)
        val reading=gps.reading.value
        val position=reading.position
        val valid=reading.valid && position!=null && now-position.fix.timeMillis in 0..config.integer("position.maximum_age_seconds")*1000L &&
            position.fix.accuracyMeters<=config.number("position.maximum_accuracy_meters")
        if(valid!=gpsValid && config.bool("beacon.on_gps_change")) gpsEvent=if(valid) "GPS acquired" else "GPS lost"
        gpsValid=valid
        return position?.copy(current=valid)
    }
    private suspend fun tick(now: Long) {
        val p=position(now)
        val localEntities=config.entities.filter {it.getProperty("enabled").toBoolean() && it.getProperty("alive").toBoolean()}.mapNotNull {e ->
            val pos=if(e.getProperty("source")=="follow") p else config.entityPosition(e,now)
            pos?.let {AprsStation(e.getProperty("name"),it.fix.latitude,it.fix.longitude,e.getProperty("symbol_table").single(),
                e.getProperty("symbol").single(),e.getProperty("comment"),it.fix.timeMillis)}
        }
        state.value=state.value.copy(position=p,odometerMeters=policy.distanceMeters,entities=localEntities)
        if(!config.bool("beacon.enabled")) {manual.set(false); statusNow.set(false); odometerNow.set(false);state.value=state.value.copy(detail="Off");return}
        val verified=client.state.value==AprsConnectionState.VERIFIED
        if(verified && !connected) reconnect=config.bool("beacon.on_reconnect")
        connected=verified
        if(!verified) {manual.set(false);state.value=state.value.copy(detail="Waiting for verified login");return}
        if(p==null) {state.value=state.value.copy(detail="Waiting for GPS fix");return}
        if(!p.current && gpsEvent!="GPS lost") {state.value=state.value.copy(detail="Waiting for GPS fix");return}
        if(odometerNow.get()) {
            val km=(policy.distanceMeters/10).toInt()/100.0
            send(BeaconPackets.position(account,p,config.format,"Odometer=${km}km","Odometer"),now,p,"Odometer")
            odometerNow.set(false)
            if(resetOdometer.getAndSet(false)) {policy.resetDistance();saveDistance(0.0)}
            return
        }
        val force=manual.get()
        val decision=policy.evaluate(p,now,gps!=null,force,reconnect,gpsEvent)
        if(decision!=null) {
            val includeComment=config.bool("beacon.comment_enabled") && (force || lastComment==null || now-lastComment!!>=config.integer("beacon.comment_interval_seconds")*1000L)
            val comment=if(includeComment && config.comments.isNotEmpty()) config.comments[commentIndex%config.comments.size] else ""
            send(BeaconPackets.position(account,p,config.format,comment,decision.reason),now,p,decision.reason)
            policy.sent(p,now); manual.set(false); reconnect=false; gpsEvent=null
            runCatching {saveDistance(policy.distanceMeters)}
            if(comment.isNotEmpty()) {lastComment=now; if(config.bool("beacon.rotate_comments")) commentIndex=(commentIndex+1)%config.comments.size}
            if(config.bool("beacon.recenter_after_transmit")) recenter(p)
            return
        }
        if(!p.current) return
        if(statusNow.get() || config.bool("status.enabled") && config.integer("status.interval_seconds")>0 &&
            (lastStatus==null || now-lastStatus!!>=config.integer("status.interval_seconds")*1000L)) {
            send(BeaconPackets.status(account,config.text("status.text"),now,config.bool("status.timestamp"),
                if(config.bool("status.grid")) BeaconPackets.grid(p.fix) else ""),now,p,"Status")
            lastStatus=now; statusNow.set(false); return
        }
        for(entity in entities) {
            val e=entity.properties
            if(!e.getProperty("enabled").toBoolean()) continue
            val follows=e.getProperty("source")=="follow"
            val pos=if(follows) p else config.entityPosition(e,now)
            val alive=e.getProperty("alive").toBoolean()
            if(!alive && (entity.kills>=3 || now-entity.killedAt<30_000)) continue
            val due=if(!alive) BeaconDecision("Killed",100.0) else entity.policy.evaluate(pos,now,follows && gps!=null)
            if(due==null) continue
            val kind=e.getProperty("kind"); val name=e.getProperty("name"); val format=config.entityFormat(e)
            val packet=if(kind=="station") BeaconPackets.position(name,pos,format,e.getProperty("comment"),due.reason)
                else BeaconPackets.entity(account,name,kind=="item",alive,pos,format,e.getProperty("comment"),due.reason)
            send(packet,now,pos,"$name: ${due.reason}"); entity.policy.sent(pos,now)
            if(!alive) {entity.kills++; entity.killedAt=now}
            return
        }
        state.value=state.value.copy(detail=if(gps==null) "Fixed" else "GPS fix")
    }
    private suspend fun send(packet: String, now: Long, position: BeaconPosition, reason: String) {
        client.sendBroadcast(account,packet)
        state.value=state.value.copy(detail="Sent",sentAt=now,reason=reason)
        println("BEACON_TX $account $reason ${packet.trimEnd()}")
    }
    suspend fun beforeExit() {
        if(config.bool("beacon.enabled") && config.bool("odometer.report_on_exit") && client.state.value==AprsConnectionState.VERIFIED) {
            val now=System.currentTimeMillis(); val p=state.value.position?.takeIf {it.current} ?: return
            client.sendBroadcast(account,BeaconPackets.position(account,p,config.format,"Odometer=${(policy.distanceMeters/10).toInt()/100.0}km","Exit"))
        }
    }
    override fun close() {gps?.close();scope.cancel()}
}
