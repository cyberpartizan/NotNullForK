package com.example.app

import com.github.cyberpartizan.notnullfork.NotNullForKIgnore
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** A Request Body with an Opt-out: Spring reads it as it would without the plugin. */
@NotNullForKIgnore
data class WriteNoteRequest(val title: String, val text: String)

/** A Request Body that uses the opted-out class as the type of a Required Field. */
data class PinNoteRequest(val board: String, val note: WriteNoteRequest)

@RestController
class NoteController {
    @PostMapping("/notes")
    fun write(@RequestBody request: WriteNoteRequest): String = request.toString()

    @PostMapping("/pins")
    fun pin(@RequestBody request: PinNoteRequest): String = request.toString()
}
