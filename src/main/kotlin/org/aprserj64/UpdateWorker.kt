package org.aprserj64

import java.nio.file.Files
import java.nio.file.Path

internal class UpdateWorker(val process:Process,val restart:Path) {
    companion object {
        fun start(home:Path,releaseApi:String):UpdateWorker {
            val launcher=Path.of(requireNotNull(System.getenv("APRSERJ64_LAUNCHER")){"Open the portable executable to install updates."})
            require(Files.isSameFile(launcher,home.resolve("APRSERJ64.exe"))) { "Use the portable APRSERJ64 launcher to install updates." }
            // Windows jpackage can put another copy of its engine between the JVM and the portable launcher.
            val parent=generateSequence(ProcessHandle.current()) { it.parent().orElse(null) }.drop(1).firstOrNull { candidate ->
                candidate.info().command().orElse(null)?.let { Files.isSameFile(launcher,Path.of(it)) } == true
            } ?: error("The portable APRSERJ64 launcher is no longer running.")
            val stage=Files.createTempDirectory(home.resolve("cache"),"update-")
            val script=stage.resolve("install.ps1")
            checkNotNull(UpdateWorker::class.java.getResourceAsStream("/update-portable.ps1")).use {Files.copy(it,script)}
            checkNotNull(UpdateWorker::class.java.getResourceAsStream("/install-verified-update.ps1")).use {Files.copy(it,stage.resolve("install-verified-update.ps1"))}
            val process=ProcessBuilder("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",script.toString(),
                launcher.toString(),parent.pid().toString(),AppVersion.value,releaseApi)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(home.resolve("logs/update-worker.log").toFile())).start()
            return UpdateWorker(process,stage.resolve("restart"))
        }
    }
}
