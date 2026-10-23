// The source API of Mihon, so that the Mihon extensions run in Moekoma. The code comes from Mihon
// under the Apache License 2.0 (see LICENSE and NOTICE in this folder). The packages and the names stay
// as in Mihon, because the extensions call them by name.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.source"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-proguard.pro")
    }
}

dependencies {
    // The extensions use these libraries of the app, so each one is "api".
    api(libs.okhttp)
    api(libs.okhttp.brotli)
    api(libs.okhttp.zstd)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.json.okio)
    api(libs.kotlinx.serialization.protobuf)
    api(libs.kotlinx.coroutines.core)
    api(libs.injekt)
    api(libs.rxjava)
    api(libs.jsoup)
    api(libs.androidx.preference)
    implementation(libs.androidx.core.ktx)
}
