plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
    namespace = "dev.touchpilot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.touchpilot.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // Development-only: make release APKs installable on emulators.
            // Replace this with a real release key before any public release.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.json:json:20250517")
    testImplementation(kotlin("test"))
    testImplementation("org.robolectric:robolectric:4.14.1")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
