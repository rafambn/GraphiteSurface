@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

val engineApiDirectory = project(":graphite-engine").projectDir.resolve("src/iosMain/cinterop")

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
        binaries.library()
    }
    wasmJs {
        browser()
        binaries.library()
    }
    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )
    macosArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.runtime)
            api(libs.ui)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.foundation)
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

    iosTargets.forEach { target ->
        target.compilations.getByName("main").cinterops {
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

tasks.withType<CInteropProcess>().configureEach {
    inputs.dir(engineApiDirectory)
}

val localSkikoRuntimeTask = gradle.includedBuild("skiko").task(":skikoWasmJar")
val localSkikoGraphiteImportsTask =
    gradle.includedBuild("skiko").task(":skiko-graphite:compileKotlinWasmJs")
val localSkikoGraphiteLinkTask =
    gradle.includedBuild("skiko").task(":skiko-graphite:linkWasm")
val graphiteSurfaceRoot = projectDir.parentFile.parentFile
val localSkikoRoot = graphiteSurfaceRoot.resolve("skiko-fork/skiko/skiko")
val localSkikoRuntimeJar =
    localSkikoRoot.resolve("build/libs/skiko-wasm-0.0.0-SNAPSHOT.jar")
val localSkikoGraphiteImports = localSkikoRoot.resolve("skiko-graphite/build/imports")
val localSkikoGraphiteWasm = localSkikoRoot.resolve(
    "skiko-graphite/build/out/link/Release-wasm-es6-wasm/skiko-graphite.unoptimized.wasm",
)
val graphiteRenderWorker = projectDir.resolve(
    "../graphite-engine/src/webMain/resources/graphite-render-worker.mjs",
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

group = "com.rafambn"
version = "0.1.0-SNAPSHOT"
