package org.aprserj64

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import com.fazecast.jSerialComm.SerialPort

class SerialDevicesTest {
    @Test fun windowsNativeDiscoveryLoadsMatchingLibrary() {
        if(!System.getProperty("os.name").startsWith("Windows")) return
        val home=Files.createTempDirectory("aprserj64-serial-discovery")
        SerialDevices.prepare(home)
        val library=java.nio.file.Path.of(System.getProperty("jSerialComm.library.path"),"jSerialComm.dll")
        assertTrue(Files.size(library)>10000)
        SerialPort.getCommPorts() // A missing or mismatched native binary must fail this test.
    }
}
