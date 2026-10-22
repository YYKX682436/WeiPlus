plugins {
    id("com.android.application")
}

android {
    namespace = "h.Hchat"
    compileSdk = 37

    defaultConfig {
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += listOf("caller", "visibility")
    productFlavors {
        create("official") {
            dimension = "caller"
            applicationId = "com.tencent.mm"
        }
        create("modified") {
            dimension = "caller"
            applicationId = "com.tencent.mm.wx"
        }
        create("attacker") {
            dimension = "caller"
            applicationId = "com.example.keepalive.attacker"
        }
        create("visible") {
            dimension = "visibility"
        }
        create("hidden") {
            dimension = "visibility"
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }

    sourceSets.getByName("main").aidl.srcDir("${rootDir}/../../../app/src/main/aidl")
}

dependencies {
    implementation("io.github.billywei01:fastkv:3.0.1")
}
