plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is driven by environment variables so that CI (or your own
// shell) can sign without a keystore ever being committed to the repository.
// When they are absent the release build is simply left unsigned.
val releaseStoreFile: String? = System.getenv("RELEASE_STORE_FILE")

android {
    namespace = "io.github.g00dtw0.shortsautoscroll"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.g00dtw0.shortsautoscroll"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
