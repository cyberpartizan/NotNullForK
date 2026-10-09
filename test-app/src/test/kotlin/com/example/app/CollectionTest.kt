package com.example.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
class CollectionTest(@Autowired private val mockMvc: MockMvc, @Autowired private val objectMapper: ObjectMapper) {

    @Test
    fun `a null element in a list of a non-null type is reported with an indexed path`() {
        val result = post("/posts", """{"tags": ["a", null, "c", null], "scores": {}, "labels": [], "notes": [], "ratings": {}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(mustNotBeNull("tags[1]"), mustNotBeNull("tags[3]"))
    }

    @Test
    fun `a null value in a map with a non-null value type is reported with a keyed path`() {
        val result = post("/posts", """{"tags": [], "scores": {"math": null, "art": 5, "music": null}, "labels": [], "notes": [], "ratings": {}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(mustNotBeNull("scores[math]"), mustNotBeNull("scores[music]"))
    }

    @Test
    fun `lists, sets and maps reach the controller with their types and in the order of the JSON`() {
        val result = post(
            "/posts",
            """{"tags": ["b", "a"], "scores": {"math": 5, "art": 4}, "labels": ["z", "x", "y", "x"], "notes": [], "ratings": {}, "aliases": ["n"]}""",
        )

        assertThat(result.response.status).isEqualTo(200)
        assertThat(received(result)).isEqualTo(
            Received(
                TagPostRequest(
                    tags = listOf("b", "a"),
                    scores = mapOf("math" to 5, "art" to 4),
                    labels = linkedSetOf("z", "x", "y"),
                    notes = emptyList(),
                    ratings = emptyMap(),
                    aliases = mutableListOf("n"),
                ).toString(),
                mapOf("tags" to "ArrayList", "scores" to "LinkedHashMap", "labels" to "LinkedHashSet", "aliases" to "ArrayList"),
            ),
        )
    }

    @Test
    fun `null elements are allowed in a list or map of a nullable type`() {
        val result = post(
            "/posts",
            """{"tags": [], "scores": {}, "labels": [], "notes": [null, "n"], "ratings": {"math": null}, "aliases": null}""",
        )

        assertThat(result.response.status).isEqualTo(200)
        assertThat(received(result).request).contains("notes=[null, n], ratings={math=null}, aliases=null")
    }

    @Test
    fun `missing Required Fields of Nested Types in lists, sets and maps are reported with indexed or keyed paths`() {
        val result = post(
            "/carts",
            """{"items": [{"name": "pen", "quantity": 1}, {"quantity": 2}], "gifts": [{"name": "card"}],
               "byCode": {"p1": {"name": "pen"}, "p2": null}, "maybe": [null, {}]}""",
        )

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(
            mustNotBeNull("items[1].name"),
            mustNotBeNull("gifts[0].quantity"),
            mustNotBeNull("byCode[p1].quantity"),
            mustNotBeNull("byCode[p2]"),
            mustNotBeNull("maybe[1].name"),
            mustNotBeNull("maybe[1].quantity"),
        )
    }

    @Test
    fun `lists, sets and maps of Nested Types are mapped element by element, in the order of the JSON`() {
        val result = post(
            "/carts",
            """{"items": [{"name": "pen", "quantity": 1}, {"name": "ink", "quantity": 2, "note": "blue"}],
               "gifts": [{"name": "card", "quantity": 1}, {"name": "bow", "quantity": 1}, {"name": "card", "quantity": 1}],
               "byCode": {"p2": {"name": "ink", "quantity": 2}, "p1": {"name": "pen", "quantity": 1}}, "maybe": [null]}""",
        )

        val pen = LineItem("pen", 1, null)
        val ink = LineItem("ink", 2, "blue")
        assertThat(result.response.status).isEqualTo(200)
        assertThat(received(result)).isEqualTo(
            Received(
                CartRequest(
                    items = listOf(pen, ink),
                    gifts = linkedSetOf(LineItem("card", 1, null), LineItem("bow", 1, null)),
                    byCode = linkedMapOf("p2" to LineItem("ink", 2, null), "p1" to pen),
                    maybe = listOf(null),
                ).toString(),
                mapOf("items" to "ArrayList", "gifts" to "LinkedHashSet", "byCode" to "LinkedHashMap", "maybe" to "ArrayList"),
            ),
        )
    }

    @Test
    fun `a null value under any key is reported at that key`() {
        val result = post("/posts", """{"tags": [], "scores": {"a]b": null, "x.y[0]": null}, "labels": [], "notes": [], "ratings": {}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(mustNotBeNull("scores[a]b]"), mustNotBeNull("scores[x.y[0]]"))
    }

    @Test
    fun `collections written through type aliases are checked by the types they stand for`() {
        val invalid = post("/aliased-collections", """{"codes": ["a", null], "texts": []}""")
        val valid = post("/aliased-collections", """{"codes": ["a"], "texts": [null, "t"]}""")

        assertThat(invalid.response.status).isEqualTo(400)
        assertThat(fieldErrors(invalid)).containsExactly(mustNotBeNull("codes[1]"))
        assertThat(valid.response.status).isEqualTo(200)
        assertThat(valid.response.contentAsString).isEqualTo(AliasedCollectionsRequest(listOf("a"), listOf(null, "t")).toString())
    }

    private fun post(path: String, json: String): MvcResult =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andReturn()

    private fun received(result: MvcResult): Received = objectMapper.readValue(result.response.contentAsString)

    private fun mustNotBeNull(field: String) = tuple(field, "NotNull", "must not be null")

    private fun fieldErrors(result: MvcResult) =
        (result.resolvedException as MethodArgumentNotValidException).bindingResult.fieldErrors
            .map { tuple(it.field, it.code, it.defaultMessage) }
}
