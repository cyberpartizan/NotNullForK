# NotNullForK

A Gradle plugin for Spring Kotlin projects. When a client leaves out a field that is non-null in a request body,
the app answers with a per-field validation error (400) instead of a deserialization failure.
See [GLOSSARY.md](GLOSSARY.md) for the terms and [docs/adr](docs/adr) for the design decisions.

## Usage

```kotlin
plugins {
    kotlin("jvm") version "..."
    id("com.github.cyberpartizan.not-null-for-k") version "..."
}
```

The plugin applies KSP and adds the processor (`com.github.cyberpartizan:not-null-for-k-processor`) and the runtime
(`com.github.cyberpartizan:not-null-for-k-runtime`) in its own version. No other build configuration is needed.
A KSP version the project applies itself is kept (ADR 0003).

## Modules and publishing

| Module          | Artifact                                                  | Published to         |
|-----------------|-----------------------------------------------------------|----------------------|
| `gradle-plugin` | plugin id `com.github.cyberpartizan.not-null-for-k`       | Gradle Plugin Portal |
| `processor`     | `com.github.cyberpartizan:not-null-for-k-processor`       | Maven Central        |
| `runtime`       | `com.github.cyberpartizan:not-null-for-k-runtime`         | Maven Central        |

All artifacts take the one `version` in `gradle.properties`. Every artifact is published with sources and
javadoc jars, POM metadata (license, developers, SCM) and Gradle Module Metadata (`.module`).

Keep Gradle Module Metadata published: only the `.module` file of the plugin carries the *preferred* KSP version
(ADR 0003). The POM lists KSP as a plain runtime dependency, so a consumer resolving through the POM alone would
lose its own KSP version.

Publications are signed only when a signing key is given (the `signingKey` and `signingPassword` Gradle
properties, e.g. as `ORG_GRADLE_PROJECT_signingKey` and `ORG_GRADLE_PROJECT_signingPassword`). Without a key,
local builds and `publishToMavenLocal` work unsigned.

To try a build locally:

```shell
./gradlew publishToMavenLocal
```

`PublishingFunctionalTest` (in `./gradlew check`) does the same into an isolated `build/maven-local` and builds the
test app against only that repository.

## Values to confirm before the first release

These were filled in from the group `com.github.cyberpartizan` and are not confirmed yet
(see the root `build.gradle.kts` and `gradle-plugin/build.gradle.kts`):

- License: Apache License 2.0. Add a matching `LICENSE` file to the repository.
- Developer: id and name `cyberpartizan`, URL `https://github.com/cyberpartizan`, no email.
- Project, website, VCS and SCM URLs: `https://github.com/cyberpartizan/not-null-for-k`.
- Plugin Portal display name `NotNullForK` and tags `kotlin, spring, spring-boot, validation, jackson, ksp`.
- The javadoc jars are empty, because the code is Kotlin. Maven Central accepts that; Dokka could fill them.

## Manual release steps

Nothing has been published yet. These steps need the maintainer's accounts and keys:

1. **Signing keys.** Create a GPG key pair, upload the public key to a key server Maven Central reads
   (e.g. `keys.openpgp.org`), and provide the ASCII-armored private key and its password as the `signingKey`
   and `signingPassword` properties (as environment variables or in `~/.gradle/gradle.properties`, never in the repository).
2. **Central namespace verification.** Sign in to the [Central Portal](https://central.sonatype.com) and verify
   the namespace `com.github.cyberpartizan`. The Central Portal may no longer accept new `com.github.*` namespaces
   and suggest `io.github.cyberpartizan` instead; if so, change `group` in `gradle.properties` and `GROUP` in
   `NotNullForKPlugin`. Generate a user token and provide it as the `mavenCentralUsername` and
   `mavenCentralPassword` properties.
3. **Plugin Portal account verification.** Create an account on the [Gradle Plugin Portal](https://plugins.gradle.org),
   linked to the GitHub user `cyberpartizan` so that it may publish `com.github.cyberpartizan.*` ids, and provide
   its API key as the `gradle.publish.key` and `gradle.publish.secret` properties.
4. Set a release `version` (without `-SNAPSHOT`) in `gradle.properties`.
5. Publish the processor and runtime first, since the plugin needs them:
   `./gradlew :not-null-for-k-runtime:publishAllPublicationsToMavenCentralRepository :not-null-for-k-processor:publishAllPublicationsToMavenCentralRepository`.
   This uploads to the Central Portal's OSSRH Staging API. Then hand the upload to the Central Portal with
   `curl -X POST -H "Authorization: Bearer <base64 of username:password of the token>" https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.github.cyberpartizan`
   and release the deployment in the Central Portal.
6. Publish the plugin: `./gradlew :not-null-for-k-gradle-plugin:publishPlugins`. The first version of a plugin
   waits for the Plugin Portal's manual approval.
