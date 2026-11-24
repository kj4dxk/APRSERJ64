// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aprsfoundation.core.BleKissProfile

internal data class BleTncDevice(val address: String, val name: String)

/** A process per GATT connection keeps WinRT handles scoped to the enabled port. */
internal class WindowsBleKiss(address: String) : AutoCloseable {
    private val process=ProcessBuilder(listOf(executable.toString(),address.filter {it!=':' && it!='-'})+
        BleKissProfile.entries.flatMap {listOf(it.service,it.write,it.notify)}).start()
    private val replies=ArrayBlockingQueue<String>(8)
    private val commands=DataOutputStream(process.outputStream)
    val input=process.inputStream
    val output: OutputStream=object : OutputStream() {
        override fun write(value: Int)=write(byteArrayOf(value.toByte()))
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if(length==0) return
            commands.writeInt(length)
            commands.write(bytes,offset,length)
            commands.flush()
            expect("OK",15)
        }
    }

    init {
        thread(isDaemon=true,name="BLE KISS status") {
            try {process.errorStream.bufferedReader().useLines {lines->lines.forEach {replies.offer(it)}}}
            catch(_: IOException) { }
            finally {replies.offer("Bluetooth LE bridge closed")}
        }
    }

    fun awaitReady()=expect("READY",45)

    private fun expect(expected: String, seconds: Long) {
        val reply=replies.poll(seconds,TimeUnit.SECONDS)
        if(reply!=expected) throw IOException(reply?.removePrefix("ERROR ") ?: "Bluetooth LE bridge timed out")
    }

    override fun close() {
        process.destroyForcibly()
        replies.offer("Bluetooth LE disconnected")
    }

    companion object {
        suspend fun discover(): List<BleTncDevice> = withContext(Dispatchers.IO) {
            val process=ProcessBuilder(listOf(executable.toString(),"discover")+BleKissProfile.entries.map {it.service})
                .redirectErrorStream(true).start()
            val output=CompletableFuture.supplyAsync {process.inputStream.bufferedReader().use {it.readLines()}}
            try {
                if(!process.waitFor(8,TimeUnit.SECONDS)) throw IOException("Bluetooth LE scan timed out")
                val lines=output.get(2,TimeUnit.SECONDS)
                if(process.exitValue()!=0) throw IOException(lines.firstOrNull() ?: "Bluetooth LE unavailable")
                lines.mapNotNull {line->
                    val address=line.substringBefore('\t')
                    if(!address.matches(Regex("[0-9A-F]{12}"))) null
                    else BleTncDevice(address.chunked(2).joinToString(":"),line.substringAfter('\t',""))
                }.sortedWith(compareBy({it.name},{it.address}))
            } finally {if(process.isAlive) process.destroyForcibly()}
        }

        private val executable by lazy {
            val directory=Files.createTempDirectory("aprserj64-ble-")
            directory.toFile().deleteOnExit()
            val path=directory.resolve("APRSERJ64.BleKiss.exe")
            path.toFile().deleteOnExit()
            val resource=WindowsBleKiss::class.java.getResourceAsStream("/windows/APRSERJ64.BleKiss.exe")
                ?: throw IOException("Bluetooth LE bridge is missing from this build")
            resource.use {Files.copy(it,path)}
            path
        }
    }
}
