import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.qmxz.pilotbot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.qmxz.pilotbot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["AMAP_KEY"] = localProperties.getProperty("amap.api.key", "")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.amap.navi._3dmap)
    implementation(libs.amap.location)
}
