// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import java.nio.file.*
import java.util.Properties
import java.security.MessageDigest

/** Keep kill progress across restarts, keyed by the complete object configuration. */
class BeaconJournal(private val directory:Path) {
 private fun path(p:Properties):Path {
  val canonical=p.stringPropertyNames().sorted().joinToString("\n") {"$it=${p.getProperty(it)}"}
  val hash=MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") {"%02x".format(it)}
  return directory.resolve("$hash.txt")
 }
 fun read(p:Properties):Pair<Int,Long> = runCatching {
  val fields=Files.readString(path(p)).trim().split(',');fields[0].toInt().coerceIn(0,3) to fields[1].toLong()
 }.getOrDefault(0 to 0L)
 fun write(p:Properties,count:Int,time:Long) {
  Files.createDirectories(directory);val temporary=Files.createTempFile(directory,"kill-",".tmp")
  try {Files.writeString(temporary,"$count,$time");Files.move(temporary,path(p),StandardCopyOption.REPLACE_EXISTING)}finally {Files.deleteIfExists(temporary)}
 }
 fun revive(p:Properties) {val dead=Properties().apply {putAll(p);setProperty("alive","false")};Files.deleteIfExists(path(dead))}
}
