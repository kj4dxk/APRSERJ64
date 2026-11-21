// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*
import java.time.Instant
import java.util.Properties
import kotlin.math.*

@Composable fun AdvancedTools(model:AppModel,tab:String) {
 val scope=rememberCoroutineScope();val revision by model.settingsRevision.collectAsState()
 var path by remember(tab){mutableStateOf("")};var result by remember(tab){mutableStateOf("")};var busy by remember(tab){mutableStateOf(false)}
 fun work(action:suspend ()->String){busy=true;scope.launch{try{result=action()}catch(e:Exception){result=e.message?:"Unable to complete operation"}finally{busy=false}}}
 when(tab) {
  "Weather boundaries"->{
   Text("Import longitude/latitude SHP + DBF files, or a ZIP containing them")
   OutlinedTextField(path,{path=it},label={Text("Boundary file path")},modifier=Modifier.fillMaxWidth())
   Row {TextButton(onClick={work {
    var selected:String?=null
    withContext(Dispatchers.IO){javax.swing.SwingUtilities.invokeAndWait {val dialog=java.awt.FileDialog(null as java.awt.Frame?,"Select SHP or ZIP",java.awt.FileDialog.LOAD);try{dialog.isVisible=true;if(dialog.file!=null)selected=java.nio.file.Path.of(dialog.directory,dialog.file).toString()}finally{dialog.dispose()}}}
    if(selected!=null)path=selected!!;""
   }},enabled=!busy){Text("Browse")}
   Button(onClick={work {
    val files=withContext(Dispatchers.IO){BoundaryImport.copy(model.directory,model.directory.resolve(path),model.settings.getProperty("legacyweather.code_field"))}
    check(model.updateFeature("legacyweather.shapefiles",encodedNames((names(model.settings.getProperty("legacyweather.shapefiles"))+files).distinct())));"Imported ${files.size} boundary datasets"
   }},enabled=path.isNotBlank()&&!busy){Text("Import boundaries")}}
   names(model.settings.getProperty("legacyweather.shapefiles")).forEach {file->Row{Text(file,Modifier.weight(1f));TextButton(onClick={model.updateFeature("legacyweather.shapefiles",encodedNames(names(model.settings.getProperty("legacyweather.shapefiles"))-file))}){Text("Remove layer")}}}
   Text("DBF code column and APRS warning reception: Settings → APRS weather")
  }

  "GPX import"->{
   val status by model.imports.status.collectAsState();val content by model.imports.content.collectAsState()
   Text(status);OutlinedTextField(path,{path=it},label={Text("GPX file path")},modifier=Modifier.fillMaxWidth())
   Row {Button(onClick={work{withContext(Dispatchers.IO){MapImports.parse(model.directory.resolve(path),model.settings.getProperty("imports.maximum_points").toInt())};check(model.updateFeature("imports.gpx_files",encodedNames((names(model.settings.getProperty("imports.gpx_files"))+path).distinct())));"GPX file added"}},enabled=path.isNotBlank()&&!busy){Text("Add file")};TextButton(onClick={model.imports.reload()}){Text("Reload files")}}
   names(model.settings.getProperty("imports.gpx_files")).forEach {file->Row {Text(file,Modifier.weight(1f));TextButton(onClick={model.updateFeature("imports.gpx_files",encodedNames(names(model.settings.getProperty("imports.gpx_files"))-file))}){Text("Remove layer")}}}
   content.waypoints.take(100).forEach {w->Text("${w.name}: ${w.fix.latitude}, ${w.fix.longitude}")}
  }
  "Satellites"->{
   val status by model.satellites.status.collectAsState();val positions by model.satellites.positions.collectAsState();val passes by model.satellites.passes.collectAsState()
   Text(status)
   Row {Checkbox(model.settings.getProperty("satellites.enabled").toBoolean(),{model.updateFeature("satellites.enabled",it.toString())});Text("Show tracked satellites")}
   OutlinedTextField(path,{path=it},label={Text("TLE file path")},modifier=Modifier.fillMaxWidth())
   Row {Button(onClick={result=if(model.updateFeature("satellites.file",path))"Loading TLE file" else "Check path"},enabled=path.isNotBlank()){Text("Load file")};TextButton(onClick={work{model.satellites.download();"TLE file downloaded"}},enabled=!busy){Text("Download TLEs")};TextButton(onClick={work{model.satellites.predict();"Pass predictions ready"}},enabled=!busy){Text("Predict passes")}}
   Text("Names, source URL, age limit and prediction window: Settings → Satellites")
   positions.forEach {s->Text(s.name,color=MaterialTheme.colors.primary);Text("Az %.1f° · El %.1f° · range %.0f km · altitude %.0f km".format(s.azimuth,s.elevation,s.rangeKm,s.altitudeKm));Text("TLE epoch ${Instant.ofEpochMilli(s.epoch)}");Divider()}
   if(passes.isNotEmpty())Text("Passes (UTC, 30-second sampling)")
   passes.take(100).forEach {p->Text("${p.name}: ${Instant.ofEpochMilli(p.rise)} — ${Instant.ofEpochMilli(p.set)} · max %.1f°".format(p.maximumElevation))}
  }
  "GPS sky"->{
   var skies by remember {mutableStateOf(model.gpsSky())}
   LaunchedEffect(Unit){while(true){skies=model.gpsSky();delay(1000)}}
   if(skies.isEmpty())Text("Waiting for GSV/GSA sentences from the configured GPS input")
   skies.forEach {(port,sky)->
    Text("$port · ${sky.fixType}D fix · PDOP ${sky.pdop?:"—"} · HDOP ${sky.hdop?:"—"} · VDOP ${sky.vdop?:"—"}")
    val fresh=System.currentTimeMillis()-sky.time<=30000
    if(!fresh)Text("Satellite data is stale",color=MaterialTheme.colors.error)
    Canvas(Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF14262C))){
     val center=Offset(size.width/2,size.height/2);val radius=min(size.width,size.height)/2-12
     listOf(1f,2f/3,1f/3).forEach {r->drawCircle(Color.Gray,radius*r,center,style=androidx.compose.ui.graphics.drawscope.Stroke(1f))}
     drawLine(Color.Gray,center-Offset(radius,0f),center+Offset(radius,0f));drawLine(Color.Gray,center-Offset(0f,radius),center+Offset(0f,radius))
     if(fresh)sky.satellites.forEach {s->if(s.azimuth!=null && s.elevation!=null){val angle=Math.toRadians(s.azimuth!!);val distance=radius*(90-s.elevation!!)/90;val point=center+Offset((sin(angle)*distance).toFloat(),(-cos(angle)*distance).toFloat());drawCircle(if(s.used)Color(0xFF72E4C3)else Color(0xFFFFC078),5f,point)}}
    }
    Text("North at top · green: used in fix · amber: visible")
    sky.satellites.sortedWith(compareBy({it.constellation},{it.id})).forEach {s->Text("${s.constellation} ${s.id} · az ${s.azimuth?:"—"}° · el ${s.elevation?:"—"}° · SNR ${s.snr?:"—"} · ${if(s.used)"used" else "visible"}")}
   }
  }
  "Local sensors"->{
   Text("Weather: ${model.settings.getProperty("weatherstation.file")}")
   Text("Telemetry: ${model.settings.getProperty("telemetry.input_file")}")
   Text("Weather JSON uses temperature_c, wind_degrees, wind_kph, gust_kph, humidity, pressure_hpa, rain_hour_mm and rain_day_mm. Missing readings are omitted.")
   Text("Telemetry JSON contains named raw values from 0 to 255. Select input:name as a telemetry channel source. Files must be refreshed by your sensor collector.")
   Text("Enable transmission and choose timing, file freshness and routes in Settings → Weatherstation / Telemetry.")
   Button(onClick={work{withContext(Dispatchers.IO){LocalSensors.weather(model,System.currentTimeMillis())};"Weather input is valid and fresh"}},enabled=!busy){Text("Read weather input")}
  }
 }
 if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(result.isNotBlank())Text(result)
}

