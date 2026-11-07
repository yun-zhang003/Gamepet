plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.skypet.pet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skypet.pet"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

// JDK17 toolchain.
// NOTE (per CONTRACT §2.1/§2.5): this machine only has JDK21 and no Android SDK,
// so whether the JDK17 toolchain can be resolved here is UNVERIFIED. The actual
// compile level is pinned by compileOptions(JavaVersion.VERSION_17) + jvmTarget="17".
// Do not lower these to 21 — README requires JDK17 compatibility.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Tests: intentionally omitted (not required by the contract's minimal set).
}
