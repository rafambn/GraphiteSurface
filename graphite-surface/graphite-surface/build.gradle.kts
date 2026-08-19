@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.rafambn.graphitesurface"
        compileSdk = 37
        minSdk = 24
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    js { browser() }
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
        }

        iosMain.dependencies {
            implementation(libs.skiko)
            implementation(libs.skiko.graphite)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "GraphiteSurface"
            isStatic = true
        }
    }
}

group = "com.rafambn"
version = "0.1.0-SNAPSHOT"
