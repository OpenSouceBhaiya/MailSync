// settings.gradle.kts
//
// What this file does (plain English):
// This is the first file Gradle reads when you run a build. It does two things:
// 1. Names the project — the name that shows in Android Studio's window title.
// 2. Lists where to find libraries (Maven Central and Google's repo).
//    When app/build.gradle.kts says "I need Retrofit", Gradle looks here to
//    find where to download it from.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GmailOtpSyncer"
include(":app")
