// Standalone template — not part of the Endless Sea settings.gradle.
// Copy this directory, then: ./gradlew assembleRelease && cp the apk to name-version.esx
plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.1.0"
}

android {
    namespace = "org.example.mysource"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.example.mysource"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release { isMinifyEnabled = true }
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
    // Provided by the host app. Do not package the API inside the .esx.
    compileOnly("dev.endlesssea:extensions-api:1.0.0")
}
