plugins {
    id("com.android.application")
}

android {
    namespace = "h.Hchat.keepalivesystemtest"
    compileSdk = 37

    defaultConfig {
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "caller"
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
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    sourceSets.getByName("main").aidl.srcDir("${rootDir}/../../../app/src/main/aidl")
}

dependencies {}
