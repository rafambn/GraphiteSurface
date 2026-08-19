rootProject.name = "GraphiteSurface"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
    }
}

include(":graphite-surface")
project(":graphite-surface").projectDir = file("graphite-surface/graphite-surface")

include(":graphite-engine")
project(":graphite-engine").projectDir = file("graphite-surface/graphite-engine")

include(":graphite-engine-android")
project(":graphite-engine-android").projectDir = file("graphite-surface/graphite-engine-android")

include(":sample")
project(":sample").projectDir = file("graphite-surface/sample")

include(":sample:sharedUI")
project(":sample:sharedUI").projectDir = file("graphite-surface/sample/sharedUI")

include(":sample:desktopApp")
project(":sample:desktopApp").projectDir = file("graphite-surface/sample/desktopApp")

include(":sample:androidApp")
project(":sample:androidApp").projectDir = file("graphite-surface/sample/androidApp")
