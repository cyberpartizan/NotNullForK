rootProject.name = "not-null-for-k"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// Project names are the published artifact ids.
include("not-null-for-k-runtime", "not-null-for-k-processor", "not-null-for-k-gradle-plugin")
project(":not-null-for-k-runtime").projectDir = file("runtime")
project(":not-null-for-k-processor").projectDir = file("processor")
project(":not-null-for-k-gradle-plugin").projectDir = file("gradle-plugin")
