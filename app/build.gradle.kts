plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.routepulse.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.routepulse.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled=  true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.firebase.auth)
    implementation(libs.google.id)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.fragment)
    implementation(libs.android.lottie)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation (libs.google.maps.services) // Routes API client
    implementation (libs.okhttp)// For HTTP requests
    implementation (libs.play.services.maps.v1820) // Maps SDK
    implementation (libs.play.services.location) // Location services
    implementation(libs.picasso)
    implementation(libs.androidx.lifecycle.runtime)
    implementation (libs.google.places.v330) // or latest


    implementation (libs.slf4j.nop)
    implementation(libs.androidx.lifecycle.process)
    implementation ("com.google.android.gms:play-services-auth:21.3.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation (libs.android.maps.utils)
}
