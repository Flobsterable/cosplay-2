# Переход на JDK 21 и актуальные библиотеки

23 октября 2026. Этот этап заменяет прежнюю конфигурацию JDK 17.
Исторические журналы и архив правил эксперимента v1 сохранены.

| Компонент | До этого этапа | Сейчас |
| --- | --- | --- |
| Java toolchain / JVM target всех десяти модулей | 17 | 21 |
| JDK release workflow | 17 | 21 |
| Java Gradle daemon | определялась окружением | критерий Java 21 |
| AGP | 9.3.3 | 9.4.1 |
| Activity Compose | 1.9.2 | 1.13.0 |
| Ktor, все используемые client artifacts | 2.3.12 | 3.6.0 |
| kotlinx.serialization JSON | 1.7.3 | 1.12.0 |
| kotlinx.datetime | 0.6.1 | 0.8.0 |
| kotlinx.coroutines | 1.8.0 | 1.11.0 |

Kotlin/Compose compiler 2.4.21, Compose Multiplatform 1.12.2 и Gradle 9.7.0
сохранены. Gradle 9.8 новее опубликованного диапазона KMP 2.4.21; обновлять его
без необходимости не стали. AGP 9.4.1 новее верхней границы полностью
проверенной матрицы JetBrains (9.3.1): фактическая проверка проекта обязательна,
она не заменяет официальную сертификацию этого сочетания.
Старая библиотека Material Icons 1.7.3 остаётся последней версией используемого
набора; переход на другие иконки потребовал бы отдельной миграции ресурсов.

Установленная Android Studio — Rabbit 1, 2026.2.1, build 262.9437.185.2621.16467767.
Она поддерживает AGP 7.1–9.4. Её собственная JBR 25 не означает, что компиляция
проекта обязана использовать Java 25: здесь явно выбрана Java 21.
JDK 17 не является общей причиной несовместимости с этой Studio; исходная ошибка
говорила об отсутствии запрошенной toolchain. Переход на 21 выполнен по запросу
пользователя и охватывает компиляцию, bytecode target и CI.
Файл `gradle/gradle-daemon-jvm.properties` создан штатной задачей
`updateDaemonJvm --jvm-version=21`: он закрепляет Java 21 и содержит URL
для получения JDK на поддерживаемых Gradle хостах. Критерий daemon имеет
приоритет над JBR, используемой IDE для запуска Wrapper.

Для kotlinx.datetime 0.8.0 импорт Clock в App.kt заменён на kotlin.time.Clock.
Расчёт текущего года по системному часовому поясу сохранён.
Платформы, модули, applicationId, minSdk, targetSdk и app version не менялись.

## Основания выбора

- [Совместимость Android Studio и AGP](https://developer.android.com/studio/releases).
- [AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes).
- [Матрица Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html).
- [Релизы Ktor](https://ktor.io/docs/releases.html), [миграция Ktor 3](https://ktor.io/docs/migrating-3.html).
- [Миграция datetime](https://github.com/Kotlin/kotlinx-datetime#deprecation-of-instant).
- Стабильные версии сверены с maven-metadata.xml официального Google Maven
  и зеркала Maven Central, которое уже использовалось для сборки проекта.

## Проверки

Проверки выполнены заново на JDK 21; результаты прежнего этапа JDK 17
не используются как доказательство текущей конфигурации.

- Android и Desktop compilation плюс `:androidApp:assembleDebug` — exit 0,
  `build/jdk21-build.log`. Скомпилированный Desktop class имеет major version 65 (Java 21).
- Девять `prepareKotlinIdeaImport` — успешно, `build/jdk21-checks.log`.
- `:composeApp:desktopTest` и `:core:platform:desktopTest` — 47 тестов,
  0 failures/errors/skips; XML в build/test-results соответствующих модулей.
- `:androidApp:lintDebug` — exit 0, `build/jdk21-lint.log`.
- `updateDaemonJvm --jvm-version=21` — exit 0, `build/jdk21-daemon.log`.
- `--version` при JAVA_HOME на JBR 25 показывает критерий daemon Java 21
  из gradle/gradle-daemon-jvm.properties, `build/jdk21-launcher-version.log`.
- `git diff --check` — exit 0; есть предупреждения LF/CRLF.
- Финальный общий запуск подготовки импорта, обеих компиляций, APK, обоих
  desktopTest и Lint через JBR 25 с критерием daemon Java 21 — exit 0,
  `build/jdk21-final.log` (330 задач, 312 up-to-date).
  Lint: 0 errors, 2 warnings (OldTargetApi и ObsoleteSdkInt).

Для проверок использовано существующее зеркало Maven Central через
`-I docs/build-upgrade/maven-mirror.init.gradle.kts`. Первый общий запуск
тестов/Lint имел exit 1 из-за тайм-аута Google Maven при скачивании
`com.android.tools.external.com-intellij:kotlin-compiler:32.4.1`, хотя тесты прошли.
Артефакт скачан повторно, его SHA-1 сверена с официальным Google Maven
(`7c2776229f721222785eb82d6347521495a78505`) и добавлена в локальный Gradle cache.
После этого Lint завершился успешно. Эта настройка кэша не входит в исходники.

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
Полная интерактивная синхронизация в Android Studio, запуск на устройстве,
подписанный release и GitHub Actions не запускались. Сохранены предупреждения
deprecated API, Android host tests и неполной локальной установки NDK.
