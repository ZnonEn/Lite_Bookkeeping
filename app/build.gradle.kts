plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nonen.Bookkeeping"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nonen.Bookkeeping"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2pre"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// 显式钉住本机 JDK，防止依赖元数据（ML Kit）把工具链需求抬到 Java 25 触发联网下载
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 「抓取调试」模块：主源集的空实现（CaptureDebug.kt / CaptureDebugCard.kt）在 test 分支的
// src/debug 源集里有真实实现（CaptureDebugImpl.kt / CaptureDebugCardImpl.kt，同名类不同文件名）。
// Kotlin 源集没有 Java 那样的同路径去重，debug 构建时需显式排除主源集的空实现让位；
// 实现文件不存在（main 分支）时条件不成立，配置零生效。
if (file("src/debug/java/com/nonen/Bookkeeping/debug/CaptureDebugImpl.kt").exists()) {
    tasks.matching { it.name == "compileDebugKotlin" }.configureEach {
        (this as org.gradle.api.tasks.util.PatternFilterable).exclude(
            "com/nonen/Bookkeeping/debug/CaptureDebug.kt",
            "com/nonen/Bookkeeping/debug/CaptureDebugCard.kt",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.commons.csv)
    implementation(libs.mlkit.text.recognition.chinese)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
