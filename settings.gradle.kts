pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }
rootProject.name = "APRSERJ64"
include(":core")
project(":core").projectDir = file("shared/aprs-core")
