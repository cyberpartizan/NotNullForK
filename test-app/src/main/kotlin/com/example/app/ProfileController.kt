package com.example.app

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** A runtime annotation that is not a Jackson annotation, so it must not be copied to the Mirror. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Audited

/** A user annotation that bundles Jackson annotations. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@JsonIgnore
annotation class Internal

/** A Request Body whose JSON shape is changed by Jackson annotations, on every kind of target. */
@Audited
data class UpdateProfileRequest(
    @JsonProperty("user_name") @Audited val userName: String,
    @JsonAlias("handle", "nick") val nickname: String,
    @JsonProperty(required = true) @JsonFormat(pattern = "dd.MM.yyyy") val birthday: LocalDate,
    @JsonIgnore val role: String = "member",
    @field:JsonProperty("display_name") val displayName: String? = null,
    @get:JsonProperty("time_zone") val timeZone: String = "UTC",
    @Internal val note: String = "none",
    @set:JsonProperty("lang") var language: String = "en",
)

/** A Request Body with a class-level Jackson annotation. */
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy::class)
data class ShippingRequest(val streetName: String, val houseNumber: Int)

@RestController
class ProfileController {
    /** Returns the Request Body's Kotlin values, so that the response doesn't depend on its Jackson annotations. */
    @PostMapping("/profiles")
    fun update(@RequestBody request: UpdateProfileRequest): String = request.toString()

    /** Returns the Request Body's Kotlin values. */
    @PostMapping("/shipments")
    fun ship(@RequestBody request: ShippingRequest): String = "${request.streetName} ${request.houseNumber}"
}
