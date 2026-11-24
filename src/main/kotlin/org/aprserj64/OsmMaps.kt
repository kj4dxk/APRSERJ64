// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import kotlinx.serialization.json.*
import org.aprsfoundation.core.AprsFix

internal data class LocalOsmMap(val lines: String, val center: AprsFix)

internal object OsmMaps {
    private const val bundled="/maps/boston.osm"

    fun bundled()=checkNotNull(javaClass.getResourceAsStream(bundled)).use(::parse)

    fun imported(path: Path): LocalOsmMap {
        require(Files.size(path)<=20_000_000) {"OSM file exceeds 20 MB"}
        return Files.newInputStream(path).use(::parse)
    }

    private fun parse(input: InputStream): LocalOsmMap {
        val factory=XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD,false)
            setProperty("javax.xml.stream.isSupportingExternalEntities",false)
        }
        val nodes=HashMap<Long,AprsFix>()
        val ways=ArrayList<List<Long>>()
        val mapTags=setOf("highway","waterway","railway","building","leisure","natural","landuse","amenity")
        var references: MutableList<Long>?=null
        var referenceCount=0
        var visible=false
        var center: AprsFix?=null
        val xml=factory.createXMLStreamReader(input)
        fun attribute(name: String)=xml.getAttributeValue(null,name)
        try {
            while(xml.hasNext()) when(xml.next()) {
                XMLStreamConstants.DTD -> error("OSM DTD is not allowed")
                XMLStreamConstants.START_ELEMENT -> when(xml.localName) {
                    "bounds" -> center=AprsFix((attribute("minlat").toDouble()+attribute("maxlat").toDouble())/2,
                        (attribute("minlon").toDouble()+attribute("maxlon").toDouble())/2,0)
                    "node" -> if(attribute("visible")!="false") {
                        require(nodes.size<200_000) {"OSM file contains too many nodes"}
                        nodes[attribute("id").toLong()]=AprsFix(attribute("lat").toDouble(),attribute("lon").toDouble(),0)
                    }
                    "way" -> {references=ArrayList();visible=false}
                    "nd" -> if(references!=null) {
                        require(++referenceCount<=1_000_000) {"OSM file contains too many way references"}
                        references.add(attribute("ref").toLong())
                    }
                    "tag" -> if(references!=null && attribute("k") in mapTags) visible=true
                }
                XMLStreamConstants.END_ELEMENT -> if(xml.localName=="way") {
                    val way=references
                    if(visible && way!=null && way.size>=2) ways.add(way)
                    references=null
                }
            }
        } finally {xml.close()}
        val lines=ways.mapNotNull {way->
            val points=way.map {nodes[it]}
            points.takeIf {it.none {point->point==null}}?.filterNotNull()
        }
        require(lines.isNotEmpty()) {"OSM file contains no complete mapped ways"}
        val json=buildJsonObject {
            put("type","Feature")
            putJsonObject("geometry") {
                put("type","MultiLineString")
                putJsonArray("coordinates") {
                    lines.forEach {line->addJsonArray {line.forEach {point->addJsonArray {add(point.longitude);add(point.latitude)}}}}
                }
            }
            putJsonObject("properties") {}
        }
        return LocalOsmMap(json.toString(),center ?: lines.first().first())
    }
}
