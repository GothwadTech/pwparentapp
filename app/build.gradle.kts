plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

// ---------------------------------------------------------------------------
// Template configuration — every app.* value comes from gradle.properties, so a
// new app is rebranded from that one block instead of hunting strings in code.
// TARGET_URL comes from .env via the secrets plugin (see .env.example).
// ---------------------------------------------------------------------------
fun templateProp(name: String, fallback: String): String =
  (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: fallback

val appName = templateProp("app.name", "Parent App")
val appId = templateProp("app.id", "com.pw.parent")
val appVersionCode = templateProp("app.versionCode", "1").toIntOrNull() ?: 1
val appVersionName = templateProp("app.versionName", "1.0.0")
val jsBridgeName = templateProp("app.jsBridgeName", "ParentApp")
val notificationChannelId = templateProp("app.notificationChannelId", "pw_parent_notifications")
val notificationChannelName = templateProp("app.notificationChannelName", "Parent App Notifications")
val notificationChannelDescription =
  templateProp("app.notificationChannelDescription", "Updates and important alerts for your child from the app")
val prefsName = templateProp("app.prefsName", "parent_app_prefs")

android {
  namespace = "com.pw.parent"
  compileSdk = 35

  defaultConfig {
    applicationId = appId
    minSdk = 23
    targetSdk = 35
    // CI passes -PversionCode / -PversionName; those win over gradle.properties.
    // Without reading them here every release shipped as 1.0.0 / code 1, which
    // makes a second Play Store upload impossible.
    versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: appVersionCode
    versionName = (project.findProperty("versionName") as String?)?.takeIf { it.isNotBlank() } ?: appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Kitchen sink of the template config, exposed to Kotlin as BuildConfig.*
    resValue("string", "app_name", appName)
    buildConfigField("String", "APP_NAME", "\"$appName\"")
    buildConfigField("String", "JS_BRIDGE_NAME", "\"$jsBridgeName\"")
    buildConfigField("String", "NOTIFICATION_CHANNEL_ID", "\"$notificationChannelId\"")
    buildConfigField("String", "NOTIFICATION_CHANNEL_NAME", "\"$notificationChannelName\"")
    buildConfigField("String", "NOTIFICATION_CHANNEL_DESCRIPTION", "\"$notificationChannelDescription\"")
    buildConfigField("String", "PREFS_NAME", "\"$prefsName\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystoreFile = file(keystorePath)
      if (keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseSigning = signingConfigs.getByName("release")
      if (releaseSigning.storeFile != null) {
        signingConfig = releaseSigning
      }
    }
    // Debug builds use AGP's default debug keystore (~/.android/debug.keystore), so no
    // checked-in debug.keystore is needed locally or in CI.
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    // AGP 9 disables resValues by default too; the launcher label is generated from
    // app.name via resValue(), so the feature has to be switched on explicitly.
    resValues = true
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging)
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}



