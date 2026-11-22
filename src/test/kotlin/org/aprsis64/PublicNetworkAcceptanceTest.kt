package org.aprsis64

import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import net.osmand.shared.aprs.AprsConnectionState
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Explicit opt-in: credentials stay in the operator's local portable settings. */
class PublicNetworkAcceptanceTest {
 @Test fun movingNmeaBeaconsAndTelemetry()=runBlocking {
  val location=System.getenv("APRS_LIVE_SETTINGS_HOME")
  assumeTrue("Public-network acceptance is opt-in",!location.isNullOrBlank())
  val saved=SettingsStore(Path.of(location!!)).values
  val base=saved.getProperty("callsign").substringBefore('-');val ca="$base-14";val cb="$base-15"
  val latitude=saved.getProperty("latitude").toDouble();val longitude=saved.getProperty("longitude").toDouble()
  java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress()).use {server->
   val simulator=launch(Dispatchers.IO) {
    server.accept().use {socket->
     val out=socket.getOutputStream()
     repeat(240){i->
      val now=java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
      fun coordinate(v:Double,width:Int):String {val a=kotlin.math.abs(v);val d=a.toInt();return String.format(java.util.Locale.US,"%0${width}d%07.4f",d,(a-d)*60)}
      val lat=latitude+i*0.000005
      val body="GPRMC,${now.format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"))},A,${coordinate(lat,2)},${if(lat>=0)"N" else "S"},${coordinate(longitude,3)},${if(longitude>=0)"E" else "W"},1.0,0.0,${now.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyy"))},,,A"
      val sum=body.fold(0){n,c->n xor c.code}.toString(16).padStart(2,'0')
      out.write("\$$body*$sum\r\n".toByteArray());out.flush();delay(1000)
     }
    }
   }
   val homes=(0..1).map {i->Files.createTempDirectory("aprs-moving-test-").also {home->PortableHome.prepare(home);SettingsStore(home).apply {
    values.setProperty("beacon.enabled",(i==0).toString());values.setProperty("beacon.radio","false")
    values.setProperty("beacon.comment","Simulated GPS acceptance test")
    values.setProperty("beacon.minimum_seconds","10");values.setProperty("beacon.maximum_seconds","30");values.setProperty("beacon.time_only","true")
    values.setProperty("position.source",if(i==0) "gps_tcp" else "fixed");values.setProperty("position.gps_endpoint","127.0.0.1:${server.localPort}")
    values.setProperty("telemetry.enabled",(i==0).toString());values.setProperty("telemetry.interval_seconds","60")
    values.setProperty("operating.monitor_capacity","10000");save()
   }}}
   try {AppModel(homes[1]).use {b->AppModel(homes[0]).use {a->
    assertTrue(b.connect(cb,saved.getProperty("passcode"),saved.getProperty("endpoint"),latitude,longitude,false))
    withTimeout(60000){while(b.state.value!=AprsConnectionState.VERIFIED)delay(250)}
    assertTrue(a.connect(ca,saved.getProperty("passcode"),saved.getProperty("endpoint"),latitude,longitude,false))
    withTimeout(60000){while(a.beaconState.value.position?.current!=true || a.state.value!=AprsConnectionState.VERIFIED)delay(250)}
    delay(185000)
    val tx=a.monitor.entries.value.filter {it.direction=="TX" && it.packet.startsWith("$ca>")}
    val rx=b.monitor.entries.value.filter {it.direction=="RX" && it.packet.startsWith("$ca>")}
    val payloads=rx.map {it.packet.substringAfter(':')}.toSet()
    assertTrue(tx.count {it.packet.substringAfter(':').startsWith("T#")}>=2,"Telemetry output")
    assertTrue(rx.count {it.packet.substringAfter(':').firstOrNull() in listOf('!','=','/','@')}>=4,"Position reception")
    assertTrue(b.stations.value.any {it.callsign==ca && it.latitude>latitude},"Moving station decoded")
    assertTrue(tx.all {it.packet.substringAfter(':') in payloads},"Every local packet independently received")
    assertFalse(rx.any {it.invalid},"Invalid received packet")
    println("MOVING_ACCEPTANCE: ${tx.size} outputs independently received, GPS movement and telemetry decoded")
   }}} finally {simulator.cancelAndJoin()}
  }
 }
 @Test fun hundredMessagesAcrossPublicNetwork()=runBlocking {
  val location=System.getenv("APRS_LIVE_SETTINGS_HOME")
  assumeTrue("Public-network acceptance is opt-in",!location.isNullOrBlank())
  val saved=SettingsStore(Path.of(location!!)).values
  val base=saved.getProperty("callsign").substringBefore('-')
  val password=saved.getProperty("passcode")
  require(base.isNotBlank() && password.isNotBlank())
  val homes=(0..1).map {Files.createTempDirectory("aprs-public-test-").also {home->
   PortableHome.prepare(home)
   SettingsStore(home).apply {
    values.setProperty("beacon.enabled","false")
    values.setProperty("autoConnect","false")
    values.setProperty("connectivity.failover","false")
    save()
   }
  }}
  AppModel(homes[0]).use {a->AppModel(homes[1]).use {b->
   val ca="$base-14";val cb="$base-15"
   val lat=saved.getProperty("latitude").toDouble();val lon=saved.getProperty("longitude").toDouble()
   assertTrue(a.connect(ca,password,saved.getProperty("endpoint"),lat,lon,false))
   assertTrue(b.connect(cb,password,saved.getProperty("endpoint"),lat,lon,false))
   withTimeout(90000){while(a.state.value!=AprsConnectionState.VERIFIED || b.state.value!=AprsConnectionState.VERIFIED)delay(500)}
   val run="QA"+System.currentTimeMillis().toString().takeLast(7)
   repeat(100){i->
    if(i%2==0)a.send(cb,"$run message ${i+1}") else b.send(ca,"$run message ${i+1}")
    delay(4000)
   }
   withTimeout(180000){while(a.database.rows(ca).count {it.outgoing && it.body.startsWith(run) && it.status=="ACKNOWLEDGED"}+b.database.rows(cb).count {it.outgoing && it.body.startsWith(run) && it.status=="ACKNOWLEDGED"}<100)delay(1000)}
   val received=a.database.rows(ca).filter {!it.outgoing && it.body.startsWith(run)}+b.database.rows(cb).filter {!it.outgoing && it.body.startsWith(run)}
   assertEquals(100,received.map {it.body}.distinct().size)
   println("PUBLIC_ACCEPTANCE: 100 distinct messages received and acknowledged across two application models")
  }}
 }
}
