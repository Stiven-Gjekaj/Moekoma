// The parts of the app that do not need a screen. They build and test on the JVM
// with Gradle, so a test does not need an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The app runs this code on Android, which takes Java 17 byte code.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // The index of a repository of Mihon extensions is in Protocol Buffers.
    implementation(libs.kotlinx.serialization.protobuf)
    testImplementation(libs.junit)
}

// The live tests read real sources from the web. They run only with MOEKOMA_LIVE=1.
tasks.test {
    environment("MOEKOMA_LIVE", System.getenv("MOEKOMA_LIVE") ?: "0")
    testLogging { showStandardStreams = true }
}
