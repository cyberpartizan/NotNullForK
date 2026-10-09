package com.github.cyberpartizan.notnullfork

import java.util.concurrent.ConcurrentHashMap

/**
 * A generated copy of a Request Body in which every Required Field is nullable and must not be null.
 * Incoming JSON is read into the Mirror, checked, and then mapped to the Request Body.
 *
 * The Mirror of `com.example.Outer.Inner` is `com.example.generated.Outer_InnerMirror`.
 */
interface Mirror<out T : Any> {
    /** Maps this Mirror to its Request Body. Only valid once the Required Fields have been checked. */
    fun toRequestBody(): T
}

internal object Mirrors {
    private val cache = ConcurrentHashMap<Class<*>, Any>()
    private val none = Any()

    /** The Mirror class generated for [requestBodyClass], or `null` if it has none. */
    fun mirrorClassFor(requestBodyClass: Class<*>): Class<out Mirror<*>>? {
        val found = cache.computeIfAbsent(requestBodyClass) { lookUp(it) ?: none }
        @Suppress("UNCHECKED_CAST")
        return if (found === none) null else found as Class<out Mirror<*>>
    }

    private fun lookUp(requestBodyClass: Class<*>): Class<*>? {
        val packageName = requestBodyClass.packageName
        val simpleNames = requestBodyClass.name.removePrefix("$packageName.").replace('$', '_')
        val mirrorName = listOf(packageName, "generated").filter { it.isNotEmpty() }.joinToString(".") +
            ".${simpleNames}Mirror"
        val mirrorClass = try {
            Class.forName(mirrorName, false, requestBodyClass.classLoader)
        } catch (e: ClassNotFoundException) {
            return null
        }
        return mirrorClass.takeIf { Mirror::class.java.isAssignableFrom(it) }
    }
}
