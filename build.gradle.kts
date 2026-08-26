import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

val verifyGraphiteSurfaceBoundary = tasks.register("verifyGraphiteSurfaceBoundary") {
    val adapterDirectory = layout.projectDirectory.dir("graphite-surface/graphite-surface").asFile
    val adapterFiles = fileTree(adapterDirectory) {
        include("build.gradle.kts")
        include("src/**/*.kt")
        include("src/**/*.kts")
        exclude("build/**")
    }
    inputs.files(adapterFiles)

    doLast {
        val forbiddenTokens = listOf(
            "org.jetbrains.skiko",
            "org.jetbrains.skia",
            "libs.engine.skiko",
            "libs.skiko",
            "skiko-graphite",
        )
        val violations = adapterFiles.files.flatMap { file ->
            val contents = file.readText()
            forbiddenTokens.filter(contents::contains).map { token ->
                "${file.relativeTo(rootDir)} contains $token"
            }
        }
        check(violations.isEmpty()) {
            "GraphiteSurface Compose adapter leaked engine-only dependencies:\n${violations.joinToString("\n")}"
        }
    }
}

project(":graphite-surface").afterEvaluate {
    tasks.named("check") {
        dependsOn(verifyGraphiteSurfaceBoundary)
    }
}

project(":graphite-surface") {
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

    val stageGraphiteWebRuntime = tasks.register<Sync>("stageGraphiteWebRuntime") {
        dependsOn(localSkikoRuntimeTask, localSkikoGraphiteImportsTask, localSkikoGraphiteLinkTask)
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
        into(layout.buildDirectory.dir("graphite-web-runtime"))
    }

    configurations.create("graphiteWebRuntimeElements") {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named("graphite-web-runtime"))
        }
        outgoing.artifact(stageGraphiteWebRuntime.map { it.destinationDir }) {
            builtBy(stageGraphiteWebRuntime)
        }
    }
}
