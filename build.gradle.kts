// Root build script. AGP 9 has Kotlin built in (the `kotlin-android` plugin must NOT be applied);
// the Kotlin Gradle plugin version it uses is the one found on the buildscript classpath, so the
// pin below is how the project selects the Kotlin version from the catalog.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
