import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "h.Hchat"
    compileSdk = 37

    val releaseVersionCode = providers.gradleProperty("HCAT_VERSION_CODE").get().toInt()
    val releaseVersionName = providers.gradleProperty("HCAT_VERSION_NAME").get()

    defaultConfig {
        applicationId = "h.Hchat"
        minSdk = 27
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    val signingProperties = mapOf(
        "HCAT_KEYSTORE_PATH" to providers.environmentVariable("HCAT_KEYSTORE_PATH")
            .orElse(providers.gradleProperty("HCAT_KEYSTORE_PATH")).orNull,
        "HCAT_STORE_PASSWORD" to providers.environmentVariable("HCAT_STORE_PASSWORD")
            .orElse(providers.gradleProperty("HCAT_STORE_PASSWORD")).orNull,
        "HCAT_KEY_ALIAS" to providers.environmentVariable("HCAT_KEY_ALIAS")
            .orElse(providers.gradleProperty("HCAT_KEY_ALIAS")).orNull,
        "HCAT_KEY_PASSWORD" to providers.environmentVariable("HCAT_KEY_PASSWORD")
            .orElse(providers.gradleProperty("HCAT_KEY_PASSWORD")).orNull
    )
    val missingSigningProperties = signingProperties.filterValues { it.isNullOrBlank() }.keys
    val signingStoreFile = signingProperties["HCAT_KEYSTORE_PATH"]
        ?.let(::file)
        ?: file("/Users/muchen/Downloads/Android App正式签名密钥/MUCHEN.jks")
    val releaseSigningReady = missingSigningProperties.isEmpty() && signingStoreFile.isFile

    if (releaseSigningReady) {
        signingConfigs {
            create("release") {
                storeFile = signingStoreFile
                storePassword = signingProperties.getValue("HCAT_STORE_PASSWORD")
                keyAlias = signingProperties.getValue("HCAT_KEY_ALIAS")
                keyPassword = signingProperties.getValue("HCAT_KEY_PASSWORD")
            }
        }
    }

    val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
        doLast {
            val missing = missingSigningProperties.toMutableList()
            if (!signingStoreFile.isFile) missing += "HCAT_KEYSTORE_PATH (Keystore 文件不存在)"
            check(missing.isEmpty()) {
                "Release 构建缺少有效签名配置：${missing.joinToString(", ")}。"
            }
        }
    }
    tasks.configureEach {
        if (name in setOf("packageRelease", "assembleRelease", "bundleRelease")) {
            dependsOn(verifyReleaseSigning)
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseSigningReady) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
                )
            }
        }
    }

    sourceSets.getByName("test").java.directories.add(rootProject.file("scripts/tests/api102").absolutePath)

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
            excludes += "META-INF/*.version"
            excludes += "META-INF/com/android/build/gradle/app-metadata.properties"
            excludes += "META-INF/version-control-info.textproto"
            excludes += "META-INF/**/LICENSE"
            excludes += "META-INF/**/LICENSE.txt"
            excludes += "META-INF/**/NOTICE"
            excludes += "META-INF/**/NOTICE.txt"
            excludes += "kotlin/**"
            excludes += "kotlin-tooling-metadata.json"
            excludes += "frameworks/android/*.apk"
            excludes += "android/attrs.xml"
            excludes += "android/attrs_manifest.xml"
            excludes += "android/res-map.txt"
            excludes += "clst/core.jcst"
            excludes += "export/**"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.billywei01:fastkv:3.0.1")
    implementation(files("libs/dexkit-2.2.0-76551eb-idle.aar"))
    implementation("dev.rikka.ndk.thirdparty:cxx:1.2.0")
    implementation("com.google.flatbuffers:flatbuffers-java:23.5.26")
    implementation("com.highcapable.kavaref:kavaref-core:1.1.0")
    implementation("com.jakewharton.android.repackaged:dalvik-dx:16.0.1")
    implementation("com.linkedin.dexmaker:dexmaker:2.28.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")
    implementation("androidx.lifecycle:lifecycle-runtime:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation("androidx.navigationevent:navigationevent:1.1.2")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation(files("libs/miuix-ui-android-0.9.4.aar"))
    implementation(files("libs/miuix-core-android-0.9.4.aar"))
    implementation(files("libs/miuix-squircle-android-0.9.4.aar"))
    implementation(files("libs/miuix-preference-android-0.9.4.aar"))
    implementation(files("libs/miuix-icons-android-0.9.4.aar"))
    implementation(files("libs/miuix-nav-android-0.9.4.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("io.github.libxposed:api:102.0.0")
    implementation(files("libs/miuix-blur-android-0.9.4.aar"))
    implementation(files("libs/miuix-shader-android-0.9.4.aar"))
}

// 构建完成后自动复制 APK 到 dist/
tasks.register<Copy>("copyToDist") {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename {
        providers.environmentVariable("HCAT_APK_NAME")
            .orElse("Hchat-${providers.gradleProperty("HCAT_VERSION_NAME").get()}-release-signed.apk")
            .get()
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("copyToDist")
}

tasks.matching { it.name == "optimizeReleaseResources" }.configureEach {
    doLast {
        val optimizeDir = layout.buildDirectory
            .dir("intermediates/optimized_processed_res/release/optimizeReleaseResources")
            .get()
            .asFile
        val expected = optimizeDir.resolve("resources-release-optimize.ap_")
        if (!expected.isFile) {
            val fallback = layout.buildDirectory
                .file("intermediates/shrunk_resources_binary_format/release/convertShrunkResourcesToBinaryRelease/shrunk-resources-binary-format-release.ap_")
                .get()
                .asFile
            if (fallback.isFile) {
                fallback.copyTo(expected, overwrite = true)
            }
        }
    }
}
