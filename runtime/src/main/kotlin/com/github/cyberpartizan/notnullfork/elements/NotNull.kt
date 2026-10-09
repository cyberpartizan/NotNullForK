package com.github.cyberpartizan.notnullfork.elements

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * The elements of the annotated collection, or the values of the annotated map, must not be null.
 * Generated Mirrors put it on collections and maps whose element or value type is non-null in the
 * Request Body. A `null` collection or map is valid.
 *
 * Each `null` is reported on its own, at its index (`tags[1]`, in iteration order) or key
 * (`scores[math]`). It is named like `jakarta.validation.constraints.NotNull`, so that its errors get
 * the same codes (`NotNull`) and message as a missing Required Field.
 *
 * (Bean Validation could check `List<@NotNull String?>` itself, but the Kotlin compiler does not put
 * such type annotations in the bytecode by default.)
 */
@MustBeDocumented
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NotNullValidator::class])
annotation class NotNull(
    val message: String = "{jakarta.validation.constraints.NotNull.message}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** Checks [NotNull]. */
class NotNullValidator : ConstraintValidator<NotNull, Any> {

    override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
        val nullsAt: List<(ConstraintValidatorContext.ConstraintViolationBuilder) -> Unit> = when (value) {
            is Collection<*> -> value.withIndex().filter { it.value == null }
                .map { (index, _) -> { builder -> builder.addBeanNode().inIterable().atIndex(index).addConstraintViolation() } }
            is Map<*, *> -> value.filterValues { it == null }.keys
                .map { key -> { builder -> builder.addBeanNode().inIterable().atKey(key).addConstraintViolation() } }
            else -> emptyList()
        }
        if (nullsAt.isEmpty()) return true
        context.disableDefaultConstraintViolation()
        for (report in nullsAt) report(context.buildConstraintViolationWithTemplate(context.defaultConstraintMessageTemplate))
        return false
    }
}
