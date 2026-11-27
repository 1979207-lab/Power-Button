plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.powerbutton.pro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.powerbutton.pro"
        minSdk = 26
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
    }
}
