package com.github.cyberpartizan.notnullfork.processor

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinSourceTest {

    @Test
    fun `reads the default value of each primary constructor parameter exactly as written`() {
        val source = KotlinSource(
            """
            package com.example

            data class Request(
                val name: String,
                val role: Role = Role.MEMBER,
                val tags: Map<String, List<Int>> = mapOf("a" to listOf(1, 2)),
                val greeting: String = listOf("Hello", "there").joinToString(separator = ", ") { it.lowercase() },
                val nickname: String?,
                val newsletter: Boolean =
                    true,
            )
            """.trimIndent(),
        )

        val parameters = source.primaryConstructorOf("Request", line = 3)!!

        assertThat(parameters.mapValues { it.value.defaultValue }).containsExactly(
            entry("name", null),
            entry("role", "Role.MEMBER"),
            entry("tags", """mapOf("a" to listOf(1, 2))"""),
            entry("greeting", """listOf("Hello", "there").joinToString(separator = ", ") { it.lowercase() }"""),
            entry("nickname", null),
            entry("newsletter", "true"),
        )
    }

    @Test
    fun `strings, characters, comments and annotations do not end a default value early`() {
        val source = KotlinSource(
            """
            class Request @JvmOverloads constructor(
                @field:Deprecated("x, y") val a: String = "a, ) b", // trailing, comment
                /* (, */ val b: String = "${'$'}{listOf(")", ",").size}" + ""${'"'}raw, ) "${'"'}",
                val c: Char = ',',
                val d: Int = if (1 < 2) 3 else 4,
                val e: List<Pair<String, Int>> = emptyList<Pair<String, Int>>(),
                val f: (Int, Int) -> Int = { x, y -> x + y } /* end */
            ) {
                val notAParameter: Int = 5
            }
            """.trimIndent(),
        )

        val parameters = source.primaryConstructorOf("Request", line = 1)!!

        assertThat(parameters.mapValues { it.value.defaultValue }).containsExactly(
            entry("a", "\"a, ) b\""),
            entry("b", "\"\${listOf(\")\", \",\").size}\" + \"\"\"raw, ) \"\"\""),
            entry("c", "','"),
            entry("d", "if (1 < 2) 3 else 4"),
            entry("e", "emptyList<Pair<String, Int>>()"),
            entry("f", "{ x, y -> x + y }"),
        )
    }

    @Test
    fun `finds the class declared at or after the given line`() {
        val source = KotlinSource(
            """
            class Other(val a: Int = 1)

            class Outer {
                @Suppress("unused")
                class Request(val a: Int = 2)
            }
            """.trimIndent(),
        )

        assertThat(source.primaryConstructorOf("Request", line = 4)!!.getValue("a").defaultValue).isEqualTo("2")
        assertThat(source.primaryConstructorOf("Missing", line = 1)).isNull()
    }

    @Test
    fun `lists the names a default value refers to, but not members or named arguments`() {
        val source = KotlinSource(
            """
            class Request(val a: Locale = Locale.ENGLISH.withRole(role = DEFAULT, other).also { it.x })
            """.trimIndent(),
        )

        val names = source.primaryConstructorOf("Request", line = 1)!!.getValue("a").referencedNames

        assertThat(names).containsExactlyInAnyOrder("Locale", "DEFAULT", "other", "it")
    }

    @Test
    fun `lists the names a default value refers to inside string templates`() {
        val source = KotlinSource(
            """
            class Request(val a: String = "Hi ${'$'}NAME, ${'$'}{PREFIX.trim() + "${'$'}{inner}"}" + ""${'"'}${'$'}raw""${'"'})
            """.trimIndent(),
        )

        val names = source.primaryConstructorOf("Request", line = 1)!!.getValue("a").referencedNames

        assertThat(names).containsExactlyInAnyOrder("NAME", "PREFIX", "inner", "raw")
    }

    @Test
    fun `reads the imports of the file`() {
        val source = KotlinSource(
            """
            @file:JvmName("Requests")
            package com.example

            import java.util.Locale
            import java.time.*
            import com.example.other.Role as OtherRole;  import kotlin.math.`max`

            class Request(val a: Int = 1)
            """.trimIndent(),
        )

        assertThat(source.imports).containsExactly(
            "java.util.Locale",
            "java.time.*",
            "com.example.other.Role as OtherRole",
            "kotlin.math.`max`",
        )
    }

    private fun entry(key: String, value: String?) = Assertions.entry(key, value)
}
