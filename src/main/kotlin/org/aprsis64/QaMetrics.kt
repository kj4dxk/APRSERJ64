// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import java.awt.EventQueue
import java.nio.file.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Opt-in QA instrumentation; no packets, callsigns or credentials are recorded. */
internal fun startQaMetrics(model:AppModel):Job? {
 val destination=System.getenv("APRSIS64_QA_METRICS")?.takeIf {it.isNotBlank()} ?: return null
 val path=Paths.get(destination);path.toAbsolutePath().parent?.let {Files.createDirectories(it)}
 return CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
  while(isActive) {
   val start=System.nanoTime();val tick=CompletableDeferred<Unit>()
   EventQueue.invokeLater {tick.complete(Unit)}
   val responsive=withTimeoutOrNull(5000){tick.await();true}?:false
   val runtime=Runtime.getRuntime()
   val line=buildJsonObject {
    put("time_ms",System.currentTimeMillis());put("ui_dispatch_ms",(System.nanoTime()-start)/1e6);put("responsive",responsive)
    put("processed",model.archive.revision.value);put("dropped",model.archive.dropped.value)
    put("stations",model.stations.value.size);put("heap_used_mb",(runtime.totalMemory()-runtime.freeMemory())/1048576)
   }
   Files.writeString(path,line.toString()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND)
   delay(1000)
  }
 }
}
