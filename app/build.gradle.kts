plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStore = providers.environmentVariable("OCEAN_SIGNING_STORE").orNull

android {
    namespace = "com.oceanhunter.hunter"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.oceanhunter.hunter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        if (signingStore != null) create("oceanRelease") {
            storeFile = file(signingStore)
            storePassword = providers.environmentVariable("OCEAN_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("OCEAN_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("OCEAN_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.findByName("oceanRelease") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation:1.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation("junit:junit:4.13.2")
}
