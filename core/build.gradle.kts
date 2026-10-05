// Endless Sea — :core (shared app-side models, network plumbing, utils)
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.endlesssea.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.logging)
    implementation(libs.squareup.okhttp.urlconnection) // JavaNetCookieJar (CAPTCHA/cookies WebView)
    implementation(libs.squareup.okhttp.dnsoverhttps) // §9 : DNS sur HTTPS avec repli système

    testImplementation(libs.test.junit)
    testImplementation(libs.test.truth)
    testImplementation(libs.test.kotlinx.coroutines)
    testImplementation(libs.squareup.mockwebserver)
}
