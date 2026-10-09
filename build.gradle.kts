import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

// Local Maven repository the TestKit test app resolves the plugin, processor and runtime from.
val testRepo = layout.buildDirectory.dir("test-repo")

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
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

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "testRepo"
                url = uri(testRepo)
            }
        }
    }
}
