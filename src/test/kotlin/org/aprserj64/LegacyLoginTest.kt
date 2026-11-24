package org.aprserj64

import java.nio.file.Files
import kotlin.test.*

class LegacyLoginTest {
    private fun profile(xml:String): LegacyLogin {
        val file=Files.createTempFile("legacy-login",".xml")
        return try {Files.writeString(file,xml);readLegacyLogin(file)} finally {Files.delete(file)}
    }
    @Test fun importsStationAndIgnoresNestedFields() {
        val p=profile("<APRSISCE><Port><Password>bad</Password></Port><CallSign>n1abc-04</CallSign><Password>12345</Password><APRSISPort>@localhost:14580</APRSISPort><Latitude>42</Latitude><Longitude>-71</Longitude></APRSISCE>")
        assertEquals("N1ABC-4",p.call);assertEquals("12345",p.pass);assertFalse(p.receiveOnly)
        assertEquals("localhost:14580",p.endpoint);assertEquals(42.0,p.latitude);assertEquals(-71.0,p.longitude)
    }
    @Test fun honorsReceiveOnlyAndRejectsUnsafeOrInvalidDocuments() {
        for(extra in listOf("<Password/>","<Password>-1</Password>","<Password>123</Password><APRSIS.XmitEnabled>0</APRSIS.XmitEnabled>"))
            assertTrue(profile("<APRSISCE><CallSign>N1ABC</CallSign>$extra</APRSISCE>").receiveOnly)
        assertFails {profile("<APRSISCE><CallSign>NOCALL</CallSign></APRSISCE>")}
        assertFails {profile("<!DOCTYPE APRSISCE [<!ENTITY e SYSTEM 'file:///unused'>]><APRSISCE><CallSign>&e;</CallSign></APRSISCE>")}
    }
}
