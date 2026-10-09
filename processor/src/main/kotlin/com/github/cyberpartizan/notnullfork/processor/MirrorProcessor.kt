package com.github.cyberpartizan.notnullfork.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.File

private const val REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody"
private const val JACKSON_PACKAGE_PREFIX = "com.fasterxml.jackson."
private const val JACKSON_ANNOTATIONS_INSIDE = "com.fasterxml.jackson.annotation.JacksonAnnotationsInside"
private const val JSON_DESERIALIZE = "com.fasterxml.jackson.databind.annotation.JsonDeserialize"

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
                if (mirrored.add(name) && isSupported(requestBody)) generate(requestBody)
            }
        return emptyList()
    }

    // Only flat classes whose properties are all in the primary constructor are supported so far;
    // anything else is left to Spring.
    private fun isSupported(requestBody: KSClassDeclaration): Boolean {
        val parameters = requestBody.primaryConstructor?.parameters
        val supported = requestBody.origin == Origin.KOTLIN &&
            requestBody.classKind == ClassKind.CLASS &&
            requestBody.typeParameters.isEmpty() &&
            parameters != null &&
            parameters.all { it.isVal || it.isVar }
        if (!supported) logger.info("NotNullForK: no Mirror generated for ${requestBody.qualifiedName?.asString()}", requestBody)
        return supported
    }

    private fun generate(requestBody: KSClassDeclaration) {
        if (requestBody.annotations.any { it.qualifiedName() == JSON_DESERIALIZE }) {
            // The Request Body's own deserializer builds the Request Body, not the Mirror.
            return noMirror(requestBody, "it has a class-level @JsonDeserialize")
        }
        val parameters = requestBody.primaryConstructor!!.parameters
        val properties = requestBody.declarations.filterIsInstance<KSPropertyDeclaration>()
            .associateBy { it.simpleName.asString() }
        val defaulted = parameters.filter { it.hasDefault }
        val source = if (defaulted.isEmpty()) null else DefaultValues.read(requestBody)
        if (defaulted.isNotEmpty() && source == null) {
            return noMirror(requestBody, "the default values of its properties can't be read from its source file")
        }

        val requiredNames = parameters.filter { !it.hasDefault && !it.type.resolve().isMarkedNullable }
            .map { it.name!!.asString() }.toSet()
        val fields = parameters.map { parameter ->
            val name = parameter.name!!.asString()
            val type = parameter.type.toTypeName()
            val mutable = parameter.isVar
            val annotations = jacksonAnnotations(parameter, properties[name])
            when {
                parameter.hasDefault -> {
                    val defaultValue = source!!.parameters[name]?.defaultValue
                        ?: return noMirror(requestBody, "the default value of '$name' can't be read from its source file")
                    val unresolvable = source.unresolvableNames(name, requiredNames)
                    if (unresolvable.isNotEmpty()) {
                        return noMirror(requestBody, "the default value of '$name' refers to ${unresolvable.joinToString { "'$it'" }}")
                    }
                    MirrorField.Defaulted(name, type, mutable, annotations, defaultValue)
                }
                type.isNullable -> MirrorField.Nullable(name, type, mutable, annotations)
                else -> MirrorField.Required(name, type, mutable, annotations)
            }
        }

        val file = MirrorGenerator.generate(
            requestBody.toClassName(),
            fields,
            source?.imports.orEmpty(),
            requestBody.annotations.toSpecs(),
        )
        val dependencies = Dependencies(aggregating = false, *listOfNotNull(requestBody.containingFile).toTypedArray())
        codeGenerator.createNewFile(dependencies, file.packageName, file.fileName).bufferedWriter().use { it.write(file.text) }
    }

    /**
     * The Jackson annotations of a primary constructor property, wherever they are written: on the
     * parameter, the property, its field, getter or setter. Each keeps its use-site target.
     */
    private fun jacksonAnnotations(parameter: KSValueParameter, property: KSPropertyDeclaration?): List<AnnotationSpec> {
        val onDeclaration = (parameter.annotations + property?.annotations.orEmpty()).toSpecs()
        // KSP may report accessor annotations without their use-site target.
        val onGetter = property?.getter?.annotations.orEmpty().toSpecs(AnnotationSpec.UseSiteTarget.GET)
        val onSetter = property?.setter?.annotations.orEmpty().toSpecs(AnnotationSpec.UseSiteTarget.SET)
        return (onDeclaration + onGetter + onSetter).distinct()
    }

    /** The Jackson annotations among these, with [target] as the use-site target of those that have none. */
    private fun Sequence<KSAnnotation>.toSpecs(target: AnnotationSpec.UseSiteTarget? = null): List<AnnotationSpec> =
        filter { it.isJackson() }.map { annotation ->
            val spec = annotation.toAnnotationSpec(omitDefaultValues = true)
            if (spec.useSiteTarget == null && target != null) spec.toBuilder().useSiteTarget(target).build() else spec
        }.toList()

    private fun KSAnnotation.qualifiedName(): String? =
        annotationType.resolve().declaration.qualifiedName?.asString()

    /** A Jackson annotation, or an annotation that bundles Jackson annotations with `@JacksonAnnotationsInside`. */
    private fun KSAnnotation.isJackson(): Boolean =
        qualifiedName()?.startsWith(JACKSON_PACKAGE_PREFIX) == true ||
            annotationType.resolve().declaration.annotations.any { it.qualifiedName() == JACKSON_ANNOTATIONS_INSIDE }

    private fun noMirror(requestBody: KSClassDeclaration, reason: String) {
        logger.warn("NotNullForK: no Mirror generated for ${requestBody.qualifiedName!!.asString()}: $reason", requestBody)
    }
}

