package com.example.app

import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** A Request Body that a controller method takes as optional. */
data class SendFeedbackRequest(val rating: Int, val comment: String)

@RestController
class FeedbackController {
    @PostMapping("/feedback")
    fun send(@RequestBody(required = false) request: SendFeedbackRequest?): String =
        request?.toString() ?: "no feedback"

    @PostMapping("/feedback/nullable")
    fun sendNullable(@RequestBody request: SendFeedbackRequest?): String = request?.toString() ?: "no feedback"

    @PostMapping("/feedback/errors")
    fun sendWithErrors(@RequestBody(required = false) request: SendFeedbackRequest?, errors: Errors): String =
        request?.toString() ?: "no feedback, ${errors.errorCount} errors"
}
