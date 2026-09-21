plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "com.hassansourcing.safe"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.hassansourcing.safe"
    minSdk = 24
    targetSdk = 35
    versionCode = 23
    versionName = "2.3"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.15.0") }