/**
 * The default values of a Request Body's primary constructor, as written in its source file, and
 * the imports they need in the Mirror's file to mean the same thing.
 */
private class DefaultValues(
    val parameters: Map<String, ParameterSource>,
    val imports: List<String>,
    /** Names a default value can't use from the Mirror: private declarations in the Request Body's scope. */
    private val privateNames: Set<String>,
) {
    /**
     * The names the default value of [parameter] refers to that the Mirror can't resolve: Required
     * Fields (which are nullable in the Mirror) and private declarations.
     */
    fun unresolvableNames(parameter: String, requiredNames: Set<String>): List<String> =
        parameters.getValue(parameter).referencedNames.filter { it in requiredNames || it in privateNames }

    companion object {
        fun read(requestBody: KSClassDeclaration): DefaultValues? {
            val file = requestBody.containingFile ?: return null
            val line = (requestBody.location as? FileLocation)?.lineNumber ?: return null
            val source = KotlinSource(File(file.filePath).takeIf { it.isFile }?.readText() ?: return null)
            val parameters = source.primaryConstructorOf(requestBody.simpleName.asString(), line) ?: return null
            val referenced = parameters.values.flatMap { it.referencedNames }.toSet()

            val imports = source.imports.toMutableList()
            if (file.packageName.asString().isNotEmpty()) imports += "${file.packageName.asString()}.*"
            val privateNames = file.declarations.filter { it.isPrivate() }.map { it.simpleName.asString() }.toMutableSet()

            // Inside a class, its nested classes and companion object members are in scope without
            // imports. The innermost class wins.
            val inScope = mutableSetOf<String>()
            for (enclosing in generateSequence(requestBody) { it.parentDeclaration as? KSClassDeclaration }) {
                val nested = enclosing.declarations.filterIsInstance<KSClassDeclaration>()
                val companion = nested.firstOrNull { it.isCompanionObject }
                val members = nested.filterNot { it.isCompanionObject } + companion?.declarations.orEmpty()
                for (member in members) {
                    val name = member.simpleName.asString()
                    if (name !in referenced || !inScope.add(name)) continue
                    if (member.isPrivate() || Modifier.PROTECTED in member.modifiers) {
                        privateNames += name
                    } else {
                        imports += member.qualifiedName!!.asString()
                    }
                }
            }
            return DefaultValues(parameters, imports, privateNames)
        }

        private fun KSDeclaration.isPrivate() = Modifier.PRIVATE in modifiers
    }
}
