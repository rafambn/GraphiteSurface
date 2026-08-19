import java.io.FileOutputStream
import java.net.URI

plugins {
    alias(libs.plugins.android.library)
}

val skiaRevision = "m152-7bb45c7c26"
val skiaBuildType = providers.gradleProperty("graphiteSurfaceSkiaBuildType")
    .map { it.replaceFirstChar(Char::uppercaseChar) }
    .orElse("Release")
    .get()
    .also {
        require(it == "Debug" || it == "Release") {
            "graphiteSurfaceSkiaBuildType must be Debug or Release"
        }
    }
val skiaArchiveUrl =
    "https://github.com/JetBrains/skia/releases/download/$skiaRevision/" +
        "Skia-$skiaRevision-android-$skiaBuildType-arm64.zip"
val skiaRoot = layout.buildDirectory.dir("skia/$skiaRevision-$skiaBuildType").get().asFile
val skiaArchive = layout.buildDirectory.file(
    "downloads/Skia-$skiaRevision-android-$skiaBuildType-arm64.zip",
).get().asFile

val prepareSkiaAndroid = tasks.register("prepareSkiaAndroid") {
    description = "Downloads the engine-owned Android Skia Graphite archive."
    group = "graphite engine"
    outputs.dir(skiaRoot)

    doLast {
        val marker = skiaRoot.resolve(".graphite-surface-ready")
        if (marker.isFile) return@doLast

        skiaArchive.parentFile.mkdirs()
        if (!skiaArchive.isFile) {
            logger.lifecycle(
                "Downloading Android Skia Graphite archive ($skiaRevision, $skiaBuildType)",
            )
            URI(skiaArchiveUrl).toURL().openStream().use { input ->
                FileOutputStream(skiaArchive).use { output ->
                    input.copyTo(output)
                }
            }
        }

        val staging = skiaRoot.resolveSibling("${skiaRoot.name}.staging")
        project.delete(staging)
        staging.mkdirs()
        project.copy {
            from(project.zipTree(skiaArchive))
            into(staging)
        }
        check(staging.resolve("include/gpu/graphite/vk/VulkanGraphiteContext.h").isFile) {
            "The downloaded Skia archive does not contain the Graphite Vulkan headers."
        }
        check(staging.resolve("out/${skiaBuildType}-android-arm64/libskia_graphite_ext.a").isFile) {
            "The downloaded Skia archive does not contain the Android Graphite library."
        }
        check(staging.renameTo(skiaRoot)) {
            "Could not install the prepared Skia archive at ${skiaRoot.absolutePath}"
        }
        marker.writeText("$skiaRevision\n")
    }
}

android {
    namespace = "com.rafambn.graphitesurface.engine.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DGRAPHITE_SKIA_ROOT=${skiaRoot.absolutePath}"
                arguments += "-DGRAPHITE_SKIA_BUILD_TYPE=$skiaBuildType"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildFeatures {
        prefab = false
    }
}

tasks.configureEach {
    if (name.contains("CMake", ignoreCase = true)) {
        dependsOn(prepareSkiaAndroid)
    }
}

dependencies {
    // The native library is self-contained; this dependency exists for Android's
    // generated JNI packaging and keeps the public bridge in the engine module.
}
