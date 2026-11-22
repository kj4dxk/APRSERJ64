// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.util.Properties
import kotlin.test.*

/** Prepared for the next authorized run; no GUI, hardware, or network is opened. */
class PortWorkspaceDraftTest {
    private fun settings()=Properties().apply {(BeaconPreferences.fields+PortPreferences.global).forEach {setProperty(it.key,it.default)}}
    private fun port(name: String,device: String,protocol: String="Simply(KISS)")=PortPreferences.defaults(protocol).apply {setProperty("name",name);setProperty("device",device)}
    private fun obj(targets: List<String>)=Properties().apply {
        BeaconPreferences.entityFields.forEach {setProperty(it.key,it.default)}
        setProperty("name","TESTOBJ");setProperty("ports",encodedNames(targets))
    }
    @Test fun renameUpdatesEveryReferenceAndUndoRestoresIt() {
        val settings=settings().apply {setProperty("position.source","port");setProperty("position.port_name","GPS")}
        val a=port("A","COM12").apply {setProperty("digi_outputs","[\"GPS\"]")}
        val draft=PortWorkspaceDraft(settings,listOf(port("GPS","COM11","TEXT").apply {setProperty("provides_nmea","true")},a),listOf(obj(listOf("GPS"))))
        draft.rename(draft.devices.first().id,"Receiver")
        assertEquals("Receiver",draft.values["position.port_name"])
        assertEquals(listOf("Receiver"),names(draft.devices[1].values["digi_outputs"]))
        assertEquals(listOf("Receiver"),names(draft.objects[0]["ports"]))
        draft.undo();assertEquals("GPS",draft.devices.first().name);assertFalse(draft.dirty)
        draft.redo();assertEquals("Receiver",draft.devices.first().name)
    }
    @Test fun deletionDoesNotBroadenAnExplicitRoute() {
        val a=port("A","COM11").apply {setProperty("digipeater","true");setProperty("digi_outputs","[\"B\"]")}
        val draft=PortWorkspaceDraft(settings(),listOf(a,port("B","COM12")),listOf(obj(listOf("B"))))
        draft.remove(draft.devices[1].id)
        assertEquals("false",draft.devices[0].values["digipeater"])
        assertEquals("false",draft.objects[0]["radio"])
        draft.undo();assertEquals(listOf("B"),names(draft.objects[0]["ports"]))
    }
    @Test fun selectingOneObjectOutputFromOffDoesNotEnableEveryPort() {
        val objectProps=obj(emptyList()).apply {setProperty("radio","false")}
        val draft=PortWorkspaceDraft(settings(),listOf(port("A","COM11"),port("B","COM12")),listOf(objectProps))
        draft.setObjectPort(0,"B",true)
        assertEquals(listOf("B"),names(draft.objects[0]["ports"]))
        draft.setObjectPort(0,"B",false);assertEquals("false",draft.objects[0]["radio"])
    }
    @Test fun wiringFromOffSelectsOnlyTheClickedDestination() {
        val draft=PortWorkspaceDraft(settings(),listOf(port("A","COM11"),port("B","COM12")),emptyList())
        draft.toggleDigiTarget(draft.devices[0],draft.devices[1])
        assertEquals(listOf("B"),names(draft.devices[0].values["digi_outputs"]))
        draft.toggleDigiTarget(draft.devices[0],draft.devices[1]);assertEquals("false",draft.devices[0].values["digipeater"])
    }
    @Test fun activeGpsRemovalRequiresAnExplicitReplacement() {
        val s=settings().apply {setProperty("position.source","port");setProperty("position.port_name","GPS")}
        val draft=PortWorkspaceDraft(s,listOf(port("GPS","COM11","NMEA")),emptyList())
        draft.remove(draft.devices.first().id)
        assertEquals("port",draft.values["position.source"])
        assertTrue(draft.issues().any {it.contains("Choose a GPS")})
    }
    @Test fun conflictAndDefaultsStayExplicit() {
        val s=settings();val ports=listOf(port("A","COM11"),port("B","COM12"))
        val draft=PortWorkspaceDraft(s,ports,emptyList())
        assertFailsWith<IllegalArgumentException> {draft.rename(draft.devices[0].id,"B")}
        assertTrue(draft.matchesBaseline(s,ports,emptyList()))
        s.setProperty("position.source","gps_tcp");assertFalse(draft.matchesBaseline(s,ports,emptyList()))
        val radio=PortPreferences.defaults();assertEquals("true",radio.getProperty("enabled"))
        assertEquals("WIDE1-1,WIDE2-1",radio.getProperty("beacon_path"))
        assertEquals("false",radio.getProperty("digipeater"))
        assertEquals(listOf("WIDE1-1="),names(radio.getProperty("digi_transforms")))
        val gps=PortPreferences.defaults("NMEA");assertEquals("false",gps.getProperty("rf_to_is"));assertEquals("false",gps.getProperty("is_to_rf"))
    }
    @Test fun loadingMissingFieldsUsesTheProfileWithoutOverwritingChoices() {
        val table=org.tomlj.Toml.parse("""
            protocol = "NMEA"
            enabled = false
            baud = 19200
        """.trimIndent())
        val gps=PortPreferences.load(table)
        assertEquals("false",gps.getProperty("enabled"))
        assertEquals("false",gps.getProperty("rf_to_is"))
        assertEquals("false",gps.getProperty("transmit"))
        assertEquals("19200",gps.getProperty("baud"))
    }
    @Test fun gpsdImportCarriesTheEndpointAndDoesNotMutateTheLiveSettings() {
        val s=settings().apply {setProperty("position.source","gpsd");setProperty("position.gpsd_endpoint","gps.example:2999")}
        val draft=PortWorkspaceDraft(s,emptyList(),emptyList())
        val id=draft.importLegacyGps();val p=draft.devices.single {it.id==id}
        assertEquals("gps.example:2999",p.values["endpoint"])
        assertTrue(names(p.values["open_commands"]).single().contains("WATCH"))
        assertEquals("port",draft.values["position.source"])
        assertEquals("gpsd",s.getProperty("position.source"))
        draft.undo();assertTrue(draft.devices.isEmpty());assertFalse(draft.dirty)
    }
}
