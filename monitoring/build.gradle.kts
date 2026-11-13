// Versions match Android Studio 2026.2, whose bundled Java 25 needs Gradle 9 and a recent Kotlin.
plugins {
    id("com.android.application") version "9.5.1" apply false
    // Not applied anywhere (the Android plugin compiles Kotlin itself); listed to pin the Kotlin version.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.13" apply false
}
