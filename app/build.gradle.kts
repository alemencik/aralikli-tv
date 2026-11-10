plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingKeystore = System.getenv("ANDROID_SIGNING_KEYSTORE_PATH")
val signingStorePassword = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")

android {
    signingConfigs {
        if (!signingKeystore.isNullOrBlank() && !signingStorePassword.isNullOrBlank()
            && !signingKeyAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
            create("stable") {
                storeFile = file(signingKeystore)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
    }
    namespace = "com.daylak.araliklitv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daylak.araliklitv"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
}
