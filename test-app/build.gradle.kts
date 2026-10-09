plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.github.cyberpartizan.not-null-for-k")
}

// Spring Boot 3.0 manages an older Kotlin; keep the stdlib in line with the Kotlin plugin.
val kotlinVersion: String by project
extra["kotlin.version"] = kotlinVersion

// Compile for, and run the tests on, JVM 17.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared-requests"))
    // Published to build/library-repo by `:published-library:publish` before the app is built.
    implementation("com.example.published:invoice-requests:1.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
