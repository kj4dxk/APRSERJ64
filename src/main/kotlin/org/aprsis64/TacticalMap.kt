// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import net.osmand.shared.aprs.*
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.*
import org.maplibre.compose.sources.*
import java.time.Instant
import java.util.Properties
import kotlin.math.abs

data class TacticalGeometry(val id:String,val shape:AprsShape,val opacity:Float=1f,val alert:WeatherAlert?=null)
private fun shapeJson(shapes:List<TacticalGeometry>,fill:Boolean):String = buildJsonObject {
 put("type","FeatureCollection");putJsonArray("features") {shapes.forEach {entry->
  val points=entry.shape.points
  if(points.size<2) return@forEach
  // Do not draw a spurious line across the world for a dateline crossing.
  val segments=mutableListOf<MutableList<AprsFix>>()
  points.forEach {p->if(segments.isEmpty() || abs(segments.last().last().longitude-p.longitude)>180)segments.add(mutableListOf());segments.last().add(p)}
  segments.filter {it.size>=2}.forEach segment@{segment->
   if(fill && (segments.size>1 || segment.size<4)) return@segment
   addJsonObject {
    put("type","Feature");putJsonObject("properties") {put("id",entry.id)}
    putJsonObject("geometry") {put("type",if(fill) "Polygon" else "LineString")
     val coordinates=JsonArray(segment.map {JsonArray(listOf(JsonPrimitive(it.longitude),JsonPrimitive(it.latitude)))})
     put("coordinates",if(fill) JsonArray(listOf(coordinates)) else coordinates)
    }
   }
  }
 }}
}.toString()

internal fun tacticalGeometry(stations:List<AprsStation>,selected:String?,paths:Map<String,HeardPath>,alerts:List<WeatherAlert>,p:Properties,now:Long):List<TacticalGeometry> = buildList {
 fun enabled(k:String)=p.getProperty("tactical.$k").toBoolean()
 val byCall=stations.associateBy {it.callsign}
 fun shape(id:String,points:List<AprsFix>,color:String,closed:Boolean=false,fill:Boolean=false,opacity:Float=1f,dashed:Boolean=false) {
  add(TacticalGeometry(id,AprsShape(points,closed,color,fill,dashed),opacity))
 }
 if(enabled("paths")) paths[selected]?.takeIf {now-it.time in 0..p.getProperty("tactical.path_age_seconds").toLong()*1000}?.let {path->
  val nodes=AprsSpatial.pathNodes(path.packet).toMutableList()
  if(path.port!="APRS-IS") nodes.add(p.getProperty("callsign"))
  nodes.zipWithNext().forEachIndexed {i,(a,b)->val from=byCall[a];val to=byCall[b]
   if(from!=null && to!=null)shape("path-$i",listOf(AprsSpatial.fix(from),AprsSpatial.fix(to)),"#f27b20")
  }
 }
 stations.forEach {s->
  val origin=AprsSpatial.fix(s);val details=AprsSpatial.details(s)
  if(s.ambiguityDigits>0) {
   val half=listOf(0.0,0.1,1.0,10.0,60.0)[s.ambiguityDigits]/120
   val south=(s.latitude-half).coerceAtLeast(-90.0);val north=(s.latitude+half).coerceAtMost(90.0)
   val west=(s.longitude-half).coerceAtLeast(-180.0);val east=(s.longitude+half).coerceAtMost(180.0)
   val points=listOf(AprsFix(south,west,now),AprsFix(north,west,now),AprsFix(north,east,now),AprsFix(south,east,now),AprsFix(south,west,now))
   shape("ambiguity-${s.callsign}",points,"#b78432",true,true,dashed=true)
  }
  if(enabled("dead_reckoning") && s.ambiguityDigits==0) AprsSpatial.predicted(s,now,p.getProperty("tactical.prediction_seconds").toInt())?.let {prediction->
   val freshness=(1-(now-s.heardAtMillis).toFloat()/(p.getProperty("tactical.prediction_seconds").toInt()*1000)).coerceIn(0f,1f)
   shape("prediction-${s.callsign}",listOf(origin,prediction),if(freshness>0.5) "#249c49" else "#d35229",opacity=0.3f+freshness*0.7f,dashed=true)
   shape("estimate-${s.callsign}",AprsSpatial.circle(prediction,35.0,12),"#d35229",true,opacity=0.3f+freshness*0.7f)
  }
  if(enabled("shapes")) (AprsSpatial.multiline(s) ?: AprsSpatial.area(s))?.let {add(TacticalGeometry("shape-${s.callsign}",it))}
  if(enabled("phg") && s.callsign==selected) details.coverage?.takeIf {it.strength==null}?.let {c->
   val center=c.bearing?.let {AprsSpatial.project(origin,it,c.radiusMeters/3)} ?: origin
   shape("phg",AprsSpatial.circle(center,c.radiusMeters),"#237ee6",true)
  }
  if(enabled("direction_finding") && now-s.heardAtMillis in 0..p.getProperty("tactical.df_age_seconds").toLong()*1000) {
   details.direction?.let {d->
    shape("bearing-${s.callsign}",listOf(origin,AprsSpatial.project(origin,d.bearing,d.rangeMeters)),"#ad45cf")
    shape("beam-${s.callsign}",listOf(origin)+ (0..20).map {AprsSpatial.project(origin,d.bearing-d.accuracyDegrees/2+it*d.accuracyDegrees/20,d.rangeMeters)}+origin,"#ad45cf",true,true)
   }
   details.coverage?.takeIf {it.strength!=null}?.let {c->
    val center=c.bearing?.let {AprsSpatial.project(origin,it,c.radiusMeters/3)} ?: origin
    // S0 is a non-detection contour; use a documented reference sensitivity.
    val radius=if(c.strength==0) p.getProperty("tactical.df_zero_radius_km").toDouble()*1000 else c.radiusMeters
    shape("df-circle-${s.callsign}",AprsSpatial.circle(center,radius),if(c.strength==0) "#222222" else "#ad45cf",true)
   }
  }
  if(enabled("horizon") && s.callsign==selected) details.altitudeMeters?.let {alt->
   val ground=p.getProperty("tactical.ground_meters").toDouble()
   if(alt>ground)shape("horizon",AprsSpatial.circle(origin,AprsSpatial.horizonMeters(alt,ground,p.getProperty("tactical.receiver_height_meters").toDouble())),"#008f96",true,dashed=true)
  }
 }
 if(enabled("direction_finding")) names(p.getProperty("tactical.df_measurements")).forEachIndexed {i,line->
  val (_,shape)=TacticalPreferences.manualBearing(line);add(TacticalGeometry("manual-df-$i",shape))
 }
 if(enabled("warning_polygons")) alerts.forEach {alert->
  if(runCatching {Instant.parse(alert.expires).toEpochMilli()<=now}.getOrDefault(false)) return@forEach
  val color=when(alert.severity){"Extreme"->"#b6002c";"Severe"->"#e53935";"Moderate"->"#e8a000";else->"#237ee6"}
  fun decode(geometry:JsonObject) {
   when(geometry["type"]?.jsonPrimitive?.content) {
    "GeometryCollection"->(geometry["geometries"] as? JsonArray)?.forEach {(it as? JsonObject)?.let(::decode)}
    "MultiPolygon"->(geometry["coordinates"] as? JsonArray)?.forEach {polygon->decode(buildJsonObject {put("type","Polygon");put("coordinates",polygon)})}
    "Polygon"->{
     val rings=geometry["coordinates"] as? JsonArray ?: return
     // Outlines preserve interior holes; only fill single-ring polygons.
     rings.forEach {ring->runCatching {
      val points=ring.jsonArray.take(20000).map {val c=it.jsonArray;AprsFix(c[1].jsonPrimitive.double,c[0].jsonPrimitive.double,0)}
      if(points.size>=4) add(TacticalGeometry(alert.id,AprsShape(points,true,color,rings.size==1),alert=alert))
     }}
    }
   }
  }
  alert.geometry?.let(::decode)
 }
}

