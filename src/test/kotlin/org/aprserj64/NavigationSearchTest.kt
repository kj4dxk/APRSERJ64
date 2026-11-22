// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import kotlin.test.*

class NavigationSearchTest {
 @Test fun stationIdentityIsDiscoverableInPlainLanguage() {
  for(query in listOf("call","callsign","my call","my callsign","call sign","change my callsign","passcode","pass code","password","SSID","N0CALL-13")) {
   assertEquals("action:connection",navigationResults(query,"N0CALL-13").first().id,query)
  }
 }
 @Test fun searchIncludesSettingsToolsAndMenuActions() {
  val maps=navigationResults("map").map {it.id}
  assertTrue("settings:Map" in maps);assertTrue("tools:Offline" in maps)
  assertTrue(navigationResults("packet").any {it.id=="action:packets"})
  assertTrue(navigationResults("baud").any {it.id=="settings:Position"})
  assertTrue(navigationResults("telemetry").map {it.id}.containsAll(listOf("settings:Telemetry","tools:Telemetry")))
  assertTrue(navigationResults("unmatchableword").isEmpty())
 }
}
