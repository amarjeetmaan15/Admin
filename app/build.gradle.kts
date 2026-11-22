plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}
val appId = providers.gradleProperty("APP_APPLICATION_ID")
    .orElse(providers.environmentVariable("APP_APPLICATION_ID"))
    .orNull
if (appId.isNullOrBlank() || !appId.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
    throw GradleException("Set APP_APPLICATION_ID to the package name chosen by the owner. It is intentionally not pre-filled.")
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
    buildTypes { release { isMinifyEnabled = false } }
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
