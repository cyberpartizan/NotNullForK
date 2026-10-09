package com.github.cyberpartizan.notnullfork.processor

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode

private val MIRROR = ClassName("com.github.cyberpartizan.notnullfork", "Mirror")
private val NOT_NULL = ClassName("jakarta.validation.constraints", "NotNull")
private val VALID = ClassName("jakarta.validation", "Valid")
private val JSON_DESERIALIZE = ClassName("com.fasterxml.jackson.databind.annotation", "JsonDeserialize")
private val NESTED_MIRROR_DESERIALIZER = ClassName("com.github.cyberpartizan.notnullfork", "NestedMirrorDeserializer")
private val PRESENT = ClassName("com.github.cyberpartizan.notnullfork", "Present")
private val PRESENT_DESERIALIZER = ClassName("com.github.cyberpartizan.notnullfork", "PresentDeserializer")
private val NON_NULL_PRESENT_DESERIALIZER = PRESENT_DESERIALIZER.nestedClass("NonNull")
private val DEFAULTS_CONSTRUCTOR = ClassName("com.github.cyberpartizan.notnullfork", "DefaultsConstructor")
private val ELEMENTS_NOT_NULL = ClassName("com.github.cyberpartizan.notnullfork.elements", "NotNull")
private val LIST = ClassName("kotlin.collections", "List")
private val MAP = ClassName("kotlin.collections", "Map")
private val ARRAY_LIST = ClassName("kotlin.collections", "ArrayList")
private val LINKED_HASH_SET = ClassName("kotlin.collections", "LinkedHashSet")
private val LINKED_HASH_MAP = ClassName("kotlin.collections", "LinkedHashMap")

/**
 * A Request Body property as it appears in the Mirror. It is a `var` in the Mirror if it is one in
 * the Request Body ([mutable]).
 *
 * [annotations] are the property's Jackson annotations, written as in the Request Body: one with a
 * use-site target keeps it; one without is written on the constructor property without a target, so
 * the compiler puts it where it put the Request Body's (this depends on the Kotlin version, which is
 * the same for both).
 *
 * A Required or nullable field whose type is a Nested Type ([nested]) is a `Mirror<Nested>?` in the
 * Mirror. The runtime reads it into the Nested Type's Mirror (unless the app's ObjectMapper reads the
 * type in its own way), and validation cascades into it.
 *
 * A Required or nullable list, set or map whose elements are checked or mapped one by one has a
 * [container]; [nested] is then the Nested Type of its elements (values of a map), if they have one.
 */
internal sealed class MirrorField(
    val name: String,
    val type: TypeName,
    val mutable: Boolean,
    val annotations: List<AnnotationSpec>,
    val nested: ClassName?,
    val container: Container?,
) {
    /** Non-null without a default value: nullable in the Mirror and must not be null. */
    class Required(
        name: String,
        type: TypeName,
        mutable: Boolean,
        annotations: List<AnnotationSpec>,
        nested: ClassName? = null,
        container: Container? = null,
    ) : MirrorField(name, type, mutable, annotations, nested, container)

    /** Nullable without a default value: copied as it is (a Nested Type becomes its Mirror). */
    class Nullable(
        name: String,
        type: TypeName,
        mutable: Boolean,
        annotations: List<AnnotationSpec>,
        nested: ClassName? = null,
        container: Container? = null,
    ) : MirrorField(name, type, mutable, annotations, nested, container)

    /**
     * Has a default value: copied as it is, with the [defaultValue] expression as written in the source.
     *
     * Without a [defaultValue] (the Request Body is compiled in another module, so its source can't be
     * read), it is a `Present<T>?` in the Mirror: `null` if the JSON doesn't have it, so that the
     * Request Body's own default value applies, or the value (`null` too) that the JSON has.
     */
    class Defaulted(
        name: String,
        type: TypeName,
        mutable: Boolean,
        annotations: List<AnnotationSpec>,
        val defaultValue: String?,
    ) : MirrorField(name, type, mutable, annotations, nested = null, container = null)

    /**
     * This field with [nested] as its Nested Type (that of its elements, if it has a [container]). A
     * Defaulted Field keeps the Request Body's type.
     */
    fun withNested(nested: ClassName): MirrorField = when (this) {
        is Required -> Required(name, type, mutable, annotations, nested, container)
        is Nullable -> Nullable(name, type, mutable, annotations, nested, container)
        is Defaulted -> this
    }
}

/**
 * How a list, set or map field of [element]s (values of a map, whose keys are [key]s) is mirrored. In
 * the Mirror it is a `List` (of a list or set) or a `Map` whose elements are nullable, so that Jackson
 * reads `null` elements; they must not be null if [elementRequired]. It is mapped to a new `ArrayList`,
 * `LinkedHashSet` or `LinkedHashMap` of the Request Body's element type, in the order of the JSON.
 */
internal class Container(val kind: Kind, val key: TypeName?, val element: TypeName, val elementRequired: Boolean) {
    enum class Kind { LIST, SET, MAP }
}

