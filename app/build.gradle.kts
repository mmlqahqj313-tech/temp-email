plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStoreFile = System.getenv("TEMP_EMAIL_KEYSTORE_PATH")
val signingStorePassword = System.getenv("TEMP_EMAIL_KEYSTORE_PASSWORD")
val signingKeyAlias = System.getenv("TEMP_EMAIL_KEY_ALIAS")
val signingKeyPassword = System.getenv("TEMP_EMAIL_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword
).all { !it.isNullOrBlank() }

val versionCodeValue = providers.gradleProperty("APP_VERSION_CODE").orNull?.toIntOrNull() ?: 1
val versionNameValue = providers.gradleProperty("APP_VERSION_NAME").orNull ?: "1.0.0"

android {
    namespace = "com.tempinbox.privateinbox"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tempinbox.privateinbox"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeValue
        versionName = versionNameValue
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storeType = "PKCS12"
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                // Multiple APK signature schemes improve compatibility with Android installers.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep debug builds separate from the published package so a debug-signed
            // APK can never block installation of the release APK due to a signature mismatch.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
