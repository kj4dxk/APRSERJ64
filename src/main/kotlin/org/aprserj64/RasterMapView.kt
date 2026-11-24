// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.Properties
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import org.aprsfoundation.core.AprsFix
import org.aprsfoundation.core.AprsStation
import org.jetbrains.skia.Image

private const val TILE_PIXELS=256
private data class VisibleTile(val tile: Tile,val x: Int,val y: Int)

private data class RasterPosition(val lat: Double,val lon: Double,val zoom: Int) {
    val count get()=1 shl zoom
    private val span get()=count.toDouble()*TILE_PIXELS
    fun project(lat: Double,lon: Double): Offset {
        val phi=lat.coerceIn(-85.05112878,85.05112878)*PI/180
        return Offset(((lon+180)/360*span).toFloat(),((1-asinh(tan(phi))/PI)/2*span).toFloat())
    }
    fun pan(delta: Offset): RasterPosition {
        val center=project(lat,lon)-delta
        val x=((center.x%span)+span)%span
        val y=center.y.toDouble().coerceIn(0.0,span)
        return copy(lat=atan(sinh(PI*(1-2*y/span)))*180/PI,lon=x/span*360-180)
    }
    fun tiles(size: IntSize): List<VisibleTile> {
        if(size.width==0 || size.height==0) return emptyList()
        val center=project(lat,lon)
        val left=floor((center.x-size.width/2.0)/TILE_PIXELS).toInt()
        val right=floor((center.x+size.width/2.0)/TILE_PIXELS).toInt()
        val top=floor((center.y-size.height/2.0)/TILE_PIXELS).toInt().coerceAtLeast(0)
        val bottom=floor((center.y+size.height/2.0)/TILE_PIXELS).toInt().coerceAtMost(count-1)
        return buildList {
            for(y in top..bottom) for(x in left..right) add(VisibleTile(Tile(zoom,Math.floorMod(x,count),y),x,y))
        }
    }
}

