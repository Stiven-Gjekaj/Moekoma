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
    // The JavaScript sources, such as the LNReader plugins, run in QuickJS. The JVM and Android each take their own build.
    api(libs.quickjs.kt)
    implementation(libs.kotlinx.coroutines.core)
    // The Aidoku sources are WebAssembly. Chicory runs them in Java, and its html module uses jsoup.
    implementation(libs.chicory.runtime)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    // The tests make small sources from the text form of WebAssembly.
    testImplementation(libs.chicory.wabt)
}

// The live tests read real sources from the web. They run only with MOEKOMA_LIVE=1.
tasks.test {
    environment("MOEKOMA_LIVE", System.getenv("MOEKOMA_LIVE") ?: "0")
    testLogging { showStandardStreams = true }
}
