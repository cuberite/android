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
        minSdk = 16
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
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
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
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("com.google.android.material:material:1.10.0")
    implementation("org.ini4j:ini4j:0.5.4")
}
