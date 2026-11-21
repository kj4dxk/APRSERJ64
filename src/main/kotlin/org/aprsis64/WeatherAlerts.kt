// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import java.net.URI
import java.net.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*

data class WeatherAlert(val id:String,val event:String,val severity:String,val headline:String,val description:String,val expires:String,
 val geometry:JsonObject?=null,val zones:List<String> = emptyList())
class WeatherAlerts(private val setting:(String)->String,private val scope:CoroutineScope):AutoCloseable {
 val alerts=MutableStateFlow<List<WeatherAlert>>(emptyList())
 val status=MutableStateFlow("Weather alerts off")
 private val client=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()
 private var job:Job?=null
 private val zoneCache=linkedMapOf<String,JsonObject>()
 fun configure(demo:Boolean){job?.cancel();job=null
  if(demo || !setting("weather.enabled").toBoolean()){alerts.value=emptyList();status.value="Weather alerts off";return}
  job=scope.launch(Dispatchers.IO){while(isActive){runInterruptible {refresh()};delay(setting("weather.refresh_seconds").toLong()*1000)}}
 }
 private fun refresh(){try {
  val url=setting("weather.alerts_url").replace("{lat}",setting("position.latitude")).replace("{lon}",setting("position.longitude"))
  val request=HttpRequest.newBuilder(URI(url)).header("User-Agent","APRSIS64 (https://github.com/kj4dxk/APRSIS64)").header("Accept","application/geo+json").timeout(java.time.Duration.ofSeconds(15)).build()
  val response=client.send(request,HttpResponse.BodyHandlers.ofInputStream())
  val bytes=response.body().use {it.readNBytes(4_000_001)};require(response.statusCode()==200 && bytes.size<=4_000_000)
  var budget=20
  alerts.value=parse(String(bytes,Charsets.UTF_8)).map {alert->
   if(alert.geometry!=null || !setting("tactical.warning_polygons").toBoolean()) alert else {
    val shapes=alert.zones.take(20).mapNotNull {zone->zoneCache[zone] ?: runCatching {
     val uri=URI(zone);require(uri.scheme=="https" && uri.host==URI(url).host && budget-- > 0)
     val response=client.send(HttpRequest.newBuilder(uri).header("User-Agent","APRSIS64").timeout(java.time.Duration.ofSeconds(5)).build(),HttpResponse.BodyHandlers.ofInputStream())
     val data=response.body().use {it.readNBytes(2_000_001)};require(response.statusCode()==200 && data.size<=2_000_000)
     val geometry=Json.parseToJsonElement(String(data,Charsets.UTF_8)).jsonObject["geometry"] as? JsonObject ?: error("No boundary")
     if(zoneCache.size>=100) zoneCache.remove(zoneCache.keys.first());zoneCache[zone]=geometry;geometry
    }.getOrNull()}
    alert.copy(geometry=shapes.takeIf {it.isNotEmpty()}?.let {buildJsonObject {put("type","GeometryCollection");put("geometries",JsonArray(it))}})
   }
  };status.value="Updated ${java.time.LocalTime.now().withNano(0)} · ${alerts.value.size} active alerts"
 }catch(_:Exception){status.value="Alert update failed; previous results may be stale"}}
 companion object {
  fun parse(text:String):List<WeatherAlert> = Json.parseToJsonElement(text).jsonObject["features"]!!.jsonArray.take(200).map {feature->
   val p=feature.jsonObject["properties"]!!.jsonObject
   fun value(k:String)=p[k]?.jsonPrimitive?.contentOrNull.orEmpty()
   WeatherAlert(value("id"),value("event"),value("severity"),value("headline"),value("description"),value("expires"),
    feature.jsonObject["geometry"] as? JsonObject,(p["affectedZones"] as? JsonArray)?.mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.orEmpty())
  }
 }
 override fun close(){job?.cancel();client.close()}
}
