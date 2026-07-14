import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 签名配置：优先读取项目根目录的 keystore.properties（已被 .gitignore 忽略），
// 其次回退到环境变量。密钥与密码绝不入库。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val hasSigning = signingValue("storeFile", "KEYSTORE_FILE") != null

android {
    namespace = "com.meituan.onetap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.meituan.onetap"
        minSdk = 26
        targetSdk = 34
        versionCode = 271
        versionName = "2.7.1"
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "meituan-bike-reminder-v${versionName}.apk"
        }
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(signingValue("storeFile", "KEYSTORE_FILE")!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 仅当提供了签名信息时才使用 release 签名，否则由 Gradle 走默认（未签名）流程，
            // 避免在未配置密钥的环境（如 CI 无 Secret）下构建失败。
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
