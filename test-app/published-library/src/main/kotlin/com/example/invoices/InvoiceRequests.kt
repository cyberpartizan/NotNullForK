package com.example.invoices

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.github.cyberpartizan.notnullfork.NotNullForKIgnore
import java.time.LocalDate

/** A Request Body of a published library, whose JSON shape is changed by Jackson annotations. */
data class CreateInvoiceRequest(
    @JsonProperty("customer_id") val customerId: Long,
    @field:JsonProperty("currency_code") val currency: String = "EUR",
    @get:JsonProperty("due_in_days") val dueInDays: Int = 30,
    @JsonFormat(pattern = "dd.MM.yyyy") val issued: LocalDate,
    @JsonIgnore val source: String = "api",
    val lines: List<InvoiceLine>,
    val note: String? = "Thank you",
)

/** A Nested Type of the published library. */
data class InvoiceLine(val item: String, val quantity: Int = 1)

/** A Request Body of the published library with an Opt-out. */
@NotNullForKIgnore
data class LegacyInvoiceRequest(val number: String, val customer: String)
