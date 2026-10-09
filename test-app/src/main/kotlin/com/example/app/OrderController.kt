package com.example.app

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class Country(val code: String)

data class City(val name: String, val country: Country)

/** A Nested Type used by several Request Bodies. */
data class Address(val street: String, val city: City, val note: String?)

data class Packaging(val kind: String)

/** Read from a plain string such as `"SAVE10"` by the deserializer on the property that uses it. */
data class Coupon(val code: String)

class CouponDeserializer : JsonDeserializer<Coupon>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext) = Coupon(parser.valueAsString)
}

/** Has Nested Types three levels deep, as Required, nullable and Defaulted Fields. */
data class PlaceOrderRequest(
    val item: String,
    val shippingAddress: Address,
    val billingAddress: Address?,
    val packaging: Packaging = Packaging(kind = "box"),
    @param:JsonDeserialize(using = CouponDeserializer::class) val coupon: Coupon?,
)

/** A Request Body that nests itself. */
data class Category(val name: String, val parent: Category?)

/** Read from a plain string such as `"ann@example.com"` through its own creator. */
data class Email @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(@get:JsonValue val value: String)

/** Read as one of its subtypes, named by the `type` property. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(JsonSubTypes.Type(Dog::class, name = "dog"))
open class Pet(val name: String) {
    override fun toString() = "${javaClass.simpleName}($name)"
}

class Dog(name: String) : Pet(name)

/** Uses [Address] too, and types that Jackson reads in its own way. */
data class RegisterCustomerRequest(val name: String, val address: Address, val email: Email, val pet: Pet?)

@RestController
class OrderController {
    /** Returns the Request Body's Kotlin values. */
    @PostMapping("/orders")
    fun place(@RequestBody request: PlaceOrderRequest): String = request.toString()

    @PostMapping("/categories")
    fun create(@RequestBody category: Category): String = category.toString()

    @PostMapping("/customers")
    fun register(@RequestBody request: RegisterCustomerRequest): String = request.toString()
}
