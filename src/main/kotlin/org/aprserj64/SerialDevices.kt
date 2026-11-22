// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.file.Files
import java.nio.file.Path

internal object SerialDevices {
    // jSerialComm 2.11 tries ARM first on Windows and makes the failed DLL read-only.
    // Extract the matching bundled binary once so enumeration never takes that path.
    fun prepare(home: Path) {
        if(!System.getProperty("os.name").startsWith("Windows")) return
        val arch=when(System.getProperty("os.arch").lowercase()) {
            "amd64","x86_64"->"x86_64"
            "aarch64","arm64"->"aarch64"
            else->return
        }
        val directory=home.resolve("cache/native/jserialcomm-2.11.0-$arch")
        val library=directory.resolve("jSerialComm.dll")
        Files.createDirectories(directory)
        val bytes=checkNotNull(javaClass.getResourceAsStream("/Windows/$arch/jSerialComm.dll")).use {it.readBytes()}
        if(!Files.exists(library) || !Files.readAllBytes(library).contentEquals(bytes)) Files.write(library,bytes)
        System.setProperty("jSerialComm.library.path",directory.toAbsolutePath().toString())
    }
}
