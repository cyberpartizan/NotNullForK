package com.github.cyberpartizan.notnullfork.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

/**
 * Builds the test app (`/test-app`), which applies only the plugin, and runs its tests
 * once per supported Spring Boot / Kotlin combination.
 */
class TestAppFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest(name = "Spring Boot {0}, Kotlin {1}")
    @CsvSource(
        "3.0.13, 2.0.21",
        "3.5.16, 2.4.21",
    )
    fun `the test app tests pass`(springBootVersion: String, kotlinVersion: String) {
        File(System.getProperty("testApp")).copyRecursively(projectDir)
        listOf("build", ".gradle", ".kotlin").forEach { File(projectDir, it).deleteRecursively() }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "test",
                "--stacktrace",
                "-PtestRepo=${File(System.getProperty("testRepo")).toURI()}",
                "-PnotNullForKVersion=${System.getProperty("notNullForKVersion")}",
                "-PspringBootVersion=$springBootVersion",
                "-PkotlinVersion=$kotlinVersion",
            )
            .forwardOutput()
            .build()

        assertThat(result.task(":kspKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(executedTestCount()).isGreaterThan(0)
    }

    private fun executedTestCount(): Int =
        File(projectDir, "build/test-results/test").listFiles { f -> f.extension == "xml" }.orEmpty()
            .sumOf { Regex("""tests="(\d+)"""").find(it.readText())!!.groupValues[1].toInt() }
}
