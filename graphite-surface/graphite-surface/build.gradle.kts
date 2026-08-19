@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops {
            create("graphiteEngine") {
                defFile(project.file("src/iosMain/cinterop/graphite_engine.def"))
                compilerOpts("-I${project.file("src/iosMain/cinterop").absolutePath}")
            }
            create("engineBridge") {
                defFile(project.file("src/iosMain/cinterop/engine_bridge.def"))
                compilerOpts("-I${project.file("../../sample/iosApp/iosApp").absolutePath}")
            }
        }
    }
}

group = "com.rafambn"
version = "0.1.0-SNAPSHOT"
