package com.example.lib.generated

import com.example.lib.LibraryRequest
import com.github.cyberpartizan.notnullfork.Mirror

/** What the app's generated Mirror of [LibraryRequest] looks like. */
class LibraryRequestMirror(val name: String? = null) : Mirror<LibraryRequest> {
    override fun toRequestBody(): LibraryRequest = LibraryRequest(name!!)
}
