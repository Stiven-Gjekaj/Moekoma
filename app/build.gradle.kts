plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.stivengjekaj.moekoma"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.stivengjekaj.moekoma"
        minSdk = 26
        targetSdk = 37
        versionCode = 22
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key comes from environment variables. GitHub Actions sets
    // them from secrets. A local release build without them is not signed.
    val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":mihon"))
    implementation(project(":tsuki"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    // The covers and the pages of the sources come with the network client of the app.
    implementation(libs.coil.network.okhttp)
    // libarchive reads ZIP, RAR, RAR5, 7z, and TAR. Apache License 2.0, with libarchive under the BSD License.
    implementation(libs.libarchive)
    // Scans the folders that the app watches, also when the app is closed.
    implementation(libs.work.runtime)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    // Runs the Accessibility Test Framework on a screen, as the accessibility audit of Xcode does in Asabunko.
    androidTestImplementation(libs.androidx.compose.ui.test.accessibility)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
