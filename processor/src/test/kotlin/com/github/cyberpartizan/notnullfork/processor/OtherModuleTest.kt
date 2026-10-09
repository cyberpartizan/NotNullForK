package com.github.cyberpartizan.notnullfork.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Request Bodies compiled in another module (`src/sampleLibrary`), which the processor sees only as classes. */
class OtherModuleTest {

    @Test
    fun `a Request Body from another module gets one Mirror however many endpoints use it`() {
        val run = runProcessor(
            "com/example/TicketController.kt" to controller("TicketController", "com.example.lib.CreateTicketRequest", "a", "b"),
            "com/example/AdminController.kt" to controller("AdminController", "com.example.lib.CreateTicketRequest", "c"),
        )

        assertThat(run.warnings).isEmpty()
        assertThat(run.errors).isEmpty()
        assertThat(run.generated.keys).containsExactlyInAnyOrder(
            "com/example/lib/generated/CreateTicketRequestMirror.kt",
            "com/example/lib/generated/ReporterMirror.kt",
        )
    }

    @Test
    fun `the Jackson annotations of a Request Body from another module are copied to its Mirror`() {
        val run = runProcessor("com/example/Controller.kt" to controller("Controller", "com.example.lib.AnnotatedRequest", "a"))

        assertThat(run.warnings).isEmpty()
        assertThat(run.generated.keys).containsExactly("com/example/lib/generated/AnnotatedRequestMirror.kt")
    }

    @Test
    fun `a Request Body from another module with an Opt-out gets no Mirror, reported at info level`() {
        val run = runProcessor("com/example/Controller.kt" to controller("Controller", "com.example.lib.IgnoredRequest", "a"))

        assertThat(run.warnings).isEmpty()
        assertThat(run.generated).isEmpty()
        assertThat(run.infos).contains("NotNullForK: no Mirror generated for com.example.lib.IgnoredRequest: it has @NotNullForKIgnore")
    }

    @Test
    fun `a Request Body from another module that the plugin can't handle gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controller("Controller", "com.example.lib.Shape", "a"),
            "com/example/Other.kt" to controller("Other", "com.example.lib.InternalConstructor", "b"),
            "com/example/Third.kt" to controller("Third", "com.example.lib.NotAProperty", "c"),
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "NotNullForK: no Mirror generated for com.example.lib.Shape: it is sealed",
            "NotNullForK: no Mirror generated for com.example.lib.InternalConstructor: its primary constructor is internal",
            "NotNullForK: no Mirror generated for com.example.lib.NotAProperty: its primary constructor parameter 'text' is not a property",
        )
        assertThat(run.generated).isEmpty()
    }

    @Test
    fun `a Request Body from another module whose default values can't be applied gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controller("Controller", "com.example.lib.WithValueClass", "a"),
            "com/example/Other.kt" to controller("Other", "com.example.lib.TwoConstructors", "b"),
            "com/example/Third.kt" to controller("Third", "com.example.lib.ValueClassOnly", "c"),
            "com/example/Fourth.kt" to controller("Fourth", "com.example.lib.OwnDeserializer", "d"),
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "NotNullForK: no Mirror generated for com.example.lib.WithValueClass: " +
                "the default values of its properties can't be applied: its property 'email' is of a value class",
            "NotNullForK: no Mirror generated for com.example.lib.TwoConstructors: " +
                "the default values of its properties can't be applied: another constructor has as many parameters and default values",
            "NotNullForK: no Mirror generated for com.example.lib.OwnDeserializer: " +
                "the default value of 'name' can't be applied: it has its own @JsonDeserialize",
        )
        assertThat(run.generated.keys).containsExactly("com/example/lib/generated/ValueClassOnlyMirror.kt")
    }

    /** A controller named [name] with an endpoint at each of [paths] taking [requestBody]. */
    private fun controller(name: String, requestBody: String, vararg paths: String): String =
        """
        package com.example

        import org.springframework.web.bind.annotation.PostMapping
        import org.springframework.web.bind.annotation.RequestBody
        import org.springframework.web.bind.annotation.RestController

        @RestController
        class $name {
        """.trimIndent() + paths.joinToString("") {
            "\n    @PostMapping(\"/$it\") fun handle${it.uppercase()}(@RequestBody request: $requestBody) = Unit"
        } + "\n}\n"
}
