description = "Runtime of NotNullForK: reads Request Bodies through their Mirrors and reports missing Required Fields per field."

dependencies {
    // Provided by the consumer's Spring Boot app.
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.webmvc)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jakarta.servlet.api)

    // Mirrors carry Jakarta constraints, and the Required Field check needs a Bean Validation provider.
    api(libs.jakarta.validation.api)
    implementation(libs.spring.boot.starter.validation)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(libs.assertj)
                implementation(platform(libs.spring.boot.dependencies))
                implementation(libs.jackson.databind)
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
            }
        }
    }
}
