// End-to-end test app for the NotNullForK plugin. It is run by the TestKit tests in the
// gradle-plugin module, which pass the versions and the local test repository as -P properties.
pluginManagement {
    val testRepo: String by settings
    val notNullForKVersion: String by settings
    val kotlinVersion: String by settings
    val springBootVersion: String by settings
    repositories {
        maven { url = uri(testRepo) }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version "1.1.7"
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
        // Where `published-library` is published to, before the app is built against its jar.
        maven { url = uri(settingsDir.resolve("build/library-repo")) }
        mavenCentral()
    }
}

rootProject.name = "test-app"

// Request Bodies in a sibling module that doesn't apply the plugin, used by the app as a project dependency.
include("shared-requests")
// Request Bodies in a library that the app uses as a published jar, not as a project dependency.
include("published-library")
