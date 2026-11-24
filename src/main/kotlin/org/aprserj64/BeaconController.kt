// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.aprsfoundation.core.*
import kotlinx.serialization.json.*

data class BeaconState(val enabled: Boolean = true, val source: String = "Fixed", val detail: String = "Waiting for connection",
    val sentAt: Long? = null, val reason: String = "", val position: BeaconPosition? = null, val odometerMeters: Double = 0.0,
    val entities: List<AprsStation> = emptyList())

/** Session-owned broadcaster. Never queues old positions for a later connection. */
class BeaconController(private val account: String, private val ports: RadioPorts,
    private val config: BeaconConfiguration, parent: CoroutineScope, initialDistanceMeters: Double,
    private val saveDistance: (Double)->Unit, private val journal:BeaconJournal?=null, private val recenter: (BeaconPosition)->Unit) : AutoCloseable {
    private val scope=CoroutineScope(parent.coroutineContext+SupervisorJob(parent.coroutineContext[Job]))
    private val gps=if(config.text("position.source") !in listOf("fixed","port")) GpsReceiver(config,scope) else null
    val state=MutableStateFlow(BeaconState(config.bool("beacon.enabled"),if(config.text("position.source")=="fixed") "Fixed" else "GPS"))
    private val policy=AdaptiveBeaconPolicy(config.timing,initialDistanceMeters)
    private var traveled=initialDistanceMeters
    private var lastFix:AprsFix?=null
    private var savedDistanceAt=0L
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
    private val entities=config.entities.map { p->Entity(p,config.entityTiming(p)).also {e->
        if(p.getProperty("alive").toBoolean())journal?.revive(p)
        else journal?.read(p)?.let {(count,time)->e.kills=count;e.killedAt=time}
    } }
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
    var extraStatus:()->String? = {null}
    fun transmitStatus() {statusNow.set(true)}
    fun reportOdometer(reset: Boolean=false) {odometerNow.set(true); resetOdometer.set(reset)}
    private fun position(now: Long): BeaconPosition? {
        if(config.text("position.source")=="fixed") return config.fixed(now).copy(current=config.bool("position.fixed_confirmed"))
        val reading=if(config.text("position.source")=="port") ports.gps(config.text("position.port_name")) else gps!!.reading.value
        val position=reading.position
        val valid=reading.valid && position!=null && now-position.fix.timeMillis in 0..config.integer("position.maximum_age_seconds")*1000L &&
            position.fix.accuracyMeters<=config.number("position.maximum_accuracy_meters")
        if(!valid) gpsEvent=null
        else if(!gpsValid && config.bool("beacon.on_gps_change")) gpsEvent="GPS acquired"
        gpsValid=valid
        return position?.copy(current=valid)
    }
    private suspend fun tick(now: Long) {
        val p=position(now)
        if(config.text("position.source")!="fixed" && p?.current==true) {
            val previous=lastFix
            if(previous==null)lastFix=p.fix else if(p.fix.timeMillis>previous.timeMillis) {
                val elapsed=p.fix.timeMillis-previous.timeMillis
                val meters=AdaptiveBeaconPolicy.distance(previous,p.fix)
                if(elapsed>config.integer("position.maximum_age_seconds")*1000L)lastFix=p.fix
                else if(meters>=config.number("odometer.minimum_meters")) {
                    if(meters/(elapsed/1000.0)<=config.number("odometer.maximum_kph")/3.6)traveled+=meters
                    lastFix=p.fix
                }
            }
            if(now-savedDistanceAt>=10000) {saveDistance(traveled);savedDistanceAt=now}
        }
        val localEntities=config.entities.filter {it.getProperty("enabled").toBoolean() && it.getProperty("alive").toBoolean()}.mapNotNull {e ->
            val pos=if(e.getProperty("source")=="follow") p else config.entityPosition(e,now)
            pos?.let {AprsStation(e.getProperty("name"),it.fix.latitude,it.fix.longitude,config.entityFormat(e).table,
                config.entityFormat(e).symbol,ObjectGeometry.comment(e),it.fix.timeMillis,it.course,it.speedKnots,it.altitudeMeters)}
        }
        state.value=state.value.copy(position=p,odometerMeters=traveled,entities=localEntities)
        if(!config.bool("beacon.enabled")) {manual.set(false); statusNow.set(false); odometerNow.set(false);state.value=state.value.copy(detail="Off");return}
        val verified=ports.beaconAvailable(config.bool("beacon.internet"),config.bool("beacon.radio"))
        if(verified && !connected) reconnect=config.bool("beacon.on_reconnect")
        connected=verified
        if(!verified) {manual.set(false);state.value=state.value.copy(detail="Waiting for a station beacon output")}
        val waiting=if(config.text("position.source")=="fixed") "Confirm your fixed beacon position in Settings" else "Waiting for GPS fix"
        if(p?.current!=true) state.value=state.value.copy(detail=waiting)
        if(verified && p?.current==true) {
            if(odometerNow.get()) {
                val km=(traveled/10).toInt()/100.0
                if(send(BeaconPackets.position(account,p,config.format,"Odometer=${km}km","Odometer"),now,p,"Odometer")) {
                    odometerNow.set(false)
                    if(resetOdometer.getAndSet(false)) {policy.resetDistance();traveled=0.0;lastFix=p.fix;saveDistance(0.0)}
                    return
                }
            }
            val force=manual.get()
            val decision=policy.evaluate(p,now,config.text("position.source")!="fixed",force,reconnect,gpsEvent)
            if(decision!=null) {
                val includeComment=config.bool("beacon.comment_enabled") && (force || lastComment==null || now-lastComment!!>=config.integer("beacon.comment_interval_seconds")*1000L)
                val comment=if(includeComment && config.comments.isNotEmpty()) config.comments[commentIndex%config.comments.size] else ""
                if(send(BeaconPackets.position(account,p,config.format,comment,decision.reason),now,p,decision.reason)) {
                    policy.sent(p,now);manual.set(false);reconnect=false;gpsEvent=null
                    runCatching {saveDistance(traveled)}
                    if(comment.isNotEmpty()) {lastComment=now;if(config.bool("beacon.rotate_comments")) commentIndex=(commentIndex+1)%config.comments.size}
                    if(config.bool("beacon.recenter_after_transmit")) recenter(p)
                    return
                }
            }
            if(p.current && (statusNow.get() || config.bool("status.enabled") && config.integer("status.interval_seconds")>0 &&
                (lastStatus==null || now-lastStatus!!>=config.integer("status.interval_seconds")*1000L))) {
                if(send(BeaconPackets.status(account,(config.text("status.text")+" "+extraStatus().orEmpty()).trim().take(200),now,config.bool("status.timestamp"),
                    if(config.bool("status.grid")) BeaconPackets.grid(p.fix) else ""),now,p,"Status")) {
                    lastStatus=now;statusNow.set(false);return
                }
            }
        }
        for(entity in entities) {
            val e=entity.properties
            if(!e.getProperty("enabled").toBoolean() || e.getProperty("query_only","false").toBoolean() && e.getProperty("alive").toBoolean()) continue
            val follows=e.getProperty("source")=="follow"
            val pos=if(follows) p?.takeIf {it.current} ?: continue else config.entityPosition(e,now)
            val alive=e.getProperty("alive").toBoolean()
            if(!alive && (entity.kills>=3 || now-entity.killedAt<30_000)) continue
            val due=if(!alive) BeaconDecision("Killed",100.0) else entity.policy.evaluate(pos,now,follows && config.text("position.source")!="fixed")
            if(due==null) continue
            val kind=e.getProperty("kind"); val name=e.getProperty("name"); val format=config.entityFormat(e)
            val packet=if(kind=="station") BeaconPackets.position(name,pos,format,ObjectGeometry.comment(e),due.reason)
                else BeaconPackets.entity(account,name,kind=="item",alive,pos,format,ObjectGeometry.comment(e),due.reason)
            if(!send(packet,now,pos,"$name: ${due.reason}",e)) continue
            entity.policy.sent(pos,now)
            if(!alive) {entity.kills++; entity.killedAt=now;journal?.write(e,entity.kills,now)}
            return
        }
        if(!verified) return
        state.value=state.value.copy(detail=if(p?.current!=true) waiting else if(config.text("position.source")=="fixed") "Fixed" else "GPS fix")
    }
    private suspend fun send(packet: String, now: Long, position: BeaconPosition, reason: String, entity: Properties?=null): Boolean {
        val useIs=entity?.getProperty("internet")?.toBooleanStrict() ?: config.bool("beacon.internet")
        val useRf=entity?.getProperty("radio")?.toBooleanStrict() ?: config.bool("beacon.radio")
        val selected=entity?.getProperty("ports")?.let {kotlinx.serialization.json.Json.parseToJsonElement(it).jsonArray.map {v->v.jsonPrimitive.content}} ?: emptyList()
        if(!ports.broadcast(packet,useIs,useRf,selected,entity?.getProperty("rf_path")?.ifBlank {null})) return false
        state.value=state.value.copy(detail="Sent",sentAt=now,reason=reason)
        println("BEACON_TX $account $reason ${packet.trimEnd()}")
        return true
    }
    suspend fun beforeExit() {
        saveDistance(traveled)
        if(config.bool("beacon.enabled") && config.bool("odometer.report_on_exit") && ports.available()) {
            val now=System.currentTimeMillis(); val p=state.value.position?.takeIf {it.current} ?: return
            ports.broadcast(BeaconPackets.position(account,p,config.format,"Odometer=${(traveled/10).toInt()/100.0}km","Exit"),config.bool("beacon.internet"),config.bool("beacon.radio"))
        }
    }
    fun gpsSky()=gps?.sky?.value
    override fun close() {gps?.close();scope.cancel()}
}
