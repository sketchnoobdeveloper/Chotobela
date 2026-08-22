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

rootProject.name = "Chotobela"

include(":app")

include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:native")
include(":core:emulator")
include(":core:ui")

include(":feature:home")
include(":feature:library")
include(":feature:store")
include(":feature:player")
include(":feature:profile")
include(":feature:settings")
include(":feature:download")

include(":native-engine")
