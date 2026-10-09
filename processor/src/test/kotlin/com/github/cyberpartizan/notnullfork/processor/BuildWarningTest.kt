package com.github.cyberpartizan.notnullfork.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The build warnings for Request Bodies the plugin leaves to Spring. */
class BuildWarningTest {

    @Test
    fun `a supported Request Body gives no warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("CreateUserRequest"),
            "com/example/CreateUserRequest.kt" to """
                package com.example

                data class CreateUserRequest(val name: String, val age: Int = 18)
            """,
        )

        assertThat(run.warnings).isEmpty()
        assertThat(run.errors).isEmpty()
    }

    @Test
    fun `a Java class taken as a request body gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("CreateUserRequest"),
            "com/example/CreateUserRequest.java" to """
                package com.example;

                public class CreateUserRequest {
                    public String name;
                }
            """,
        )

        assertThat(run.warnings).containsExactly(
            "CreateUserRequest.java:3: NotNullForK: no Mirror generated for com.example.CreateUserRequest: it is a Java class",
        )
    }

    @Test
    fun `a sealed Request Body gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Shape"),
            "com/example/Shape.kt" to """
                package com.example

                sealed class Shape {
                    data class Circle(val radius: Int) : Shape()
                }
            """,
        )

        assertThat(run.warnings).containsExactly(
            "Shape.kt:3: NotNullForK: no Mirror generated for com.example.Shape: it is sealed",
        )
    }

    @Test
    fun `a polymorphic Request Body gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Shape"),
            "com/example/Shape.kt" to """
                package com.example

                import com.fasterxml.jackson.annotation.JsonTypeInfo

                @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
                open class Shape(val name: String)
            """,
        )

        assertThat(run.warnings).containsExactly(
            "Shape.kt:6: NotNullForK: no Mirror generated for com.example.Shape: it is polymorphic (@JsonTypeInfo)",
        )
    }

    @Test
    fun `a Request Body whose supertype is polymorphic gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Circle"),
            "com/example/Shape.kt" to """
                package com.example

                import com.fasterxml.jackson.annotation.JsonTypeInfo

                @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
                interface Shape

                data class Circle(val radius: Int) : Shape
            """,
        )

        assertThat(run.warnings).containsExactly(
            "Shape.kt:8: NotNullForK: no Mirror generated for com.example.Circle: it is polymorphic (@JsonTypeInfo on com.example.Shape)",
        )
    }

    @Test
    fun `an abstract Request Body or an interface gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Shape", "Named"),
            "com/example/Shape.kt" to """
                package com.example

                abstract class Shape(val name: String)

                interface Named
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Shape.kt:3: NotNullForK: no Mirror generated for com.example.Shape: it is abstract",
            "Shape.kt:5: NotNullForK: no Mirror generated for com.example.Named: it is an interface",
        )
    }

    @Test
    fun `a generic Request Body gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Page<String>"),
            "com/example/Page.kt" to """
                package com.example

                data class Page<T>(val items: List<T>)
            """,
        )

        assertThat(run.warnings).containsExactly(
            "Page.kt:3: NotNullForK: no Mirror generated for com.example.Page: it is generic",
        )
    }

    @Test
    fun `a value class Request Body gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Email"),
            "com/example/Email.kt" to """
                package com.example

                @JvmInline
                value class Email(val address: String)
            """,
        )

        assertThat(run.warnings).containsExactly(
            "Email.kt:4: NotNullForK: no Mirror generated for com.example.Email: it is a value class",
        )
    }

    @Test
    fun `a Request Body built through a @JsonCreator gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Point", "Size"),
            "com/example/Point.kt" to """
                package com.example

                import com.fasterxml.jackson.annotation.JsonCreator

                data class Point @JsonCreator constructor(val x: Int, val y: Int)

                data class Size(val width: Int, val height: Int) {
                    companion object {
                        @JsonCreator
                        @JvmStatic
                        fun of(width: Int, height: Int) = Size(width, height)
                    }
                }
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Point.kt:5: NotNullForK: no Mirror generated for com.example.Point: it has a @JsonCreator",
            "Point.kt:7: NotNullForK: no Mirror generated for com.example.Size: it has a @JsonCreator",
        )
    }

    @Test
    fun `a Request Body with a property outside the primary constructor gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Profile", "Account"),
            "com/example/Profile.kt" to """
                package com.example

                class Profile(val name: String) {
                    var bio: String = ""
                }

                open class Audited {
                    var createdBy: String? = null
                }

                class Account(val name: String) : Audited()
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Profile.kt:3: NotNullForK: no Mirror generated for com.example.Profile: its property 'bio' is not in the primary constructor",
            "Profile.kt:11: NotNullForK: no Mirror generated for com.example.Account: its property 'createdBy' is not in the primary constructor",
        )
    }

    @Test
    fun `a property computed from the others is not one that Jackson reads, so gives no warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Person"),
            "com/example/Person.kt" to """
                package com.example

                data class Person(val first: String, val last: String) {
                    val fullName: String get() = "${'$'}first ${'$'}last"
                }
            """,
        )

        assertThat(run.warnings).isEmpty()
    }

    @Test
    fun `a property outside the primary constructor that Jackson ignores gives no warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Profile"),
            "com/example/Profile.kt" to """
                package com.example

                import com.fasterxml.jackson.annotation.JsonIgnore

                class Profile(val name: String) {
                    @JsonIgnore
                    var cache: String? = null
                }
            """,
        )

        assertThat(run.warnings).isEmpty()
    }

    @Test
    fun `a Defaulted Field whose type has an Opt-out gives no warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Order"),
            "com/example/Order.kt" to """
                package com.example

                import com.github.cyberpartizan.notnullfork.NotNullForKIgnore

                @NotNullForKIgnore
                data class Address(val street: String)

                data class Order(val id: String, val shipping: Address = Address("Main St"))
            """,
        )

        assertThat(run.warnings).isEmpty()
    }

    @Test
    fun `a Request Body that a Mirror can't construct gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Hidden", "Factory", "Plain", "Outer.Inner"),
            "com/example/Shapes.kt" to """
                package com.example

                private class Hidden(val name: String)

                class Factory private constructor(val name: String)

                class Plain(name: String) {
                    val upper = name.uppercase()
                }

                class Outer {
                    inner class Inner(val name: String)
                }
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Shapes.kt:3: NotNullForK: no Mirror generated for com.example.Hidden: it is private",
            "Shapes.kt:5: NotNullForK: no Mirror generated for com.example.Factory: its primary constructor is private",
            "Shapes.kt:7: NotNullForK: no Mirror generated for com.example.Plain: its primary constructor parameter 'name' is not a property",
            "Shapes.kt:12: NotNullForK: no Mirror generated for com.example.Outer.Inner: it is an inner class",
        )
    }

    @Test
    fun `a Request Body with its own deserializer gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Direct", "Bundled"),
            "com/example/Bodies.kt" to """
                package com.example

                import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
                import com.fasterxml.jackson.databind.JsonDeserializer
                import com.fasterxml.jackson.databind.annotation.JsonDeserialize

                @JsonDeserialize(using = JsonDeserializer.None::class)
                data class Direct(val name: String)

                @Retention(AnnotationRetention.RUNTIME)
                @JacksonAnnotationsInside
                @JsonDeserialize(using = JsonDeserializer.None::class)
                annotation class ReadByHand

                @ReadByHand
                data class Bundled(val name: String)
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Bodies.kt:8: NotNullForK: no Mirror generated for com.example.Direct: it has a class-level @JsonDeserialize",
            "Bodies.kt:16: NotNullForK: no Mirror generated for com.example.Bundled: it has a class-level @JsonDeserialize",
        )
    }

    @Test
    fun `a Request Body with managed and back references gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Folder", "File"),
            "com/example/Folder.kt" to """
                package com.example

                import com.fasterxml.jackson.annotation.JsonBackReference
                import com.fasterxml.jackson.annotation.JsonManagedReference

                class Folder(val name: String, @field:JsonManagedReference val files: List<File>)

                class File(val name: String, @field:JsonBackReference var folder: Folder?)
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Folder.kt:6: NotNullForK: no Mirror generated for com.example.Folder: its property 'files' has @JsonManagedReference",
            "Folder.kt:8: NotNullForK: no Mirror generated for com.example.File: its property 'folder' has @JsonBackReference",
        )
    }

    @Test
    fun `a Defaulted Field whose type has Required Fields gets a warning naming the Request Body and the field`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Order"),
            "com/example/Order.kt" to """
                package com.example

                data class Address(val street: String, val city: String? = null)

                data class Note(val text: String? = null)

                data class Order(
                    val id: String,
                    val shipping: Address = Address("Main St"),
                    val billing: List<Address> = emptyList(),
                    val note: Note = Note(),
                    val tags: List<String> = emptyList(),
                )
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Order.kt:9: NotNullForK: the Defaulted Field 'shipping' of com.example.Order is left as it is, " +
                "so the Required Fields of com.example.Address in it are not checked",
            "Order.kt:10: NotNullForK: the Defaulted Field 'billing' of com.example.Order is left as it is, " +
                "so the Required Fields of com.example.Address in it are not checked",
        )
    }

    @Test
    fun `a Request Body whose default value can't be copied to the Mirror gets a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Greeting", "Secret"),
            "com/example/Greeting.kt" to """
                package com.example

                private const val HIDDEN = "x"

                data class Greeting(val name: String, val text: String = "Hi " + name)

                data class Secret(val value: String = HIDDEN)
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Greeting.kt:5: NotNullForK: no Mirror generated for com.example.Greeting: the default value of 'text' refers to 'name'",
            "Greeting.kt:7: NotNullForK: no Mirror generated for com.example.Secret: the default value of 'value' refers to 'HIDDEN'",
        )
    }

    @Test
    fun `an Opt-out is reported at info level, not as a warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Note"),
            "com/example/Note.kt" to """
                package com.example

                import com.github.cyberpartizan.notnullfork.NotNullForKIgnore

                @NotNullForKIgnore
                data class Note(val text: String)
            """,
        )

        assertThat(run.warnings).isEmpty()
        assertThat(run.infos).contains("Note.kt:6: NotNullForK: no Mirror generated for com.example.Note: it has @NotNullForKIgnore")
    }

    @Test
    fun `a library type as the Request Body gives no warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("String", "com.fasterxml.jackson.databind.JsonNode", "Map<String, Int>"),
        )

        assertThat(run.warnings).isEmpty()
    }

    @Test
    fun `a field type the plugin can't handle gets a warning and is left as it is`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Drawing"),
            "com/example/Drawing.kt" to """
                package com.example

                import com.fasterxml.jackson.annotation.JsonCreator

                sealed interface Shape

                data class Point @JsonCreator constructor(val x: Int, val y: Int)

                data class Drawing(val shapes: List<Shape>, val origin: Point?)
            """,
        )

        assertThat(run.warnings).containsExactlyInAnyOrder(
            "Drawing.kt:5: NotNullForK: no Mirror generated for com.example.Shape: it is sealed",
            "Drawing.kt:7: NotNullForK: no Mirror generated for com.example.Point: it has a @JsonCreator",
        )
    }

    @Test
    fun `enum classes, objects and value classes as field types give no warning`() {
        val run = runProcessor(
            "com/example/Controller.kt" to controllerFor("Account"),
            "com/example/Account.kt" to """
                package com.example

                enum class Role { ADMIN, MEMBER }

                @JvmInline
                value class Email(val address: String)

                data class Account(val role: Role, val email: Email, val unit: Unit?)
            """,
        )

        assertThat(run.warnings).isEmpty()
    }

    /** A controller with an endpoint for each of [requestBodies] (classes in `com.example`). */
    private fun controllerFor(vararg requestBodies: String): String =
        """
        package com.example

        import org.springframework.web.bind.annotation.PostMapping
        import org.springframework.web.bind.annotation.RequestBody
        import org.springframework.web.bind.annotation.RestController

        @RestController
        class Controller {
        """.trimIndent() + requestBodies.joinToString("") {
            "\n    @PostMapping(\"/$it\") fun handle$it(@RequestBody request: $it) = Unit"
        } + "\n}\n"
}
