package com.example.app

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post

/** Request Bodies the plugin can't handle are left to Spring, as they would be without the plugin. */
@SpringBootTest
@AutoConfigureMockMvc
class FallbackTest(@Autowired private val mockMvc: MockMvc) {

    @ParameterizedTest
    @ValueSource(strings = ["JavaTicketRequest", "Message", "TextMessage", "Labelled", "Rating", "Draft"])
    fun `a Request Body the plugin can't handle gets no Mirror`(requestBody: String) {
        assertThatThrownBy { Class.forName("com.example.app.generated.${requestBody}Mirror") }
            .isInstanceOf(ClassNotFoundException::class.java)
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        textBlock = """
            /fallback/java     | {"title": "Bug"}                     | JavaTicketRequest(title=Bug)
            /fallback/message  | {"kind": "text", "text": "Hi"}       | TextMessage(text=Hi)
            /fallback/labelled | {"label": "count", "value": 3}       | Labelled(label=count, value=3)
            /fallback/rating   | {"stars": 5, "by": "Ann"}            | Rating(stars=5, by=Ann)
            /fallback/draft    | {"title": "Plan", "body": "Step 1"}  | Draft(title=Plan, body=Step 1)""",
    )
    fun `a valid request reaches the controller`(path: String, json: String, expected: String) {
        val result = post(path, json)

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        textBlock = """
            /fallback/message  | {"kind": "text"}
            /fallback/labelled | {"value": 3}
            /fallback/rating   | {"stars": 5}
            /fallback/draft    | {"body": "Step 1"}""",
    )
    fun `a missing field fails in Spring's own Jackson converter, not in the plugin`(path: String, json: String) {
        val result = post(path, json)

        assertThat(result.response.status).isEqualTo(400)
        val exception = result.resolvedException as HttpMessageNotReadableException
        assertThat(exception.stackTrace.map { it.className })
            .contains("org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter")
            .noneMatch { it.startsWith("com.github.cyberpartizan.notnullfork.") }
    }

    @Test
    fun `a missing field of a Java Request Body is left null, as Jackson leaves it`() {
        val result = post("/fallback/java", "{}")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo("JavaTicketRequest(title=null)")
    }

    private fun post(path: String, json: String): MvcResult =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andReturn()
}
