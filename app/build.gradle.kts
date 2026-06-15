plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.aistudio.nexvoice"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.nexvoice.vsmrpx"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
        ?: "${rootDir}/my-upload-key.jks"

      val storeFileRef = file(keystorePath)

      // SAFE CHECK (prevent crash if file missing)
      if (storeFileRef.exists()) {
        storeFile = storeFileRef
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }

    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false

      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )

      // SAFE SIGNING (fallback to debug if missing)
      signingConfig = if (signingConfigs["release"].storeFile?.exists() == true) {
        signingConfigs["release"]
      } else {
        signingConfigs["debugConfig"]
      }
    }

    debug {
      signingConfig = signingConfigs["debugConfig"]
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

// Secrets plugin
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)

  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)

  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)

  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
