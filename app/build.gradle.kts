plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun signingProperty(name: String): String? {
    return (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name)
}

val releaseKeystorePath = signingProperty("TOUCHPILOT_KEYSTORE_PATH")
val releaseKeystorePassword = signingProperty("TOUCHPILOT_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingProperty("TOUCHPILOT_KEY_ALIAS")
val releaseKeyPassword = signingProperty("TOUCHPILOT_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

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

    signingConfigs {
        if (hasReleaseSigning) {
            create("releaseSigned") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("releaseSigned")
            } else {
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
    testImplementation("junit:junit:4.13.2")
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
