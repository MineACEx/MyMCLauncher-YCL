// ============================================================
// YCL启动器 — App 模块构建配置
// 适配 Android 16 (API 36)，仅 arm64-v8a
// ============================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mymc.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mymc.launcher"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 仅保留 arm64-v8a 架构
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // ============================================================
    // Release 使用 debug 签名（用户自行重签名为正式版）
    // ============================================================
    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true   // 开启 R8 混淆
            isShrinkResources = true // 开启资源压缩
            // Release 使用 debug 签名（用户自行重签名）
            signingConfig = signingConfigs.getByName("debug")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 编译选项
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 打包排除项
    packaging {
        resources {
            // 排除重复文件
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
        // 仅保留 arm64 原生库
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Kotlin 编译器选项（Kotlin 2.4+ 新 DSL，替代已废弃的 kotlinOptions）
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // ============================================================
    // AndroidX 核心
    // ============================================================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ============================================================
    // Jetpack Compose (BOM 统一版本)
    // ============================================================
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // ============================================================
    // Navigation 导航
    // ============================================================
    implementation(libs.navigation.compose)

    // ============================================================
    // 网络层
    // ============================================================
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // ============================================================
    // 图片加载
    // ============================================================
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ============================================================
    // 文件处理
    // ============================================================
    implementation(libs.zip4j)
    implementation(libs.commons.compress)
    implementation(libs.commons.io)

    // ============================================================
    // 数据存储
    // ============================================================
    implementation(libs.datastore.preferences)

    // ============================================================
    // 协程
    // ============================================================
    implementation(libs.kotlinx.coroutines.android)

    // ============================================================
    // 测试
    // ============================================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
}