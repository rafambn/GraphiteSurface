@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    js {
        browser()
    }
    wasmJs {
        browser()
    }
    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.engine.skiko)
            implementation(libs.engine.skiko.graphite)
        }

        webMain.dependencies {
            implementation(libs.kotlinx.browser)
        }

        jsMain.dependencies {
            implementation(libs.engine.skiko)
            implementation(libs.engine.skiko.graphite)
        }

        wasmJsMain.dependencies {
            implementation(libs.engine.skiko)
            implementation(libs.engine.skiko.graphite)
        }

        iosMain.dependencies {
            implementation(libs.engine.skiko)
            implementation(libs.engine.skiko.graphite)
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops {
            create("gsTypes") {
                defFile(project.file("src/iosMain/cinterop/gs_types.def"))
                compilerOpts("-I${project.file("src/iosMain/cinterop").absolutePath}")
            }
        }

        binaries.framework {
            baseName = "GraphiteEngine"
            isStatic = false
            binaryOptions["bundleId"] = "com.rafambn.graphitesurface.engine"
            linkerOpts("-Wl,-dead_strip")
        }
    }
}

group = "com.rafambn"
version = "0.1.0-SNAPSHOT"
