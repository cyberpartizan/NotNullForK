package com.example.app

import com.github.cyberpartizan.notnullfork.Mirror
import jakarta.validation.constraints.NotNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

class MirrorTest {

    private val mirror = Class.forName("com.example.app.generated.CreateUserRequestMirror").kotlin

    @Test
    fun `a Request Body gets a Mirror in the generated sub-package that maps to it`() {
        val mirror = mirror.constructors.single().call("Ann", 42) as Mirror<*>

        assertThat(mirror.toRequestBody()).isEqualTo(CreateUserRequest(name = "Ann", age = 42))
    }

    @Test
    fun `every Required Field is nullable and must not be null in the Mirror`() {
        val properties = mirror.memberProperties.associateBy { it.name }

        for (name in listOf("name", "age")) {
            val property = properties.getValue(name)
            assertThat(property.returnType.isMarkedNullable).`as`(name).isTrue()
            assertThat(property.javaField!!.isAnnotationPresent(NotNull::class.java)).`as`(name).isTrue()
        }
    }
}
