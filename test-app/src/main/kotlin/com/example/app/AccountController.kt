package com.example.app

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

enum class Plan { FREE, PRO }

/** Mixes Required Fields, Defaulted Fields and nullable fields. */
data class CreateAccountRequest(
    val email: String,
    val plan: Plan = Plan.FREE,
    val trialDays: Int = DEFAULT_TRIAL_DAYS,
    val language: String = Locale.ENGLISH.language,
    val greeting: String = listOf("Hello", "there").joinToString(separator = ", ") { it.lowercase() },
    val newsletter: Boolean =
        true,
    val nickname: String?,
    val referrer: String? = "organic",
    val age: Int,
) {
    companion object {
        const val DEFAULT_TRIAL_DAYS = 14
    }
}

@RestController
class AccountController {
    @PostMapping("/accounts")
    fun create(@RequestBody request: CreateAccountRequest): CreateAccountRequest = request
}
