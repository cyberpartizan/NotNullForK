plugins {
    `java-gradle-plugin`
}

dependencies {
    // Only preferred: a KSP version the consumer puts on the build script classpath wins, older or newer.
    // The plugin applies KSP by id, so it needs no KSP classes to compile.
    runtimeOnly(libs.ksp.gradle.plugin.get().module.toString()) {
        version { prefer(libs.versions.ksp.get()) }
    }
}

gradlePlugin {
    plugins {
        create("notNullForK") {
            id = "com.github.cyberpartizan.not-null-for-k"
            implementationClass = "com.github.cyberpartizan.notnullfork.gradle.NotNullForKPlugin"
        }
    }
}

// The plugin adds the processor and runtime in its own version.
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("**/not-null-for-k.properties") { expand("version" to version) }
}

testing {
    suites {
        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(gradleTestKit())
                implementation(libs.assertj)
                implementation("org.junit.jupiter:junit-jupiter-params")
            }
            targets.configureEach {
                testTask.configure {
                    dependsOn(
                        listOf(":not-null-for-k-runtime", ":not-null-for-k-processor", ":not-null-for-k-gradle-plugin")
                            .map { "$it:publishAllPublicationsToTestRepoRepository" },
                    )
                    val testApp = rootProject.layout.projectDirectory.dir("test-app")
                    inputs.files(fileTree(testApp) { exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**") })
                        .withPropertyName("testApp")
                    systemProperty("testApp", testApp.asFile.absolutePath)
                    val testKspConsumer = rootProject.layout.projectDirectory.dir("test-ksp-consumer")
                    inputs.files(fileTree(testKspConsumer) { exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**") })
                        .withPropertyName("testKspConsumer")
                    systemProperty("testKspConsumer", testKspConsumer.asFile.absolutePath)
                    systemProperty("testedKspVersion", libs.versions.ksp.get())
                    systemProperty("testRepo", rootProject.layout.buildDirectory.dir("test-repo").get().asFile.absolutePath)
                    systemProperty("notNullForKVersion", project.version.toString())
                    testLogging {
                        events("passed", "failed", "skipped")
                        showStandardStreams = true
                    }
                }
            }
        }
    }
}

tasks.check { dependsOn(testing.suites.named("functionalTest")) }
