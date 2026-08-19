plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val iosArm64Main by getting {
            dependencies {
                implementation(libs.skiko)
                implementation(libs.skiko.graphite)
            }
            kotlin.srcDir("src/iosShared/kotlin")
        }

        val iosSimulatorArm64Main by getting {
            dependencies {
                implementation(libs.skiko)
                implementation(libs.skiko.graphite)
            }
            kotlin.srcDir("src/iosShared/kotlin")
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops {
            create("gsTypes") {
                defFile(project.file("src/iosShared/cinterop/gs_types.def"))
                compilerOpts("-I${project.file("src/iosShared/cinterop").absolutePath}")
            }
        }

        binaries.framework {
            baseName = "GraphiteEngine"
            isStatic = false
            binaryOptions["bundleId"] = "com.rafambn.graphitesurface.engine"
            linkerOpts("-Wl,-dead_strip")
        }
    }
}

group = "com.rafambn"
version = "0.1.0-SNAPSHOT"