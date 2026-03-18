rootProject.name = "Cosplay"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":composeApp")
include(":core:model")
include(":core:network")
include(":core:platform")
include(":data:festival")
include(":data:update")
include(":feature:festival")
include(":feature:update")
