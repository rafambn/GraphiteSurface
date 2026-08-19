plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.rafambn.graphitesurface.sample.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rafambn.graphitesurface.sample.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":sample:sharedUI"))
    implementation(libs.androidx.activity)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
}
