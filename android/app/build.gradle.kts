// app/build.gradle.kts
//
// What this file does (plain English):
// This is the main recipe for building the Gmail OTP Syncer app.
// It answers: what Android version does it support? What features does it use?
// What libraries does it need?
//
// Key decisions explained:
//   minSdk 26 (Android 8.0):  Required by EncryptedSharedPreferences, which we
//                              use to securely store the API key. Anything below
//                              API 26 doesn't support it.
//   Compose enabled:           We use Jetpack Compose (not XML layouts) for the UI.
//   composeOptions.kotlinCompilerExtensionVersion:
//                              The Compose compiler must match the Kotlin version
//                              (Kotlin 1.9.23 ↔ Compose Compiler 1.5.13).
//
// Library groups:
//   Compose BOM — "Bill of Materials": one import that pins all Compose library
//                 versions to a known-working combination. No version conflicts.
//   Security    — EncryptedSharedPreferences for storing the API key securely.
//   Networking  — Retrofit + OkHttp for calling the backend REST API.
//   WorkManager — Runs background sync even when the app is closed.
//   Browser     — Chrome Custom Tabs for OAuth (NOT a WebView — CCT keeps the
//                 user's saved passwords and security indicators, WebView doesn't).

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    kotlin("kapt")
}

android {
    namespace  = "com.mailsync.app"
    compileSdk = 34

    defaultConfig {
        applicationId  = "com.mailsync.app"
        minSdk         = 26    // EncryptedSharedPreferences requires API 26+
        targetSdk      = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("mailsync-release-key.jks")
            storePassword = "mailsync123"
            keyAlias = "mailsync"
            keyPassword = "mailsync123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled   = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose     = true
        buildConfig = true   // Lets us access BuildConfig.DEBUG in code
    }

    composeOptions {
        // This must match the Kotlin version declared in the root build.gradle.kts.
        // Kotlin 1.9.23 → Compose Compiler 1.5.13
        // See: https://developer.android.com/jetpack/androidx/releases/compose-kotlin
        kotlinCompilerExtensionVersion = "1.5.13"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    // ── Compose BOM ───────────────────────────────────────────────────────────
    // One import pins all Compose library versions to a tested combination.
    // Never add individual Compose version numbers alongside the BOM.
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)

    // ── Core AndroidX ─────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    // ── Compose UI ────────────────────────────────────────────────────────────
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // ── Material 3 ────────────────────────────────────────────────────────────
    // Material Design 3: the "modern" Material — not Material 2 ("M2").
    // Provides ThemedSurface, dynamic color, tokens-based theming.
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── ViewModel + State ─────────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // ── Security — EncryptedSharedPreferences ─────────────────────────────────
    // Stores the API key and server URL encrypted on-device.
    // Requires minSdk 26 (hence our minSdk setting).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── Networking ────────────────────────────────────────────────────────────
    // Retrofit: type-safe HTTP client. Turns interface functions into API calls.
    // OkHttp: the underlying HTTP engine. Handles connections, timeouts, caching.
    // Gson converter: converts JSON responses to Kotlin data classes.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // ── WorkManager ───────────────────────────────────────────────────────────
    // Schedules reliable background work that survives app closes, reboots,
    // and battery optimization. The right tool for periodic sync tasks.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ── Google Sign-In & Gmail API ────────────────────────────────────────────
    // Native Android Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.1.1")
    
    // Google API Client for Android
    implementation("com.google.api-client:google-api-client-android:1.33.0")
    
    // Gmail API
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-1.32.1") {
        exclude(group = "org.apache.httpcomponents")
    }

    // ── Autofill ─────────────────────────────────────────────────────────────
    implementation("androidx.autofill:autofill:1.1.0")

    // ── DataStore ─────────────────────────────────────────────────────────────
    // Async, Kotlin-native alternative to SharedPreferences for non-sensitive
    // settings (poll interval, theme preference). Sensitive data (API key,
    // server URL) stays in EncryptedSharedPreferences.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Splash Screen ─────────────────────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ── Debug tooling ─────────────────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // ── AppAuth (Chrome Custom Tabs OAuth) ────────────────────────────────────
    // AppAuth removed because backend handles the callback natively with HTML

    // ── Firebase ──────────────────────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-database")


    // ── QR Scanner (CameraX + ML Kit) ─────────────────────────────────────────
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // ── Local Database (Room) ─────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // ── SQLCipher (Database Encryption) ───────────────────────────────────────
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // ── Biometric Authentication ──────────────────────────────────────────────
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
}
