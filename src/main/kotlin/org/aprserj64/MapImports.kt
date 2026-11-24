// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.nio.file.*
import javax.xml.stream.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.aprsfoundation.core.*

data class ImportedWaypoint(val name:String,val fix:AprsFix)
data class GpxContent(val shapes:List<TacticalGeometry>,val waypoints:List<ImportedWaypoint>)
class MapImports(private val model:AppModel,private val scope:CoroutineScope) {
 val content=MutableStateFlow(GpxContent(emptyList(),emptyList()));val status=MutableStateFlow("")
 internal val localMap=MutableStateFlow<LocalOsmMap?>(null)
 val localMapStatus=MutableStateFlow("")
 private var mapJob:Job?=null
 private var job:Job?=null
 fun reload(){
  refreshLocalMap()
  job?.cancel();job=scope.launch(Dispatchers.IO){
  val files=names(model.settings.getProperty("imports.gpx_files"));val limit=model.settings.getProperty("imports.maximum_points").toInt()
  runCatching {val data=files.map {parse(model.directory.resolve(it),limit)};require(data.sumOf {it.waypoints.size+it.shapes.sumOf {s->s.shape.points.size}}<=limit){"Too many GPX points"};GpxContent(data.flatMap {it.shapes},data.flatMap {it.waypoints})}
   .onSuccess {content.value=it;status.value="${it.shapes.size} tracks/routes, ${it.waypoints.size} waypoints"}
   .onFailure {content.value=GpxContent(emptyList(),emptyList());status.value=it.message?:"GPX import failed"}
 }}
 private fun refreshLocalMap() {
  mapJob?.cancel()
  val path=model.settings.getProperty("imports.osm_file")
  val bundled=model.settings.getProperty("imports.bundled_osm").toBoolean()
  mapJob=scope.launch(Dispatchers.IO) {
   val result=runCatching {
    if(path.isNotBlank()) OsmMaps.imported(model.directory.resolve(path))
    else if(bundled) OsmMaps.bundled() else null
   }
   ensureActive()
   localMap.value=result.getOrNull()
   localMapStatus.value=result.fold({if(it==null) "" else "Local map loaded"},{it.message ?: "OSM import failed"})
  }
 }
 companion object {
  fun parse(path:Path,limit:Int):GpxContent {
   require(Files.size(path)<=20_000_000){"GPX file exceeds 20 MB"}
   val factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false)
   val shapes=mutableListOf<TacticalGeometry>();val waypoints=mutableListOf<ImportedWaypoint>();var count=0
   Files.newInputStream(path).use {input->val xml=factory.createXMLStreamReader(input)
    var points=mutableListOf<AprsFix>();var point:AprsFix?=null;var pointKind="";var name="";var text="";var capture="";var segmentName=""
    fun finish(){if(points.size>=2)shapes.add(TacticalGeometry("gpx-${path.fileName}-${shapes.size}-$segmentName",AprsShape(points.toList(),false,"#c454d1")));points=mutableListOf()}
    try{while(xml.hasNext()){when(xml.next()) {
     XMLStreamConstants.DTD->error("GPX DTD is not allowed")
     XMLStreamConstants.START_ELEMENT->when(xml.localName){
      "trkseg","rte"->{finish();segmentName=""}
      "trkpt","rtept","wpt"->{require(++count<=limit){"Too many GPX points"};point=AprsFix(xml.getAttributeValue(null,"lat").toDouble(),xml.getAttributeValue(null,"lon").toDouble(),0);pointKind=xml.localName;name=""}
      "name","time"->{capture=xml.localName;text=""}
     }
     XMLStreamConstants.CHARACTERS->if(capture.isNotEmpty() && text.length<1024)text+=xml.text
     XMLStreamConstants.END_ELEMENT->when(xml.localName){
      "name"->{if(point!=null)name=text else segmentName=text;capture=""}
      "time"->{point=point?.copy(timeMillis=runCatching {java.time.Instant.parse(text).toEpochMilli()}.getOrDefault(0));capture=""}
      "trkpt","rtept","wpt"->{point?.let {if(pointKind=="wpt")waypoints.add(ImportedWaypoint(name.ifBlank {"Waypoint ${waypoints.size+1}"},it))else points.add(it)};point=null;pointKind=""}
      "trkseg","rte"->finish()
     }
    }};finish()}finally{xml.close()}
   };return GpxContent(shapes,waypoints)
  }
 }
}
