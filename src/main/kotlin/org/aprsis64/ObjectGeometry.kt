// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import java.util.Properties
import net.osmand.shared.aprs.*

internal object ObjectGeometry {
 val fields=listOf(
  BeaconField("qru_group","QRU group (blank: not queryable)",""),
  BeaconField("query_only","Transmit only when queried","false",SettingKind.BOOLEAN),
  BeaconField("shape","Object geometry","point",choices=listOf("point","line","polygon","area")),
  BeaconField("vertices","Line/polygon vertices: coordinates, map link or Plus Code per line","[]",SettingKind.STRINGS),
  BeaconField("line_style","Multiline style (a–l)","j",choices=('a'..'l').map {it.toString()}),
  BeaconField("area_type","Area type","4",choices=(0..9).map {it.toString()}),
  BeaconField("area_color","Area color (0–15)","4",SettingKind.INTEGER),
  BeaconField("area_latitude_code","Area north/south extent code (0–99)","10",SettingKind.INTEGER),
  BeaconField("area_longitude_code","Area east/west extent code (0–99)","10",SettingKind.INTEGER)
 )
 fun points(e:Properties)=names(e.getProperty("vertices","[]")).map {line->
  val reference=runCatching {MapLocation(e.getProperty("latitude").toDouble(),e.getProperty("longitude").toDouble())}.getOrNull()
  val result=CoordinateInput.parse(line,reference)
  val point=requireNotNull(result.location) {result.error}
  AprsFix(point.latitude,point.longitude,0)
 }
 fun validate(e:Properties) {
  val group=e.getProperty("qru_group","");require(group.isEmpty() || group.matches(Regex("[A-Z0-9_-]{1,20}")))
  if(group.isNotEmpty())require(e.getProperty("kind")=="object" && e.getProperty("name").none {it==' '})
  if(group.isNotEmpty())require(e.getProperty("symbol").single() !in "|~{")
  if(e.getProperty("query_only","false").toBoolean())require(group.isNotEmpty())
  val shape=e.getProperty("shape","point");require(shape in listOf("point","line","polygon","area"))
  if(shape!="point")require(e.getProperty("kind")=="object" && e.getProperty("source")=="fixed")
  if(shape=="area") {
   require(e.getProperty("area_type","4").toInt() in 0..9 && e.getProperty("area_color","4").toInt() in 0..15)
   require(e.getProperty("area_latitude_code","10").toInt() in 0..99 && e.getProperty("area_longitude_code","10").toInt() in 0..99)
  }
  comment(e)
  if(shape=="area")require(AprsSpatial.area(AprsStation(e.getProperty("name"),e.getProperty("latitude").toDouble(),e.getProperty("longitude").toDouble(),'\\','l',comment(e),0))!=null)
 }
 fun format(e:Properties,base:BeaconFormat):BeaconFormat = when(e.getProperty("shape","point")) {
  "point"->base
  "area"->base.copy(table='\\',symbol='l',compressed=false,courseSpeed=false,altitude=false,includeReason=false,statusTag="",precision=0)
  else->base.copy(compressed=false,courseSpeed=false,altitude=false,includeReason=false,statusTag="",precision=0)
 }
 fun comment(e:Properties):String {
  val text=e.getProperty("comment","")
  return when(e.getProperty("shape","point")) {
   "line","polygon"->{
    val origin=AprsFix(e.getProperty("latitude").toDouble(),e.getProperty("longitude").toDouble(),0)
    val shape=AprsSpatial.encodeMultiline(origin,points(e),e.getProperty("shape")=="polygon",e.getProperty("line_style","j").single())
    require(text.length+shape.length<=120) {"Shorten the comment or reduce vertices"};text+shape
   }
   "area"->"${e.getProperty("area_type","4")}${e.getProperty("area_latitude_code","10").padStart(2,'0')}/${e.getProperty("area_color","4").toInt().toString(16).uppercase()}${e.getProperty("area_longitude_code","10").padStart(2,'0')}"+text
   else->text
  }
 }
}
