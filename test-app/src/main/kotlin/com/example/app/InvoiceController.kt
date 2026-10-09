package com.example.app

import com.example.invoices.CreateInvoiceRequest
import com.example.invoices.LegacyInvoiceRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Endpoints taking Request Bodies of the published library jar. */
@RestController
class InvoiceController {
    /** Returns the Request Body's Kotlin values, so that the response doesn't depend on its Jackson annotations. */
    @PostMapping("/invoices")
    fun create(@RequestBody request: CreateInvoiceRequest): String = request.toString()

    @PostMapping("/legacy-invoices")
    fun createLegacy(@RequestBody request: LegacyInvoiceRequest): String = request.toString()
}
