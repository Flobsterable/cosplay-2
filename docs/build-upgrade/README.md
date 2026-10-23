# Обновление сборки, 23 октября 2026

Текущая конфигурация после следующего этапа — JDK/JVM 21, AGP 9.4.1 и новые
библиотеки: [JDK21-UPGRADE.md](JDK21-UPGRADE.md). Ниже сохранено описание
первоначального этапа миграции; его результаты относятся к прежним версиям.

Выбраны Gradle 9.7.0, Kotlin/Compose Compiler 2.4.21, Compose Multiplatform 1.12.2,
AGP 9.3.3. Матрица KMP протестирована до AGP 9.3.1; выбран более новый patch той же
ветки с исправлением падения Lint на JDK 17 в 9.3.1. Это не последний AGP.
compileSdk повышен до 37 для новых Compose Android
артефактов; targetSdk 35 и minSdk 28 сохраняют прежние настройки приложения.
Ktor, serialization, datetime и coroutines не обновлялись отдельно.

AGP 9 требует отдельного Android application module. MainActivity, AndroidManifest
и res перенесены из composeApp/src/androidMain в androidApp/src/main.
Все девять KMP-модулей используют com.android.kotlin.multiplatform.library
и kotlin.android вместо старого androidTarget/com.android.library.
В Android application применяется встроенный Kotlin AGP; Compose compiler
остаётся связан с версией Kotlin. Подпись, applicationId, app version и FileProvider
сохранены. Release workflow теперь собирает APK из androidApp.

BuildConfig недоступен в новом Android KMP library plugin. Для Android AppVersion
core/platform генерирует Kotlin-файл из APP_VERSION_NAME как Gradle task output,
с declared input/output и зависимостью через source directory provider.
Новые сторонние плагины не добавлены.

Исходники первой генерации, diff и исходная конфигурация v1 остаются в
docs/ai-agent/v1. Проверки после миграции относятся к новому toolchain и сохраняются
здесь отдельно. Сервис FestivalSearch и его тесты в рамках миграции не исправляются.

Команды проверки:

```powershell
.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:compileAndroidMain
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :composeApp:desktopTest :core:platform:desktopTest --continue
.\gradlew.bat :androidApp:lintDebug
```

В данной среде часть URL Maven Central возвращает HTTP 403. Для локальной
проверки использован init script с зеркалом Central на Google Cloud Storage:
добавьте `-I docs/build-upgrade/maven-mirror.init.gradle.kts` к команде при такой же
ошибке. Это опциональная настройка конкретного запуска; settings.gradle.kts и
глобальные Gradle repositories не заменялись. Gradle скачан с downloads.gradle.org,
SHA-256 закреплена в Wrapper. Общие предупреждения deprecated API не подавлялись.

Официальные основания:
- https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
- https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
- https://developer.android.com/kotlin/multiplatform/plugin
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://docs.gradle.org/9.7.0/release-notes.html
