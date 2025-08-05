plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gmailish"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.gmailish"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    //OkHttp for HTTP requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0");

    //Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.13.1");
    implementation("com.github.bumptech.glide:glide:4.15.1");
    annotationProcessor ("com.github.bumptech.glide:compiler:4.15.1");

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}