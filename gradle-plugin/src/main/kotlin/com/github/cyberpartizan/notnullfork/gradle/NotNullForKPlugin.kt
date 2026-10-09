package com.github.cyberpartizan.notnullfork.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

/**
 * Applies KSP to a Kotlin JVM project and adds the NotNullForK processor and runtime,
 * so the consumer needs no other build configuration. A KSP version the consumer applies itself is used
 * instead of the plugin's own (see ADR 0003).
 */
class NotNullForKPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.pluginManager.apply("com.google.devtools.ksp")
            project.dependencies.add("ksp", "$GROUP:not-null-for-k-processor:$version")
            project.dependencies.add("implementation", "$GROUP:not-null-for-k-runtime:$version")
        }
    }

    private companion object {
        const val GROUP = "com.github.cyberpartizan"

        val version: String by lazy {
            val properties = Properties()
            NotNullForKPlugin::class.java.getResourceAsStream("/META-INF/not-null-for-k.properties")!!
                .use { properties.load(it) }
            properties.getProperty("version")
        }
    }
}
