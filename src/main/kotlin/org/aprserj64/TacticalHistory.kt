// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.nio.file.Path
import java.sql.DriverManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import net.osmand.shared.aprs.*

data class HeardPath(val call:String,val port:String,val packet:RadioPacket,val time:Long)
data class DxRecord(val port:String,val call:String,val latitude:Double,val longitude:Double,val kilometers:Double,val time:Long)

/** Radio observations retain their receiving port; Internet packets can never become RF DX. */
class TacticalHistory(home:Path,parent:CoroutineScope,private val setting:(String)->String):AutoCloseable {
 val paths=MutableStateFlow<Map<String,HeardPath>>(emptyMap())
 val dx=MutableStateFlow<List<DxRecord>>(emptyList())
 val status=MutableStateFlow("")
 private data class Input(val port:String,val line:String,val own:AprsFix?,val time:Long)
 private val input=Channel<Input>(1024)
 private val db=DriverManager.getConnection("jdbc:sqlite:${home.resolve("data/radio-history.sqlite")}")
 private val worker:Job
 init {
  db.createStatement().use {it.execute("PRAGMA journal_mode=WAL");it.execute("PRAGMA busy_timeout=5000");it.execute("CREATE TABLE IF NOT EXISTS dx(port TEXT,call TEXT,lat REAL,lon REAL,km REAL,time INTEGER,PRIMARY KEY(port,call))")}
  prune();readDx()
  worker=parent.launch(Dispatchers.IO) {for(e in input) try {
   val packet=RadioPacket.parse(e.line) ?: continue
   val station=AprsProtocol.parse(e.line,e.time)
   val key=station?.callsign ?: packet.source
   paths.value=(paths.value.filterValues {e.time-it.time<=setting("tactical.path_age_seconds").toLong()*1000}+
    (key to HeardPath(key,e.port,packet,e.time))).entries.sortedByDescending {it.value.time}.take(2000).associate {it.toPair()}
   if(e.port=="APRS-IS" || station==null || station.callsign!=packet.source || e.own==null || !AprsSpatial.directlyHeard(packet)) continue
   val distance=AdaptiveBeaconPolicy.distance(e.own,AprsSpatial.fix(station))/1000
   if(distance<setting("tactical.dx_minimum_km").toDouble()) continue
   prune()
   db.prepareStatement("INSERT INTO dx(port,call,lat,lon,km,time) VALUES(?,?,?,?,?,?) ON CONFLICT(port,call) DO UPDATE SET lat=excluded.lat,lon=excluded.lon,km=excluded.km,time=excluded.time WHERE excluded.km>=dx.km").use {
    it.setString(1,e.port);it.setString(2,station.callsign);it.setDouble(3,station.latitude);it.setDouble(4,station.longitude);it.setDouble(5,distance);it.setLong(6,e.time);it.executeUpdate()
   }
   prune();readDx()
  }catch(_:Exception){status.value="Unable to save radio history"}}
 }
 private fun prune() {
  db.prepareStatement("DELETE FROM dx WHERE time < ?").use {it.setLong(1,System.currentTimeMillis()-setting("tactical.dx_retention_days").toLong()*86400000);it.executeUpdate()}
  db.createStatement().use {it.executeUpdate("DELETE FROM dx WHERE rowid NOT IN (SELECT rowid FROM dx ORDER BY time DESC LIMIT 10000)")}
 }
 private fun readDx() {
  val result=mutableListOf<DxRecord>()
  db.createStatement().use {it.executeQuery("SELECT port,call,lat,lon,km,time FROM dx ORDER BY km DESC").use {r->while(r.next())result.add(DxRecord(r.getString(1),r.getString(2),r.getDouble(3),r.getDouble(4),r.getDouble(5),r.getLong(6)))}}
  dx.value=result
 }
 fun offer(port:String,line:String,own:AprsFix?) {if(!input.trySend(Input(port,line,own,System.currentTimeMillis())).isSuccess)status.value="Radio history queue full; some observations omitted"}
 fun summary():String?=dx.value.firstOrNull {System.currentTimeMillis()-it.time<=setting("tactical.dx_retention_days").toLong()*86400000}?.let {"DX ${it.call} ${"%.1f".format(java.util.Locale.US,it.kilometers)}km"}
 suspend fun finish(){input.close();worker.join();db.close()}
 override fun close(){input.close();worker.cancel();db.close()}
}
