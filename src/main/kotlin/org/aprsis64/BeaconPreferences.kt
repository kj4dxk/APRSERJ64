// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.util.Properties
import kotlinx.serialization.json.*
import net.osmand.shared.aprs.*
import org.tomlj.TomlTable

enum class SettingKind { TEXT, BOOLEAN, INTEGER, DECIMAL, STRINGS }
data class BeaconField(val key: String, val label: String, val default: String,
                       val kind: SettingKind = SettingKind.TEXT, val choices: List<String> = emptyList())

/** One typed schema drives readable TOML and the settings editor. */
object BeaconPreferences {
    private val b=SettingKind.BOOLEAN; private val i=SettingKind.INTEGER; private val d=SettingKind.DECIMAL
    val fields=listOf(
        BeaconField("beacon.enabled","Beaconing enabled","true",b),
        BeaconField("position.source","Position source","fixed",choices=listOf("fixed","port","gps_serial","gps_tcp","gpsd")),
        BeaconField("position.port_name","Named NMEA port",""),
        BeaconField("beacon.internet","Beacon to APRS-IS","true",b),
        BeaconField("beacon.radio","Beacon to RF ports","true",b),
        BeaconField("position.latitude","Fixed latitude","28.536725",d),
        BeaconField("position.longitude","Fixed longitude","-81.242932",d),
        BeaconField("position.altitude_meters","Fixed altitude (meters)","0",d),
        BeaconField("position.gps_endpoint","GPS network host:port",ServicePreferences.defaultText("gps_defaults.tcp_endpoint")),
        BeaconField("position.gpsd_endpoint","GPSd host:port",ServicePreferences.defaultText("gps_defaults.gpsd_endpoint")),
        BeaconField("position.serial_port","GPS serial port (blank: use only attached port)",""),
        BeaconField("position.serial_baud","GPS serial baud","4800",i),
        BeaconField("position.maximum_age_seconds","GPS fix age limit (seconds)","30",i),
        BeaconField("position.maximum_accuracy_meters","GPS accuracy limit (meters)","100",d),
        BeaconField("beacon.smart","Use speed-based SmartBeaconing","false",b),
        BeaconField("beacon.smart_slow_knots","Smart slow speed (knots)","5",d),
        BeaconField("beacon.smart_fast_knots","Smart fast speed (knots)","60",d),
        BeaconField("beacon.smart_fast_seconds","Smart fast interval (seconds)","120",i),
        BeaconField("beacon.smart_turn_slope","Smart turn slope (degrees × knots)","255",d),
        BeaconField("beacon.smart_turn_seconds","Smart minimum turn interval (seconds)","30",i),
        BeaconField("beacon.minimum_seconds","Minimum automatic interval (seconds)","10",i),
        BeaconField("beacon.maximum_seconds","Maximum interval (seconds)","1800",i),
        BeaconField("beacon.time_only","Use time only","false",b),
        BeaconField("beacon.start_stop","Beacon on start / stop","true",b),
        BeaconField("beacon.heading_degrees","Heading change (degrees; 0 disables)","30",d),
        BeaconField("beacon.forecast_error_meters","Forecast error (meters)","160.9344",d),
        BeaconField("beacon.distance_meters","Maximum distance (meters)","1609.344",d),
        BeaconField("beacon.moving_knots","Moving speed threshold (knots)","0.5",d),
        BeaconField("beacon.on_reconnect","Beacon on reconnect","true",b),
        BeaconField("beacon.on_gps_change","Beacon on GPS fix gained / lost","true",b),
        BeaconField("beacon.recenter_after_transmit","Recenter map after transmitting","false",b),
        BeaconField("beacon.symbol_table","Symbol table or overlay","/"),
        BeaconField("beacon.symbol","Symbol code","-"),
        BeaconField("beacon.timestamp","Timestamp","hms",choices=listOf("none","hms","dhm")),
        BeaconField("beacon.compressed","Compressed position","false",b),
        BeaconField("beacon.course_speed","Include course / speed","true",b),
        BeaconField("beacon.altitude","Include altitude","true",b),
        BeaconField("beacon.precision","Precision (0, 1, 2, 4; ambiguity -1 to -4)","0",i),
        BeaconField("beacon.messaging","Advertise messaging capability","true",b),
        BeaconField("beacon.include_reason","Include transmission reason","false",b),
        BeaconField("beacon.status_tag","Mic-E status tag","",choices=listOf("","!OFF-DUTY!","!ENROUTE!","!INSERVICE!","!RETURNING!","!COMMITTED!","!SPECIAL!","!PRIORITY!","!EMERGENCY!")),
        BeaconField("beacon.comments","Comments (one per line)","[\"APRSIS64\"]",SettingKind.STRINGS),
        BeaconField("beacon.comment_enabled","Include comments","true",b),
        BeaconField("beacon.comment_interval_seconds","Minimum comment interval (seconds)","0",i),
        BeaconField("beacon.rotate_comments","Rotate comments","false",b),
        BeaconField("beacon.selected_comment","Selected comment (0 is first)","0",i),
        BeaconField("status.enabled","Send status reports","false",b),
        BeaconField("status.interval_seconds","Status interval (seconds)","1800",i),
        BeaconField("status.timestamp","Include status timestamp","true",b),
        BeaconField("status.grid","Include grid instead of timestamp","false",b),
        BeaconField("status.text","Status text","APRSIS64"),
        BeaconField("odometer.report_on_exit","Report odometer on exit","false",b)
    )
    val entityFields=listOf(
        BeaconField("name","Name / companion callsign",""),
        BeaconField("internet","Send to APRS-IS","true",b),
        BeaconField("radio","Send to RF ports","true",b),
        BeaconField("ports","RF ports (empty: all eligible ports)","[]",SettingKind.STRINGS),
        BeaconField("rf_path","Object RF path (blank: port beacon path)",""),
        BeaconField("kind","Report type","object",choices=listOf("object","item","station")),
        BeaconField("enabled","Enabled","true",b),
        BeaconField("alive","Alive (off sends a kill report)","true",b),
        BeaconField("source","Position source","fixed",choices=listOf("fixed","follow")),
        BeaconField("latitude","Latitude","28.536725",d), BeaconField("longitude","Longitude","-81.242932",d),
        BeaconField("altitude_meters","Altitude (meters)","0",d),
        BeaconField("course","Course (degrees)","0",d), BeaconField("speed_knots","Speed (knots)","0",d),
        BeaconField("symbol_table","Symbol table / overlay","/"), BeaconField("symbol","Symbol","-"),
        BeaconField("comment","Comment",""), BeaconField("interval_seconds","Maximum interval (seconds)","1800",i),
        BeaconField("minimum_seconds","Minimum interval (seconds)","30",i),
        BeaconField("time_only","Use time only","false",b), BeaconField("start_stop","Beacon on start / stop","false",b),
        BeaconField("heading_degrees","Heading change (degrees)","30",d),
        BeaconField("forecast_error_meters","Forecast error (meters)","160.9344",d),
        BeaconField("distance_meters","Maximum distance (meters)","1609.344",d)
    )+ObjectGeometry.fields
    fun load(properties: Properties, document: TomlTable?) {
        loadFields(properties,document,fields)
        if(document?.get("position.latitude")==null) properties.setProperty("position.latitude",properties.getProperty("latitude","28.536725"))
        if(document?.get("position.longitude")==null) properties.setProperty("position.longitude",properties.getProperty("longitude","-81.242932"))
    }
    fun loadFields(properties: Properties, document: TomlTable?, schema: List<BeaconField>) {
        schema.forEach { field ->
            val value=document?.get(field.key)
            val text=if(value==null) field.default else when(field.kind) {
                SettingKind.TEXT -> {require(value is String) {"${field.key}: expected quoted text"}; value}
                SettingKind.BOOLEAN -> {require(value is Boolean) {"${field.key}: expected true or false"}; value.toString()}
                SettingKind.INTEGER -> {require(value is Long) {"${field.key}: expected integer"}; value.toString()}
                SettingKind.DECIMAL -> {require(value is Number && value.toDouble().isFinite()) {"${field.key}: expected number"}; value.toString()}
                SettingKind.STRINGS -> {
                    val array=document!!.getArray(field.key)!!
                    JsonArray((0 until array.size()).map { JsonPrimitive(array.getString(it)) }).toString()
                }
            }
            if(field.choices.isNotEmpty() && field.key!="beacon.status_tag") require(text in field.choices) {"${field.key}: unsupported selection"}
            properties.setProperty(field.key,text)
        }
    }
    fun write(properties: Properties, schema: List<BeaconField>, sections: Boolean = true): String = buildString {
        var previous=""
        schema.forEach { field ->
            val section=field.key.substringBefore('.')
            if(sections && section!=previous) {append("\n[$section]\n"); previous=section}
            val value=properties.getProperty(field.key,field.default)
            val encoded=when(field.kind) {
                SettingKind.TEXT -> JsonPrimitive(value).toString()
                SettingKind.STRINGS -> JsonArray(Json.parseToJsonElement(value).jsonArray.map { JsonPrimitive(it.jsonPrimitive.content) }).toString()
                SettingKind.BOOLEAN -> value.toBooleanStrict().toString()
                SettingKind.INTEGER -> value.toLong().toString()
                SettingKind.DECIMAL -> value.toDouble().also {require(it.isFinite())}.toString()
            }
            append(field.key.substringAfter('.')).append(" = ").append(encoded).append('\n')
        }
    }
    // Grouping avoids reopening a TOML table when the editor groups fields differently.
    fun writeMain(properties: Properties)=write(properties,fields.sortedBy { it.key.substringBefore('.') })
}

