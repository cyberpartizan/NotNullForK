package com.github.cyberpartizan.notnullfork

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.exc.InvalidNullException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** Defaulted Fields of Request Bodies compiled in another module, whose Mirrors can't copy their default values. */
class DefaultsTest {

    data class Ticket(
        val title: String,
        val priority: Int = 3,
        val labels: List<String> = listOf("new"),
        val assignee: String? = "triage",
    )

    /** What the Mirror of [Ticket] looks like when [Ticket] is compiled in another module. */
    class TicketMirror(
        val title: String? = null,
        @param:JsonDeserialize(using = PresentDeserializer.NonNull::class) val priority: Present<Int>? = null,
        @param:JsonDeserialize(using = PresentDeserializer.NonNull::class) val labels: Present<List<String>>? = null,
        @param:JsonDeserialize(using = PresentDeserializer::class) val assignee: Present<String?>? = null,
    ) : Mirror<Ticket> {
        override fun toRequestBody(): Ticket = DEFAULTS.newInstance(title!!, priority, labels, assignee)

        private companion object {
            val DEFAULTS = DefaultsConstructor(Ticket::class.java, 4, intArrayOf(1, 2, 3))
        }
    }

    private val objectMapper = jacksonObjectMapper()

    private fun read(json: String): Ticket = objectMapper.readValue<TicketMirror>(json).toRequestBody()

    @Test
    fun `missing Defaulted Fields get the Request Body's default values`() {
        assertThat(read("""{"title": "Bug"}""")).isEqualTo(Ticket("Bug"))
    }

    @Test
    fun `present Defaulted Fields keep the values sent`() {
        assertThat(read("""{"title": "Bug", "priority": 1, "labels": ["ui"], "assignee": "ann"}"""))
            .isEqualTo(Ticket("Bug", 1, listOf("ui"), "ann"))
    }

    @Test
    fun `an explicit null for a nullable Defaulted Field is null, not the default value`() {
        assertThat(read("""{"title": "Bug", "assignee": null}""")).isEqualTo(Ticket("Bug", assignee = null))
    }

    @Test
    fun `an explicit null for a non-null Defaulted Field fails as it does without a Mirror`() {
        val json = """{"title": "Bug", "labels": null}"""
        assertThatThrownBy { objectMapper.readValue<Ticket>(json) }.isInstanceOf(MismatchedInputException::class.java)

        assertThatThrownBy { read(json) }.isInstanceOf(InvalidNullException::class.java)
    }

    @Test
    fun `an explicit null for a non-null primitive Defaulted Field is read as Jackson reads it without a Mirror`() {
        val json = """{"title": "Bug", "priority": null}"""

        assertThat(read(json)).isEqualTo(objectMapper.readValue<Ticket>(json)).isEqualTo(Ticket("Bug", priority = 0))

        val strict = jacksonObjectMapper().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        assertThatThrownBy { strict.readValue<Ticket>(json) }.isInstanceOf(MismatchedInputException::class.java)
        assertThatThrownBy { strict.readValue<TicketMirror>(json) }.isInstanceOf(MismatchedInputException::class.java)
    }

    /** More than 32 parameters, so the default values are selected by two bit masks. */
    data class Wide(
        val p0: Int = 0, val p1: Int = 1, val p2: Int = 2, val p3: Int = 3, val p4: Int = 4, val p5: Int = 5,
        val p6: Int = 6, val p7: Int = 7, val p8: Int = 8, val p9: Int = 9, val p10: Int = 10, val p11: Int = 11,
        val p12: Int = 12, val p13: Int = 13, val p14: Int = 14, val p15: Int = 15, val p16: Int = 16, val p17: Int = 17,
        val p18: Int = 18, val p19: Int = 19, val p20: Int = 20, val p21: Int = 21, val p22: Int = 22, val p23: Int = 23,
        val p24: Int = 24, val p25: Int = 25, val p26: Int = 26, val p27: Int = 27, val p28: Int = 28, val p29: Int = 29,
        val p30: Int = 30, val p31: Int = 31, val p32: Long = 32, val p33: Boolean = true, val p34: String = "34",
    )

    @Test
    fun `default values are applied to any of more than 32 parameters`() {
        val constructor = DefaultsConstructor(Wide::class.java, 35, IntArray(35) { it })
        val arguments = Array<Any?>(35) { null }
        arguments[1] = Present(-1)
        arguments[32] = Present(-32L)
        arguments[34] = Present("x")

        assertThat(constructor.newInstance(*arguments)).isEqualTo(Wide(p1 = -1, p32 = -32L, p34 = "x"))
    }
}
