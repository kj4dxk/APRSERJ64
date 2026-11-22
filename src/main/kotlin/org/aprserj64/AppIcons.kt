// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

/** Small UI vectors with accessible text labels; independent of the APRS symbol tables. */
internal object AppIcons {
 private fun icon(name:String,draw:PathBuilder.()->Unit)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
  path(stroke=SolidColor(Color.Black),strokeLineWidth=2f,pathBuilder=draw)
 }.build()
 val Menu=icon("Menu") {moveTo(4f,6f);lineTo(20f,6f);moveTo(4f,12f);lineTo(20f,12f);moveTo(4f,18f);lineTo(20f,18f)}
 val Add=icon("Zoom in") {moveTo(5f,12f);lineTo(19f,12f);moveTo(12f,5f);lineTo(12f,19f)}
 val Remove=icon("Zoom out") {moveTo(5f,12f);lineTo(19f,12f)}
 val ArrowDropDown=icon("Choose") {moveTo(6f,9f);lineTo(12f,15f);lineTo(18f,9f)}
 val MoreVert=icon("More") {moveTo(12f,4f);lineTo(12f,7f);moveTo(12f,10f);lineTo(12f,13f);moveTo(12f,16f);lineTo(12f,19f)}
 val Send=icon("Send") {moveTo(3f,3f);lineTo(21f,12f);lineTo(3f,21f);lineTo(6f,12f);close();moveTo(6f,12f);lineTo(21f,12f)}
 val Search=icon("Search") {moveTo(16f,10f);arcToRelative(6f,6f,0f,true,true,-12f,0f);arcToRelative(6f,6f,0f,true,true,12f,0f);moveTo(15f,15f);lineTo(21f,21f)}
 val Settings=icon("Settings") {
  moveTo(18f,12f);arcToRelative(6f,6f,0f,true,true,-12f,0f);arcToRelative(6f,6f,0f,true,true,12f,0f)
  moveTo(14f,12f);arcToRelative(2f,2f,0f,true,true,-4f,0f);arcToRelative(2f,2f,0f,true,true,4f,0f)
  moveTo(12f,2f);lineTo(12f,6f);moveTo(12f,18f);lineTo(12f,22f);moveTo(2f,12f);lineTo(6f,12f);moveTo(18f,12f);lineTo(22f,12f)
  moveTo(5f,5f);lineTo(8f,8f);moveTo(16f,16f);lineTo(19f,19f);moveTo(5f,19f);lineTo(8f,16f);moveTo(16f,8f);lineTo(19f,5f)
 }
}
