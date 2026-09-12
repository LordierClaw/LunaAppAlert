plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.lordierclaw.fixture"
    compileSdk { version = release(37) }
    buildFeatures { resValues = true }
    defaultConfig {
        applicationId = "dev.lordierclaw.fixture"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    flavorDimensions += "target"
    productFlavors {
        create("alpha") {
            dimension = "target"
            applicationIdSuffix = ".alpha"
            resValue("string", "app_name", "Ứng dụng thử A")
        }
        create("beta") {
            dimension = "target"
            applicationIdSuffix = ".beta"
            resValue("string", "app_name", "Ứng dụng thử B")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
