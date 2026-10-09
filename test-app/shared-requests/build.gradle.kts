// A sibling module with Request Bodies. It doesn't apply the NotNullForK plugin: the app does.
plugins {
    kotlin("jvm")
}

val notNullForKVersion: String by project
val springBootVersion: String by project

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    compileOnly("com.fasterxml.jackson.core:jackson-annotations")
    // For the Opt-out annotation; the app gets the runtime from the plugin.
    compileOnly("io.github.cyberpartizan:not-null-for-k-runtime:$notNullForKVersion")
}
