// Endless Sea — :app (Compose UI · MVVM · Hilt)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.endlesssea.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.endlesssea.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.3.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // Signature debug FIXE (keystore public dédié au debug — aucun secret de production)
    // → les mises à jour GitHub s'installent SANS désinstaller l'ancienne version.
    signingConfigs {
        named("debug") {
            storeFile = rootProject.file("keystore/debug.p12")
            storePassword = "endlesssea"
            keyAlias = "endlesssea-debug"
            keyPassword = "endlesssea"
            storeType = "PKCS12"
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true   // java.time on API 26+ (doc 11)
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }

    packaging {
        resources { excludes += "META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":extensions-api"))
    implementation(project(":extensions-loader"))
    implementation(project(":downloader"))
    implementation(project(":player"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // icons-extended : icônes riches (Explore, Extension, PlayListPlay…).
    // Le volume de classes n'est un problème que pour D8 local en mémoire restreinte ;
    // la CI (16 Go) le digère sans peine — et R8 élaguera au release.
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.workmanager.ktx)

    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)

    implementation(libs.io.coil)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // HLS/DASH + Media3 session for background local playback
    implementation(libs.bundles.media3)

    testImplementation(libs.test.junit)
    testImplementation(libs.test.truth)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
