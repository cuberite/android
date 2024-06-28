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

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    // Compose
    implementation(composeBom)
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.ini4j:ini4j:0.5.4")
}
