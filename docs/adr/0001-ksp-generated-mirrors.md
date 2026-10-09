# Generate Mirrors at compile time with KSP

When a Required Field is missing, jackson-module-kotlin fails during object construction, before Bean Validation runs, so clients get a generic 400 instead of per-field errors. We fix this by generating a nullable Mirror of each Request Body at compile time with KSP. At request time we deserialize into the Mirror, check it, and map it to the Request Body.

## Considered Options

- **Runtime-only (rejected):** deserialize into a JSON tree and use kotlin-reflect to find missing Required Fields. It needs less code, but nothing appears in the build output, it uses reflection on every request, and it's harder to debug.
- **K2 compiler plugin (rejected):** it relies on unstable compiler APIs and would break with each Kotlin release.
