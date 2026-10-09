package com.example.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.MethodArgumentNotValidException

@WebMvcTest(UserController::class)
class WebMvcSliceTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `a missing Required Field returns a per-field 400 in a @WebMvcTest slice`() {
        val result = mockMvc.post("/users") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"age": 42}"""
        }.andReturn()

        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.resolvedException).isInstanceOf(MethodArgumentNotValidException::class.java)
    }
}
