package com.github.cyberpartizan.notnullfork

/**
 * An Opt-out: the annotated class gets no Mirror, so Spring reads it as it would without the plugin.
 *
 * On a Request Body, a missing Required Field fails with Jackson's own error. A class with an Opt-out
 * is not a Nested Type either: a Request Body that uses it holds it as it is, and Jackson reads it.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotNullForKIgnore
