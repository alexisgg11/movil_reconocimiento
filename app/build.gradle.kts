plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val startLocalServices = tasks.register<Exec>("startLocalServices") {
    group = "application"
    description = "Prepara PostgreSQL y la API de Reconocimiento."
    commandLine("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", rootProject.file("scripts/start-local-services.ps1").absolutePath)
    val enabled = providers.gradleProperty("START_LOCAL_SERVICES").orNull != "false"
    onlyIf { enabled }
    notCompatibleWithConfigurationCache("Arranca servicios locales externos.")
}
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(startLocalServices) }

android {
    namespace = "com.example.reconocimiento"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.reconocimiento"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources { noCompress += "tflite" }
}

dependencies {
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.exif)
    coreLibraryDesugaring(libs.android.desugar)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.google.mlkit.face)
    implementation(libs.google.litert)
    implementation(libs.google.location)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
