package com.github.cyberpartizan.notnullfork.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

/**
 * Builds the test app (`/test-app`), which applies only the plugin, and runs its tests
 * once per supported Spring Boot / Kotlin combination. Below Kotlin 2.0, the app applies the matching KSP
 * itself (ADR 0004). The app uses Request Bodies of a sibling module
 * and of a published library jar, which don't apply the plugin; the jar is published first.
 */
class TestAppFunctionalTest {

    // Not deleted: on Windows, the TestKit daemon keeps the library jars that KSP read open.
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var projectDir: File

    @ParameterizedTest(name = "Spring Boot {0}, Kotlin {1}, KSP {2}")
    @CsvSource(
        "3.0.13, 1.8.22, 1.8.22-1.0.11",
        "3.0.13, 2.0.21, ",
        "3.5.16, 2.4.21, ",
    )
    fun `the test app tests pass`(springBootVersion: String, kotlinVersion: String, kspVersion: String?) {
        File(System.getProperty("testApp")).copyRecursively(projectDir)
        projectDir.walkTopDown().filter { it.isDirectory && it.name in setOf("build", ".gradle", ".kotlin") }.toList()
            .forEach { it.deleteRecursively() }
        if (kspVersion != null) {
            val buildFile = File(projectDir, "build.gradle.kts")
            buildFile.writeText(
                buildFile.readText()
                    .replaceFirst("plugins {", "plugins {\n    id(\"com.google.devtools.ksp\") version \"$kspVersion\""),
            )
        }

        val properties = listOf(
            "-PtestRepo=${File(System.getProperty("testRepo")).toURI()}",
            "-PnotNullForKVersion=${System.getProperty("notNullForKVersion")}",
            "-PspringBootVersion=$springBootVersion",
            "-PkotlinVersion=$kotlinVersion",
        )
        val publish = gradle(listOf(":published-library:publish") + properties)
        assertThat(publish.task(":published-library:publish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val result = gradle(listOf("test") + properties)

        assertThat(result.task(":kspKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        // The modules with the Request Bodies don't apply the plugin.
        assertThat(result.task(":shared-requests:kspKotlin")).isNull()
        assertThat(result.task(":published-library:kspKotlin")).isNull()
        assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(executedTestCount()).isGreaterThan(0)
        // Request Bodies the plugin can't handle don't fail the build: it warns about them.
        val warnings = result.output.lines().filter { it.startsWith("w: [ksp]") }
        assertThat(warnings).anyMatch {
            it.endsWith("NotNullForK: no Mirror generated for com.example.app.Draft: its property 'body' is not in the primary constructor")
        }
        assertThat(warnings).anyMatch {
            it.endsWith(
                "NotNullForK: the Defaulted Field 'packaging' of com.example.app.PlaceOrderRequest is left as it is, " +
                    "so the Required Fields of com.example.app.Packaging in it are not checked",
            )
        }
        assertThat(warnings).noneMatch { "NotNullForKIgnore" in it }
        assertThat(warnings).noneMatch { "com.example.shared." in it || "com.example.invoices." in it }
        // The generated Mirrors compile without warnings, so a consumer may turn warnings into errors.
        assertThat(result.output.lines()).noneMatch { it.startsWith("w: ") && "/build/generated/ksp/" in it }
    }

    /** Runs the test app's build with [arguments]; it must succeed. */
    private fun gradle(arguments: List<String>) =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(arguments + "--stacktrace")
            .forwardOutput()
            .build()

    private fun executedTestCount(): Int =
        File(projectDir, "build/test-results/test").listFiles { f -> f.extension == "xml" }.orEmpty()
            .sumOf { Regex("""tests="(\d+)"""").find(it.readText())!!.groupValues[1].toInt() }
}
