package com.example.app

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.validation.BindingResult
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.reflect.KClass

/** Validation group for the stricter sign-up checks. */
interface Strict

/** A Request Body with Bean Validation constraints of its own, on every kind of target. */
@NotAdmin
data class SignUpRequest(
    @field:Email val email: String,
    @field:Size(min = 8) @field:NoWhitespace val password: String,
    @get:Min(18) val age: Int,
    @field:Size(min = 3, groups = [Strict::class]) val nickname: String? = null,
    val website: @Size(max = 30) String? = null,
)

@RestController
class SignUpController {
    @PostMapping("/sign-ups")
    fun create(@RequestBody request: SignUpRequest): SignUpRequest = request

    @PostMapping("/sign-ups/valid")
    fun createValid(@Valid @RequestBody request: SignUpRequest): SignUpRequest = request

    @PostMapping("/sign-ups/strict")
    fun createStrict(@Validated(Strict::class) @RequestBody request: SignUpRequest): SignUpRequest = request

    /** Returns the fields that broke a constraint, as Spring hands them to the controller. */
    @PostMapping("/sign-ups/errors")
    fun createWithErrors(@Valid @RequestBody request: SignUpRequest, errors: BindingResult): List<String> =
        errors.fieldErrors.map { it.field }
}

/** A custom field constraint: the value has no whitespace. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NoWhitespaceValidator::class])
annotation class NoWhitespace(
    val message: String = "must not contain whitespace",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NoWhitespaceValidator : ConstraintValidator<NoWhitespace, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext) = value == null || value.none(Char::isWhitespace)
}

/** A custom class-level constraint: nobody signs up as the admin. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NotAdminValidator::class])
annotation class NotAdmin(
    val message: String = "must not sign up as the admin",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NotAdminValidator : ConstraintValidator<NotAdmin, SignUpRequest> {
    override fun isValid(value: SignUpRequest?, context: ConstraintValidatorContext) = value?.email != "admin@example.com"
}
