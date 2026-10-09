plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.ksp.gradle.plugin)
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
                    inputs.files(fileTree(testApp) { exclude("build/**", ".gradle/**", ".kotlin/**") })
                        .withPropertyName("testApp")
                    systemProperty("testApp", testApp.asFile.absolutePath)
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
