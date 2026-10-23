import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "ru.flobsterable.cosplay2.data.update"
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:network"))
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
