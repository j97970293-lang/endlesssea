// Endless Sea — :player (swappable player backend; Media3 default)
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.endlesssea.player"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }
}

dependencies {
    api(project(":extensions-api"))
    implementation(project(":core"))
    implementation(libs.bundles.media3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.squareup.okhttp)

    // Tests unitaires JVM (décalage des sous-titres, niveaux d'upscaling) :
    // exécutés par la CI (`:player:testDebugUnitTest`).
    testImplementation(libs.test.junit)
    testImplementation(libs.test.truth)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
