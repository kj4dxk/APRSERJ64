// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Keep the user's text while passing only validated decimal coordinates to existing editors. */
@Composable internal fun LocationField(label:String,latitude:String,longitude:String,onChange:(String,String)->Unit) {
    val reference=remember {runCatching {MapLocation(latitude.toDouble(),longitude.toDouble())}.getOrNull()}
    var text by remember {mutableStateOf(if(latitude.isBlank() && longitude.isBlank()) "" else "$latitude, $longitude")}
    val result=remember(text) {CoordinateInput.parse(text,reference)}
    Column(Modifier.fillMaxWidth()) {
        HelpField("Enter decimal degrees, degrees/minutes/seconds, APRS coordinates, a Google Maps coordinate link or a Plus Code. Short Plus Codes use this editor's original location as the reference. Check the decoded preview before saving.",
            text,{text=it;val parsed=CoordinateInput.parse(it,reference).location
                // Invalid text must invalidate the draft, never silently retain a previous location.
                onChange(parsed?.latitude?.toString()?:"",parsed?.longitude?.toString()?:"")},
            modifier=Modifier.fillMaxWidth(),label={HelpText(label)},
            placeholder={HelpText("Coordinates, map link or Plus Code")},singleLine=true,fill=true,isError=result.location==null)
        result.location?.let {HelpText(it.display())}
        if(result.explanation.isNotBlank()) HelpText(result.explanation)
        if(result.location==null) HelpText(result.error,color=MaterialTheme.colors.error)
    }
}
