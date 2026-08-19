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
            "project(\":graphite-engine\")",
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
