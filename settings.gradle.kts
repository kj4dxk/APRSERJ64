pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }
rootProject.name = "APRSIS64"
include(":core")
project(":core").projectDir = file("shared/aprs-core")
