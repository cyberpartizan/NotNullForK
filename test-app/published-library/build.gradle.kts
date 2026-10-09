// A library with Request Bodies, published as a jar that the app depends on. It doesn't apply the
// NotNullForK plugin.
plugins {
    kotlin("jvm")
    `maven-publish`
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
    compileOnly("com.github.cyberpartizan:not-null-for-k-runtime:$notNullForKVersion")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            // Not the project's own coordinates, so that Gradle can't mistake the jar for the project.
            groupId = "com.example.published"
            artifactId = "invoice-requests"
            version = "1.0"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "library"
            url = uri(rootProject.layout.buildDirectory.dir("library-repo"))
        }
    }
}
