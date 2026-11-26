// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.nio.file.*
import kotlinx.serialization.json.*
import org.aprsfoundation.core.*
import kotlin.math.roundToInt

/** File bridge for hardware collectors. Input must be fresh; no stale sensor retransmission. */
object LocalSensors {
 fun wxnow(call:String,input:String,now:Long,maximumAgeSeconds:Int):AprsWeather? {
  val lines=input.lines();if(lines.size<2)return null
  val stamp=runCatching {java.time.LocalDateTime.parse(lines[0].trim().replace(Regex("\\s+")," "),java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy HH:mm",java.util.Locale.US)).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()}.getOrNull()?:return null
  require(now-stamp in -60000..maximumAgeSeconds*1000L){"wxnow embedded timestamp is outside the freshness window: "+java.time.Instant.ofEpochMilli(stamp)+" (read as UTC, maximum age "+maximumAgeSeconds+"s, future allowance 60s)"}
  val data=lines.drop(1).joinToString("").trim()
  val wind=Regex("^(\\d{3}|\\.\\.\\.)/(\\d{3}|\\.\\.\\.)").find(data)?:return null
  val payload="c"+wind.groupValues[1]+"s"+wind.groupValues[2]+data.substring(wind.value.length)
  return AprsObservations.weather("$call>APRS:_00000000$payload",stamp)
 }
 fun read(home:Path,file:String,maxAgeSeconds:Int,now:Long):String {
  val path=home.resolve(file).toAbsolutePath().normalize()
  try {
   require(Files.isRegularFile(path) || !Files.exists(path)) {"Not a regular file"}
   require(Files.size(path)<=65536) {"Sensor file too large"}
   val modified=Files.getLastModifiedTime(path)
   require(now-modified.toMillis() in -5000..maxAgeSeconds*1000L) {
    "File modification timestamp is outside the freshness window: "+modified.toInstant()+" (maximum age "+maxAgeSeconds+"s, future allowance 5s)"
   }
   return Files.readString(path).trim()
  } catch(error: NoSuchFileException) {
   throw IllegalArgumentException("File missing: "+path,error)
  } catch(error: java.nio.charset.CharacterCodingException) {
   throw IllegalArgumentException("File is not valid UTF-8: "+path,error)
  } catch(error: java.io.IOException) {
   throw IllegalArgumentException("File unreadable: "+path+" ("+error.message+")",error)
  } catch(error: IllegalArgumentException) {
   throw IllegalArgumentException(path.toString()+" · "+error.message,error)
  }
 }
 fun telemetry(model:AppModel,now:Long):Map<String,Double> {
  val p=model.settings;val doc=Json.parseToJsonElement(read(model.directory,p.getProperty("telemetry.input_file"),p.getProperty("telemetry.input_age_seconds").toInt(),now)).jsonObject
  return doc.mapValues {(_,v)->v.jsonPrimitive.double.also {require(it.isFinite() && it in 0.0..255.0)}}
 }
 fun weatherInputLocation(model:AppModel):String {
  val settings=model.settings
  return if(settings.getProperty("weatherstation.source")=="port") "Port "+settings.getProperty("weatherstation.port")
  else runCatching {model.directory.resolve(settings.getProperty("weatherstation.file")).toAbsolutePath().normalize().toString()}
   .getOrElse {"Invalid path: "+settings.getProperty("weatherstation.file")}
 }
 fun checkWeatherInput(model:AppModel,now:Long) { weatherSample(model,now) }
 private fun weatherSample(model:AppModel,now:Long): AprsWeather = try { readWeatherSample(model,now) }
 catch(error: Exception) { throw IllegalArgumentException(weatherInputLocation(model)+" · "+(error.message ?: "Invalid weather input"),error) }
 private fun readWeatherSample(model:AppModel,now:Long):AprsWeather {
  val p=model.settings;val input=if(p.getProperty("weatherstation.source")=="port")"" else read(model.directory,p.getProperty("weatherstation.file"),p.getProperty("weatherstation.maximum_age_seconds").toInt(),now)
  return if(p.getProperty("weatherstation.source")=="port") {
   val sample=model.localWeather(p.getProperty("weatherstation.port"))?:error("Waiting for weather port")
   require(now-sample.time in 0..p.getProperty("weatherstation.maximum_age_seconds").toLong()*1000){"Weather port data is stale"};sample
  }else if(input.startsWith('{')) {
   val json=Json.parseToJsonElement(input).jsonObject
   fun n(key:String,min:Double,max:Double)=json[key]?.jsonPrimitive?.double?.also {require(it.isFinite() && it in min..max){"Invalid weather $key"}}
   AprsWeather(model.account.value,now,n("temperature_c",-90.0,80.0),n("wind_degrees",0.0,360.0)?.toInt(),n("wind_kph",0.0,500.0),n("gust_kph",0.0,500.0),n("humidity",0.0,100.0)?.toInt(),n("pressure_hpa",100.0,1200.0),n("rain_hour_mm",0.0,253.0),n("rain_day_mm",0.0,253.0))
  }else (wxnow(model.account.value,input,now,p.getProperty("weatherstation.maximum_age_seconds").toInt()) ?: UltimeterWeather.decode(model.account.value,input,now) ?: AprsObservations.weather(input,now))?:error("Expected SI weather JSON or an APRS weather packet")
 }
 fun weather(model:AppModel,now:Long):String {
  val weather=weatherSample(model,now)
  val p=model.settings
  fun digits(value:Double?,width:Int)=value?.roundToInt()?.coerceIn(0,("9".repeat(width)).toInt())?.toString()?.padStart(width,'0')?:".".repeat(width)
  val c=buildString {
   append(digits(weather.windDegrees?.toDouble(),3));append('/');append(digits(weather.windKph?.div(1.609344),3))
   weather.gustKph?.let {append("g"+digits(it/1.609344,3))}
   weather.temperatureC?.let {val f=(it*1.8+32).roundToInt();append("t"+if(f<0)"-"+(-f).coerceAtMost(99).toString().padStart(2,'0') else f.coerceAtMost(999).toString().padStart(3,'0'))}
   weather.humidity?.let {append("h"+(if(it==100)0 else it).toString().padStart(2,'0'))}
   weather.pressureHpa?.let {append("b"+digits(it*10,5))}
   weather.rainMidnightMm?.let {append("P"+digits(it/0.254,3))}
   weather.rainHourMm?.let {append("r"+digits(it/0.254,3))};weather.rainDayMm?.let {append("p"+digits(it/0.254,3))}
  }
  val position=model.beaconState.value.position?.takeIf {it.current}?:error("Weather needs a current position")
  val name=p.getProperty("weatherstation.object")
  return if(name.isBlank())BeaconPackets.position(model.account.value,position,BeaconFormat(table='/',symbol='_',courseSpeed=false,altitude=false),c)
   else BeaconPackets.entity(model.account.value,name,false,true,position,BeaconFormat(table='/',symbol='_',courseSpeed=false,altitude=false),c)
 }
}
