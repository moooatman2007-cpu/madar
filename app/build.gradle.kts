import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

fun parseEnvFile(file: File): Map<String, String> {
  val envMap = mutableMapOf<String, String>()
  if (file.exists() && file.isFile) {
    for (line in file.readLines()) {
      val trimmed = line.trim()
      if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
        val splitIndex = trimmed.indexOf('=')
        if (splitIndex != -1) {
          val key = trimmed.substring(0, splitIndex).trim()
          val value = trimmed.substring(splitIndex + 1).trim().trim('"', '\'')
          if (key.isNotEmpty()) {
            envMap[key] = value
          }
        }
      }
    }
  }
  return envMap
}

val rootEnvFile = File(rootDir, ".env")
val rootEnvMap = parseEnvFile(rootEnvFile)

fun resolveConfigProperty(key: String, envMap: Map<String, String> = rootEnvMap): String {
  val systemEnv = System.getenv(key)
  if (!systemEnv.isNullOrBlank()) {
    return systemEnv.trim()
  }
  return envMap[key]?.trim() ?: ""
}

fun validateSupabaseConfig(
  supabaseUrl: String,
  supabaseKey: String,
  supabaseEdgeUrl: String,
  hasEnvFile: Boolean
): List<String> {
  val errors = mutableListOf<String>()

  if (!hasEnvFile && System.getenv("SUPABASE_URL").isNullOrBlank()) {
    errors.add("Missing root '.env' file. Production Release builds require a real '.env' file or environment variables containing Production Supabase configuration.")
  }

  if (supabaseUrl.isBlank()) {
    errors.add("Missing or empty 'SUPABASE_URL' (neither set in environment variables nor in root .env).")
  } else if (supabaseUrl.contains("placeholder", ignoreCase = true)) {
    errors.add("'SUPABASE_URL' contains placeholder value.")
  } else if (!supabaseUrl.startsWith("https://") && !supabaseUrl.startsWith("http://")) {
    errors.add("'SUPABASE_URL' must start with 'https://'.")
  }

  if (supabaseKey.isBlank()) {
    errors.add("Missing or empty 'SUPABASE_PUBLISHABLE_KEY' (neither set in environment variables nor in root .env).")
  } else if (supabaseKey.contains("placeholder", ignoreCase = true)) {
    errors.add("'SUPABASE_PUBLISHABLE_KEY' contains placeholder value.")
  }

  if (supabaseEdgeUrl.isBlank()) {
    errors.add("Missing or empty 'SUPABASE_EDGE_FUNCTION_URL' (neither set in environment variables nor in root .env).")
  } else if (supabaseEdgeUrl.contains("placeholder", ignoreCase = true)) {
    errors.add("'SUPABASE_EDGE_FUNCTION_URL' contains placeholder value.")
  }

  return errors
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.studentmanager.xpseeg"
    minSdk = 24
    targetSdk = 36
    // CI sets VERSION_CODE (must increase on every Play upload)
    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val resolvedSupabaseUrl = resolveConfigProperty("SUPABASE_URL")
    val resolvedSupabasePublishableKey = resolveConfigProperty("SUPABASE_PUBLISHABLE_KEY")
    val resolvedSupabaseEdgeFunctionUrl = resolveConfigProperty("SUPABASE_EDGE_FUNCTION_URL")

    buildConfigField("String", "SUPABASE_URL", "\"$resolvedSupabaseUrl\"")
    buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$resolvedSupabasePublishableKey\"")
    buildConfigField("String", "SUPABASE_EDGE_FUNCTION_URL", "\"$resolvedSupabaseEdgeFunctionUrl\"")
  }


  signingConfigs {
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    val releaseStorePassword = System.getenv("STORE_PASSWORD") ?: ""
    val releaseKeyAlias = System.getenv("KEY_ALIAS") ?: "upload"
    val releaseKeyPassword = System.getenv("KEY_PASSWORD") ?: releaseStorePassword

    create("release") {
      storeFile = file(releaseKeystorePath)
      storePassword = releaseStorePassword
      keyAlias = releaseKeyAlias
      keyPassword = releaseKeyPassword
    }
    // Optional: only used when a shared debug.keystore exists (it is git-ignored).
    // Otherwise AGP falls back to the default ~/.android/debug.keystore.
    create("debugConfig") {
      val sharedDebugKeystore = file("${rootDir}/debug.keystore")
      if (sharedDebugKeystore.exists()) {
        storeFile = sharedDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // STRICT: Release builds MUST ONLY use the production release signing configuration.
      // Under NO circumstances is debugConfig allowed as a fallback.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val debugConfig = signingConfigs.getByName("debugConfig")
      if (debugConfig.storeFile != null) signingConfig = debugConfig
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
      isReturnDefaultValues = true
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}




val verifyReleasePreconditions = tasks.register("verifyReleasePreconditions") {
  description = "Validates that all production release preconditions (signing keystore, passwords, and .env Supabase credentials) are properly satisfied."
  group = "verification"
  val rootDirPath = rootDir.absolutePath
  doFirst {
    val errors = mutableListOf<String>()

    // 1. Keystore file check
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "$rootDirPath/my-upload-key.jks"
    val keystoreFile = File(releaseKeystorePath)
    if (!keystoreFile.exists()) {
      errors.add("Production Keystore file missing at: ${keystoreFile.absolutePath} (Set KEYSTORE_PATH env var or place 'my-upload-key.jks' in root directory).")
    }

    // 2. STORE_PASSWORD check
    val storePassword = System.getenv("STORE_PASSWORD")
    if (storePassword.isNullOrBlank()) {
      errors.add("Missing or empty 'STORE_PASSWORD' environment variable.")
    }

    // 3. KEY_ALIAS check
    val keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
    if (keyAlias.isBlank()) {
      errors.add("Missing or empty 'KEY_ALIAS' environment variable.")
    }

    // 4. KEY_PASSWORD check
    val keyPassword = System.getenv("KEY_PASSWORD") ?: storePassword
    if (keyPassword.isNullOrBlank()) {
      errors.add("Missing or empty 'KEY_PASSWORD' environment variable.")
    }

    // 5. Supabase configuration check
    // Uses the exact same parser and priority resolution logic as buildConfigField
    val currentEnvFile = File(rootDirPath, ".env")
    val currentEnvMap = parseEnvFile(currentEnvFile)

    val currentSupabaseUrl = resolveConfigProperty("SUPABASE_URL", currentEnvMap)
    val currentSupabaseKey = resolveConfigProperty("SUPABASE_PUBLISHABLE_KEY", currentEnvMap)
    val currentSupabaseEdgeUrl = resolveConfigProperty("SUPABASE_EDGE_FUNCTION_URL", currentEnvMap)

    errors.addAll(
      validateSupabaseConfig(
        supabaseUrl = currentSupabaseUrl,
        supabaseKey = currentSupabaseKey,
        supabaseEdgeUrl = currentSupabaseEdgeUrl,
        hasEnvFile = currentEnvFile.exists() && currentEnvFile.isFile
      )
    )

    if (errors.isNotEmpty()) {
      val errorMessage = buildString {
        appendLine()
        appendLine("==================================================================================")
        appendLine("❌ PRODUCTION BUILD SAFETY GATE FAILED:")
        appendLine("==================================================================================")
        errors.forEachIndexed { index, err ->
          appendLine("  [${index + 1}] $err")
        }
        appendLine("==================================================================================")
        appendLine("Release build cannot proceed with missing or invalid production credentials.")
        appendLine("Please provide the required keystore and .env configurations before building release.")
        appendLine("==================================================================================")
      }
      throw GradleException(errorMessage)
    }
  }
}

tasks.matching {
  it.name in listOf(
    "preReleaseBuild",
    "bundleRelease",
    "assembleRelease",
    "packageReleaseBundle",
    "signReleaseBundle",
    "validateSigningRelease"
  )
}.configureEach {
  dependsOn(verifyReleasePreconditions)
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.zxing.core)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.auth)
  implementation(libs.supabase.storage)
  implementation(libs.ktor.client.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.work.testing)
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
}
