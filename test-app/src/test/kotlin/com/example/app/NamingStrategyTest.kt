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

/** The app's ObjectMapper is configured with the SNAKE_CASE naming strategy. */
@SpringBootTest(properties = ["spring.jackson.property-naming-strategy=SNAKE_CASE"])
@AutoConfigureMockMvc
class NamingStrategyTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `snake_case JSON is accepted for a Request Body with a Mirror`() {
        val result = post("/accounts", """{"email": "ann@example.com", "trial_days": 30, "age": 42}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains(""""email":"ann@example.com"""", """"trial_days":30""")
    }

    @Test
    fun `a missing Required Field is reported under its Kotlin name, not its snake_case name`() {
        val result = post("/payments", """{"total": "1.00", "cardHolder": "Ann"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("cardHolder"))
    }

    @Test
    fun `an explicit @JsonProperty name wins over the naming strategy`() {
        val result = post("/profiles", """{"user_name": "ann", "nickname": "annie", "birthday": "24.12.1990"}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).startsWith("UpdateProfileRequest(userName=ann,")
    }

    private fun post(path: String, json: String): MvcResult =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andReturn()

    private fun mustNotBeNull(field: String) = tuple(field, "NotNull", "must not be null")

    private fun fieldErrors(result: MvcResult) =
        (result.resolvedException as MethodArgumentNotValidException).bindingResult.fieldErrors
            .map { tuple(it.field, it.code, it.defaultMessage) }
}
