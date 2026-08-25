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

    signingConfigs {
        create("release") {
            val sp = localProperties
            // local.properties paths are relative to the gradle root (android/).
            storeFile = sp.getProperty("signing.keystore")?.let { rootProject.file(it) }
            storePassword = sp.getProperty("signing.storePassword", "")
            keyAlias = sp.getProperty("signing.keyAlias", "")
            keyPassword = sp.getProperty("signing.keyPassword", "")
        }
    }

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
            signingConfig = signingConfigs.getByName("release")
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
    // Amap navi SDK: maven.amap.com is permanently unavailable (2026-08). Fetch the merged
    // 3DMap+Navi+Search+Location jar + native libs with scripts/fetch-amap-sdk.ps1(.sh);
    // artifacts live in libs/ and jniLibs/ (gitignored).
    implementation(files("libs/amap-all.jar"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
