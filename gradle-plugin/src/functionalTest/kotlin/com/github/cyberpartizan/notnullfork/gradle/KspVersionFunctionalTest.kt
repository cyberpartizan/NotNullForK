package com.github.cyberpartizan.notnullfork.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

/**
 * Builds a small consumer app (`/test-ksp-consumer`) that either applies KSP itself, before or after
 * the plugin, or leaves it to the plugin. The app also has another KSP processor of its own.
 */
class KspVersionFunctionalTest {

    // Not deleted: on Windows, the TestKit daemon keeps the processor jars open.
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var projectDir: File

    @ParameterizedTest(name = "Kotlin {0}, no KSP of its own")
    @CsvSource("1.9.25", "2.0.21", "2.4.21")
    fun `a consumer without KSP gets the tested KSP version`(kotlinVersion: String) {
        val result = build(kotlinVersion, plugins = listOf(KOTLIN, NOT_NULL_FOR_K))

        assertThat(kspGradlePluginVersion(result.output)).isEqualTo(System.getProperty("testedKspVersion"))
    }

    @ParameterizedTest(name = "Kotlin {0}, KSP {1} applied {2} the plugin")
    @CsvSource(
        // KSP1, for a Kotlin older than 2.0; a consumer of Kotlin 1.8 must apply it (ADR 0004).
        "1.8.22, 1.8.22-1.0.11, BEFORE",
        "1.8.22, 1.8.22-1.0.11, AFTER",
        "1.9.25, 1.9.25-1.0.20, BEFORE",
        "1.9.25, 1.9.25-1.0.20, AFTER",
        // Older than the tested KSP version.
        "2.0.21, 2.0.21-1.0.28, BEFORE",
        "2.0.21, 2.0.21-1.0.28, AFTER",
        // Newer than the tested KSP version.
        "2.4.21, 2.3.12, BEFORE",
        "2.4.21, 2.3.12, AFTER",
    )
    fun `a consumer that applies KSP keeps its own KSP version`(kotlinVersion: String, kspVersion: String, order: Order) {
        val ksp = """id("com.google.devtools.ksp") version "$kspVersion""""
        val plugins = if (order == Order.BEFORE) listOf(KOTLIN, ksp, NOT_NULL_FOR_K) else listOf(KOTLIN, NOT_NULL_FOR_K, ksp)

        val result = build(kotlinVersion, plugins)

        assertThat(kspGradlePluginVersion(result.output)).isEqualTo(kspVersion)
    }

    @Test
    fun `a consumer of Kotlin 1_8 without KSP is told to apply KSP itself`() {
        val result = consumer("1.8.22", plugins = listOf(KOTLIN, NOT_NULL_FOR_K)).buildAndFail()

        assertThat(result.output).contains(
            "NotNullForK: Kotlin 1.8.22 needs KSP1",
            """id("com.google.devtools.ksp") version "1.8.22-1.0.""",
        )
    }

    /**
     * Builds the consumer app with [plugins] applied in that order. The app's code compiles only if
     * both its other KSP processor and the NotNullForK processor ran.
     */
    private fun build(kotlinVersion: String, plugins: List<String>): BuildResult {
        val result = consumer(kotlinVersion, plugins).build()

        assertThat(result.task(":kspKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(projectDir, "build/generated/ksp/main/kotlin/com/example/consumer/generated/GreetRequestMirror.kt"))
            .exists()
        return result
    }

    /** A runner of `compileKotlin` in the consumer app with [plugins] applied in that order. */
    private fun consumer(kotlinVersion: String, plugins: List<String>): GradleRunner {
        File(System.getProperty("testKspConsumer")).copyRecursively(projectDir)
        projectDir.walkTopDown().filter { it.isDirectory && it.name in setOf("build", ".gradle", ".kotlin") }.toList()
            .forEach { it.deleteRecursively() }
        File(projectDir, "build.gradle.kts").writeText(
            """
            |plugins {
            |${plugins.joinToString("\n") { "    $it" }}
            |}
            |
            |// Kotlin 1.8 can't target the JVM the build runs on.
            |kotlin {
            |    jvmToolchain(17)
            |}
            |
            |dependencies {
            |    ksp(project(":other-processor"))
            |    implementation("org.springframework.boot:spring-boot-starter-web:3.0.13")
            |}
            |
            |// The KSP Gradle plugin jar on the build script classpath, named after its version.
            |println("KSP Gradle plugin: " + plugins.getPlugin("com.google.devtools.ksp").javaClass
            |    .protectionDomain.codeSource.location.path.substringAfterLast('/'))
            |""".trimMargin(),
        )

        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "compileKotlin",
                "--stacktrace",
                "-PtestRepo=${File(System.getProperty("testRepo")).toURI()}",
                "-PnotNullForKVersion=${System.getProperty("notNullForKVersion")}",
                "-PkotlinVersion=$kotlinVersion",
            )
            .forwardOutput()
    }

    private fun kspGradlePluginVersion(output: String): String =
        Regex("""KSP Gradle plugin: symbol-processing-gradle-plugin-(.+)\.jar""").find(output)!!.groupValues[1]

    /** Whether the consumer applies KSP before or after the plugin. */
    enum class Order { BEFORE, AFTER }

    private companion object {
        const val KOTLIN = """kotlin("jvm")"""
        const val NOT_NULL_FOR_K = """id("io.github.cyberpartizan.not-null-for-k")"""
    }
}