@Composable internal fun TacticalLayers(geometry:List<TacticalGeometry>,onAlert:(WeatherAlert)->Unit) {
 // Batch alike features; per-station prediction does not create thousands of layers.
 geometry.groupBy {Triple(it.shape.color,it.shape.dashed,(it.opacity*5).toInt())}.forEach {(style,entries)->
  key(style) {
   // Prediction freshness and selection change group order. Layer IDs must
   // follow the stable Compose key, or disposing one group removes another.
   val layerId="${style.first.removePrefix("#")}-${style.second}-${style.third}"
   val color=Color(java.lang.Long.parseLong(style.first.removePrefix("#"),16) or 0xff000000L)
   val source=rememberGeoJsonSource(GeoJsonData.JsonString(shapeJson(entries,false)))
   LineLayer(id="tactical-lines-$layerId",source=source,color=const(color),width=const(2.dp),opacity=const(style.third/5f),
    dasharray=if(style.second) const(listOf(3,2)) else null,
    onClick={hits->hits.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content?.let {id->entries.firstOrNull {it.id==id}?.alert?.let(onAlert)};ClickResult.Consume})
   val filled=entries.filter {it.shape.filled && it.shape.closed}
   if(filled.isNotEmpty()) {
    val fill=rememberGeoJsonSource(GeoJsonData.JsonString(shapeJson(filled,true)))
    FillLayer(id="tactical-fill-$layerId",source=fill,color=const(color),opacity=const(0.12f),
     onClick={hits->hits.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content?.let {id->filled.firstOrNull {it.id==id}?.alert?.let(onAlert)};ClickResult.Consume})
   }
  }
 }
}
