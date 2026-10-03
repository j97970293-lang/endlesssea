// Endless Sea — :extensions-loader (repos, .esx installation, DEX loading, JSON engine)
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.endlesssea.extensions.loader"
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
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.squareup.okhttp)
    implementation(libs.org.jsoup)          // declarative JSON providers use jsoup selectors
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.test.junit)
    testImplementation(libs.test.truth)
    testImplementation(libs.test.kotlinx.coroutines)
    testImplementation(libs.squareup.mockwebserver)
}
