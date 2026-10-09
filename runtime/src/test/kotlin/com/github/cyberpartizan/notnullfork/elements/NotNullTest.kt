package com.github.cyberpartizan.notnullfork.elements

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class NotNullTest {

    class Collections(
        @field:NotNull val tags: List<String?>?,
        @field:NotNull val scores: Map<String, Int?>?,
    )

    private val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }

    @Test
    fun `null elements of a list are reported at their index and null values of a map at their key`() {
        val errors = fieldErrors(Collections(tags = listOf("a", null, "c", null), scores = mapOf("math" to null, "art" to 1)))

        assertThat(errors).containsExactlyInAnyOrder(
            tuple("tags[1]", "NotNull", "must not be null", null),
            tuple("tags[3]", "NotNull", "must not be null", null),
            tuple("scores[math]", "NotNull", "must not be null", null),
        )
    }

    @Test
    fun `a missing collection, and collections without nulls, are valid`() {
        assertThat(fieldErrors(Collections(tags = null, scores = null))).isEmpty()
        assertThat(fieldErrors(Collections(tags = listOf("a"), scores = mapOf("math" to 1)))).isEmpty()
    }

    private fun fieldErrors(target: Any) = BeanPropertyBindingResult(target, "request")
        .also { validator.validate(target, it) }
        .fieldErrors.map { tuple(it.field, it.code, it.defaultMessage, it.rejectedValue) }
}
