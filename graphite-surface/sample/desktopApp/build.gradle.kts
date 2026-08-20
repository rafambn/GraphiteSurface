import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":sample:sharedUI"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.rafambn.graphitesurface.sample.MainKt"
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        mainClass.set("com.rafambn.graphitesurface.sample.MainKt")
    }
}
