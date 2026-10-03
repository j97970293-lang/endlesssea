// Endless Sea — demo extension: Internet Archive (public-domain films)
// Built as a normal application module; the produced APK IS the .esx container:
//     cp app/build/outputs/apk/release/app-release.apk archiveorg-1.0.0.esx
// (docs/en/04 §2.1 — classes.dex + assets/extension.json inside the same zip)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.endlesssea.extension.archiveorg"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.endlesssea.extension.archiveorg"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug { isMinifyEnabled = false }
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
    // Provided by the host app at runtime — never bundle the API into the .esx.
    compileOnly(project(":extensions-api"))
}
