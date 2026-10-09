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
class NestedRequestBodyTest(@Autowired private val mockMvc: MockMvc) {

    private val address = """{"street": "Main St", "city": {"name": "Oslo", "country": {"code": "NO"}}, "note": null}"""

    @Test
    fun `nested objects three levels deep reach the controller as the Request Body's types`() {
        val result = post(
            "/orders",
            """{"item": "book", "shippingAddress": $address, "billingAddress": $address, "packaging": {"kind": "bag"}}""",
        )

        assertThat(result.response.status).isEqualTo(200)
        val expectedAddress = Address(street = "Main St", city = City(name = "Oslo", country = Country(code = "NO")), note = null)
        assertThat(result.response.contentAsString).isEqualTo(
            PlaceOrderRequest(
                item = "book",
                shippingAddress = expectedAddress,
                billingAddress = expectedAddress,
                packaging = Packaging(kind = "bag"),
                coupon = null,
            ).toString(),
        )
    }

    @Test
    fun `a missing Required Field inside a nested object is reported with a dotted path`() {
        val result = post("/orders", """{"item": "book", "shippingAddress": {"city": {"name": "Oslo", "country": {"code": "NO"}}}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("shippingAddress.street"))
    }

    @Test
    fun `missing Required Fields are reported at every depth in one response`() {
        val result = post("/orders", """{"shippingAddress": {"street": null, "city": {"country": {}}}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(
            mustNotBeNull("item"),
            mustNotBeNull("shippingAddress.street"),
            mustNotBeNull("shippingAddress.city.name"),
            mustNotBeNull("shippingAddress.city.country.code"),
        )
    }

    @Test
    fun `a missing nested object that is a Required Field is reported at its own path`() {
        val result = post("/orders", """{"item": "book"}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("shippingAddress"))
    }

    @Test
    fun `a nullable nested object may be missing or null`() {
        for (billingAddress in listOf("", """, "billingAddress": null""")) {
            val result = post("/orders", """{"item": "book", "shippingAddress": $address$billingAddress}""")

            assertThat(result.response.status).`as`(billingAddress).isEqualTo(200)
            assertThat(result.response.contentAsString).`as`(billingAddress).contains("billingAddress=null")
        }
    }

    @Test
    fun `the Required Fields of a nullable nested object are checked when it is present`() {
        val result = post("/orders", """{"item": "book", "shippingAddress": $address, "billingAddress": {"city": {"name": "Oslo"}}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactlyInAnyOrder(
            mustNotBeNull("billingAddress.street"),
            mustNotBeNull("billingAddress.city.country"),
        )
    }

    @Test
    fun `a missing Defaulted Field of a Nested Type gets its default value`() {
        val result = post("/orders", """{"item": "book", "shippingAddress": $address}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("packaging=Packaging(kind=box)")
    }

    @Test
    fun `a Nested Type read by a deserializer on the property is read by that deserializer`() {
        val result = post("/orders", """{"item": "book", "shippingAddress": $address, "coupon": "SAVE10"}""")

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("coupon=Coupon(code=SAVE10)")
    }

    @Test
    fun `a Nested Type used by another Request Body is checked there too`() {
        val result = post("/customers", """{"name": "Ann", "email": "ann@example.com", "address": {"street": "Main St", "city": {"name": "Oslo", "country": {}}}}""")

        assertThat(result.response.status).isEqualTo(400)
        assertThat(fieldErrors(result)).containsExactly(mustNotBeNull("address.city.country.code"))
    }

    @Test
    fun `types that Jackson reads through a creator or as a subtype are read as without the plugin`() {
        val result = post(
            "/customers",
            """{"name": "Ann", "address": $address, "email": "ann@example.com", "pet": {"type": "dog", "name": "Rex"}}""",
        )

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).contains("email=Email(value=ann@example.com), pet=Dog(Rex)")
    }

    @Test
    fun `a Request Body that nests itself is checked at every depth`() {
        val valid = post("/categories", """{"name": "a", "parent": {"name": "b", "parent": {"name": "c"}}}""")
        val missing = post("/categories", """{"name": "a", "parent": {"name": "b", "parent": {"parent": null}}}""")

        assertThat(valid.response.status).isEqualTo(200)
        assertThat(valid.response.contentAsString)
            .isEqualTo(Category("a", Category("b", Category("c", parent = null))).toString())
        assertThat(missing.response.status).isEqualTo(400)
        assertThat(fieldErrors(missing)).containsExactly(mustNotBeNull("parent.parent.name"))
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
