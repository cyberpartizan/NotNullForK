package com.example.app

import com.fasterxml.jackson.databind.ObjectMapper
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
class JacksonAnnotationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    @Test
    fun `Jackson annotations on Request Body properties shape the JSON the Mirror reads`() {
        val result = post(
            "/profiles",
            """
            {
              "user_name": "ann",
              "handle": "annie",
              "birthday": "24.12.1990",
              "role": "admin",
              "display_name": "Ann A.",
              "time_zone": "Europe/Paris"
            }
            """,
        )

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo(
            "UpdateProfileRequest(userName=ann, nickname=annie, birthday=1990-12-24, role=member, " +
                "displayName=Ann A., timeZone=Europe/Paris, note=none, language=en)",
        )
    }

    @Test
    fun `the Mirror reads the same JSON as the Request Body would`() {
        // Where Kotlin puts an annotation without a use-site target depends on the Kotlin version,
        // and so does what Jackson makes of it; the Mirror must behave as the Request Body does.
        val json = """
            {
              "user_name": "ann", "userName": "bob",
              "nick": "annie", "nickname": "bobby",
              "birthday": "24.12.1990",
              "role": "admin",
              "display_name": "Ann A.", "displayName": "Bob B.",
              "time_zone": "Europe/Paris", "timeZone": "Europe/Berlin",
              "note": "changed",
              "lang": "fr", "language": "de"
            }
        """
        val result = post("/profiles", json)

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString)
            .isEqualTo(objectMapper.readValue(json, UpdateProfileRequest::class.java).toString())
    }

    @Test
    fun `a property renamed with @JsonProperty is not read under its Kotlin name`() {
        val result = post("/profiles", """{"userName": "ann", "nickname": "annie", "birthday": "24.12.1990"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("userName"))
    }

    @Test
    fun `a missing Required Field renamed with @JsonProperty is reported under its Kotlin name`() {
        val result = post("/profiles", """{"nick": "annie", "birthday": "24.12.1990"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("userName"))
    }

    @Test
    fun `a missing Required Field marked @JsonProperty(required = true) is reported as a missing Required Field`() {
        val result = post("/profiles", """{"user_name": "ann", "nickname": "annie"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("birthday"))
    }

    @Test
    fun `a date in another format than @JsonFormat's is rejected as unreadable`() {
        val result = post("/profiles", """{"user_name": "ann", "nickname": "annie", "birthday": "1990-12-24"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.resolvedException).isNotInstanceOf(MethodArgumentNotValidException::class.java)
    }

    @Test
    fun `class-level Jackson annotations shape the JSON the Mirror reads`() {
        val valid = post("/shipments", """{"street-name": "Main St", "house-number": 7}""")
        val missing = post("/shipments", """{"streetName": "Main St", "house-number": 7}""")

        assertThat(valid.response.status).isEqualTo(200)
        assertThat(valid.response.contentAsString).isEqualTo("Main St 7")
        assertThat(missing.response.status).isEqualTo(400)
        assertThat(fieldErrors(missing)).containsExactly(mustNotBeNull("streetName"))
    }

    @Test
    fun `serializers and deserializers registered on the app's ObjectMapper apply to Mirror properties`() {
        val valid = post("/payments", """{"total": "12.34", "cardHolder": "Ann"}""")
        val missing = post("/payments", """{"cardHolder": "Ann"}""")

        assertThat(valid.response.status).isEqualTo(200)
        assertThat(valid.response.contentAsString).isEqualTo("""{"total":"12.34","cardHolder":"Ann"}""")
        assertThat(missing.response.status).isEqualTo(400)
        assertThat(fieldErrors(missing)).containsExactly(mustNotBeNull("total"))
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
