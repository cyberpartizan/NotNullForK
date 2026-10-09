package com.example.app

import com.example.invoices.CreateInvoiceRequest
import com.example.invoices.InvoiceLine
import com.example.invoices.LegacyInvoiceRequest
import com.example.shared.CommentRequest
import com.example.shared.CreateTicketRequest
import com.example.shared.Reporter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.readValue
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
import java.time.LocalDate

/**
 * Request Bodies of the sibling module `shared-requests` and of the published library jar
 * `invoice-requests`, neither of which applies the plugin: their Mirrors are generated in this module.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OtherModuleTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @Test
    fun `missing Required Fields of a sibling module's Request Body get per-field errors`() {
        val result = post("/tickets", """{"reporter": {"email": "ann@example.com"}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(
            tuple("title", "NotNull", "must not be null"),
            tuple("reporter.name", "NotNull", "must not be null"),
        )
    }

    @Test
    fun `missing Defaulted Fields of a sibling module's Request Body get its default values, whatever they refer to`() {
        val result = post("/tickets", """{"title": "Broken login", "reporter": {"name": "Ann"}}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(objectMapper.readValue<CreateTicketRequest>(result.response.contentAsString)).isEqualTo(
            CreateTicketRequest(
                title = "Broken login",
                priority = 3,
                labels = listOf("new"),
                assignee = "triage",
                summary = "Broken login (P3)",
                reporter = Reporter(name = "Ann", email = "unknown@example.com"),
                dueDate = null,
            ),
        )
    }

    @Test
    fun `present Defaulted Fields of a sibling module's Request Body keep the values sent, explicit nulls too`() {
        val result = post(
            "/tickets",
            """
            {"title": "Broken login", "priority": 2, "labels": ["auth"], "assignee": null, "summary": "Login",
             "reporter": {"name": "Ann", "email": "ann@example.com"}, "due_date": "tomorrow"}
            """,
        )

        assertThat(result.response.status).isEqualTo(200)
        assertThat(objectMapper.readValue<CreateTicketRequest>(result.response.contentAsString)).isEqualTo(
            CreateTicketRequest("Broken login", 2, listOf("auth"), null, "Login", Reporter("Ann", "ann@example.com"), "tomorrow"),
        )
    }

    @Test
    fun `an explicit null for a non-null Defaulted Field of a sibling module's Request Body behaves as it would without the plugin`() {
        val json = """{"title": "Broken login", "reporter": {"name": "Ann"}, "labels": null}"""
        assertThatThrownBy { objectMapper.readValue<CreateTicketRequest>(json) }.isInstanceOf(MismatchedInputException::class.java)

        val result = post("/tickets", json)

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.resolvedException).isInstanceOf(HttpMessageNotReadableException::class.java)
    }

    @Test
    fun `endpoints of several controllers taking the same Request Body share one Mirror`() {
        val mirrorClasses = listOf("/tickets", "/tickets/urgent", "/admin/tickets").map { path ->
            val result = post(path, """{"reporter": {"name": "Ann"}}""")
            assertThat(fieldErrors(result)).`as`(path).containsExactly(tuple("title", "NotNull", "must not be null"))
            (result.resolvedException as MethodArgumentNotValidException).bindingResult.target!!::class.java
        }

        assertThat(mirrorClasses.toSet()).containsExactly(Class.forName("com.example.shared.generated.CreateTicketRequestMirror"))
    }

    @Test
    fun `a sibling module's Request Body with an Opt-out is left to Spring`() {
        assertThatThrownBy { Class.forName("com.example.shared.generated.CommentRequestMirror") }
            .isInstanceOf(ClassNotFoundException::class.java)

        val result = post("/comments", """{}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat((result.resolvedException!!.cause as MismatchedInputException).originalMessage)
            .isEqualTo(jacksonErrorReading<CommentRequest>("{}"))
    }

    @Test
    fun `missing Required Fields of a published library's Request Body get per-field errors`() {
        val result = post("/invoices", """{"lines": [{"quantity": 2}]}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(
            tuple("customerId", "NotNull", "must not be null"),
            tuple("issued", "NotNull", "must not be null"),
            tuple("lines[0].item", "NotNull", "must not be null"),
        )
    }

    @Test
    fun `a published library's Request Body is read with its Jackson annotations and default values`() {
        val result = post(
            "/invoices",
            """
            {"customer_id": 7, "currency_code": "USD", "due_in_days": 14, "issued": "01.02.2024", "source": "ignored",
             "lines": [{"item": "Pen"}], "note": null}
            """,
        )

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo(
            CreateInvoiceRequest(
                customerId = 7,
                currency = "USD",
                dueInDays = 14,
                issued = LocalDate.of(2024, 2, 1),
                source = "api",
                lines = listOf(InvoiceLine(item = "Pen", quantity = 1)),
                note = null,
            ).toString(),
        )
    }

    @Test
    fun `missing Defaulted Fields of a published library's Request Body get its default values`() {
        val result = post("/invoices", """{"customer_id": 7, "issued": "01.02.2024", "lines": []}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo(
            CreateInvoiceRequest(customerId = 7, issued = LocalDate.of(2024, 2, 1), lines = emptyList()).toString(),
        )
    }

    @Test
    fun `a published library's Request Body with an Opt-out is left to Spring`() {
        assertThatThrownBy { Class.forName("com.example.invoices.generated.LegacyInvoiceRequestMirror") }
            .isInstanceOf(ClassNotFoundException::class.java)

        val result = post("/legacy-invoices", """{"number": "A-1"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat((result.resolvedException!!.cause as MismatchedInputException).originalMessage)
            .isEqualTo(jacksonErrorReading<LegacyInvoiceRequest>("""{"number": "A-1"}"""))
    }

    /** The message of the error Jackson itself reports when it reads [json] as a [T]. */
    private inline fun <reified T> jacksonErrorReading(json: String): String =
        (catchThrowable { objectMapper.readValue(json, T::class.java) } as MismatchedInputException).originalMessage

    private fun post(path: String, json: String): MvcResult =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andReturn()

    private fun fieldErrors(result: MvcResult) =
        (result.resolvedException as MethodArgumentNotValidException).bindingResult.fieldErrors
            .map { tuple(it.field, it.code, it.defaultMessage) }
}
