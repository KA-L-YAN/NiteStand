// Top-level build file. Plugins are declared here (not applied) so every module
// resolves the same versions from the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"). Declaring the Kotlin Gradle
    // plugin here pins the Kotlin compiler version so it matches the Compose compiler plugin.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
