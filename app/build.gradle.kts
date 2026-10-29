plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  id("com.chaquo.python")
}

val baseVersion = "1.3"
val buildCode = project.findProperty("BUILD_NUMBER")?.toString()?.toIntOrNull() ?: 1
val computedVersion = project.findProperty("VERSION_NAME")?.toString() ?: "$baseVersion.$buildCode"

android {
    namespace = "com.tgserver.streamer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.tgserver.streamer"
        minSdk = 24
        targetSdk = 36
        versionCode = buildCode
        versionName = computedVersion
    }

    flavorDimensions += "abi"
    productFlavors {
        create("arm64") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
        create("armv7") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("armeabi-v7a")
            }
        }
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
    }

    signingConfigs {
        create("shared") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Server, Networking & UI
  implementation(libs.nanohttpd)
  implementation(libs.okhttp)
  implementation(libs.gson)
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pyc {
            src = true
            pip = true
            stdlib = true
        }
        val venv311 = rootProject.file("venv311/bin/python")
        val venvPython = rootProject.file("venv/bin/python")
        if (venv311.exists()) {
            buildPython(venv311.absolutePath)
        } else if (venvPython.exists()) {
            buildPython(venvPython.absolutePath)
        } else {
            buildPython("python3")
        }
        pip {
            install(rootProject.file("app/wheels/pyaes-1.6.1-py3-none-any.whl").absolutePath)
            install(rootProject.file("app/wheels/PySocks-1.7.1-py3-none-any.whl").absolutePath)
            install(rootProject.file("app/wheels/Pyrogram-2.0.106-py3-none-any.whl").absolutePath)
            install("tgcrypto")
        }
    }
}
