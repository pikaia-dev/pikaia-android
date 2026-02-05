// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

allprojects {
    group = "dev.pikaia.android"
    version = project.findProperty("pikaia.sdk.version") as String? ?: "0.1.0-SNAPSHOT"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}