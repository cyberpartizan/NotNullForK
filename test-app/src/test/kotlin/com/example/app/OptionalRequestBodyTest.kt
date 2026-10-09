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
class OptionalRequestBodyTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `an empty body for an optional Request Body reaches the controller as null`() {
        val result = mockMvc.post("/feedback") { contentType = MediaType.APPLICATION_JSON }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("no feedback")
    }

    @Test
    fun `a left-out body without a content type for an optional Request Body reaches the controller as null`() {
        val result = mockMvc.post("/feedback").andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("no feedback")
    }

    @Test
    fun `an empty body with a content type other than JSON is still unsupported`() {
        val result = mockMvc.post("/feedback") { contentType = MediaType.TEXT_PLAIN }.andReturn()

        assertThat(result.response.status).isEqualTo(415)
    }

    @Test
    fun `a body for an optional Request Body missing Required Fields returns a per-field 400`() {
        val result = post("/feedback", """{"rating": 5}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(tuple("comment", "NotNull", "must not be null"))
    }

    @Test
    fun `a valid body for an optional Request Body reaches the controller filled`() {
        val result = post("/feedback", """{"rating": 5, "comment": "Great"}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("SendFeedbackRequest(rating=5, comment=Great)")
    }

    @Test
    fun `an empty body for a nullable Request Body parameter reaches the controller as null`() {
        val result = mockMvc.post("/feedback/nullable") { contentType = MediaType.APPLICATION_JSON }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("no feedback")
    }

    @Test
    fun `an empty body for an optional Request Body still gives an Errors parameter`() {
        val result = mockMvc.post("/feedback/errors") { contentType = MediaType.APPLICATION_JSON }.andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("no feedback, 0 errors")
    }

    private fun post(path: String, json: String): MvcResult =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andReturn()

    private fun fieldErrors(result: MvcResult) =
        (result.resolvedException as MethodArgumentNotValidException).bindingResult.fieldErrors
            .map { tuple(it.field, it.code, it.defaultMessage) }
}
