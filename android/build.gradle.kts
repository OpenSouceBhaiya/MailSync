// build.gradle.kts  (project root — NOT the app module's file)
//
// What this file does (plain English):
// This is the top-level "recipe book" for the whole project.
// It declares *which version* of two essential tools to use:
//   - Android Gradle Plugin (AGP): knows how to compile Android apps
//   - Kotlin plugin: knows how to compile Kotlin code
// It does NOT list app libraries — that's the app/build.gradle.kts job.
// The "apply false" means "make these plugins available, but don't apply them
// here at the root — apply them inside the :app module instead."

plugins {
    id("com.android.application") version "8.3.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
}
