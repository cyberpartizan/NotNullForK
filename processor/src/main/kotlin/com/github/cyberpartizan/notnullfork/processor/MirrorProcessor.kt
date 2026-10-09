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
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.File

private const val REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody"
private const val JACKSON_PACKAGE_PREFIX = "com.fasterxml.jackson."
private const val JACKSON_ANNOTATIONS_INSIDE = "com.fasterxml.jackson.annotation.JacksonAnnotationsInside"
private const val JSON_DESERIALIZE = "com.fasterxml.jackson.databind.annotation.JsonDeserialize"
private const val JSON_CREATOR = "com.fasterxml.jackson.annotation.JsonCreator"
private val OWN_WAY_CLASS_ANNOTATIONS = setOf(JSON_DESERIALIZE, "com.fasterxml.jackson.annotation.JsonTypeInfo")

class MirrorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        MirrorProcessor(environment.codeGenerator, environment.logger)
}

/**
 * Generates a Mirror for every Kotlin class used as a `@RequestBody` parameter, and, recursively, for
 * the Nested Types of its Required and nullable fields. Each class gets at most one
 * Mirror, however many places use it.
 */
class MirrorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    /** What each class examined so far looks like in its Mirror, or `null` if it gets none. */
    private val plans = mutableMapOf<String, MirrorPlan?>()
    private val generated = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Every class to mirror, with the files of the `@RequestBody` parameters that lead to it.
        val toMirror = linkedMapOf<String, Pair<KSClassDeclaration, MutableSet<KSFile>>>()
        for (parameter in resolver.getSymbolsWithAnnotation(REQUEST_BODY).filterIsInstance<KSValueParameter>()) {
            val requestBody = parameter.type.resolve().declaration.classDeclaration() ?: continue
            for ((name, mirrored) in withNestedTypes(requestBody)) {
                toMirror.getOrPut(name) { mirrored to mutableSetOf() }.second += listOfNotNull((parameter.parent as? KSDeclaration)?.containingFile)
            }
        }
        for ((name, mirrored) in toMirror) {
            val (declaration, users) = mirrored
            if (generated.add(name)) write(declaration, planFor(declaration)!!, users)
        }
        return emptyList()
    }

    /** [requestBody] and every Nested Type it reaches that get Mirrors, by qualified name. */
    private fun withNestedTypes(requestBody: KSClassDeclaration): Map<String, KSClassDeclaration> {
        val reached = linkedMapOf<String, KSClassDeclaration>()
        val pending = ArrayDeque(listOf(requestBody))
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            val plan = planFor(next) ?: continue
            if (reached.putIfAbsent(plan.name, next) == null) pending += plan.fields.mapNotNull { it.nestedType() }
        }
        return reached
    }

    /** The field's Nested Type: the class of its type, if that may be one and gets a Mirror. */
    private fun PlannedField.nestedType(): KSClassDeclaration? = possiblyNested?.takeIf { planFor(it) != null }

    /** Writes the Mirror of [mirrored]. [users] are the files of the `@RequestBody` parameters that lead to it. */
    private fun write(mirrored: KSClassDeclaration, plan: MirrorPlan, users: Set<KSFile>) {
        val fields = plan.fields.map { planned -> planned.nestedType()?.let { planned.field.withNested(it.toClassName()) } ?: planned.field }
        val file = MirrorGenerator.generate(mirrored.toClassName(), fields, plan.imports, plan.annotations)
        // The Mirror changes with the class, and with whether its fields' types get Mirrors. The users'
        // files are included so that KSP processes them again, and so writes this Mirror again, when
        // any of these change.
        val sources = (listOf(mirrored) + plan.fields.mapNotNull { it.possiblyNested }).mapNotNull { it.containingFile } + users
        codeGenerator.createNewFile(Dependencies(aggregating = false, *sources.distinct().toTypedArray()), file.packageName, file.fileName)
            .bufferedWriter().use { it.write(file.text) }
    }

    /** What [requestBody] looks like in its Mirror, or `null` (once logged) if it gets none. */
    private fun planFor(requestBody: KSClassDeclaration): MirrorPlan? {
        val name = requestBody.qualifiedName?.asString() ?: return null
        return plans.getOrPut(name) { if (isSupported(requestBody)) plan(requestBody) else null }
    }

    // Only classes whose properties are all in the primary constructor, and that a Mirror in another
    // file can construct, are supported so far; anything else is left to Spring.
    private fun isSupported(requestBody: KSClassDeclaration): Boolean {
        val parameters = requestBody.primaryConstructor?.parameters
        val supported = requestBody.origin == Origin.KOTLIN &&
            requestBody.classKind == ClassKind.CLASS &&
            requestBody.typeParameters.isEmpty() &&
            UNCONSTRUCTIBLE.none { it in requestBody.modifiers } &&
            Modifier.PRIVATE !in requestBody.primaryConstructor?.modifiers.orEmpty() &&
            parameters != null &&
            parameters.all { it.isVal || it.isVar }
        if (!supported) logger.info("NotNullForK: no Mirror generated for ${requestBody.qualifiedName?.asString()}", requestBody)
        return supported
    }

    private fun plan(requestBody: KSClassDeclaration): MirrorPlan? {
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
                    // A Defaulted Field keeps the Request Body's type, even if that type has a Mirror.
                    PlannedField(MirrorField.Defaulted(name, type, mutable, annotations, defaultValue), possiblyNested = null)
                }
                else -> {
                    val (container, possiblyNested) = containerAndNestedTypeOf(parameter, annotations)
                    val field = if (type.isNullable) {
                        MirrorField.Nullable(name, type, mutable, annotations, container = container)
                    } else {
                        MirrorField.Required(name, type, mutable, annotations, container = container)
                    }
                    PlannedField(field, possiblyNested)
                }
            }
        }
        return MirrorPlan(requestBody.qualifiedName!!.asString(), fields, source?.imports.orEmpty(), requestBody.annotations.toSpecs())
    }

    /**
     * The [Container] of a Required or nullable list, set or map field whose elements (values of a map)
     * are non-null or may be Nested Types, and the class of the field's type (of its elements, for a
     * container) if it may be a Nested Type.
     *
     * A field that the property's own `@JsonDeserialize` ([annotations]) reads is left as it is. Only
     * the field's own list, set or map is a container: a list in a list is left as it is.
     */
    private fun containerAndNestedTypeOf(
        parameter: KSValueParameter,
        annotations: List<AnnotationSpec>,
    ): Pair<Container?, KSClassDeclaration?> {
        if (annotations.any { (it.typeName as? ClassName)?.canonicalName == JSON_DESERIALIZE }) return null to null
        val type = parameter.type.resolve().expanded()
        val kind = CONTAINERS[(type.declaration as? KSClassDeclaration)?.qualifiedName?.asString()]
            ?: return null to possiblyNestedTypeOf(type)
        val arguments = type.arguments
        if (arguments.any { it.variance != Variance.INVARIANT }) return null to null
        val element = arguments.last().type!!
        val elementType = element.resolve().expanded()
        val possiblyNested = possiblyNestedTypeOf(elementType)
        if (elementType.isMarkedNullable && possiblyNested == null) return null to null
        // Type annotations on the type arguments (e.g. constraints) are not copied to the Mirror.
        val key = arguments.takeIf { kind == Container.Kind.MAP }?.first()?.type?.toTypeName()?.copy(annotations = emptyList())
        val container = Container(kind, key, element.toTypeName().copy(annotations = emptyList()), !elementType.isMarkedNullable)
        return container to possiblyNested
    }

    /** The type a non-generic type alias stands for (nullable if either is), or this type. */
    private fun KSType.expanded(): KSType {
        val alias = declaration as? KSTypeAlias ?: return this
        if (alias.typeParameters.isNotEmpty()) return this
        val target = alias.type.resolve().expanded()
        return if (isMarkedNullable) target.makeNullable() else target
    }

    /**
     * The class of [type] if it may be a Nested Type: a class of this compilation. It is one if it gets
     * a Mirror. Library types such as `String` or `List` are not, nor is one that Jackson reads in its
     * own way (see [isReadByJacksonItsOwnWay]); those are left as they are, without a warning.
     */
    private fun possiblyNestedTypeOf(type: KSType): KSClassDeclaration? {
        if (type.arguments.isNotEmpty()) return null
        val declaration = type.declaration.classDeclaration() ?: return null
        return declaration.takeIf {
            it.origin == Origin.KOTLIN && it.classKind == ClassKind.CLASS && !it.isReadByJacksonItsOwnWay()
        }
    }

    /**
     * A class that Jackson builds through a `@JsonCreator`, reads with its own `@JsonDeserialize`, or
     * reads as a subtype (`@JsonTypeInfo` on it or a supertype): its Mirror would not be read the same way.
     */
    private fun KSClassDeclaration.isReadByJacksonItsOwnWay(): Boolean {
        val creators = getConstructors() + declarations.filterIsInstance<KSClassDeclaration>()
            .filter { it.isCompanionObject }.flatMap { it.getDeclaredFunctions() }
        if (creators.any { creator -> creator.annotations.any { it.qualifiedName() == JSON_CREATOR } }) return true
        val types = generateSequence(listOf(this)) { classes ->
            classes.flatMap { c -> c.superTypes.mapNotNull { it.resolve().declaration.classDeclaration() } }.takeIf { it.isNotEmpty() }
        }.flatten()
        return types.any { c -> c.annotations.any { it.qualifiedName() in OWN_WAY_CLASS_ANNOTATIONS } }
    }

    /** This class, or the class this type alias stands for. */
    private fun KSDeclaration.classDeclaration(): KSClassDeclaration? = when (this) {
        is KSClassDeclaration -> this
        is KSTypeAlias -> type.resolve().declaration.classDeclaration()
        else -> null
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

    private fun noMirror(requestBody: KSClassDeclaration, reason: String): MirrorPlan? {
        logger.warn("NotNullForK: no Mirror generated for ${requestBody.qualifiedName!!.asString()}: $reason", requestBody)
        return null
    }

    private companion object {
        /** Classes a Mirror can't construct. */
        val UNCONSTRUCTIBLE = listOf(Modifier.ABSTRACT, Modifier.SEALED, Modifier.INNER, Modifier.VALUE, Modifier.INLINE, Modifier.PRIVATE)

        /** The collection and map types whose elements are checked, or mapped, one by one. */
        val CONTAINERS = mapOf(
            "kotlin.collections.Collection" to Container.Kind.LIST,
            "kotlin.collections.MutableCollection" to Container.Kind.LIST,
            "kotlin.collections.List" to Container.Kind.LIST,
            "kotlin.collections.MutableList" to Container.Kind.LIST,
            "kotlin.collections.Set" to Container.Kind.SET,
            "kotlin.collections.MutableSet" to Container.Kind.SET,
            "kotlin.collections.Map" to Container.Kind.MAP,
            "kotlin.collections.MutableMap" to Container.Kind.MAP,
        )
    }
}

/** What a Request Body or Nested Type looks like in its Mirror, before it is known which of its field types have Mirrors. */
private class MirrorPlan(
    val name: String,
    val fields: List<PlannedField>,
    val imports: List<String>,
    val annotations: List<AnnotationSpec>,
)

/** A Mirror field, and the class of its type if that may be a Nested Type. */
private class PlannedField(val field: MirrorField, val possiblyNested: KSClassDeclaration?)

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
