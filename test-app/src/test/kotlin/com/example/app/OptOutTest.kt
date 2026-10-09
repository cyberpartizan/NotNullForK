package com.example.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
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
import kotlin.reflect.full.memberProperties

@SpringBootTest
@AutoConfigureMockMvc
class OptOutTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @Test
    fun `a Request Body with an Opt-out gets no Mirror`() {
        assertThatThrownBy { Class.forName("com.example.app.generated.WriteNoteRequestMirror") }
            .isInstanceOf(ClassNotFoundException::class.java)
    }

    @Test
    fun `a valid request reaches the controller with an opted-out Request Body`() {
        val result = post("/notes", """{"title": "Todo", "text": "Buy milk"}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo(WriteNoteRequest(title = "Todo", text = "Buy milk").toString())
    }

    @Test
    fun `a missing field of an opted-out Request Body fails with Jackson's own error`() {
        val result = post("/notes", """{"title": "Todo"}""")

        assertThat(result.response.status).isEqualTo(400)
        val exception = result.resolvedException as HttpMessageNotReadableException
        // Spring's own Jackson converter read the body, not the plugin.
        assertThat(exception.stackTrace.map { it.className })
            .contains("org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter")
            .noneMatch { it.startsWith("com.github.cyberpartizan.notnullfork.") }
        assertThat(exception.message).isEqualTo("JSON parse error: ${jacksonErrorReading("""{"title": "Todo"}""")}")
    }

    @Test
    fun `other Request Bodies in the app are still handled`() {
        val result = post("/users", """{"age": 42}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(tuple("name", "NotNull", "must not be null"))
    }

    @Test
    fun `an opted-out class used as a field type is not a Nested Type and keeps its type in the Mirror`() {
        val mirror = Class.forName("com.example.app.generated.PinNoteRequestMirror").kotlin

        assertThat(mirror.memberProperties.single { it.name == "note" }.returnType.toString())
            .isEqualTo("com.example.app.WriteNoteRequest?")
    }

    @Test
    fun `a valid request reaches the controller with the opted-out class in a field`() {
        val result = post("/pins", """{"board": "home", "note": {"title": "Todo", "text": "Buy milk"}}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString)
            .isEqualTo(PinNoteRequest(board = "home", note = WriteNoteRequest(title = "Todo", text = "Buy milk")).toString())
    }

    @Test
    fun `the Request Body that uses an opted-out class still gets per-field errors for its own Required Fields`() {
        val result = post("/pins", """{"note": {"title": "Todo", "text": "Buy milk"}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(tuple("board", "NotNull", "must not be null"))
    }

    @Test
    fun `a missing field inside an opted-out class fails with Jackson's own error`() {
        val result = post("/pins", """{"board": "home", "note": {"title": "Todo"}}""")

        assertThat(result.response.status).isEqualTo(400)
        val exception = result.resolvedException as HttpMessageNotReadableException
        assertThat((exception.cause as MismatchedInputException).originalMessage)
            .isEqualTo(jacksonErrorReading("""{"title": "Todo"}"""))
    }

    /** The message of the error Jackson itself reports when it reads [json] as a [WriteNoteRequest]. */
    private fun jacksonErrorReading(json: String): String =
        (catchThrowable { objectMapper.readValue(json, WriteNoteRequest::class.java) } as MismatchedInputException).originalMessage

    private fun post(path: String, json: String): MvcResult =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andReturn()

    private fun fieldErrors(result: MvcResult) =
        (result.resolvedException as MethodArgumentNotValidException).bindingResult.fieldErrors
            .map { tuple(it.field, it.code, it.defaultMessage) }
}
