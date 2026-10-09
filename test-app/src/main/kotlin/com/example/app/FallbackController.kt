package com.example.app

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

// Request Bodies the plugin can't handle: each gets no Mirror and a build warning, and Spring reads
// it as it would without the plugin.

/** Sealed and polymorphic: read as one of its subtypes, named by the `kind` property. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(JsonSubTypes.Type(TextMessage::class, name = "text"))
sealed class Message

data class TextMessage(val text: String) : Message()

data class Labelled<T>(val label: String, val value: T)

/** Built through its own creator. */
data class Rating @JsonCreator constructor(@JsonProperty("stars") val stars: Int, @JsonProperty("by") val by: String)

/** Has a property outside its primary constructor. */
class Draft(val title: String) {
    var body: String = ""

    override fun toString() = "Draft(title=$title, body=$body)"
}

@RestController
class FallbackController {
    @PostMapping("/fallback/java")
    fun java(@RequestBody request: JavaTicketRequest): String = request.toString()

    @PostMapping("/fallback/message")
    fun message(@RequestBody request: Message): String = request.toString()

    @PostMapping("/fallback/labelled")
    fun labelled(@RequestBody request: Labelled<Int>): String = request.toString()

    @PostMapping("/fallback/rating")
    fun rating(@RequestBody request: Rating): String = request.toString()

    @PostMapping("/fallback/draft")
    fun draft(@RequestBody request: Draft): String = request.toString()
}
