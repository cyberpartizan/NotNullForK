package com.github.cyberpartizan.notnullfork

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase
import com.fasterxml.jackson.databind.deser.ContextualDeserializer

/**
 * Reads a Mirror property of type `Mirror<T>`, where `T` is a Nested Type, or (as `contentUsing`) the
 * `Mirror<T>` elements of a list or the values of a map property. Generated Mirrors use it.
 *
 * If the app's ObjectMapper reads `T` as a bean, the property is read into `T`'s Mirror, so that its
 * Required Fields are checked too. If the ObjectMapper has its own deserializer for `T` (e.g. from a
 * Jackson module), the property is read as `T` with that deserializer, as it would be without the
 * plugin. So is a `T` without a Mirror, which only happens if the build output is inconsistent.
 */
class NestedMirrorDeserializer : JsonDeserializer<Mirror<*>>(), ContextualDeserializer {

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> {
        // As the `contentUsing` of a list or map property, the contextual type is the element type.
        val mirrorType = context.contextualType?.takeIf { it.isTypeOrSubTypeOf(Mirror::class.java) } ?: property?.type
            ?: return context.reportBadDefinition(Mirror::class.java, "NotNullForK: no type for NestedMirrorDeserializer")
        val nestedType = mirrorType.containedType(0)
            ?: return context.reportBadDefinition(mirrorType, "NotNullForK: $mirrorType has no type argument")
        return ForProperty(nestedType, property)
    }

    override fun deserialize(parser: JsonParser, context: DeserializationContext): Mirror<*> =
        throw IllegalStateException("NotNullForK: NestedMirrorDeserializer is only used through createContextual")

    /**
     * Reads one property. It finds the deserializer to delegate to on first use, not while Jackson
     * builds the enclosing Mirror's deserializer, so that types that nest themselves don't recurse.
     */
    private class ForProperty(
        private val nestedType: JavaType,
        private val property: BeanProperty?,
    ) : JsonDeserializer<Mirror<*>>() {

        @Volatile
        private var delegate: JsonDeserializer<*>? = null

        override fun deserialize(parser: JsonParser, context: DeserializationContext): Mirror<*>? {
            val deserializer = delegate ?: delegateFor(context).also { delegate = it }
            return deserializer.deserialize(parser, context) as Mirror<*>?
        }

        private fun delegateFor(context: DeserializationContext): JsonDeserializer<*> {
            val nestedDeserializer = context.findContextualValueDeserializer(nestedType, property)
            val mirrorClass = Mirrors.mirrorClassFor(nestedType.rawClass)
            return if (nestedDeserializer is BeanDeserializerBase && mirrorClass != null) {
                context.findContextualValueDeserializer(context.constructType(mirrorClass), property)
            } else {
                AsMirror(nestedDeserializer)
            }
        }
    }

    /** Reads a `T` with [deserializer] and puts it behind the `Mirror` interface, with nothing to check. */
    private class AsMirror(private val deserializer: JsonDeserializer<*>) : JsonDeserializer<Mirror<*>>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Mirror<*>? =
            deserializer.deserialize(parser, context)?.let { AlreadyBuilt(it) }
    }

    /** A `T` that the ObjectMapper has already built, behind the `Mirror` interface. */
    private class AlreadyBuilt(private val value: Any) : Mirror<Any> {
        override fun toRequestBody(): Any = value
    }
}
