dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
