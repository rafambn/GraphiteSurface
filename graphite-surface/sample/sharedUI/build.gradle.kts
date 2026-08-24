@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.gradle.api.tasks.Copy
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

    js {
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":graphite-surface"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
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

// Compose's published web artifacts carry the stock Skiko runtime. The sample
// intentionally replaces that runtime with the local fork that contains the
// Dawn Graphite bridge and the WebGPU-enabled Emscripten module.
val localSkikoRuntimeTask = gradle.includedBuild("skiko").task(":skikoWasmJar")
val localSkikoGraphiteImportsTask = gradle.includedBuild("skiko").task(":skiko-graphite:compileKotlinWasmJs")
val localSkikoGraphiteLinkTask = gradle.includedBuild("skiko").task(":skiko-graphite:linkWasm")
val localSkikoRuntimeJar = rootProject.file(
    "skiko-fork/skiko/skiko/build/libs/skiko-wasm-0.0.0-SNAPSHOT.jar",
)
val localSkikoGraphiteImports = rootProject.file(
    "skiko-fork/skiko/skiko/skiko-graphite/build/imports",
)
val localSkikoGraphiteWasm = rootProject.file(
    "skiko-fork/skiko/skiko/skiko-graphite/build/out/link/Release-wasm-es6-wasm/skiko-graphite.unoptimized.wasm",
)
val graphiteRenderWorker = rootProject.file(
    "graphite-surface/graphite-engine/src/webMain/resources/graphite-render-worker.mjs",
)

configurations.configureEach {
    exclude(group = "org.jetbrains.skiko", module = "skiko-js-wasm-runtime")
    exclude(group = "org.jetbrains.skiko", module = "skiko-js-runtime")
}

tasks.matching { task ->
    task.name in setOf(
        "jsDevelopmentExecutableCompileSync",
        "wasmJsDevelopmentExecutableCompileSync",
        "jsBrowserDevelopmentWebpack",
        "wasmJsBrowserDevelopmentWebpack",
    )
}.configureEach {
    dependsOn(localSkikoRuntimeTask)
}

val copyLocalSkikoRuntimeForJs by tasks.registering(Copy::class) {
    dependsOn(localSkikoRuntimeTask, localSkikoGraphiteImportsTask, localSkikoGraphiteLinkTask)
    outputs.upToDateWhen { false }
    from(zipTree(localSkikoRuntimeJar)) {
        include("js-skiko-reexport-symbols.mjs", "skiko.mjs", "skiko.wasm")
    }
    from(localSkikoGraphiteImports) {
        include("js-skiko-graphite-reexport-symbols.mjs", "skiko-graphite.mjs")
    }
    from(localSkikoGraphiteWasm) {
        rename { "skiko-graphite.wasm" }
    }
    from(graphiteRenderWorker)
    into(rootProject.layout.buildDirectory.dir("js/packages/GraphiteSurface-sample-sharedUI/kotlin"))
}

val copyLocalSkikoRuntimeForWasm by tasks.registering(Copy::class) {
    dependsOn(localSkikoRuntimeTask, localSkikoGraphiteImportsTask, localSkikoGraphiteLinkTask)
    outputs.upToDateWhen { false }
    from(zipTree(localSkikoRuntimeJar)) {
        include("skiko.mjs", "skiko.wasm")
    }
    from(localSkikoGraphiteImports) {
        include("skiko-graphite.mjs")
    }
    from(localSkikoGraphiteWasm) {
        rename { "skiko-graphite.wasm" }
    }
    from(graphiteRenderWorker)
    into(rootProject.layout.buildDirectory.dir("wasm/packages/GraphiteSurface-sample-sharedUI/kotlin"))
}

copyLocalSkikoRuntimeForJs.configure {
    mustRunAfter("jsDevelopmentExecutableCompileSync")
}

copyLocalSkikoRuntimeForWasm.configure {
    mustRunAfter("wasmJsDevelopmentExecutableCompileSync")
}

tasks.named("jsBrowserDevelopmentWebpack") {
    dependsOn(copyLocalSkikoRuntimeForJs)
}

tasks.named("wasmJsBrowserDevelopmentWebpack") {
    dependsOn(copyLocalSkikoRuntimeForWasm)
}
