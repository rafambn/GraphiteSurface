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

include(":sample")
project(":sample").projectDir = file("graphite-surface/sample")

include(":sample:sharedUI")
project(":sample:sharedUI").projectDir = file("graphite-surface/sample/sharedUI")

include(":sample:desktopApp")
project(":sample:desktopApp").projectDir = file("graphite-surface/sample/desktopApp")
