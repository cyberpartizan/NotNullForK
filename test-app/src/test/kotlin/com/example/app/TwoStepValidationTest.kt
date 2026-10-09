package com.example.app

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.MethodArgumentNotValidException

@SpringBootTest
@AutoConfigureMockMvc
class TwoStepValidationTest(@Autowired private val mockMvc: MockMvc) {

    private val breaksDefaultAndStrictConstraints =
        """{"email": "admin@example.com", "password": "has space", "age": 10, "nickname": "ab"}"""

    @Test
    fun `with @Valid, Request Body constraints are checked once the Required Fields are present`() {
        val result = post("/sign-ups/valid", """{"email": "not-an-email", "password": "short", "age": 17}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.resolvedException).isInstanceOf(MethodArgumentNotValidException::class.java)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(
            tuple("email", "Email", "must be a well-formed email address"),
            tuple("password", "Size", "size must be between 8 and 2147483647"),
            tuple("age", "Min", "must be greater than or equal to 18"),
        )
    }

    @Test
    fun `with @Valid, custom field and class-level constraints are checked`() {
        val result = post("/sign-ups/valid", """{"email": "admin@example.com", "password": "has a space", "age": 42}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(tuple("password", "NoWhitespace", "must not contain whitespace"))
        assertThat(globalErrors(result)).containsExactly(tuple("NotAdmin", "must not sign up as the admin"))
    }

    @Test
    fun `with @Valid, a body that meets every constraint reaches the controller`() {
        val result = post("/sign-ups/valid", """{"email": "ann@example.com", "password": "long-enough", "age": 42}""")

        assertThat(result.response.status).isEqualTo(200)
    }

    @Test
    fun `without @Valid, Request Body constraints are not checked`() {
        val result = post("/sign-ups", breaksDefaultAndStrictConstraints)

        assertThat(result.response.status).isEqualTo(200)
    }

    @Test
    fun `without @Valid, the Required Field check still runs`() {
        val result = post("/sign-ups", """{"email": "ann@example.com", "password": "long-enough"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("age"))
    }

    @Test
    fun `a missing Required Field is the only error reported, even if other constraints are broken`() {
        val result = post("/sign-ups/valid", """{"email": "admin@example.com", "password": "has space"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("age"))
        assertThat(globalErrors(result)).isEmpty()
    }

    @Test
    fun `validation groups given through @Validated apply to the Request Body`() {
        val result = post("/sign-ups/strict", breaksDefaultAndStrictConstraints)

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(tuple("nickname", "Size", "size must be between 3 and 2147483647"))
        assertThat(globalErrors(result)).isEmpty()
    }

    @Test
    fun `the Required Field check runs whatever validation groups @Validated gives`() {
        val result = post("/sign-ups/strict", """{"email": "ann@example.com", "password": "long-enough", "nickname": "annie"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("age"))
    }

    @Test
    fun `with @Valid and a BindingResult parameter, Request Body constraint errors reach the controller`() {
        val result = post("/sign-ups/errors", """{"email": "not-an-email", "password": "long-enough", "age": 42}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("""["email"]""")
    }

    private fun post(path: String, json: String): MvcResult =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andReturn()

    private fun mustNotBeNull(field: String) = tuple(field, "NotNull", "must not be null")

    private fun bindingResult(result: MvcResult) = (result.resolvedException as MethodArgumentNotValidException).bindingResult

    private fun fieldErrors(result: MvcResult) = bindingResult(result).fieldErrors.map { tuple(it.field, it.code, it.defaultMessage) }

    private fun globalErrors(result: MvcResult) = bindingResult(result).globalErrors.map { tuple(it.code, it.defaultMessage) }
}
