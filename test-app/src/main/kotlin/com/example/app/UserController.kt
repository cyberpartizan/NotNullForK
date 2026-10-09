package com.example.app

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CreateUserRequest(val name: String, val age: Int)

data class AdultRequest(val age: Int) {
    init {
        require(age >= 18) { "must be an adult" }
    }
}

@RestController
class UserController {
    @PostMapping("/users")
    fun create(@RequestBody request: CreateUserRequest): CreateUserRequest = request

    @PostMapping("/users/valid")
    fun createValid(@Valid @RequestBody request: CreateUserRequest): CreateUserRequest = request

    @PostMapping("/users/adult")
    fun createAdult(@RequestBody request: AdultRequest): AdultRequest = request
}
