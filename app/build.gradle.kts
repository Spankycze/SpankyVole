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
        versionCode = 40
        versionName = "0.4.0"
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
