plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val cpVersionName = providers.gradleProperty("cpVersionName").orElse("0.1.0-dev")
val cpVersionCode = providers.gradleProperty("cpVersionCode").orElse("1")
val cpKeyStorePath = System.getenv("CP_KEYSTORE_PATH")

android {
    namespace = "io.github.huzeifaumar34.cipherpocket"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    buildToolsVersion = "37.0.0"

    if (!cpKeyStorePath.isNullOrBlank()) {
        signingConfigs {
            create("cpRelease") {
                storeFile = file(cpKeyStorePath)
                storePassword = System.getenv("CP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CP_KEY_ALIAS")
                keyPassword = System.getenv("CP_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.huzeifaumar34.cipherpocket"
        minSdk = 26
        targetSdk = 36
        versionCode = cpVersionCode.get().toInt()
        versionName = cpVersionName.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!cpKeyStorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("cpRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/AL2.0", "/META-INF/LGPL2.1")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
