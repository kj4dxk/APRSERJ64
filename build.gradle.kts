import java.util.zip.ZipFile
plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}
group = "org.aprsfoundation.community"
version = "0.2.0"
repositories { google(); mavenCentral() }
kotlin { jvmToolchain(25) }
dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation("org.maplibre.compose:maplibre-compose:0.20.0")
    runtimeOnly("org.maplibre.compose:maplibre-compose-runtime-vulkan-windows-x64:0.20.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation(kotlin("test-junit"))
}
compose.desktop {
    application {
        mainClass = "org.aprsis64.MainKt"
        // Vulkan shares textures with Compose's Direct3D12 host; Intel WGL import failed in testing.
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Xmx768m")
        nativeDistributions {
            packageName = "APRSIS64"
            packageVersion = "0.2.0"
            description = "APRS map and persistent chat prototype"
            vendor = "APRSIS64 contributors"
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            modules("java.sql", "java.net.http", "jdk.unsupported", "jdk.crypto.ec")
        }
    }
}

val collectNotices by tasks.registering {
    val output = layout.buildDirectory.dir("appResources/common")
    inputs.files(configurations.runtimeClasspath)
    inputs.files("LICENSE", "THIRD-PARTY.txt")
    outputs.dir(output)
    doLast {
        val target = output.get().asFile.apply { mkdirs() }
        listOf("LICENSE", "THIRD-PARTY.txt").forEach { file(it).copyTo(target.resolve(it), overwrite=true) }
        configurations.runtimeClasspath.get().files.filter { it.extension == "jar" }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { entry ->
                    !entry.isDirectory && entry.size in 1..1_000_000 &&
                        entry.name.substringAfterLast('/').matches(Regex("(?i)(license|notice|copying|copyright)([.\\-_].*)?"))
                }.forEach { entry ->
                    val dest=target.resolve("dependency-notices/${jar.nameWithoutExtension}/${entry.name.replace('/','_')}")
                    dest.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(collectNotices) }
tasks.matching { it.name == "createDistributable" }.configureEach {
    // Compose's app-image up-to-date check otherwise misses notice-only edits.
    inputs.files("LICENSE", "THIRD-PARTY.txt")
}
tasks.withType<Test>().configureEach { jvmArgs("--enable-native-access=ALL-UNNAMED") }

tasks.register<Exec>("packagePortable") {
    dependsOn("createDistributable")
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", file("portable/build.ps1").absolutePath)
}
