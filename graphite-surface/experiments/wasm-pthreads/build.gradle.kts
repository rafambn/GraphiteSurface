@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "pthreadExperiment.js"
            }
        }
        binaries.executable()
    }

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "pthreadExperiment.js"
            }
        }
        binaries.executable()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        webMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}

val localSkikoRuntimeTask = gradle.includedBuild("skiko").task(":skikoWasmJar")
val localSkikoGraphiteImportsTask =
    gradle.includedBuild("skiko").task(":skiko-graphite:compileKotlinWasmJs")
val localSkikoGraphiteLinkTask =
    gradle.includedBuild("skiko").task(":skiko-graphite:linkWasm")
val localSkikoRuntimeJar = rootProject.file(
    "skiko-fork/skiko/skiko/build/libs/skiko-wasm-0.0.0-SNAPSHOT.jar",
)
val localSkikoGraphiteImports = rootProject.file(
    "skiko-fork/skiko/skiko/skiko-graphite/build/imports",
)
val localSkikoGraphiteWasm = rootProject.file(
    "skiko-fork/skiko/skiko/skiko-graphite/build/out/link/Release-wasm-es6-wasm/" +
        "skiko-graphite.unoptimized.wasm",
)

fun Copy.configureRuntimeCopy(outputDirectory: String) {
    dependsOn(localSkikoRuntimeTask, localSkikoGraphiteImportsTask, localSkikoGraphiteLinkTask)
    outputs.upToDateWhen { false }
    from(zipTree(localSkikoRuntimeJar)) {
        include("skiko.mjs", "skiko.wasm", "*.worker.mjs", "*.worker.js")
    }
    from(localSkikoGraphiteImports) {
        include("skiko-graphite.mjs")
    }
    from(localSkikoGraphiteWasm) {
        rename { "skiko-graphite.wasm" }
    }
    into(rootProject.layout.buildDirectory.dir(outputDirectory))
}

val copyRuntimeForJs by tasks.registering(Copy::class) {
    configureRuntimeCopy(
        "js/packages/GraphiteSurface-experiments-wasm-pthreads/kotlin",
    )
}

val copyRuntimeForWasm by tasks.registering(Copy::class) {
    configureRuntimeCopy(
        "wasm/packages/GraphiteSurface-experiments-wasm-pthreads/kotlin",
    )
}

copyRuntimeForJs.configure {
    mustRunAfter("jsDevelopmentExecutableCompileSync")
}

copyRuntimeForWasm.configure {
    mustRunAfter("wasmJsDevelopmentExecutableCompileSync")
}

tasks.named("jsBrowserDevelopmentWebpack") {
    dependsOn(copyRuntimeForJs)
}

tasks.named("wasmJsBrowserDevelopmentWebpack") {
    dependsOn(copyRuntimeForWasm)
}

val stageJsPthreadExperiment by tasks.registering(Sync::class) {
    dependsOn("jsBrowserDevelopmentWebpack")
    from(
        rootProject.layout.buildDirectory.dir(
            "js/packages/GraphiteSurface-experiments-wasm-pthreads/kotlin",
        ),
    )
    from(layout.buildDirectory.dir("kotlin-webpack/js/developmentExecutable"))
    into(layout.buildDirectory.dir("pthreadExperiment/js"))
}

val stageWasmPthreadExperiment by tasks.registering(Sync::class) {
    dependsOn("wasmJsBrowserDevelopmentWebpack")
    from(
        rootProject.layout.buildDirectory.dir(
            "wasm/packages/GraphiteSurface-experiments-wasm-pthreads/kotlin",
        ),
    )
    from(layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable"))
    into(layout.buildDirectory.dir("pthreadExperiment/wasm"))
}

group = "com.rafambn"
version = "0.1.0-SNAPSHOT"
