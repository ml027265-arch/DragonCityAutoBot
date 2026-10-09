plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.dragoncity.autobot"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.dragoncity.autobot"
  minSdk = 30
  targetSdk = 35
  versionCode = 1
  versionName = "0.4-alpha"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
}