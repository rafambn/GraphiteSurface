@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":sample:sharedUI"))
            implementation(libs.compose.ui)
        }
    }
}

// Compose's published web artifacts carry the stock Skiko runtime. The sample
// replaces it with the local fork containing the Dawn Graphite bridge.
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
val graphiteRenderWorker = rootProject.file(
    "graphite-surface/graphite-engine/src/webMain/resources/graphite-render-worker.mjs",
)
val kotlinPackageName = "${rootProject.name}${project.path.replace(':', '-')}"

configurations.configureEach {
    exclude(group = "org.jetbrains.skiko", module = "skiko-js-wasm-runtime")
    exclude(group = "org.jetbrains.skiko", module = "skiko-js-runtime")
}

tasks.matching { task ->
    task.name in setOf(
        "wasmJsDevelopmentExecutableCompileSync",
        "wasmJsBrowserDevelopmentWebpack",
    )
}.configureEach {
    dependsOn(localSkikoRuntimeTask)
}

val copyLocalSkikoRuntime = tasks.register<Copy>("copyLocalSkikoRuntime") {
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
    into(rootProject.layout.buildDirectory.dir("wasm/packages/$kotlinPackageName/kotlin"))
}

copyLocalSkikoRuntime.configure {
    mustRunAfter("wasmJsDevelopmentExecutableCompileSync")
}

tasks.named("wasmJsBrowserDevelopmentWebpack") {
    dependsOn(copyLocalSkikoRuntime)
}
