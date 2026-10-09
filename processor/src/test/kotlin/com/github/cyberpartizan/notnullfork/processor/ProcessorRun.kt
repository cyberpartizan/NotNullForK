package com.github.cyberpartizan.notnullfork.processor

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.nio.file.Files

/** What the build shows when the processor runs: its messages by level, as `file:line: message`. */
class ProcessorRun(val warnings: List<String>, val infos: List<String>, val errors: List<String>)

/**
 * Runs the Mirror processor with KSP on sample sources, as the build of a module that applies the
 * plugin would. [sources] maps a path under the source root (`.kt` or `.java`) to its content. The
 * samples can use Spring Web, Jackson, the Kotlin standard library and the NotNullForK runtime.
 */
fun runProcessor(vararg sources: Pair<String, String>): ProcessorRun {
    val dir = Files.createTempDirectory("not-null-for-k-ksp").toFile()
    try {
        val sourceRoot = File(dir, "src").apply { mkdirs() }
        for ((path, content) in sources) {
            File(sourceRoot, path).apply { parentFile.mkdirs() }.writeText(content.trimIndent())
        }
        val logger = CollectingLogger()
        val config = KSPJvmConfig.Builder().apply {
            moduleName = "main"
            sourceRoots = listOf(sourceRoot)
            javaSourceRoots = listOf(sourceRoot)
            libraries = System.getProperty("sampleLibraries").split(File.pathSeparator).map(::File)
            jdkHome = File(System.getProperty("java.home"))
            jvmTarget = "17"
            languageVersion = "2.0"
            apiVersion = "2.0"
            projectBaseDir = dir
            outputBaseDir = File(dir, "out")
            cachesDir = File(dir, "caches")
            classOutputDir = File(dir, "out/classes")
            kotlinOutputDir = File(dir, "out/kotlin")
            javaOutputDir = File(dir, "out/java")
            resourceOutputDir = File(dir, "out/resources")
        }.build()
        val exitCode = KotlinSymbolProcessing(config, listOf(MirrorProcessorProvider()), logger).execute()
        check(exitCode == KotlinSymbolProcessing.ExitCode.OK || logger.errors.isNotEmpty()) { "KSP failed: $exitCode" }
        return ProcessorRun(logger.warnings, logger.infos, logger.errors)
    } finally {
        dir.deleteRecursively()
    }
}

private class CollectingLogger : KSPLogger {
    val warnings = mutableListOf<String>()
    val infos = mutableListOf<String>()
    val errors = mutableListOf<String>()

    override fun logging(message: String, symbol: KSNode?) = Unit
    override fun info(message: String, symbol: KSNode?) { infos += located(message, symbol) }
    override fun warn(message: String, symbol: KSNode?) { warnings += located(message, symbol) }
    override fun error(message: String, symbol: KSNode?) { errors += located(message, symbol) }
    override fun exception(e: Throwable) { throw e }

    private fun located(message: String, symbol: KSNode?): String {
        val location = symbol?.location as? FileLocation ?: return message
        return "${File(location.filePath).name}:${location.lineNumber}: $message"
    }
}
