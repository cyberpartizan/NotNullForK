package com.example.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
class DefaultedAndNullableFieldTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @Test
    fun `missing Defaulted Fields reach the controller with their default values`() {
        val result = post("""{"email": "ann@example.com", "age": 42}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(received(result)).isEqualTo(
            CreateAccountRequest(email = "ann@example.com", nickname = null, age = 42),
        )
        assertThat(received(result)).isEqualTo(
            CreateAccountRequest(
                email = "ann@example.com",
                plan = Plan.FREE,
                trialDays = 14,
                language = "en",
                greeting = "hello, there",
                newsletter = true,
                nickname = null,
                referrer = "organic",
                age = 42,
            ),
        )
    }

    @Test
    fun `present Defaulted and nullable fields reach the controller with the sent values`() {
        val result = post(
            """
            {"email": "ann@example.com", "plan": "PRO", "trialDays": 30, "language": "de", "greeting": "hi",
             "newsletter": false, "nickname": "annie", "referrer": "ads", "age": 42}
            """,
        )

        assertThat(result.response.status).isEqualTo(200)
        assertThat(received(result)).isEqualTo(
            CreateAccountRequest("ann@example.com", Plan.PRO, 30, "de", "hi", false, "annie", "ads", 42),
        )
    }

    @Test
    fun `an explicit null for a non-null Defaulted Field behaves as it would without the plugin`() {
        val json = """{"email": "ann@example.com", "age": 42, "plan": null}"""
        // Without the plugin, Spring's Jackson converter reads the Request Body directly.
        assertThatThrownBy { objectMapper.readValue<CreateAccountRequest>(json) }

        val result = post(json)

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.resolvedException).isInstanceOf(HttpMessageNotReadableException::class.java)
    }

    @Test
    fun `missing or null nullable fields reach the controller as null`() {
        val result = post("""{"email": "ann@example.com", "age": 42, "referrer": null}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(received(result).nickname).isNull()
        assertThat(received(result).referrer).isNull()
    }

    @Test
    fun `a body mixing all field kinds reports only the missing Required Fields`() {
        val result = post("""{"plan": "PRO", "nickname": null, "referrer": null}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(
            tuple("email", "NotNull", "must not be null"),
            tuple("age", "NotNull", "must not be null"),
        )
    }

    private fun post(json: String): MvcResult =
        mockMvc.post("/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andReturn()

    private fun received(result: MvcResult): CreateAccountRequest =
        objectMapper.readValue(result.response.contentAsString)

    private fun fieldErrors(result: MvcResult) =
        (result.resolvedException as MethodArgumentNotValidException).bindingResult.fieldErrors
            .map { tuple(it.field, it.code, it.defaultMessage) }
}
