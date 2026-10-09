package com.example.app

import com.example.shared.CommentRequest
import com.example.shared.CreateTicketRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Endpoints taking Request Bodies of the sibling module `shared-requests`. */
@RestController
class TicketController {
    @PostMapping("/tickets")
    fun create(@RequestBody request: CreateTicketRequest): CreateTicketRequest = request

    @PostMapping("/tickets/urgent")
    fun createUrgent(@RequestBody request: CreateTicketRequest): CreateTicketRequest = request.copy(priority = 1)

    @PostMapping("/comments")
    fun comment(@RequestBody request: CommentRequest): CommentRequest = request
}

/** Another controller taking the same Request Body. */
@RestController
class TicketAdminController {
    @PostMapping("/admin/tickets")
    fun import(@RequestBody request: CreateTicketRequest): CreateTicketRequest = request
}
