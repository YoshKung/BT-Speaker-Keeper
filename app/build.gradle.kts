plugins {
    id("com.android.application")
}

android {
    namespace = "com.btspeakerkeeper.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.btspeakerkeeper.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    testImplementation("junit:junit:4.13.2")
}
