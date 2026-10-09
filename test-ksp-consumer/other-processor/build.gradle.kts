plugins {
    kotlin("jvm")
}

dependencies {
    // The oldest KSP API in the tests; newer KSP versions run processors built against it.
    compileOnly("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.28")
}
