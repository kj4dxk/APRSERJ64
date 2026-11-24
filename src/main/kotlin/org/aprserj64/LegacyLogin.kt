// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.aprsfoundation.core.AprsEndpoint

internal class LegacyLogin(val call:String, val pass:String, val endpoint:String?, val latitude:Double?, val longitude:Double?, val receiveOnly:Boolean)

internal fun readLegacyLogin(path:Path):LegacyLogin {
    require(Files.size(path) in 1..32L*1024*1024) { "Choose an APRSIS32 XML file under 32 MB." }
    val factory=DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
        isXIncludeAware=false
        isExpandEntityReferences=false
    }
    val root=Files.newInputStream(path).use { factory.newDocumentBuilder().parse(it).documentElement }
    require(root.tagName=="APRSISCE") { "Choose an APRSIS32 configuration XML file." }
    fun field(name:String):String? {
        val nodes=root.getElementsByTagName(name)
        return (0 until nodes.length).map {nodes.item(it) as Element}.firstOrNull {it.parentNode==root}?.textContent?.trim()?.ifEmpty {null}
    }
    val call=ConnectionSetup.normalizedCall(field("CallSign").orEmpty())
    val password=field("Password").orEmpty()
    require(ConnectionSetup.problem(call,"-1")==null) { "This XML has no usable station callsign." }
    val receiveOnly=password.isEmpty() || password=="-1" || field("APRSIS.XmitEnabled")=="0"
    require(receiveOnly || ConnectionSetup.problem(call,password)==null) { "The saved APRS-IS passcode is invalid. Use manual setup." }
    val endpoint=field("APRSISPort")?.removePrefix("@")?.also { AprsEndpoint.parse(it) }
    val lat=field("Latitude")?.toDoubleOrNull()?.takeIf {it in -90.0..90.0}
    val lon=field("Longitude")?.toDoubleOrNull()?.takeIf {it in -180.0..180.0}
    return LegacyLogin(call,password,endpoint,lat,lon,receiveOnly)
}
