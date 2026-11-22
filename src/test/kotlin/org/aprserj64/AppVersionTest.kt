// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import kotlin.test.*
import net.osmand.shared.aprs.AprsProtocol

class AppVersionTest {
    @Test fun releaseVersionIsPackagedAndIdentifiesTheConnection() {
        assertTrue(AppVersion.value.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{4}")))
        assertTrue(AprsProtocol.login("N0CALL","-1","","APRSERJ64",AppVersion.value).contains("vers APRSERJ64 ${AppVersion.value} "))
        assertFailsWith<IllegalArgumentException> {AprsProtocol.login("N0CALL","-1","","APRSERJ64","bad\r\nversion")}
        assertTrue(AprsProtocol.login("N0CALL","-1","").contains("vers APRSERJa 0.1 "))
    }
}
