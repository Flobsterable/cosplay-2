import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "ru.flobsterable.cosplay2.shared"
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
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":core:model"))
            implementation(project(":core:network"))
            implementation(project(":core:platform"))
            implementation(project(":data:festival"))
            implementation(project(":data:update"))
            implementation(project(":feature:festival"))
            implementation(project(":feature:update"))
            implementation(project(":feature:settings"))
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "ru.flobsterable.cosplay2.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Cosplay"
            packageVersion = providers.gradleProperty("APP_VERSION_NAME").get()
        }
    }
}
