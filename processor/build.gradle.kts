description = "KSP processor of NotNullForK: generates a Mirror of every Request Body."

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)
}

// Compiled sample Request Bodies, which the processor tests see as classes of another module.
val sampleLibrary by sourceSets.creating

// The classpath of the sample Request Bodies the processor tests run KSP on.
val sampleLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    "sampleLibraryImplementation"(project(":not-null-for-k-runtime"))
    "sampleLibraryImplementation"(platform(libs.spring.boot.dependencies))
    "sampleLibraryImplementation"(libs.jackson.databind)
    sampleLibraries(sampleLibrary.output)
    sampleLibraries(project(":not-null-for-k-runtime"))
    sampleLibraries(platform(libs.spring.boot.dependencies))
    sampleLibraries(libs.spring.webmvc)
    sampleLibraries(libs.jackson.databind)
    sampleLibraries("org.jetbrains.kotlin:kotlin-stdlib")
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(libs.assertj)
                // Runs KSP (KSP2, standalone) on sample sources in the tests.
                implementation(libs.ksp.aa.embeddable)
                implementation(libs.ksp.common.deps)
                runtimeOnly(libs.kotlinx.coroutines.core)
            }
            targets.configureEach {
                testTask.configure {
                    val libraries = sampleLibraries
                    inputs.files(libraries).withPropertyName("sampleLibraries")
                    jvmArgumentProviders += CommandLineArgumentProvider {
                        listOf("-DsampleLibraries=${libraries.files.joinToString(File.pathSeparator)}")
                    }
                }
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