@Composable internal fun RasterMapView(stations: List<AprsStation>, selected: String?, model: AppModel,
    recenter: AprsFix?, modifier: Modifier, values: Properties, nativeMap: ()->Unit, select: (String)->Unit) {
    val minimum=values.getProperty("map.minimum_zoom").toDouble().toInt().coerceIn(0,22)
    val maximum=values.getProperty("map.maximum_zoom").toDouble().toInt().coerceIn(minimum,22)
    val initial=remember {RasterPosition(values.getProperty("latitude").toDouble(),
        values.getProperty("longitude").toDouble(),values.getProperty("map.initial_zoom").toDouble().toInt().coerceIn(minimum,maximum))}
    var camera by remember {mutableStateOf(initial)}
    var size by remember {mutableStateOf(IntSize.Zero)}
    val offline=values.getProperty("offline.enabled").toBoolean()
    val tilesEnabled=offline || values.getProperty("map.online_enabled").toBoolean()
    val template=values.getProperty("map.tile_url")
    val localMap by model.imports.localMap.collectAsState()
    val localLines=remember(localMap) {
        localMap?.let {
            Json.parseToJsonElement(it.lines).jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.map {line->
                line.jsonArray.map {point->point.jsonArray.let {xy->xy[1].jsonPrimitive.double to xy[0].jsonPrimitive.double}}
            }
        }.orEmpty()
    }
    val visible=remember(camera,size) {camera.tiles(size)}
    val needed=remember(visible) {visible.map {it.tile}.toSet()}
    val images=remember(template,offline) {mutableStateMapOf<Tile,ImageBitmap>()}
    var failure by remember(template) {mutableStateOf("")}
    var loading by remember(template) {mutableStateOf(false)}
    LaunchedEffect(needed,template,offline,tilesEnabled) {
        images.keys.retainAll(needed)
        failure=""
        if(!tilesEnabled || needed.isEmpty()) return@LaunchedEffect
        loading=true
        val parallel=Semaphore(6)
        try {
            kotlinx.coroutines.coroutineScope {
                needed.filterNot(images::containsKey).forEach {tile->launch {
                    parallel.withPermit {
                        try {
                            model.offline.visibleTile(template,tile,offline)?.let {
                                images[tile]=Image.makeFromEncoded(it).use {image->image.toComposeImageBitmap()}
                            }
                        } catch(cancelled: CancellationException) {throw cancelled}
                        catch(error: Exception) {failure=error.message ?: "Map tile could not be loaded"}
                    }
                }}
            }
        } finally {loading=false}
    }
    LaunchedEffect(selected) {stations.firstOrNull {it.callsign==selected}?.let {camera=camera.copy(lat=it.latitude,lon=it.longitude)}}
    LaunchedEffect(recenter) {recenter?.let {camera=camera.copy(lat=it.latitude,lon=it.longitude,zoom=if(it==localMap?.center) 16.coerceIn(minimum,maximum) else camera.zoom)}}
    val currentStations by rememberUpdatedState(stations)
    Box(modifier.background(Color(0xFFD9E3DF))) {
        Canvas(Modifier.fillMaxSize().clipToBounds().onSizeChanged {size=it}
            .pointerInput(Unit) {detectDragGestures {change,delta->change.consume();camera=camera.pan(delta)}}
            .pointerInput(Unit) {detectTapGestures {tap->
                val center=camera.project(camera.lat,camera.lon)
                val middle=Offset(size.width/2f,size.height/2f)
                currentStations.minByOrNull {(camera.project(it.latitude,it.longitude)-center+middle-tap).getDistance()}
                    ?.takeIf {(camera.project(it.latitude,it.longitude)-center+middle-tap).getDistance()<24f}
                    ?.let {select(it.callsign)}
            }}) {
            val center=camera.project(camera.lat,camera.lon)
            val origin=Offset(this.size.width/2,this.size.height/2)-center
            visible.forEach {placed->images[placed.tile]?.let {bitmap->
                drawImage(bitmap,dstOffset=IntOffset((placed.x*TILE_PIXELS+origin.x).roundToInt(),(placed.y*TILE_PIXELS+origin.y).roundToInt()),
                    dstSize=IntSize(TILE_PIXELS,TILE_PIXELS))
            }}
            localLines.forEach {line->line.zipWithNext().forEach {(a,b)->
                drawLine(Color(0xFF476976),camera.project(a.first,a.second)+origin,camera.project(b.first,b.second)+origin,2f)
            }}
            stations.forEach {station->
                val point=camera.project(station.latitude,station.longitude)+origin
                drawCircle(Color.White,8f,point)
                drawCircle(if(station.callsign==selected) Color(0xFFB44928) else Color(0xFF006D65),6f,point)
            }
        }
        Column(Modifier.align(Alignment.TopStart).padding(6.dp).background(MaterialTheme.colors.surface)) {
            Row {
                HelpTextButton(UiHelp.action("Zoom in"),onClick={camera=camera.copy(zoom=(camera.zoom+1).coerceAtMost(maximum))}){HelpText("+")}
                HelpTextButton(UiHelp.action("Zoom out"),onClick={camera=camera.copy(zoom=(camera.zoom-1).coerceAtLeast(minimum))}){HelpText("−")}
                HelpTextButton(UiHelp.action("Home"),onClick={camera=initial}){HelpText("Home")}
                HelpTextButton("Use the full map renderer",onClick=nativeMap){HelpText("Full map")}
            }
            if(images.isEmpty() && needed.isNotEmpty() && tilesEnabled) {
                val detail=when {
                    loading -> "Loading map tiles…"
                    offline -> "No downloaded tiles in this area"
                    failure.isNotBlank() -> failure
                    else -> "No map tiles available"
                }
                HelpText(detail,Modifier.padding(horizontal=8.dp,vertical=4.dp))
            }
        }
        HelpText(values.getProperty("map.attribution"),Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.White).padding(4.dp),
            color=Color(0xFF263740))
    }
}
