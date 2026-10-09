// A small consumer app for the KSP version tests in the gradle-plugin module. The test writes its
// build.gradle.kts, which decides whether and in which order the consumer applies KSP itself.
pluginManagement {
    val testRepo: String by settings
    val notNullForKVersion: String by settings
    val kotlinVersion: String by settings
    repositories {
        maven { url = uri(testRepo) }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version kotlinVersion
        id("com.github.cyberpartizan.not-null-for-k") version notNullForKVersion
    }
}

plugins {
    // Downloads a JDK 17 for the toolchain if none is installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    val testRepo: String by settings
    repositories {
        maven { url = uri(testRepo) }
        mavenCentral()
    }
}

rootProject.name = "ksp-consumer"

// Another KSP processor of the consumer, which runs alongside the NotNullForK processor.
include("other-processor")