@Composable fun FrequencyEditor(model:AppModel) {
 var name by remember{mutableStateOf("")};var mhz by remember{mutableStateOf("")};var tone by remember{mutableStateOf("Toff")};var offset by remember{mutableStateOf("+000")};var range by remember{mutableStateOf("10")};var latitude by remember{mutableStateOf(model.settings.getProperty("position.latitude"))};var longitude by remember{mutableStateOf(model.settings.getProperty("position.longitude"))};var result by remember{mutableStateOf("")}
 Text("Frequency object")
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},Modifier.weight(1f),label={Text("Object name")});OutlinedTextField(mhz,{mhz=it},Modifier.weight(1f),label={Text("MHz (146.940)")})}
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(tone,{tone=it},Modifier.weight(1f),label={Text("Tone (T123 / Toff)")});OutlinedTextField(offset,{offset=it},Modifier.weight(1f),label={Text("Offset (+060 / -060)")});OutlinedTextField(range,{range=it},Modifier.weight(1f),label={Text("Range miles")})}
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(latitude,{latitude=it},Modifier.weight(1f),label={Text("Latitude")});OutlinedTextField(longitude,{longitude=it},Modifier.weight(1f),label={Text("Longitude")})}
 Button(onClick={result=runCatching {
  require(name.length in 3..9 && mhz.matches(Regex("[0-9]{3}\\.[0-9]{3}")) && tone.matches(Regex("[TCD][0-9]{3}|Toff")) && offset.matches(Regex("[+-][0-9]{3}")) && range.toInt() in 0..99)
  require(model.entities.none {it.getProperty("name")==name}){"Object name already exists"};AprsFix(latitude.toDouble(),longitude.toDouble(),0)
  val entity=Properties().apply {BeaconPreferences.entityFields.forEach {setProperty(it.key,it.default)};setProperty("name",name);setProperty("latitude",latitude);setProperty("longitude",longitude);setProperty("symbol","r");setProperty("comment","${mhz}MHz $tone $offset R${range.padStart(2,'0')}");setProperty("enabled","false")}
  check(model.savePreferences(Properties().apply {putAll(model.settings)},model.entities+entity));"Object saved. Enable and select outputs in Settings → Objects."
 }.getOrElse{it.message?:"Check frequency object values"}}){Text("Create object")}
 if(result.isNotBlank())Text(result)
}
