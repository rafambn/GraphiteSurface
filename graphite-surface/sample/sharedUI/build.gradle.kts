import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.rafambn.graphitesurface.sample.sharedui"
        compileSdk = 37
        minSdk = 24
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":graphite-surface"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        val swiftPlatform = if (name.startsWith("iosSimulator")) "iphonesimulator" else "iphoneos"
        val developerDirectory = (System.getenv("DEVELOPER_DIR")
            ?.let(::File)
            ?: File("/Applications/Xcode.app/Contents/Developer"))
            .let { directory ->
                if (directory.name == "Xcode.app") directory.resolve("Contents/Developer") else directory
            }

        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts(
                "-L${developerDirectory.resolve("Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$swiftPlatform")}",
            )
        }
    }
}
