plugins {
    kotlin("jvm")
}

// Kotlin 1.8 can't target the JVM the build runs on.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // The oldest KSP API in the tests; newer KSP versions run processors built against it.
    compileOnly("com.google.devtools.ksp:symbol-processing-api:1.8.22-1.0.11")
}
