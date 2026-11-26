// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest

internal val LocalLocationModel = staticCompositionLocalOf<AppModel?> { null }

internal fun Modifier.submitPlaceOnEnter(requests:MutableSharedFlow<Unit>,enabled:Boolean)=onPreviewKeyEvent {event->
    if(enabled && (event.key==Key.Enter || event.key==Key.NumPadEnter)){
        if(event.type==KeyEventType.KeyUp)requests.tryEmit(Unit)
        true
    } else false
}
@Composable internal fun PlaceSearch(query:String, requests:MutableSharedFlow<Unit>,choose:(MapLocation)->Unit) {
    val model=LocalLocationModel.current ?: return
    if(query.isBlank()) return
    key(query) {
        val scope=rememberCoroutineScope()
        var candidates by remember { mutableStateOf(emptyList<Pair<String,MapLocation>>()) }
        var status by remember { mutableStateOf("") }
        var searching by remember { mutableStateOf(false) }
        fun submit(){
            if(searching)return
            searching=true
            scope.launch {
                try {
                    candidates=withContext(Dispatchers.IO) {
                        val url=model.settings.getProperty("services.search_url")+"?limit=5&q="+URLEncoder.encode(query,Charsets.UTF_8)
                        val request=HttpRequest.newBuilder(URI(url)).timeout(java.time.Duration.ofSeconds(8)).GET().build()
                        val response=model.network.getWeb(request,256*1024).decodeToString()
                        Json.parseToJsonElement(response).jsonObject["features"]!!.jsonArray.mapNotNull { feature ->
                            val props=feature.jsonObject["properties"]!!.jsonObject
                            val coordinates=feature.jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
                            val point=MapLocation(coordinates[1].jsonPrimitive.double,coordinates[0].jsonPrimitive.double)
                            val label=listOf("name","city","state","country").mapNotNull { props[it]?.jsonPrimitive?.contentOrNull }.distinct().joinToString(", ")
                            label to point
                        }
                    }
                    status=if(candidates.isEmpty()) "No places found" else "Choose a location"
                } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(_:Exception){status="Place search unavailable. Coordinates still work offline."}
                finally { searching=false }
            }
        }
        LaunchedEffect(requests){requests.collect {submit()}}
        HelpTextButton("Search the configured place index. Select a result to use its coordinates.",enabled=!searching,onClick=::submit) {HelpText(if(searching) "Searching…" else "Search places")}
        if(status.isNotEmpty()) HelpText(status)
        candidates.forEach { (label,point) -> HelpTextButton(point.display(),onClick={choose(point)}) { HelpText(label+" · "+point.display()) } }
    }
}

/** Keep the user's text while passing only validated decimal coordinates to existing editors. */
@Composable internal fun LocationField(label:String,latitude:String,longitude:String,onChange:(String,String)->Unit) {
    val reference=remember {runCatching {MapLocation(latitude.toDouble(),longitude.toDouble())}.getOrNull()}
    var text by remember {mutableStateOf(if(latitude.isBlank() && longitude.isBlank()) "" else "$latitude, $longitude")}
    val result=remember(text) {CoordinateInput.parse(text,reference)}
    val requests=remember {MutableSharedFlow<Unit>(extraBufferCapacity=1)}
    Column(Modifier.fillMaxWidth()) {
        HelpField("Enter decimal degrees, degrees/minutes/seconds, APRS coordinates, a Google Maps coordinate link or a Plus Code. Short Plus Codes use this editor's original location as the reference. Check the decoded preview before saving.",
            text,{text=it;val parsed=CoordinateInput.parse(it,reference).location
                // Invalid text must invalidate the draft, never silently retain a previous location.
                onChange(parsed?.latitude?.toString()?:"",parsed?.longitude?.toString()?:"")},
            modifier=Modifier.fillMaxWidth().submitPlaceOnEnter(requests,result.location==null && text.isNotBlank()),label={HelpText(label)},
            placeholder={HelpText("Place, coordinates, map link or Plus Code")},singleLine=true,fill=true,isError=result.location==null)
        result.location?.let {HelpText(it.display())}
        if(result.explanation.isNotBlank()) HelpText(result.explanation)
        if(result.location==null) HelpText(result.error,color=MaterialTheme.colors.error)
        if(result.location==null) PlaceSearch(text,requests) { point ->
            text=point.latitude.toString()+", "+point.longitude
            onChange(point.latitude.toString(),point.longitude.toString())
        }
    }
}
