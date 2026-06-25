plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.meituan.onetap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.meituan.onetap"
        minSdk = 26
        targetSdk = 34
        versionCode = 240
        versionName = "2.4.0"
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "meituan-bike-reminder-v${versionName}.apk"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../../release.keystore")
            storePassword = "meituan123"
            keyAlias = "meituan_bike"
            keyPassword = "meituan123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
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
