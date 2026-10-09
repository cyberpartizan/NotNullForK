package com.example.shared

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.cyberpartizan.notnullfork.NotNullForKIgnore

private const val DEFAULT_PRIORITY = 3

/**
 * A Request Body of a sibling module that doesn't apply the plugin, used by several endpoints. Its
 * default values use what a Mirror in another module can't see: a private constant and other fields.
 */
data class CreateTicketRequest(
    val title: String,
    val priority: Int = DEFAULT_PRIORITY,
    val labels: List<String> = listOf("new"),
    val assignee: String? = "triage",
    val summary: String = "$title (P$priority)",
    val reporter: Reporter,
    @JsonProperty("due_date") val dueDate: String?,
)

/** A Nested Type of the sibling module. */
data class Reporter(val name: String, val email: String = "unknown@example.com")

/** A Request Body of the sibling module with an Opt-out. */
@NotNullForKIgnore
data class CommentRequest(val text: String)
