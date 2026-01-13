plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hearing.hearingtest"
    compileSdk = 35

    // Optional: lock NDK version for reproducible native builds.
    // Uncomment and set to the NDK version you have installed, e.g.:
    // ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = "com.hearing.hearingtest"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        // If you want a per-ABI set here (optional). Usually better under
        // defaultConfig.ndk or in splits if publishing.
        // ndk {
        //     abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        // }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }


    // Optional: restrict ABIs for native build (helps reduce APK size).
    // Remove or change if you need more ABIs.
    splits {
        // keep as-is if you don't use apk splits; otherwise configure splits here.
    }

    // You can also add packagingOptions or other android settings here if needed.
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.7.2")

    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.7")
    implementation("dev.shreyaspatil:capturable:1.0.3")

    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    val nav_version = "2.7.7" // Use the latest version
    implementation("androidx.navigation:navigation-compose:$nav_version")
    implementation("androidx.datastore:datastore-preferences:1.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")

}

