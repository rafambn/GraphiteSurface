import java.util.Properties

rootProject.name = "GraphiteSurface"

val rootLocalProperties = Properties().apply {
    val propertiesFile = file("local.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use(::load)
}
rootLocalProperties.getProperty("sdk.dir")?.let { sdkDirectory ->
    if (System.getProperty("android.home") == null) {
        System.setProperty("android.home", sdkDirectory)
    }
}

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
    }
}

includeBuild("skiko-fork/skiko/skiko") {
    dependencySubstitution {
        substitute(module("org.jetbrains.skiko:skiko")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-js")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-wasm-js")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-graphite")).using(project(":skiko-graphite"))
        substitute(module("org.jetbrains.skiko:skiko-graphite-js")).using(project(":skiko-graphite"))
        substitute(module("org.jetbrains.skiko:skiko-graphite-wasm-js")).using(project(":skiko-graphite"))
        substitute(module("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-graphite-awt-runtime-macos-arm64")).using(project(":skiko-graphite"))
        substitute(module("org.jetbrains.skiko:skiko-awt-runtime-macos-x64")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-graphite-awt-runtime-macos-x64")).using(project(":skiko-graphite"))
        substitute(module("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-graphite-awt-runtime-linux-arm64")).using(project(":skiko-graphite"))
        substitute(module("org.jetbrains.skiko:skiko-awt-runtime-linux-x64")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-graphite-awt-runtime-linux-x64")).using(project(":skiko-graphite"))
        substitute(module("org.jetbrains.skiko:skiko-awt-runtime-windows-arm64")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-graphite-awt-runtime-windows-arm64")).using(project(":skiko-graphite"))
        substitute(module("org.jetbrains.skiko:skiko-awt-runtime-windows-x64")).using(project(":"))
        substitute(module("org.jetbrains.skiko:skiko-graphite-awt-runtime-windows-x64")).using(project(":skiko-graphite"))
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
    }
}

include(":graphite-surface")
project(":graphite-surface").projectDir = file("graphite-surface/graphite-surface")

include(":graphite-engine")
project(":graphite-engine").projectDir = file("graphite-surface/graphite-engine")

include(":sample")
project(":sample").projectDir = file("graphite-surface/sample")

include(":sample:sharedUI")
project(":sample:sharedUI").projectDir = file("graphite-surface/sample/sharedUI")

include(":sample:desktopApp")
project(":sample:desktopApp").projectDir = file("graphite-surface/sample/desktopApp")

include(":sample:androidApp")
project(":sample:androidApp").projectDir = file("graphite-surface/sample/androidApp")

include(":sample:jsApp")
project(":sample:jsApp").projectDir = file("graphite-surface/sample/jsApp")

include(":sample:wasmApp")
project(":sample:wasmApp").projectDir = file("graphite-surface/sample/wasmApp")

if (providers.gradleProperty("graphite.pthreadsExperiment").orNull == "true") {
    include(":experiments")
    project(":experiments").projectDir = file("graphite-surface/experiments")
    include(":experiments:wasm-pthreads")
    project(":experiments:wasm-pthreads").projectDir =
        file("graphite-surface/experiments/wasm-pthreads")
}
