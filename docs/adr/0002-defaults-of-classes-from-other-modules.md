# Apply the default values of Request Bodies from other modules through their defaults constructor

A Mirror copies each Defaulted Field's default value expression from the Request Body's source file, because KSP doesn't expose default values. A Request Body compiled in another module or a library jar has no source file to copy from. For such a class, the Mirror holds each Defaulted Field as a `Present<T>?`. It is `null` when the JSON doesn't have the field, and holds the value otherwise, `null` included. The Mirror then builds the Request Body through the constructor that the Kotlin compiler generates for default values: `<init>(params..., int masks..., DefaultConstructorMarker)`. Each absent field's bit is set in the mask. The runtime finds that constructor once per Mirror class and keeps it as a `MethodHandle`, so no request uses kotlin-reflect. A runtime deserializer reads `Present<T>`. It treats a JSON `null` as Jackson would treat it for the Request Body: a non-null field fails the request, and a primitive becomes `0` or `false` unless `FAIL_ON_NULL_FOR_PRIMITIVES` is on.

Classes compiled in this module keep the copied defaults. Ticket 02 requires a Defaulted Field to keep its type and default value in the Mirror, and their behaviour and tests are unchanged.

## Considered Options

- **Use the defaults constructor for every class (rejected for now):** with one code path, the source lexer could go, and default values that refer to other fields or private declarations would work. But every Mirror would lose its plain field types, the Jackson annotations of a Defaulted Field would apply to a wrapper type, and every existing Mirror would change. This is still an option if the lexer becomes a burden.
- **`java.util.Optional<T>?` instead of `Present<T>?` (rejected):** it reads absent, `null` and present values apart for free, but only if the app's ObjectMapper has `Jdk8Module`.
- **Track presence with setters outside the constructor (rejected):** this would keep the field types, but every use-site target of the Request Body's annotations would need moving, and a non-null field would still need a placeholder value.

## Consequences

- A Request Body from another module can't get a Mirror if its Defaulted Fields can't be applied that way. This happens when its constructor takes a value class, whose JVM parameter type is the unboxed value, or when another constructor has as many parameters and default values. The build warns about it.
- A Defaulted Field's own `@JsonDeserialize` would clash with the Mirror's, so such a class gets no Mirror either, and the build warns about it.
- The Mirror reads a JSON `null` for a non-null Defaulted Field as an error. It does so even when jackson-module-kotlin's `NullIsSameAsDefault` or `@JsonSetter(nulls = AS_EMPTY)` would give the default or an empty value. Classes of this module, whose defaults are copied, keep Jackson's behaviour.
