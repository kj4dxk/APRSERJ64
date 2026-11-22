import java.util.zip.ZipFile
plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}
group = "org.aprsfoundation.community"
version = "0.8.0"
repositories { google(); mavenCentral() }
kotlin { jvmToolchain(25) }
dependencies {
    implementation(project(":core"))
    implementation("com.github.davidmoten:predict4java:1.3.1")
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation("org.maplibre.compose:maplibre-compose:0.20.0")
    runtimeOnly("org.maplibre.compose:maplibre-compose-runtime-vulkan-windows-x64:0.20.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.tomlj:tomlj:1.1.1")
    compileOnly("org.checkerframework:checker-qual:3.43.0")
    implementation("com.fazecast:jSerialComm:2.11.0")
    testImplementation(kotlin("test-junit"))
}
tasks.processResources {
    from("assets/aprs-symbols") {
        include("primary/*.svg", "alternate/*.svg")
        into("aprs-symbols")
    }
}

compose.desktop {
    application {
        mainClass = "org.aprsis64.MainKt"
        // Vulkan shares textures with Compose's Direct3D12 host; Intel WGL import failed in testing.
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Xmx768m")
        nativeDistributions {
            windows { iconFile.set(project.file("assets/aprsis64.ico")) }
            packageName = "APRSIS64"
            packageVersion = "0.8.0"
            description = "Portable APRS mapping, messaging and radio gateway"
            vendor = "APRSIS64 contributors"
            appResourcesRootDir.set(layout.buildDirectory.dir("portableResources"))
            modules("java.sql", "java.net.http", "jdk.unsupported", "jdk.crypto.ec", "jdk.httpserver")
        }
    }
}

val collectNotices = tasks.register("collectNotices") {
    val output = layout.buildDirectory.dir("portableResources/common")
    inputs.files(configurations.runtimeClasspath)
    inputs.files("LICENSE", "THIRD-PARTY.txt")
    outputs.dir(output)
    doLast {
        val target = output.get().asFile.apply { mkdirs() }
        listOf("LICENSE", "THIRD-PARTY.txt").forEach { file(it).copyTo(target.resolve(it), overwrite=true) }
        val noticeIndex = StringBuilder()
        configurations.runtimeClasspath.get().files.filter { it.extension == "jar" }.sortedBy { it.name }.forEachIndexed { jarIndex, jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { entry ->
                    !entry.isDirectory && entry.size in 1..1_000_000 &&
                        entry.name.substringAfterLast('/').matches(Regex("(?i)(license|notice|copying|copyright)([.\\-_].*)?"))
                }.forEachIndexed { entryIndex, entry ->
                    val relative="notices/$jarIndex/$entryIndex.txt"
                    noticeIndex.append("$relative: ${jar.name} / ${entry.name}\n")
                    val dest=target.resolve(relative)
                    dest.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        target.resolve("notice-index.txt").writeText(noticeIndex.toString())
    }
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(collectNotices) }
tasks.matching { it.name == "createDistributable" }.configureEach {
    // Compose's app-image up-to-date check otherwise misses notice-only edits.
    inputs.files("LICENSE", "THIRD-PARTY.txt")
    inputs.dir(layout.buildDirectory.dir("portableResources/common"))
    dependsOn(collectNotices)
}
tasks.withType<Test>().configureEach { jvmArgs("--enable-native-access=ALL-UNNAMED") }

tasks.register<Exec>("packagePortable") {
    dependsOn("createDistributable")
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", file("portable/build.ps1").absolutePath)
}
