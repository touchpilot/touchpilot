plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
    namespace = "dev.touchpilot.app"
    compileSdk = 35
    ndkVersion = "27.3.13750724"

    signingConfigs {
        create("release") {
            val keystorePath = providers.environmentVariable("TOUCHPILOT_RELEASE_KEYSTORE").orNull
            val keystorePassword = providers.environmentVariable("TOUCHPILOT_RELEASE_KEYSTORE_PASSWORD").orNull
            val keyAlias = providers.environmentVariable("TOUCHPILOT_RELEASE_KEY_ALIAS").orNull
            val keyPassword = providers.environmentVariable("TOUCHPILOT_RELEASE_KEY_PASSWORD").orNull
            if (
                !keystorePath.isNullOrBlank() &&
                !keystorePassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank() &&
                !keyPassword.isNullOrBlank()
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "dev.touchpilot.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = if (signingConfigs.findByName("release")?.storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                // Development-only fallback: make release APKs installable on
                // emulators when release secrets are not configured.
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    testImplementation("org.json:json:20250517")
    testImplementation(kotlin("test"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.register<Test>("localModelEval") {
    group = "verification"
    description = "Runs the committed local model evaluation fixture suite."

    val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
    classpath = debugUnitTest.classpath
    testClassesDirs = debugUnitTest.testClassesDirs
    filter {
        includeTestsMatching("dev.touchpilot.app.eval.LocalModelEvalRunnerTest")
    }
    dependsOn("compileDebugUnitTestKotlin", "processDebugUnitTestJavaRes")
}
