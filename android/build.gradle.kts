// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 has built-in Kotlin; the standalone kotlin-android plugin is no longer applied.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.dagger.hilt.android) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
}
