package com.github.cyberpartizan.notnullfork.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Publishes this project to a local Maven repository, as a release would publish it, without signing keys.
 * Then builds the test app (`/test-app`) against only that repository: the app resolves the plugin by id,
 * and the plugin brings the processor and runtime.
 */
class PublishingFunctionalTest {

    // Not deleted: on Windows, the TestKit daemon keeps the library jars that KSP read open.
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var testAppDir: File

    private val version = System.getProperty("notNullForKVersion")

    // An isolated local Maven repository, so the test doesn't touch ~/.m2.
    private val mavenLocal = File(System.getProperty("mavenLocal"))

    @Test
    fun `the published artifacts are complete and the test app builds against them`() {
        mavenLocal.deleteRecursively()

        val publish = GradleRunner.create()
            .withProjectDir(File(System.getProperty("rootProjectDir")))
            // Without a signing key, even where one is set up for releases.
            .withEnvironment(System.getenv().filterKeys { !it.startsWith("ORG_GRADLE_PROJECT_signing") })
            .withArguments("publishToMavenLocal", "-Dmaven.repo.local=${mavenLocal.absolutePath}", "--stacktrace")
            .forwardOutput()
            .build()

        for (module in listOf("runtime", "processor", "gradle-plugin")) {
            assertThat(publish.task(":not-null-for-k-$module:publishToMavenLocal")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        // Maven Central needs sources and javadoc jars and POM metadata; Gradle needs the module metadata.
        for (module in listOf("runtime", "processor")) {
            val artifactId = "not-null-for-k-$module"
            for (suffix in ARTIFACT_SUFFIXES) {
                assertThat(artifact(artifactId, suffix)).exists()
            }
            assertThat(artifact(artifactId, ".pom").readText()).contains(
                "<name>$artifactId</name>",
                "<description>",
                "<url>https://github.com/cyberpartizan/NotNullForK</url>",
                "<name>The Apache License, Version 2.0</name>",
                "<id>cyberpartizan</id>",
                "<connection>scm:git:https://github.com/cyberpartizan/NotNullForK.git</connection>",
            )
        }
        // Only the module metadata keeps the KSP version only preferred (ADR 0003).
        for (suffix in ARTIFACT_SUFFIXES) {
            assertThat(artifact("not-null-for-k-gradle-plugin", suffix)).exists()
        }
        assertThat(artifact("not-null-for-k-gradle-plugin", ".module").readText()).contains("\"prefers\"")
        val marker = File(
            mavenLocal,
            "io/github/cyberpartizan/not-null-for-k/io.github.cyberpartizan.not-null-for-k.gradle.plugin/$version/" +
                "io.github.cyberpartizan.not-null-for-k.gradle.plugin-$version.pom",
        )
        assertThat(marker.readText()).contains("<name>NotNullForK</name>", "<artifactId>not-null-for-k-gradle-plugin</artifactId>")
        // Without signing keys nothing is signed.
        assertThat(mavenLocal.walkTopDown().filter { it.extension == "asc" }.toList()).isEmpty()

        File(System.getProperty("testApp")).copyRecursively(testAppDir)
        testAppDir.walkTopDown().filter { it.isDirectory && it.name in setOf("build", ".gradle", ".kotlin") }.toList()
            .forEach { it.deleteRecursively() }
        // The newest supported Spring Boot / Kotlin combination; TestAppFunctionalTest covers the others.
        val arguments = listOf(
            "-Dmaven.repo.local=${mavenLocal.absolutePath}",
            "-PnotNullForKVersion=$version",
            "-PspringBootVersion=3.5.16",
            "-PkotlinVersion=2.4.21",
            "--stacktrace",
        )
        val publishLibrary = testApp(listOf(":published-library:publish") + arguments)
        assertThat(publishLibrary.task(":published-library:publish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val result = testApp(listOf("test") + arguments)

        assertThat(result.task(":kspKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(executedTestCount()).isGreaterThan(0)
    }

    private fun artifact(artifactId: String, suffix: String) =
        File(mavenLocal, "io/github/cyberpartizan/$artifactId/$version/$artifactId-$version$suffix")

    private fun testApp(arguments: List<String>) =
        GradleRunner.create()
            .withProjectDir(testAppDir)
            .withArguments(arguments)
            .forwardOutput()
            .build()

    private companion object {
        val ARTIFACT_SUFFIXES = listOf(".jar", "-sources.jar", "-javadoc.jar", ".pom", ".module")
    }

    private fun executedTestCount(): Int =
        File(testAppDir, "build/test-results/test").listFiles { f -> f.extension == "xml" }.orEmpty()
            .sumOf { Regex("""tests="(\d+)"""").find(it.readText())!!.groupValues[1].toInt() }
}
