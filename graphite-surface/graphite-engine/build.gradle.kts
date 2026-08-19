plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val skikoVersion = "0.152.0-alpha01"

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val iosArm64Main by getting {
            dependencies {
                implementation("org.jetbrains.skiko:skiko:$skikoVersion")
                implementation("org.jetbrains.skiko:skiko-graphite:$skikoVersion")
            }
            kotlin.srcDir("src/iosShared/kotlin")
        }

        val iosSimulatorArm64Main by getting {
            dependencies {
                implementation("org.jetbrains.skiko:skiko:$skikoVersion")
                implementation("org.jetbrains.skiko:skiko-graphite:$skikoVersion")
            }
            kotlin.srcDir("src/iosShared/kotlin")
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
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
