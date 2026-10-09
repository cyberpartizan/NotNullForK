package com.github.cyberpartizan.notnullfork.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

/**
 * Applies KSP to a Kotlin JVM project and adds the NotNullForK processor and runtime,
 * so the consumer needs no other build configuration from Kotlin 1.9 on. A KSP version the consumer applies
 * itself is used instead of the plugin's own (see ADR 0003); on Kotlin 1.8 the consumer must apply KSP (ADR 0004).
 */
class NotNullForKPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            checkKspFitsKotlin(project)
            project.pluginManager.apply("com.google.devtools.ksp")
            project.dependencies.add("ksp", "$GROUP:not-null-for-k-processor:$version")
            project.dependencies.add("implementation", "$GROUP:not-null-for-k-runtime:$version")
        }
    }

    /**
     * Fails if the project's Kotlin is older than 1.9 and the KSP on the build script classpath is not KSP1,
     * as when the project leaves KSP to the plugin: that KSP fails on Kotlin 1.8 only when it runs, with an
     * unclear error (see ADR 0004). If either version can't be read, nothing is checked.
     */
    private fun checkKspFitsKotlin(project: Project) {
        val kotlinPlugin = project.plugins.findPlugin("org.jetbrains.kotlin.jvm") ?: return
        // E.g. "1.8.22-release-407(1.8.22)" or "2.4.21".
        val kotlinVersion = kotlinPlugin.javaClass.`package`?.implementationVersion?.substringBefore('-') ?: return
        if (!kotlinVersion.startsWith("1.") || kotlinVersion.startsWith("1.9.")) return
        // The KSP version without the Kotlin version, e.g. "1.0.11" for KSP 1.8.22-1.0.11.
        val kspVersion = try {
            Class.forName("com.google.devtools.ksp.gradle.KSPVersionsKt", false, javaClass.classLoader)
                .getMethod("getKSP_VERSION").invoke(null) as? String
        } catch (e: ReflectiveOperationException) {
            null
        } ?: return
        if (kspVersion.startsWith("1.")) return
        throw GradleException(
            "NotNullForK: Kotlin $kotlinVersion needs KSP1, but the build uses KSP $kspVersion, which needs Kotlin 1.9 " +
                "or newer (NotNullForK brings it unless the project applies its own KSP). " +
                "Apply the KSP of your Kotlin version in the plugins block, e.g. " +
                "id(\"com.google.devtools.ksp\") version \"$kotlinVersion-1.0.<n>\" (see https://github.com/google/ksp/releases).",
        )
    }

    private companion object {
        const val GROUP = "io.github.cyberpartizan"

        val version: String by lazy {
            val properties = Properties()
            NotNullForKPlugin::class.java.getResourceAsStream("/META-INF/not-null-for-k.properties")!!
                .use { properties.load(it) }
            properties.getProperty("version")
        }
    }
}
