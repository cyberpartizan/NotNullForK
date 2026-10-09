package com.example.app

import com.github.cyberpartizan.notnullfork.Mirror
import jakarta.validation.Constraint
import jakarta.validation.constraints.NotNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

class MirrorTest {

    private val mirror = mirrorOf("CreateUserRequest")
    private val accountMirror = mirrorOf("CreateAccountRequest")

    @Test
    fun `a Request Body gets a Mirror in the generated sub-package that maps to it`() {
        val mirror = mirror.constructors.single().call("Ann", 42) as Mirror<*>

        assertThat(mirror.toRequestBody()).isEqualTo(CreateUserRequest(name = "Ann", age = 42))
    }

    @Test
    fun `every Required Field is nullable and must not be null in the Mirror`() {
        for (name in listOf("name", "age")) {
            assertThat(isNullable(mirror, name)).`as`(name).isTrue()
            assertThat(mustNotBeNull(mirror, name)).`as`(name).isTrue()
        }
        for (name in listOf("email", "age")) {
            assertThat(isNullable(accountMirror, name)).`as`(name).isTrue()
            assertThat(mustNotBeNull(accountMirror, name)).`as`(name).isTrue()
        }
    }

    @Test
    fun `a Defaulted Field keeps its type and default value and is not constrained in the Mirror`() {
        val constructor = accountMirror.primaryConstructor!!
        val defaults = constructor.callBy(
            constructor.parameters.filter { it.name in setOf("email", "nickname", "age") }
                .associateWith { if (it.name == "age") 42 else null },
        ) as Mirror<*>

        for (name in listOf("plan", "trialDays", "language", "greeting", "newsletter")) {
            assertThat(isNullable(accountMirror, name)).`as`(name).isFalse()
            assertThat(mustNotBeNull(accountMirror, name)).`as`(name).isFalse()
        }
        assertThat(isNullable(accountMirror, "referrer")).isTrue()
        assertThat(mustNotBeNull(accountMirror, "referrer")).isFalse()
        assertThat(valueOf(defaults, "plan")).isEqualTo(Plan.FREE)
        assertThat(valueOf(defaults, "trialDays")).isEqualTo(14)
        assertThat(valueOf(defaults, "language")).isEqualTo("en")
        assertThat(valueOf(defaults, "greeting")).isEqualTo("hello, there")
        assertThat(valueOf(defaults, "newsletter")).isEqualTo(true)
        assertThat(valueOf(defaults, "referrer")).isEqualTo("organic")
    }

    @Test
    fun `a nullable field stays nullable and is not constrained in the Mirror`() {
        assertThat(isNullable(accountMirror, "nickname")).isTrue()
        assertThat(mustNotBeNull(accountMirror, "nickname")).isFalse()
    }

    @Test
    fun `the Request Body's constraint annotations are not copied to the Mirror`() {
        // The Request Body has constraints on its class, fields, getter and a type.
        assertThat(constraintsOn(SignUpRequest::class)).contains(
            "class" to "NotAdmin",
            "field email" to "Email",
            "field password" to "NoWhitespace",
            "method getAge" to "Min",
            "type of website" to "Size",
        )

        assertThat(constraintsOn(mirrorOf("SignUpRequest"))).containsExactlyInAnyOrder(
            "field email" to "NotNull",
            "field password" to "NotNull",
            "field age" to "NotNull",
        )
    }

    private fun mirrorOf(requestBody: String): KClass<*> = Class.forName("com.example.app.generated.${requestBody}Mirror").kotlin

    /** `(where, constraint annotation name)` for every Bean Validation constraint on [type]. */
    private fun constraintsOn(type: KClass<*>): List<Pair<String, String?>> = buildList {
        type.java.annotations.forEach { add("class" to it) }
        type.java.declaredFields.forEach { field -> field.annotations.forEach { add("field ${field.name}" to it) } }
        type.java.declaredMethods.forEach { method -> method.annotations.forEach { add("method ${method.name}" to it) } }
        type.java.declaredConstructors.forEach { constructor ->
            constructor.parameters.forEach { parameter -> parameter.annotations.forEach { add("constructor ${parameter.name}" to it) } }
        }
        type.memberProperties.forEach { property ->
            property.returnType.annotations.forEach { add("type of ${property.name}" to it) }
        }
    }.filter { (_, annotation) -> annotation.annotationClass.java.isAnnotationPresent(Constraint::class.java) }
        .map { (target, annotation) -> target to annotation.annotationClass.simpleName }

    private fun property(mirror: KClass<*>, name: String) = mirror.memberProperties.single { it.name == name }

    private fun isNullable(mirror: KClass<*>, name: String) = property(mirror, name).returnType.isMarkedNullable

    private fun mustNotBeNull(mirror: KClass<*>, name: String) =
        property(mirror, name).javaField!!.isAnnotationPresent(NotNull::class.java)

    private fun valueOf(mirror: Any, name: String) = property(mirror::class, name).getter.call(mirror)
}
