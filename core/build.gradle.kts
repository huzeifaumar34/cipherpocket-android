plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.huzeifaumar34.cipherpocket.core"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCP_OPENSSL_ROOT=${rootProject.projectDir}/third_party/openssl/build"
                )
                cppFlags += listOf("-std=c++17", "-fvisibility=hidden")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
