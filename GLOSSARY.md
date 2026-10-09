# NotNullForK

A Gradle plugin for Spring Kotlin projects. When a client leaves out a field that is non-null in a request body, it reports a per-field validation error instead of a deserialization failure.

## Language

**Request Body**:
A Kotlin class that a controller method takes as its `@RequestBody` parameter, written by the user.
_Avoid_: DTO, payload, original

**Mirror**:
A generated copy of a Request Body in which every Required Field is nullable and marked as must-not-be-null. Incoming JSON is read into the Mirror first.
_Avoid_: copy, shadow, twin, generated body

**Required Field**:
A Request Body property whose type is non-null and that has no default value.
_Avoid_: mandatory field, not-null field

**Defaulted Field**:
A Request Body property that has a default value. The plugin doesn't check it: when the JSON leaves it out, it gets the Request Body's default value.
_Avoid_: optional field

**Nested Type**:
A Kotlin class used as the type of a Required Field or nullable field of a Request Body, or as the element type of such a list or set or the value type of such a map, at any depth, that gets its own Mirror. The Request Body's Mirror holds the Nested Type's Mirror and checks its Required Fields too.
_Avoid_: nested Request Body, sub-DTO

**Opt-out**:
A marker on a Request Body that stops the plugin from handling it. A class with an Opt-out gets no Mirror, so it is not a Nested Type either.
_Avoid_: exclusion, skip
