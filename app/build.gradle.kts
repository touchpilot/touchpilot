plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
    namespace = "dev.touchpilot.app"
    compileSdk = 35
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "dev.touchpilot.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
