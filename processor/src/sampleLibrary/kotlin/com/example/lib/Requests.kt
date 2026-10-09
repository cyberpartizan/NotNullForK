package com.example.lib

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.github.cyberpartizan.notnullfork.NotNullForKIgnore
import java.time.LocalDate

// Request Bodies compiled in another module: the processor tests see only their class files.

data class CreateTicketRequest(
    val title: String,
    val priority: Int = 3,
    val labels: List<String> = listOf("new"),
    val assignee: String? = "triage",
    val reporter: Reporter,
    val watchers: List<Reporter>?,
)

data class Reporter(val name: String, val email: String?)

@NotNullForKIgnore
data class IgnoredRequest(val text: String)

data class AnnotatedRequest(
    @JsonProperty("user_name") val userName: String,
    @JsonAlias("handle", "nick") val nickname: String,
    @JsonFormat(pattern = "dd.MM.yyyy") val birthday: LocalDate,
    @JsonIgnore val role: String = "member",
    @field:JsonProperty("display_name") val displayName: String? = null,
    @get:JsonProperty("time_zone") val timeZone: String = "UTC",
    @set:JsonProperty("lang") var language: String = "en",
)

sealed class Shape {
    data class Circle(val radius: Int) : Shape()
}

class InternalConstructor internal constructor(val text: String)

class NotAProperty(text: String) {
    val length = text.length
}

@JvmInline
value class Email(val address: String)

data class WithValueClass(val email: Email, val note: String = "")

/** A value class field is fine without Defaulted Fields: the Mirror calls the constructor in Kotlin. */
data class ValueClassOnly(val email: Email, val note: String?)

class TwoConstructors(val name: String, val size: Int = 1) {
    constructor(size: Int, name: String = "x") : this(name, size)
}

data class OwnDeserializer(
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.fasterxml.jackson.databind.deser.std.StringDeserializer::class)
    val name: String = "x",
)
