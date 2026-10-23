// Tsuki, the API of the Kotatsu parsers, from UsagiApp/Tsuki at the tag 1.0.5, under the GPL 3 (see NOTICE).
// The parser plugin links to these classes by name, so the packages and the names stay.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tsuki"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-proguard.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=tsuki.InternalParsersApi",
        )
    }
}

dependencies {
    // The parsers use these libraries of the app, so each one is "api".
    api(libs.okhttp)
    api(libs.jsoup)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.collection)
    api(libs.androidx.annotation)
}
