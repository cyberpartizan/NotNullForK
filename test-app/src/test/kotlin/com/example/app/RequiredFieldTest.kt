package com.example.app

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.MethodArgumentNotValidException

@SpringBootTest
@AutoConfigureMockMvc
class RequiredFieldTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `a valid request reaches the controller with a filled Request Body`() {
        val result = post("/users", """{"name": "Ann", "age": 42}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("""{"name":"Ann","age":42}""")
    }

    @Test
    fun `a missing Required Field returns a per-field 400`() {
        val result = post("/users", """{"age": 42}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("name"))
    }

    @Test
    fun `a null Required Field returns a per-field 400`() {
        val result = post("/users", """{"name": null, "age": 42}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("name"))
    }

    @Test
    fun `all missing Required Fields are reported in one response`() {
        val result = post("/users", """{}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(
            mustNotBeNull("name"),
            mustNotBeNull("age"),
        )
    }

    @Test
    fun `the Required Field check also runs when the parameter has @Valid`() {
        val result = post("/users/valid", """{"name": "Ann"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("age"))
    }

    @Test
    fun `a valid request reaches a @Valid parameter`() {
        val result = post("/users/valid", """{"name": "Ann", "age": 42}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("""{"name":"Ann","age":42}""")
    }

    @Test
    fun `a body that is not JSON is still rejected as an unsupported media type`() {
        val result = post("/users", "name=Ann", MediaType.TEXT_PLAIN)

        assertThat(result.response.status).isEqualTo(415)
    }

    @Test
    fun `a left-out body without a content type returns a 400 for the missing body`() {
        val result = mockMvc.post("/users").andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.resolvedException).isInstanceOf(HttpMessageNotReadableException::class.java)
            .hasMessageStartingWith("Required request body is missing")
    }

    private fun post(path: String, json: String, type: MediaType = MediaType.APPLICATION_JSON): MvcResult =
        mockMvc.post(path) {
            contentType = type
            content = json
        }.andReturn()

    private fun mustNotBeNull(field: String) = tuple(field, "NotNull", "must not be null")

    private fun fieldErrors(result: MvcResult) =
        (result.resolvedException as MethodArgumentNotValidException).bindingResult.fieldErrors
            .map { tuple(it.field, it.code, it.defaultMessage) }
}
