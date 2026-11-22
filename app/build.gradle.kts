
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aistudio.mailseptian.kxqzmr"
        minSdk = 24
        targetSdk = 36
        versionCode = 2245
        versionName = "MSFR_RATNA_2245"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing configuration
    signingConfigs {
        create("release") {
            val keystorePath =
                System.getenv("KEYSTORE_PATH")
                    ?: "${rootDir}/my-upload-key.jks"

            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }

        // Use Android's default debug signing configuration.
        getByName("debug") {
            // Gradle automatically manages the debug keystore.
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true

            buildConfigField(
                "boolean",
                "ENABLE_LOGS",
                "false"
            )

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            versionNameSuffix = "-debug"
            isMinifyEnabled = false

            buildConfigField(
                "boolean",
                "ENABLE_LOGS",
                "true"
            )

            signingConfig = signingConfigs.getByName("debug")
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

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

// Configure Secrets Gradle Plugin
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
    ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

// Allow build without google-services.json
googleServices {
    missingGoogleServicesStrategy =
        MissingGoogleServicesStrategy.WARN
}

// Dependencies
dependencies {
    implementation(platform(libs.androidx.compose.bom))

    // AndroidX
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    // Android Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)

    // Debug
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
