import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.plugin.publish) apply false
}

// Release metadata shared by all published artifacts. All artifacts take the one version in gradle.properties.
val projectUrl = "https://github.com/cyberpartizan/not-null-for-k"

// Local Maven repository the TestKit test app resolves the plugin, processor and runtime from.
val testRepo = layout.buildDirectory.dir("test-repo")

// The fast loop: the test app's tests on one Spring Boot / Kotlin combination, the newest row of
// TestAppFunctionalTest unless -PspringBootVersion / -PkotlinVersion say otherwise.
tasks.register<GradleBuild>("runTestApp") {
    group = "verification"
    description = "Publishes NotNullForK to build/test-repo and runs the test app's tests against it."
    dependsOn(subprojects.map { "${it.path}:publishAllPublicationsToTestRepoRepository" })
    dir = file("test-app")
    tasks = listOf(":published-library:publish", "test")
    startParameter.projectProperties = mapOf(
        "testRepo" to testRepo.get().asFile.toURI().toString(),
        "notNullForKVersion" to version.toString(),
        "springBootVersion" to (findProperty("springBootVersion") ?: "3.5.16").toString(),
        "kotlinVersion" to (findProperty("kotlinVersion") ?: "2.4.21").toString(),
    )
}

// The hard Avoid terms of GLOSSARY.md. Its softer ones ("copy", "skip", "original") are fine as plain verbs,
// so review checks those (CODING_STANDARDS.md).
val checkGlossaryTerms = tasks.register("checkGlossaryTerms") {
    group = "verification"
    description = "Fails when a source or doc file uses a term GLOSSARY.md says to avoid."
    val sources = fileTree(projectDir) {
        include("**/*.kt", "**/*.kts", "**/*.md")
        // This file holds the terms themselves.
        exclude("GLOSSARY.md", "build.gradle.kts", ".scratch/**", "**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }
    inputs.files(sources)
    val avoid = Regex(
        """\bDTOs?\b|\bpayloads?\b|\bshadows?\b|\btwins?\b|generated bod(y|ies)|mandatory fields?|not-null fields?|""" +
            """optional fields?|nested Request Bod(y|ies)|sub-DTOs?|\bexclusions?\b""",
        RegexOption.IGNORE_CASE,
    )
    doLast {
        val hits = sources.flatMap { file ->
            file.readLines().withIndex()
                // Bean Validation requires a constraint's `payload: Array<KClass<out Payload>>` member.
                .filter { (_, line) -> avoid.containsMatchIn(line) && "Payload" !in line }
                .map { (i, line) -> "${file.relativeTo(projectDir)}:${i + 1}: ${line.trim()}" }
        }
        if (hits.isNotEmpty()) throw GradleException("Terms GLOSSARY.md says to avoid:\n" + hits.joinToString("\n"))
    }
}
tasks.check { dependsOn(checkGlossaryTerms) }

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    extensions.configure<JavaPluginExtension> {
        // Maven Central and the Gradle Plugin Portal need both. The Kotlin sources give an empty javadoc jar.
        withSourcesJar()
        withJavadocJar()
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    extensions.configure<KotlinJvmProjectExtension> {
        // Consumers may use Kotlin 1.8.22 (ADR 0004), so keep the metadata and stdlib readable by it.
        coreLibrariesVersion = "1.8.22"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            apiVersion.set(KotlinVersion.KOTLIN_1_8)
            languageVersion.set(KotlinVersion.KOTLIN_1_8)
            // Language version 1.8 is deprecated, but it is the one consumers need; silence the warning on every compilation.
            freeCompilerArgs.add("-Xsuppress-version-warnings")
        }
    }

    val publishing = extensions.getByType<PublishingExtension>()
    publishing.repositories {
        maven {
            name = "testRepo"
            url = uri(testRepo)
        }
    }
    // The processor and runtime go to Maven Central; the Gradle plugin goes to the Gradle Plugin Portal.
    if (project.name != "not-null-for-k-gradle-plugin") {
        publishing.repositories {
            maven {
                // Credentials: the mavenCentralUsername and mavenCentralPassword properties (a Central Portal user token).
                name = "mavenCentral"
                url = uri(
                    if (version.toString().endsWith("-SNAPSHOT")) "https://central.sonatype.com/repository/maven-snapshots/"
                    else "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/",
                )
                credentials(PasswordCredentials::class)
            }
        }
    }
    publishing.publications.withType<MavenPublication>().configureEach {
        pom {
            // Conventions only: the plugin marker publications set their own name and description.
            name.convention(project.name)
            description.convention(provider { project.description })
            url.set(projectUrl)
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("cyberpartizan")
                    name.set("cyberpartizan")
                    url.set("https://github.com/cyberpartizan")
                }
            }
            scm {
                connection.set("scm:git:$projectUrl.git")
                developerConnection.set("scm:git:$projectUrl.git")
                url.set(projectUrl)
            }
        }
    }

    // Signs only when a key is given (see README), so local builds and publishToMavenLocal need none.
    val signingKey = providers.gradleProperty("signingKey")
    extensions.configure<SigningExtension> {
        isRequired = signingKey.isPresent
        if (signingKey.isPresent) {
            useInMemoryPgpKeys(signingKey.get(), providers.gradleProperty("signingPassword").orNull)
            sign(publishing.publications)
        }
    }
}
