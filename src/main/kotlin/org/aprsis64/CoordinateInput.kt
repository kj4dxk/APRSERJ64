// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import com.google.openlocationcode.OpenLocationCode
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import kotlin.math.abs

data class MapLocation(val latitude:Double,val longitude:Double) {
 init {require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)}
 fun display()=String.format(Locale.US,"%.6f, %.6f",latitude,longitude)
}
data class CoordinateResult(val location:MapLocation?=null,val explanation:String="",val error:String="")

/** Offline coordinate parsing. Never treats arbitrary prose or URL identifiers as coordinates. */
object CoordinateInput {
 private val numeric=Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
 private val olc=Regex("(?i)^[23456789CFGHJMPQRVWX0]{0,8}\\+[23456789CFGHJMPQRVWX]{0,7}$")
 fun parse(input:String,reference:MapLocation?=null):CoordinateResult = try {
  require(input.length<=4096) {"Location text is too long. Paste just the coordinates, Plus Code or map link."}
  var text=input.trim()
  require(text.isNotEmpty()) {"Enter coordinates, a Plus Code or a Google Maps coordinate link."}
  val notes=mutableListOf<String>()
  if(text.startsWith("http://",true) || text.startsWith("https://",true)) {
   text=fromUrl(text);notes.add("Location extracted from map link")
  }
  val codeText=text.replace(Regex("\\s+"),"").uppercase(Locale.ROOT)
  if(olc.matches(codeText)) {
   val code=OpenLocationCode(codeText)
   val full=if(code.isFull) code else {
    require(reference!=null) {"A shortened Plus Code needs a nearby reference. Center the map near it first, or enter the full code."}
    notes.add("Short Plus Code resolved near reference location ${reference.display()}")
    code.recover(reference.latitude,reference.longitude)
   }
   val area=full.decode()
   notes.add("Center of Plus Code ${full.code}")
   return CoordinateResult(MapLocation(area.centerLatitude,area.centerLongitude),notes.joinToString(". "))
  }
  if(Regex("(?i)^[23456789CFGHJMPQRVWX]{2,8}\\+[23456789CFGHJMPQRVWX]{2,7}\\s+.+").matches(text))
   error("A Plus Code with a place name needs that place as its reference. Center the map there and paste the code alone, or use a full Plus Code.")
  val original=text
  text=text.replace('−','-').replace('–','-').replace('＋','+').replace('，',',')
   .replace('º','°').replace('′','\'').replace('’','\'').replace('″','"').replace('“','"').replace('”','"')
  val words=mapOf("latitude" to "","longitude" to "","lat" to "","lon" to "","lng" to "",
   "north" to "N","south" to "S","east" to "E","west" to "W",
   "degrees" to "°","degree" to "°","deg" to "°","minutes" to "'","minute" to "'","min" to "'","seconds" to "\"","second" to "\"","sec" to "\"")
  words.forEach {(word,replacement)->text=text.replace(Regex("(?i)\\b$word\\b"),replacement)}
  text=text.replace(Regex("(?<=\\d)[!?#_]+(?=\\d)"),"").replace(Regex("\\.{2,}(?=\\d)"),".")
  text=text.replace(Regex("(?<=\\d)\\s*\\.\\s*(?=\\d)"),".")
  // Only recognizable number-lookalikes inside numeric tokens are repaired. Compass letters stay compass letters.
  text=text.replace(Regex("[0-9OolIiB]+(?:\\.[0-9OolIiB]+)?")) {m->
   if(m.value.any {it.isDigit()}) m.value.map {when(it){'O','o'->'0';'I','i','l'->'1';'B'->'8';else->it}}.joinToString("") else m.value
  }
  if(text!=original) notes.add("Formatting or typing corrected; check the coordinates below")
  text=text.replace(Regex("[()\\[\\]{}<>!?=#]")," ").trim()
  // Decimal commas are accepted when whitespace/semicolon clearly separates the two values.
  Regex("^\\s*([+-]?\\d+,\\d+)\\s*[;\\s]\\s*([+-]?\\d+,\\d+)\\s*$").matchEntire(text)?.let {
   text=it.groupValues[1].replace(',','.')+" "+it.groupValues[2].replace(',','.')
  }
  val hemis=Regex("[NSEW]",RegexOption.IGNORE_CASE).findAll(text).toList()
  val point:MapLocation
  if(hemis.isNotEmpty()) {
   require(hemis.size==2) {"Give one N/S direction and one E/W direction."}
   val first=hemis[0];val second=hemis[1]
   val before=text.substring(0,first.range.first)
   val middle=text.substring(first.range.last+1,second.range.first)
   val after=text.substring(second.range.last+1)
   val pieces=when {
    numeric.containsMatchIn(before) && !numeric.containsMatchIn(after)->listOf(before,middle)
    !numeric.containsMatchIn(before) && numeric.containsMatchIn(after)->listOf(middle,after)
    numeric.containsMatchIn(before) && numeric.containsMatchIn(after) && numbers(middle).isEmpty()->listOf(before,after)
    !numeric.containsMatchIn(before) && !numeric.containsMatchIn(after)-> {
     val chunks=middle.trim().split(Regex("[,;|]"))
     if(chunks.size==2 && chunks.all {numeric.containsMatchIn(it)}) chunks else {
      val parts=numbers(middle)
      require(parts.size==2) {"Separate these coordinates with a comma: N 28.5, 81.2 W."}
      parts
     }
    }
    else->error("Keep directions with their coordinate: 28.5 N, 81.2 W or N 28.5 W 81.2.")
   }
   val directions=hemis.map {it.value.uppercase(Locale.ROOT).single()}
   require(directions.count {it in "NS"}==1 && directions.count {it in "EW"}==1) {"Use N/S for latitude and E/W for longitude."}
   val values=pieces.mapIndexed {i,p->component(p,directions[i] in "NS",directions[i])}
   point=if(directions[0] in "NS") MapLocation(values[0],values[1]) else MapLocation(values[1],values[0])
  } else {
   require(!text.any {it.isLetter()}) {"Unrecognized letters. Use numbers, N/S/E/W, or a Plus Code; check typing errors."}
   val nums=numbers(text)
   require(nums.size in listOf(2,4,6)) {"Enter both latitude and longitude, separated by a comma or space."}
   require(!(nums.size>2 && text.count {it==','}>=2)) {"Ambiguous commas. Separate latitude and longitude with a semicolon, or use decimal points."}
   val half=nums.size/2
   var lat=component(nums.take(half).joinToString(" "),true)
   var lon=component(nums.drop(half).joinToString(" "),false)
   if(half==1 && abs(lat)>90 && abs(lat)<=180 && abs(lon)<=90) {val old=lat;lat=lon;lon=old;notes.add("Longitude-first order corrected")}
   point=MapLocation(lat,lon)
  }
  CoordinateResult(point,notes.joinToString(". "))
 } catch(e:Exception) {
  CoordinateResult(error=e.message?.takeIf {it.isNotBlank() && it!="Failed requirement."}
   ?: "Check latitude (-90 to 90), longitude (-180 to 180), and minutes/seconds (less than 60).")
 }
 private fun numbers(text:String):List<String> {
  val remainder=numeric.replace(text,"")
  require(remainder.all {it.isWhitespace() || it in ",;:/|°'\""}) {"Unrecognized coordinate text. Check punctuation and typing errors."}
  return numeric.findAll(text).map {it.value}.toList()
 }
 private fun component(text:String,latitude:Boolean,hemisphere:Char?=null):Double {
  val nums=numbers(text.trim()).map {it.toDouble()}.toMutableList()
  require(nums.size in 1..3) {"Each coordinate needs degrees, optionally minutes and seconds."}
  if(nums.size==1 && hemisphere!=null) {
   val token=numeric.find(text)!!.value.trimStart('+','-').substringBefore('.')
   if(token.length==(if(latitude)4 else 5)) {
    val n=abs(nums[0]);val degrees=(n/100).toInt();val minutes=n-degrees*100
    nums[0]=if(nums[0]<0)-degrees.toDouble() else degrees.toDouble();nums.add(minutes)
   }
  }
  require(nums.drop(1).all {it>=0 && it<60}) {"Minutes and seconds must be from 0 up to, but not including, 60."}
  if(nums.size>1)require(nums[0]%1.0==0.0) {"Use whole degrees before minutes."}
  if(nums.size>2)require(nums[1]%1.0==0.0) {"Use whole minutes before seconds."}
  val negative=text.trimStart().startsWith('-')
  require(!(negative && hemisphere in listOf('N','E'))) {"The minus sign conflicts with N/E. Use either a signed number or a direction."}
  val magnitude=abs(nums[0])+(nums.getOrNull(1)?:0.0)/60+(nums.getOrNull(2)?:0.0)/3600
  return if(hemisphere in listOf('S','W') || negative) -magnitude else magnitude
 }
 private fun decode(value:String)=URLDecoder.decode(value.replace("+","%2B"),Charsets.UTF_8)
 private fun fromUrl(text:String):String {
  val uri=URI(text.replace(" ","%20"));val host=uri.host?.lowercase(Locale.ROOT).orEmpty()
  require(host=="maps.google.com" || host.matches(Regex("(?:www\\.|maps\\.)?google\\.[a-z.]+")) || host=="maps.app.goo.gl" || host=="goo.gl") {"Paste coordinates or a Google Maps coordinate URL."}
  val decoded=decode(text)
  val pin=Regex("!3d([+-]?[0-9.]+)!4d([+-]?[0-9.]+)").findAll(decoded).map {it.groupValues[1]+","+it.groupValues[2]}.distinct().toList()
  require(pin.size<=1) {"This link contains multiple locations. Copy the coordinates of the intended pin."}
  if(pin.size==1)return pin.single()
  val query=uri.rawQuery.orEmpty().split('&').mapNotNull {part->part.indexOf('=').takeIf {it>0}?.let {
   val raw=part.substring(it+1);val preserved=decode(raw)
   decode(part.substring(0,it)) to if(olc.matches(preserved)) preserved else URLDecoder.decode(raw,Charsets.UTF_8)
  }}.toMap()
  for(key in listOf("query","q","destination","ll","center")) query[key]?.let {return it}
  Regex("@([+-]?[0-9.]+),([+-]?[0-9.]+)").find(decoded)?.let {return it.groupValues[1]+","+it.groupValues[2]}
  Regex("/maps/(?:place|search)/([^/?#]+)").find(decoded)?.let {return it.groupValues[1]}
  error("This link has no coordinates. Open it in Google Maps, then copy the coordinates or a full coordinate link.")
 }
}
