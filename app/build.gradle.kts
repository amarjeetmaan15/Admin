plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}
val appId = providers.gradleProperty("APP_APPLICATION_ID")
    .orElse(providers.environmentVariable("APP_APPLICATION_ID"))
    .orElse("com.admin")
    .get()
if (!appId.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
    throw GradleException("APP_APPLICATION_ID must be a valid Android package name, for example com.admin.")
}
android {
    namespace = "privateadmin.internal"
    compileSdk = 35
    defaultConfig {
        applicationId = appId
        minSdk = 23
        targetSdk = 35
        versionCode = (project.findProperty("appVersionCode")?.toString()?.toIntOrNull() ?: 1)
        versionName = "1.0"
    }
    val keyPath = System.getenv("TESTING_KEYSTORE_PATH")
    val keyFile = keyPath?.let { file(it) }?.takeIf { it.exists() }
    if (keyFile != null) {
        signingConfigs.create("ownerKey") {
            storeFile = keyFile
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("debug") { if (keyFile != null) signingConfig = signingConfigs.getByName("ownerKey") }
        getByName("release") { isMinifyEnabled = false; if (keyFile != null) signingConfig = signingConfigs.getByName("ownerKey") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
