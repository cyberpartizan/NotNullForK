plugins {
    `java-gradle-plugin`
    // Publishes to the Gradle Plugin Portal (publishPlugins); see the README for the release steps.
    alias(libs.plugins.plugin.publish)
}

description = "Gradle plugin of NotNullForK: a Required Field missing from a Spring Kotlin Request Body " +
    "gives a per-field validation error instead of a deserialization failure."

dependencies {
    // Only preferred: a KSP version the consumer puts on the build script classpath wins, older or newer.
    // The plugin applies KSP by id, so it needs no KSP classes to compile.
    runtimeOnly(libs.ksp.gradle.plugin.get().module.toString()) {
        version { prefer(libs.versions.ksp.get()) }
    }
}

gradlePlugin {
    website = "https://github.com/cyberpartizan/not-null-for-k"
    vcsUrl = "https://github.com/cyberpartizan/not-null-for-k.git"
    plugins {
        create("notNullForK") {
            id = "com.github.cyberpartizan.not-null-for-k"
            displayName = "NotNullForK"
            description = project.description
            tags = listOf("kotlin", "spring", "spring-boot", "validation", "jackson", "ksp")
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
                    val publishedModules = listOf(":not-null-for-k-runtime", ":not-null-for-k-processor", ":not-null-for-k-gradle-plugin")
                    dependsOn(publishedModules.map { "$it:publishAllPublicationsToTestRepoRepository" })
                    // What gets published, so that the tests rerun when it changes.
                    inputs.files(publishedModules.map { project(it).tasks.named("jar") }).withPropertyName("publishedJars")
                    inputs.files(
                        rootProject.fileTree(rootProject.layout.projectDirectory) {
                            include("build.gradle.kts", "gradle.properties", "gradle/libs.versions.toml", "*/build.gradle.kts")
                            exclude("test-app/**", "test-ksp-consumer/**")
                        },
                    ).withPropertyName("buildScripts")
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
                    // PublishingFunctionalTest publishes this project to an isolated local Maven repository.
                    systemProperty("rootProjectDir", rootProject.layout.projectDirectory.asFile.absolutePath)
                    systemProperty("mavenLocal", rootProject.layout.buildDirectory.dir("maven-local").get().asFile.absolutePath)
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
