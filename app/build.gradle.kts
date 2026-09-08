plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cz.spanky.hclimate"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.spanky.hclimate"
        minSdk = 26
        targetSdk = 35
        versionCode = 50
        versionName = "0.5.0-modern"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
