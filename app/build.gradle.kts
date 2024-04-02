plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.cuberite.android"
    compileSdk = 34
    defaultConfig {
        applicationId = "org.cuberite.android"
        resourceConfigurations += listOf("en", "de", "nl", "pt", "zh_CN")
        minSdk = 21
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 15
        versionName = "1.6.3"
        vectorDrawables.useSupportLibrary = true
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        //...
        compose = true
    }
    composeOptions {
        // For support for Kotlin-2.0.0-Beta5
        kotlinCompilerExtensionVersion = "1.5.11-dev-k2.0.0-Beta5-b5a216d0ac6"
    }

}

dependencies {

    val composeBom = platform("androidx.compose:compose-bom:2024.03.00")

    // Compose
    implementation(composeBom)
    implementation("androidx.compose.runtime:runtime")
    // Should be removed once migration to Kotlin-Flows in done
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("com.google.android.material:material:1.10.0")
    implementation("org.ini4j:ini4j:0.5.4")
}
