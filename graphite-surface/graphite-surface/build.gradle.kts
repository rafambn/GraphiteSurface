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

    js {
        browser()
        binaries.executable()
    }
    wasmJs {
        browser()
        binaries.executable()
    }
    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.kotlinx.coroutines.core)
            api(libs.scribe)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.foundation)
        }

        jvmMain.dependencies {
            implementation(project(":graphite-engine"))
        }

        androidMain.dependencies {
            implementation(project(":graphite-engine"))
        }

        jsMain.dependencies {
            implementation(project(":graphite-engine"))
        }

        wasmJsMain.dependencies {
            implementation(project(":graphite-engine"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    val engineApiDirectory = rootProject.file("graphite-surface/graphite-engine/src/iosMain/cinterop")

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops {
            create("graphiteEngine") {
                defFile(project.file("src/iosMain/cinterop/engine.def"))
                compilerOpts("-I${engineApiDirectory.absolutePath}")
            }
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
