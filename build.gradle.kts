plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.kotlin.plugin.scripting") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.10" // Adding Kotlin serialization plugin
}

group = "org.deveshm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral() // This will provide the libraries from Maven Central
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test") // For unit tests
    implementation(kotlin("script-runtime")) // Required for script runtime

    // Adding OkHttp for making HTTP requests
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    // Adding Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Adding Kotlin Serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    implementation("org.apache.kafka:kafka-clients:3.3.1")
}

tasks.test {
    useJUnitPlatform()
}
