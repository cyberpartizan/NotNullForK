# Depend on the tested KSP version only as a preferred version

The plugin applies KSP by its plugin id, so the KSP Gradle plugin must be on the consumer's build script classpath. The plugin brings it as a runtime dependency whose version is only *preferred* (`prefer` in Gradle Module Metadata), not required. When the consumer applies KSP itself, the version it asks for wins conflict resolution, even if it is older than ours. When it doesn't, the preferred version, the one the plugin is tested with, is used. KSP is applied through the plugin manager, so applying it twice, in either order, is a no-op.

The tested version (`ksp` in `gradle/libs.versions.toml`) is the newest KSP that runs on both Kotlin 1.9 (measured, see ADR 0004) and the latest Kotlin (KSP 2.2+ needs Kotlin Gradle plugin 2.2+ APIs). With other Kotlin versions it prints KSP's version mismatch warning; a consumer who wants a matching KSP applies it.

## Considered Options

- **A required version (rejected):** Gradle picks the highest required version, so a consumer's older KSP would be silently upgraded.
- **`compileOnly`, so the consumer must bring KSP (rejected):** a consumer without KSP would have to apply it too, and the plugin is meant to need no other build configuration.
- **Pick the KSP version from the consumer's Kotlin version (rejected for now):** the build script classpath is resolved before any plugin runs, so a project plugin can't choose it. It would take a settings plugin.

## Consequences

- Only Gradle reads `prefer`. The POM lists the version as a plain dependency, so the plugin must be published with Gradle Module Metadata, which the Gradle Plugin Portal and `maven-publish` do by default.
- If KSP comes from a parent project's build script and the plugin from a subproject's, the parent's KSP classes are used, though the subproject's classpath also lists the preferred version.
- Below Kotlin 1.9 the preferred KSP doesn't work, so a Kotlin 1.8 consumer must apply KSP itself (ADR 0004).
