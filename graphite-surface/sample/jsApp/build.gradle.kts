import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
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
        "jsDevelopmentExecutableCompileSync",
        "jsBrowserDevelopmentWebpack",
    )
}.configureEach {
    dependsOn(localSkikoRuntimeTask)
}

val copyLocalSkikoRuntime = tasks.register<Copy>("copyLocalSkikoRuntime") {
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
    into(rootProject.layout.buildDirectory.dir("js/packages/$kotlinPackageName/kotlin"))
}

copyLocalSkikoRuntime.configure {
    mustRunAfter("jsDevelopmentExecutableCompileSync")
}

tasks.named("jsBrowserDevelopmentWebpack") {
    dependsOn(copyLocalSkikoRuntime)
}
