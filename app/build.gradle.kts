
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 支持从命令行参数注入版本号（GitHub Actions Release 工作流使用）
// 例：./gradlew assembleRelease -PversionName=1.2.3 -PversionCode=10203
val ciVersionName: String = findProperty("versionName")?.toString() ?: "1.0.0"
val ciVersionCode: Int = findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1

android {
    namespace = "com.pengjunlong.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.pengjunlong.aichat"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = ciVersionCode
        versionName = ciVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── 可通过 BuildConfig 访问的配置常量 ──────────────────────────────
        // API 服务地址（本项目为 WebView 聚合，无需后端 API，占位保留）
        buildConfigField("String", "API_BASE_URL", "\"https://api.example.com/\"")
        // GitHub 仓库信息（检查更新用）
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"pengjunlong\"")
        buildConfigField("String", "UPDATE_REPO_NAME",  "\"ai_chat\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // ── APK 输出命名：自动从 strings.xml 读取 app_name ─────────────────────────
    // Debug  → {app_name}-debug.apk
    // Release → {app_name}-release.apk
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            // 读取 app/src/main/res/values/strings.xml 中的 app_name
            val stringsXml = file("src/main/res/values/strings.xml")
            val appName = if (stringsXml.exists()) {
                val content = stringsXml.readText()
                Regex("<string name=\"app_name\">(.*?)</string>").find(content)?.groupValues?.get(1) ?: "app"
            } else "app"
            output.outputFileName = "${appName}-${variant.buildType.name.lowercase()}.apk"
        }
    }
}

dependencies {
    // 框架模块
    implementation(project(":framework-core"))
    implementation(project(":framework-crash"))
    implementation(project(":framework-logger"))
    implementation(project(":framework-network"))   // 检查更新功能依赖
    implementation(project(":framework-storage"))
    implementation(project(":framework-ui"))

    // Material（底部导航栏 BottomNavigationView、FAB、进度条等）
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

