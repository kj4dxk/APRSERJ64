// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64
import java.nio.file.Files
import java.net.*
import java.net.http.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import net.osmand.shared.aprs.*
import kotlin.test.*

class ExtendedFeaturesTest {
 @Test fun persistentTracksObservationsAndExports():Unit=runBlocking {
  val home=Files.createTempDirectory("APRS history test");PortableHome.prepare(home)
  val settings=SettingsStore(home).values
  val parent=CoroutineScope(SupervisorJob()+Dispatchers.IO)
  val archive=StationArchive(home,parent) {settings.getProperty(it)}
  archive.offer("N0CALL>APRS:!2832.20N/08114.58W>first",1000)
  archive.offer("N0CALL>APRS:!2832.21N/08114.59W>second",2000)
  archive.offer("N0CALL>APRS:T#001,1,2,3,4,5,10101010",3000)
  archive.offer("N0CALL>APRS:_11201400c180s010g020t032r001p010h00b10132",4000)
  withTimeout(5000){while(archive.revision.value<4)delay(10)}
  assertEquals(2,archive.track("N0CALL").size)
  assertTrue(Files.readString(archive.export("N0CALL","GPX")).contains("<trkpt"))
  archive.finish()
  val reopened=StationArchive(home,parent) {settings.getProperty(it)}
  try {assertEquals(2,reopened.track("N0CALL").size);assertEquals(1,reopened.telemetry.value["N0CALL"]?.size);assertEquals(100,reopened.weather.value["N0CALL"]?.humidity)}
  finally {reopened.finish();parent.cancel()}
 }
 @Test fun downloadedTileRemainsUsableWithoutProvider():Unit=runBlocking {
  val home=Files.createTempDirectory("APRS offline test");val settings=SettingsStore(home).values
  val provider=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
  val png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
  provider.createContext("/"){e->e.responseHeaders.set("Content-Type","image/png");e.sendResponseHeaders(200,png.size.toLong());e.responseBody.use {it.write(png)};e.close()};provider.start()
  settings.setProperty("map.tile_url","http://127.0.0.1:${provider.address.port}/{z}/{x}/{y}.png")
  val parent=CoroutineScope(SupervisorJob()+Dispatchers.IO);val offline=OfflineMaps(home,parent){settings.getProperty(it)}
  try {
   val plan=TilePlan.region(0.0,0.0,1.0,1.0,0,0,10);assertEquals(1,plan.size)
   assertFails {TilePlan.region(-80.0,-170.0,80.0,170.0,1,18,10)}
   offline.download(plan);withTimeout(10000){while(offline.running.value)delay(20)}
   assertTrue(offline.status.value.startsWith("Download complete"),offline.status.value)
   provider.stop(0)
   val url=offline.template().replace("{z}","0").replace("{x}","0").replace("{y}","0")
   val response=HttpClient.newHttpClient().use {it.send(HttpRequest.newBuilder(URI(url)).build(),HttpResponse.BodyHandlers.ofByteArray())}
   assertEquals(200,response.statusCode());assertContentEquals(png,response.body())
  }finally{provider.stop(0);offline.close();parent.cancel()}
 }
 @Test fun alertsDeduplicateAndWeatherUsesGeoJson() {
  val values=SettingsStore(Files.createTempDirectory("APRS alert test")).values
  val alerts=ActivityAlerts {values.getProperty(it)}
  val packet=AprsMessages.packet("N1CALL","N0CALL","Hello","123").trimEnd()
  alerts.incoming(packet,"N0CALL");alerts.incoming(packet,"N0CALL")
  assertEquals(1,alerts.notices.value.size)
  val data="""{"features":[{"properties":{"id":"test","event":"Flood Warning","severity":"Severe","headline":"Test only","description":"Example","expires":"2030-01-01T00:00:00Z"}}]}"""
  assertEquals("Flood Warning",WeatherAlerts.parse(data).single().event)
  alerts.dismiss();assertTrue(alerts.notices.value.isEmpty())
 }
 @Test fun optionalFallbackConnectsToSecondLocalServer():Unit=runBlocking {
  val unavailable=java.net.ServerSocket(0).use {it.localPort}
  DemoServer().use {server->
   val client=AprsIsClient()
   try {
    client.start(AprsIsConfig("N0CALL","12345","127.0.0.1",unavailable,0.0,0.0,fallbackServers=listOf(AprsEndpoint("127.0.0.1",server.port))))
    withTimeout(10000){while(client.state.value!=AprsConnectionState.VERIFIED)delay(20)}
    assertEquals(server.port,client.activeEndpoint.value?.port)
   }finally{client.stop()}
  }
 }
 @Test fun bundledSvgSymbolsRenderArtworkAndOverlaysAtRequestedSize() {
  val size = 64
  fun pixels(table: Char, symbol: Char): IntArray {
   val bitmap = StationSymbols.bitmap(table, symbol, size)
   assertEquals(size, bitmap.width)
   assertEquals(size, bitmap.height)
   return IntArray(size * size).also { bitmap.readPixels(it) }
  }

  val police = pixels('/', '!')
  assertEquals(0, police.first() ushr 24)
  assertTrue(0xFF0000CE.toInt() in police)
  assertTrue(0xFFFFFF00.toInt() in police)

  val alternate = pixels('\\', '>')
  for (overlay in listOf('A', '7')) {
   val overlaid = pixels(overlay, '>')
   assertFalse(alternate.contentEquals(overlaid))
   assertEquals(0xFFFFFFFF.toInt(), overlaid[8 * size + size / 2])
   assertTrue((49 * size until size * size).all { alternate[it] == overlaid[it] })
  }

  val tray = AppIcon.tray(32)
  assertEquals(32, tray.width)
  assertEquals(0xFFFF0000.toInt(), tray.getRGB(0, 0))
  assertTrue(0xFF66BFFF.toInt() in tray.getRGB(0, 0, 32, 32, null, 0, 32))
 }

 @Test fun newPreferencesSurviveReloadAndSymbolsCoverTables() {
  val home=Files.createTempDirectory("APRS features settings")
  val store=SettingsStore(home);store.values.setProperty("display.follow","true")
  store.values.setProperty("kiosk.entries","[\"INFO1=Local repeater 146.940\"]");store.values.setProperty("groups.names","[\"TEST\"]");store.save()
  val restored=SettingsStore(home)
  assertEquals("true",restored.values.getProperty("display.follow"));assertEquals(listOf("TEST"),names(restored.values.getProperty("groups.names")))
  assertFalse(restored.values.getProperty("weather.enabled").toBoolean());assertFalse(restored.values.getProperty("alerts.sound").toBoolean())
  for(table in listOf('/','\\','A')) for(code in 33..126) {assertEquals(24,StationSymbols.bitmap(table,code.toChar()).width)}
  assertEquals(2,kotlinx.serialization.json.Json.parseToJsonElement(trailJson(listOf(AprsFix(1.0,179.0,0),AprsFix(1.1,179.1,1),AprsFix(1.2,-179.9,2),AprsFix(1.3,-179.8,3)))).let {it as kotlinx.serialization.json.JsonObject}["features"].let {it as kotlinx.serialization.json.JsonArray}.size)
 }
}
