package com.github.cyberpartizan.notnullfork

import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.exc.InvalidNullException
import com.fasterxml.jackson.core.JsonParser
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

/*
 * The default values of a Request Body compiled in another module (or a library) can't be copied
 * from its source into its Mirror. Its Mirror holds each Defaulted Field as a `Present<T>?` instead:
 * `null` if the JSON doesn't have the field, or the value the JSON has. It maps to the Request Body
 * through the constructor that the Kotlin compiler generates to apply default values. See
 * docs/adr/0002-defaults-of-classes-from-other-modules.md.
 */

/** The value, possibly `null`, that the JSON has for a Defaulted Field. Used by generated Mirrors. */
class Present<out T>(val value: T)

/**
 * Reads a `Present<T>?` Defaulted Field of a generated Mirror: the field's value, read as Jackson reads
 * a `T` property, or the value Jackson reads a JSON `null` as. A field that the JSON doesn't have
 * stays `null`.
 *
 * Use [NonNull] for a field of a non-null type: a JSON `null` (or a value read as `null`) then fails
 * the request, as it would without the plugin; a primitive type is read as Jackson reads it, so a JSON
 * `null` is `0` or `false` unless `FAIL_ON_NULL_FOR_PRIMITIVES` is on.
 */
open class PresentDeserializer : JsonDeserializer<Present<*>>(), ContextualDeserializer {

    /** Whether the Request Body's field is of a non-null type. */
    protected open val nonNull: Boolean get() = false

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> {
        val presentType = context.contextualType?.takeIf { it.hasRawClass(Present::class.java) } ?: property?.type
            ?: return context.reportBadDefinition(Present::class.java, "NotNullForK: no type for PresentDeserializer")
        var valueType = presentType.containedType(0)
            ?: return context.reportBadDefinition(presentType, "NotNullForK: $presentType has no type argument")
        // Kotlin's non-null Int is a JVM int in the Request Body's constructor.
        val primitive = valueType.rawClass.kotlin.javaPrimitiveType
        if (nonNull && primitive != null) valueType = context.constructType(primitive)
        return ForProperty(context.findContextualValueDeserializer(valueType, property), valueType, property, nonNull)
    }

    override fun deserialize(parser: JsonParser, context: DeserializationContext): Present<*> =
        throw IllegalStateException("NotNullForK: PresentDeserializer is only used through createContextual")

    /** Reads a Defaulted Field of a non-null type. */
    class NonNull : PresentDeserializer() {
        override val nonNull: Boolean get() = true
    }

    private class ForProperty(
        private val deserializer: JsonDeserializer<*>,
        private val valueType: JavaType,
        private val property: BeanProperty?,
        private val nonNull: Boolean,
    ) : JsonDeserializer<Present<*>>() {

        override fun deserialize(parser: JsonParser, context: DeserializationContext): Present<*> =
            present(deserializer.deserialize(parser, context), context)

        override fun getNullValue(context: DeserializationContext): Present<*> =
            present(deserializer.getNullValue(context), context)

        /** A field that the JSON doesn't have stays `null`, so that the Request Body's default value applies. */
        override fun getAbsentValue(context: DeserializationContext): Any? = null

        private fun present(value: Any?, context: DeserializationContext): Present<*> {
            if (value == null && nonNull) {
                throw InvalidNullException.from(context, property?.fullName ?: PropertyName.NO_NAME, valueType)
            }
            return Present(value)
        }
    }
}

/**
 * Builds a [T] through the constructor that the Kotlin compiler generates to apply the default values
 * of its primary constructor, which has [parameterCount] parameters. The constructor is looked up once.
 *
 * The arguments at the [defaulted] indexes are `Present<*>?`: `null` gets the parameter's default value.
 * Used by generated Mirrors.
 */
class DefaultsConstructor<T : Any>(type: Class<T>, private val parameterCount: Int, private val defaulted: IntArray) {

    private val parameterTypes: Array<Class<*>>
    private val handle: MethodHandle

    init {
        // (parameters..., one Int bit mask per 32 parameters, DefaultConstructorMarker)
        val masks = (parameterCount + Int.SIZE_BITS - 1) / Int.SIZE_BITS
        val constructor = type.declaredConstructors.singleOrNull {
            it.isSynthetic && it.parameterCount == parameterCount + masks + 1 &&
                it.parameterTypes.last().name == "kotlin.jvm.internal.DefaultConstructorMarker"
        } ?: throw IllegalStateException(
            "NotNullForK: ${type.name} has no single constructor that applies the default values of its primary constructor",
        )
        parameterTypes = constructor.parameterTypes
        handle = MethodHandles.lookup().unreflectConstructor(constructor)
    }

    /** A new [T] built from the primary constructor's [arguments], in order. */
    fun newInstance(vararg arguments: Any?): T {
        require(arguments.size == parameterCount) { "NotNullForK: expected $parameterCount arguments, got ${arguments.size}" }
        val all = arrayOfNulls<Any?>(parameterTypes.size)
        val masks = IntArray(parameterTypes.size - parameterCount - 1)
        for (index in 0 until parameterCount) all[index] = arguments[index]
        for (index in defaulted) {
            val present = arguments[index] as Present<*>?
            if (present == null) {
                masks[index / Int.SIZE_BITS] = masks[index / Int.SIZE_BITS] or (1 shl (index % Int.SIZE_BITS))
                all[index] = zeroOf(parameterTypes[index])
            } else {
                all[index] = present.value
            }
        }
        for ((i, mask) in masks.withIndex()) all[parameterCount + i] = mask
        @Suppress("UNCHECKED_CAST")
        return handle.invokeWithArguments(*all) as T
    }

    /** A placeholder for a parameter that gets its default value: the constructor ignores it. */
    private fun zeroOf(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Character.TYPE -> '\u0000'
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }
}
