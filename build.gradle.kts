import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.plugin.publish) apply false
}

// Release metadata shared by all published artifacts. All artifacts take the one version in gradle.properties.
val projectUrl = "https://github.com/cyberpartizan/not-null-for-k"

// Local Maven repository the TestKit test app resolves the plugin, processor and runtime from.
val testRepo = layout.buildDirectory.dir("test-repo")

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    extensions.configure<JavaPluginExtension> {
        // Maven Central and the Gradle Plugin Portal need both. The Kotlin sources give an empty javadoc jar.
        withSourcesJar()
        withJavadocJar()
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    extensions.configure<KotlinJvmProjectExtension> {
        // Consumers may use Kotlin 2.0, so keep the metadata and stdlib readable by it.
        coreLibrariesVersion = "2.0.21"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            apiVersion.set(KotlinVersion.KOTLIN_2_0)
            languageVersion.set(KotlinVersion.KOTLIN_2_0)
        }
    }

    val publishing = extensions.getByType<PublishingExtension>()
    publishing.repositories {
        maven {
            name = "testRepo"
            url = uri(testRepo)
        }
    }
    // The processor and runtime go to Maven Central; the Gradle plugin goes to the Gradle Plugin Portal.
    if (project.name != "not-null-for-k-gradle-plugin") {
        publishing.repositories {
            maven {
                // Credentials: the mavenCentralUsername and mavenCentralPassword properties (a Central Portal user token).
                name = "mavenCentral"
                url = uri(
                    if (version.toString().endsWith("-SNAPSHOT")) "https://central.sonatype.com/repository/maven-snapshots/"
                    else "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/",
                )
                credentials(PasswordCredentials::class)
            }
        }
    }
    publishing.publications.withType<MavenPublication>().configureEach {
        pom {
            // Conventions only: the plugin marker publications set their own name and description.
            name.convention(project.name)
            description.convention(provider { project.description })
            url.set(projectUrl)
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("cyberpartizan")
                    name.set("cyberpartizan")
                    url.set("https://github.com/cyberpartizan")
                }
            }
            scm {
                connection.set("scm:git:$projectUrl.git")
                developerConnection.set("scm:git:$projectUrl.git")
                url.set(projectUrl)
            }
        }
    }

    // Signs only when a key is given (see README), so local builds and publishToMavenLocal need none.
    val signingKey = providers.gradleProperty("signingKey")
    extensions.configure<SigningExtension> {
        isRequired = signingKey.isPresent
        if (signingKey.isPresent) {
            useInMemoryPgpKeys(signingKey.get(), providers.gradleProperty("signingPassword").orNull)
            sign(publishing.publications)
        }
    }
}
