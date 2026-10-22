plugins {
    id("com.android.application")
}

android {
    namespace = "h.Hchat"
    compileSdk = 37

    defaultConfig {
        applicationId = "h.Hchat.r2system"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "r2-system-probe"
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

}

dependencies {
    implementation("io.github.billywei01:fastkv:3.0.1")
}
