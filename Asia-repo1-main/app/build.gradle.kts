plugins {
    alias(libs.plugins.android.application)
    // Idinagdag ang Google Services Plugin para gumana ang Firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.myapplication"

    // Naka-set sa 36 para sa compatibility sa androidx.core 1.18.0
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24

        // Naka-set sa 35 para manatili sa stable runtime behavior
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Siguraduhin na ang NDK 27 ay naka-install sa iyong SDK Manager
        ndkVersion = "27.0.12077973"
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

    // Importante para sa 16 KB Alignment fix ng Mapbox
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.play.services.location)

    // Siguraduhin na Mapbox 11.3.0+ ang gamit sa libs.versions.toml
    implementation(libs.mapbox.maps)

    // ==============================================
    // Mga Firebase dependencies
    // ==============================================
    // Firebase BoM — pamamahala ng bersyon ng lahat ng Firebase library
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))

    // Firebase Analytics — kasama bilang simula
    implementation("com.google.firebase:firebase-analytics")

    // ✅ IDINAGDAG ITO — Para sa Realtime Database (kinakailangan para sa pagsubaybay ng lokasyon)
    implementation("com.google.firebase:firebase-database")

    // Iba pang posibleng gamitin:
    // implementation("com.google.firebase:firebase-auth")       -> Para sa pag-login
    // implementation("com.google.firebase:firebase-firestore")  -> Para sa database
    // implementation("com.google.firebase:firebase-storage")   -> Para sa pag-imbak ng files
    // ==============================================

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}