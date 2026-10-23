import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val generatedVersionDirectory = layout.buildDirectory.dir("generated/appVersion/kotlin")
val generateAppVersion by tasks.registering {
    val versionName = providers.gradleProperty("APP_VERSION_NAME")
    inputs.property("versionName", versionName)
    outputs.dir(generatedVersionDirectory)
    doLast {
        val targetFile = generatedVersionDirectory.get().file("ru/flobsterable/cosplay2/platform/AppBuildInfo.kt").asFile
        targetFile.parentFile.mkdirs()
        targetFile.writeText("package ru.flobsterable.cosplay2.platform\n\ninternal const val APP_VERSION_NAME = \"${versionName.get()}\"\n")
    }
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "ru.flobsterable.cosplay2.core.platform"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        androidMain {
            kotlin.srcDir(generateAppVersion.map { generatedVersionDirectory.get() })
        }
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":core:model"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
