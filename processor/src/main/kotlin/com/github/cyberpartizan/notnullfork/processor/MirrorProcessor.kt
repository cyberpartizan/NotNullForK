package com.github.cyberpartizan.notnullfork.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Origin
import com.squareup.kotlinpoet.ksp.writeTo

private const val REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody"

class MirrorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        MirrorProcessor(environment.codeGenerator, environment.logger)
}

/**
 * Generates a Mirror for every Kotlin class used as a `@RequestBody` parameter.
 */
class MirrorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private val mirrored = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(REQUEST_BODY)
            .filterIsInstance<KSValueParameter>()
            .mapNotNull { it.type.resolve().declaration as? KSClassDeclaration }
            .forEach { requestBody ->
                val name = requestBody.qualifiedName?.asString() ?: return@forEach
                if (mirrored.add(name) && isSupported(requestBody)) {
                    MirrorGenerator.generate(requestBody)
                        .writeTo(codeGenerator, aggregating = false, originatingKSFiles = listOfNotNull(requestBody.containingFile))
                }
            }
        return emptyList()
    }

    // Only flat classes made of Required Fields are supported so far; anything else is left to Spring.
    private fun isSupported(requestBody: KSClassDeclaration): Boolean {
        val parameters = requestBody.primaryConstructor?.parameters
        val supported = requestBody.origin == Origin.KOTLIN &&
            requestBody.classKind == ClassKind.CLASS &&
            requestBody.typeParameters.isEmpty() &&
            parameters != null &&
            parameters.all { (it.isVal || it.isVar) && !it.hasDefault && !it.type.resolve().isMarkedNullable }
        if (!supported) logger.info("NotNullForK: no Mirror generated for ${requestBody.qualifiedName?.asString()}", requestBody)
        return supported
    }
}
