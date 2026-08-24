@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.rafambn.graphitesurface.sample.sharedui"
        compileSdk = 37
        minSdk = 24
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }

    js { browser() }
    wasmJs { browser() }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":graphite-surface"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jsMain.dependencies {
            implementation(files(rootProject.file("skiko-fork/skiko/skiko/build/libs/skiko-wasm-0.0.0-SNAPSHOT.jar")))
        }

        wasmJsMain.dependencies {
            implementation(files(rootProject.file("skiko-fork/skiko/skiko/build/libs/skiko-wasm-0.0.0-SNAPSHOT.jar")))
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        val swiftPlatform = if (name.startsWith("iosSimulator")) "iphonesimulator" else "iphoneos"
        val developerDirectory = (System.getenv("DEVELOPER_DIR")
            ?.let(::File)
            ?: File("/Applications/Xcode.app/Contents/Developer"))
            .let { directory ->
                if (directory.name == "Xcode.app") directory.resolve("Contents/Developer") else directory
            }

        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOptions["bundleId"] = "com.rafambn.graphitesurface.sample"
            val engineFrameworkDirectory = rootProject.file(
                "graphite-surface/graphite-engine/build/bin/$name/${buildType.name.lowercase()}Framework",
            )
            linkerOpts("-F${engineFrameworkDirectory.absolutePath}", "-framework", "GraphiteEngine")
            linkerOpts(
                "-L${developerDirectory.resolve("Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$swiftPlatform")}",
            )
        }

        tasks.matching { task ->
            task.name.startsWith("link") && task.name.contains("FrameworkIos")
        }.configureEach {
            val buildType = if (name.startsWith("linkRelease")) "Release" else "Debug"
            val targetName = name.substringAfter("Framework")
            dependsOn(":graphite-engine:link${buildType}Framework$targetName")
        }
    }
}