class BeaconConfiguration(val values: Properties, val entities: List<Properties>) {
    fun text(key: String)=values.getProperty(key)
    fun bool(key: String)=text(key).toBooleanStrict()
    fun number(key: String)=text(key).toDouble()
    fun integer(key: String)=text(key).toInt()
    val timing=BeaconTiming(integer("beacon.minimum_seconds"),integer("beacon.maximum_seconds"),bool("beacon.time_only"),
        bool("beacon.start_stop"),number("beacon.heading_degrees"),number("beacon.forecast_error_meters"),number("beacon.distance_meters"),number("beacon.moving_knots"),bool("beacon.smart"),number("beacon.smart_slow_knots"),number("beacon.smart_fast_knots"),
        integer("beacon.smart_fast_seconds"),number("beacon.smart_turn_slope"),integer("beacon.smart_turn_seconds"))
    val format=BeaconFormat(text("beacon.symbol_table").single(),text("beacon.symbol").single(),BeaconTimestamp.valueOf(text("beacon.timestamp").uppercase()),
        bool("beacon.compressed"),bool("beacon.course_speed"),bool("beacon.altitude"),integer("beacon.precision"),bool("beacon.messaging"),bool("beacon.include_reason"),text("beacon.status_tag"))
    val comments=Json.parseToJsonElement(text("beacon.comments")).jsonArray.map {it.jsonPrimitive.content}
    init {
        AprsFix(number("position.latitude"),number("position.longitude"),0)
        require(number("position.altitude_meters").isFinite())
        require(integer("position.maximum_age_seconds") in 1..120 && number("position.maximum_accuracy_meters") in 1.0..10000.0)
        require(integer("position.serial_baud") in 1200..921600)
        gpsEndpoint()
        require(text("position.source") in listOf("fixed","port","gps_serial","gps_tcp","gpsd"))
        require(integer("beacon.comment_interval_seconds") in 0..86400 && integer("beacon.selected_comment") >= 0)
        require(comments.size<=32 && comments.all {it.length<=120 && it.all {c->c.code in 32..126}})
        require(integer("status.interval_seconds") in 0..86400 && (integer("status.interval_seconds")==0 || integer("status.interval_seconds")>=10))
        require(text("status.text").length<=200 && text("status.text").all {it.code in 32..126})
        require(entities.size<=32 && entities.map {it.getProperty("name")}.distinct().size==entities.size)
        entities.forEach { e ->
            ObjectGeometry.validate(e)
            val path=e.getProperty("rf_path").split(',').filter {it.isNotBlank()}
            require(path.size<=8 && path.all {RadioPacket.address(it)})
            val name=e.getProperty("name"); val kind=e.getProperty("kind")
            require(kind in listOf("object","item","station") && e.getProperty("source") in listOf("fixed","follow"))
            if(kind=="station") require(AprsProtocol.validCallsign(name) && e.getProperty("alive").toBooleanStrict())
            else require(name.length in 3..9 && name.isNotBlank() && name==name.trim() && name.all {it.code in (if(kind=="item") 33 else 32)..126 && it !in "!_"})
            entityTiming(e)
            val position=entityPosition(e,0)
            val f=entityFormat(e)
            val comment=ObjectGeometry.comment(e)
            if(kind=="station") BeaconPackets.position(name,position,f,comment)
            else BeaconPackets.entity("N0CALL",name,kind=="item",true,position,f,comment)
        }
    }
    fun fixed(now: Long)=BeaconPosition(AprsFix(number("position.latitude"),number("position.longitude"),now),altitudeMeters=number("position.altitude_meters"))
    fun gpsEndpoint(): AprsEndpoint {
        val gpsd=text("position.source")=="gpsd"
        val default=AprsEndpoint.parse(ServicePreferences.defaultText(if(gpsd) "gps_defaults.gpsd_endpoint" else "gps_defaults.tcp_endpoint"))
        return AprsEndpoint.parse(text(if(gpsd) "position.gpsd_endpoint" else "position.gps_endpoint"),default.port)
    }
    fun entityPosition(e: Properties, now: Long)=BeaconPosition(AprsFix(e.getProperty("latitude").toDouble(),e.getProperty("longitude").toDouble(),now),
        e.getProperty("speed_knots").toDouble(),e.getProperty("course").toDouble(),e.getProperty("altitude_meters").toDouble())
    fun entityFormat(e: Properties)=ObjectGeometry.format(e,format.copy(table=e.getProperty("symbol_table").single(),symbol=e.getProperty("symbol").single()))
    fun entityTiming(e: Properties)=BeaconTiming(e.getProperty("minimum_seconds").toInt(),e.getProperty("interval_seconds").toInt(),
        e.getProperty("time_only").toBooleanStrict(),e.getProperty("start_stop").toBooleanStrict(),e.getProperty("heading_degrees").toDouble(),
        e.getProperty("forecast_error_meters").toDouble(),e.getProperty("distance_meters").toDouble(),timing.movingKnots)
}
