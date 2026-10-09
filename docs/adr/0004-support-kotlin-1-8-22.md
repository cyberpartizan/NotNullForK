# Support Kotlin 1.8.22 and newer; a Kotlin 1.8 consumer applies KSP itself

Consumers may use Kotlin 1.8.22 or newer. The runtime, processor and Gradle plugin are compiled with language and API version 1.8 and the 1.8.22 standard library, so a Kotlin 1.8 compiler and standard library can use them. The processor is compiled against the oldest KSP it runs on, the KSP1 API `1.8.22-1.0.11`. The KSP the plugin brings (ADR 0003) runs on Kotlin 1.9 and newer, but on Kotlin 1.8 it fails when `kspKotlin` runs, with an error that doesn't name the cause. Below Kotlin 1.9, the plugin therefore fails at configuration time unless the build uses KSP1, and it tells the consumer to apply the KSP of their Kotlin version. "No other build configuration is needed" holds only from Kotlin 1.9 on.

## Considered Options

- **Fail for all of Kotlin 1.x (rejected):** KSP 2.x isn't meant for Kotlin 1.9, but it was measured to build and test there. Failing would break a build that works.
- **Pick KSP1 for a Kotlin 1.8 consumer (rejected):** as in ADR 0003, a project plugin can't choose the build script classpath.

## Consequences

- When a future Kotlin removes language version 1.8, this repository stays on the last Kotlin that still has it, or support for Kotlin 1.8 is dropped.
- The processor runs under both KSP1 and KSP2. Its unit tests run KSP2. KSP1 is covered by the functional tests, where the test app is built with Kotlin 1.8.22.
- Under KSP1, KotlinPoet names a type alias of a nullable type with a redundant `?`. The processor removes that `?`, so the generated Mirrors compile without warnings.
