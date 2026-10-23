rootProject.name = "Cosplay"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":composeApp")
include(":androidApp")
include(":core:model")
include(":core:network")
include(":core:platform")
include(":data:festival")
include(":data:update")
include(":feature:festival")
include(":feature:update")

include(":feature:settings")