/** A generated Kotlin source file. */
internal class GeneratedFile(val packageName: String, val fileName: String, val text: String)

internal object MirrorGenerator {

    /**
     * Builds `{package}.generated.{RequestBody}Mirror`. Nested classes are joined with `_`
     * (`Outer.Inner` becomes `Outer_InnerMirror`); the runtime looks Mirrors up by this name.
     *
     * [imports] (e.g. `java.util.Locale`, `a.b.*`, `a.b.C as D`) are added as written, so that the
     * copied default values resolve as they do in the Request Body's file.
     *
     * [annotations] are the Request Body's class-level Jackson annotations.
     */
    fun generate(
        requestBody: ClassName,
        fields: List<MirrorField>,
        imports: List<String>,
        annotations: List<AnnotationSpec>,
    ): GeneratedFile {
        val mirrorName = ClassName(
            listOf(requestBody.packageName, "generated").filter { it.isNotEmpty() }.joinToString("."),
            requestBody.simpleNames.joinToString("_") + "Mirror",
        )
        // Default values are raw source text; placeholders keep KotlinPoet from reformatting them.
        val placeholders = mutableMapOf<String, String>()

        val constructor = FunSpec.constructorBuilder()
        val mirror = TypeSpec.classBuilder(mirrorName)
            .addKdoc("Mirror of [%T], generated by NotNullForK.", requestBody)
            .addSuperinterface(MIRROR.parameterizedBy(requestBody))
            .addAnnotations(annotations)
        for (field in fields) {
            val nested = field.nested
            val container = field.container
            val type = field.mirrorType()
            val parameter = ParameterSpec.builder(field.name, type)
            val property = PropertySpec.builder(field.name, type).mutable(field.mutable).initializer("%N", field.name)
            for (annotation in field.annotations) {
                val target = annotation.useSiteTarget
                if (target == null || target == AnnotationSpec.UseSiteTarget.PARAM) {
                    parameter.addAnnotation(annotation)
                } else {
                    property.addAnnotation(annotation)
                }
            }
            when (field) {
                is MirrorField.Required -> {
                    parameter.defaultValue("null")
                    property.addAnnotation(AnnotationSpec.builder(NOT_NULL).useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD).build())
                }
                is MirrorField.Defaulted -> if (field.defaultValue != null) {
                    val placeholder = "__notNullForKDefault${placeholders.size}__"
                    placeholders[placeholder] = field.defaultValue
                    parameter.defaultValue("%L", placeholder)
                } else {
                    parameter.defaultValue("null")
                    val deserializer = if (field.type.isNullable) PRESENT_DESERIALIZER else NON_NULL_PRESENT_DESERIALIZER
                    parameter.addAnnotation(
                        AnnotationSpec.builder(JSON_DESERIALIZE)
                            .useSiteTarget(AnnotationSpec.UseSiteTarget.PARAM)
                            .addMember("using = %T::class", deserializer)
                            .build(),
                    )
                }
                is MirrorField.Nullable -> Unit
            }
            if (container?.elementRequired == true) {
                property.addAnnotation(AnnotationSpec.builder(ELEMENTS_NOT_NULL).useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD).build())
            }
            if (nested != null) {
                parameter.addAnnotation(
                    AnnotationSpec.builder(JSON_DESERIALIZE)
                        .useSiteTarget(AnnotationSpec.UseSiteTarget.PARAM)
                        .addMember(if (container != null) "contentUsing = %T::class" else "using = %T::class", NESTED_MIRROR_DESERIALIZER)
                        .build(),
                )
                property.addAnnotation(AnnotationSpec.builder(VALID).useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD).build())
            }
            constructor.addParameter(parameter.build())
            mirror.addProperty(property.build())
        }
        mirror.primaryConstructor(constructor.build())
        val toRequestBody = FunSpec.builder("toRequestBody").addModifiers(KModifier.OVERRIDE).returns(requestBody)
        // The indexes (in the primary constructor, whose parameters [fields] are in order) of the
        // Defaulted Fields whose default values the Request Body's constructor applies.
        val defaultedIndexes = fields.indices.filter { (fields[it] as? MirrorField.Defaulted)?.let { field -> field.defaultValue == null } == true }
        if (defaultedIndexes.isEmpty()) {
            val arguments = fields.map { CodeBlock.of("%N = %L", it.name, it.toRequestBodyValue()) }
            toRequestBody.addStatement("return %T(%L)", requestBody, arguments.joinToCode(", "))
        } else {
            // Kotlin code can't call the constructor that applies the Request Body's default values.
            val defaults = PropertySpec.builder("DEFAULTS", DEFAULTS_CONSTRUCTOR.parameterizedBy(requestBody))
                .initializer("%T(%T::class.java, %L, intArrayOf(%L))", DEFAULTS_CONSTRUCTOR, requestBody, fields.size, defaultedIndexes.joinToString(", "))
                .build()
            mirror.addType(TypeSpec.companionObjectBuilder().addModifiers(KModifier.PRIVATE).addProperty(defaults).build())
            // Qualified, so that a field named DEFAULTS doesn't hide it.
            toRequestBody.addStatement("return %T.DEFAULTS.newInstance(%L)", mirrorName, fields.map { it.toRequestBodyValue() }.joinToCode(", "))
        }
        mirror.addFunction(toRequestBody.build())

        val file = FileSpec.builder(mirrorName).addType(mirror.build()).build()
        var text = withImports(file, imports)
        for ((placeholder, defaultValue) in placeholders) text = text.replace(placeholder, defaultValue)
        return GeneratedFile(mirrorName.packageName, mirrorName.simpleName, text)
    }

    private fun MirrorField.mirrorType(): TypeName {
        val nested = nested
        val container = container
        return when {
            container != null -> {
                val element = (nested?.let { MIRROR.parameterizedBy(it) } ?: container.element).copy(nullable = true)
                val mirrored = when (container.kind) {
                    Container.Kind.LIST, Container.Kind.SET -> LIST.parameterizedBy(element)
                    Container.Kind.MAP -> MAP.parameterizedBy(container.key!!, element)
                }
                mirrored.copy(nullable = true)
            }
            nested != null -> MIRROR.parameterizedBy(nested).copy(nullable = true)
            this is MirrorField.Required -> type.copy(nullable = true)
            this is MirrorField.Defaulted && defaultValue == null -> PRESENT.parameterizedBy(type).copy(nullable = true)
            else -> type
        }
    }

    /**
     * The Request Body's value of this field; Required Fields have been checked by now. A Defaulted Field
     * without a default value is passed as it is, to [DEFAULTS_CONSTRUCTOR].
     */
    private fun MirrorField.toRequestBodyValue(): CodeBlock {
        val required = this is MirrorField.Required
        val container = container
        if (container != null) {
            val value = if (container.kind == Container.Kind.MAP) "it.value" else "it"
            val element = when {
                nested != null && container.elementRequired -> "$value!!.toRequestBody()"
                nested != null -> "$value?.toRequestBody()"
                container.elementRequired -> "$value!!"
                else -> value
            }
            val receiver = if (required) "%N!!" else "%N?"
            val (function, target) = when (container.kind) {
                Container.Kind.LIST -> "mapTo" to ARRAY_LIST.parameterizedBy(container.element)
                Container.Kind.SET -> "mapTo" to LINKED_HASH_SET.parameterizedBy(container.element)
                Container.Kind.MAP -> "mapValuesTo" to LINKED_HASH_MAP.parameterizedBy(container.key!!, container.element)
            }
            return CodeBlock.of("$receiver.$function(%T())·{ $element }", name, target)
        }
        return when {
            nested != null && required -> CodeBlock.of("%N!!.toRequestBody()", name)
            nested != null -> CodeBlock.of("%N?.toRequestBody()", name)
            required -> CodeBlock.of("%N!!", name)
            else -> CodeBlock.of("%N", name)
        }
    }

    /**
     * Renders [file] with [imports] added. Where one of KotlinPoet's own imports would clash with an
     * import of the same name, KotlinPoet's is aliased, so names in the copied default values keep
     * their meaning.
     */
    private fun withImports(file: FileSpec, imports: List<String>): String {
        if (imports.isEmpty()) return file.toString()
        val importedNames = imports.filterNot { it.endsWith(".*") }.associate { importedName(it) to unescaped(it) }

        var rendered = file.toString()
        val clashes = importLines(rendered).filter { (path, name) -> importedNames[name]?.let { it != path } == true }
        if (clashes.isNotEmpty()) {
            val builder = file.toBuilder()
            for ((path, name) in clashes) builder.addAliasedImport(ClassName.bestGuess(path), "NotNullForK_$name")
            rendered = builder.build().toString()
        }

        val existing = importLines(rendered).map { it.first }.toSet()
        val added = imports.filterNot { unescaped(it) in existing }.map { "import $it" }
        if (added.isEmpty()) return rendered
        val lines = rendered.lines()
        val insertAt = lines.indexOfLast { it.startsWith("import ") }.takeIf { it >= 0 }
            ?: lines.indexOfFirst { it.startsWith("package ") }
        return (lines.take(insertAt + 1) + added + lines.drop(insertAt + 1)).joinToString("\n")
    }

    /**
     * `(import without backticks, name it makes visible)` for each non-aliased, non-star import
     * KotlinPoet emitted. KotlinPoet escapes keywords such as `annotation` in package names.
     */
    private fun importLines(rendered: String): List<Pair<String, String>> =
        rendered.lines().filter { it.startsWith("import ") && " as " !in it }
            .map { unescaped(it.removePrefix("import ").trim()) }
            .map { it to importedName(it) }

    private fun unescaped(import: String): String = import.replace("`", "")

    private fun importedName(import: String): String =
        import.substringAfter(" as ", import.substringAfterLast('.')).trim().removeSurrounding("`")
}
