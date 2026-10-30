// AGP 9 compiles Kotlin itself (built-in Kotlin), so the
// org.jetbrains.kotlin.android plugin is not applied here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.startupassistant"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.startupassistant"
        // ML Kit GenAI agents need API 34+; AppFunctions need API 36+ to be
        // called by the Gemini app. Older devices get the plain UI only.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    // Target Java 17 bytecode with whatever JDK runs Gradle, so the build
    // doesn't require a separately installed JDK 17 toolchain.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Collects every @AppFunctionDeclaration in the app into the generated
    // service and XML metadata.
    arg("appfunctions:aggregateAppFunctions", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.graphics.shapes)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    // On-device Gemini Nano: agent loop and rewriting
    implementation(libs.mlkit.genai.agent)
    implementation(libs.mlkit.genai.rewriting)

    // Hybrid Cloud Grounding: Firebase AI Logic (Google Search Grounding)
    implementation(libs.firebase.ai)

    // AppFunctions: tools for the in-app agent and for the Gemini app
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
