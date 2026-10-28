# Результат аудита готовности сборки

Проверено 24 октября 2026. Это аудит, обновления и миграция не выполнялись.

## Диагноз

Конфигурация текущего проекта успешно проходит на Gradle 9.7.0 с daemon Java 21. Подтверждённая исходная проблема данной консоли: JAVA_HOME пуст, java отсутствует в PATH; обычный Wrapper --version завершился с exit 9009 до запуска Gradle. С установленной JBR в JAVA_HOME только процесса Wrapper запускается успешно. Для такого консольного запуска достаточно выбрать существующий JDK/JBR в окружении процесса; менять версии библиотек для устранения этой причины не требуется.

Текущий запуск не подтвердил поломку от обновления Android Studio. Однако сочетание KMP 2.4.21 и AGP 9.4.1 находится вне опубликованного диапазона полной совместимости JetBrains. Успешная конфигурация и UP-TO-DATE компиляции не превращают его в официально проверенный набор.

## Какие Java используются

| Роль | Наблюдение и граница доказательства |
| --- | --- |
| Запущенная Android Studio | build AI-262.9437.185.2621.16467767, соответствует Rabbit 1 2026.2.1; процесс studio64.exe PID 38004 фактически работает на JBR 25.0.3+-15898627-b508.16, подтверждено jcmd VM.version. |
| Wrapper в исходной консоли | Не запускал Java: JAVA_HOME пуст и java не найден. |
| Wrapper в диагностическом процессе | Launcher JVM 25.0.3 из C:\Program Files\Android\Android Studio\jbr, согласно --version. |
| Gradle daemon текущей проверки | PID 29576, OpenJDK 21.0.2+13-58; javaToolchains указывает Current JVM C:\Users\loki5\.jdks\openjdk-21.0.2; jcmd VM.version подтверждает Java 21. |
| Выбор daemon | gradle/gradle-daemon-jvm.properties задаёт toolchainVersion=21. Критерий приоритетнее JAVA_HOME и org.gradle.java.home: [Gradle Daemon](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria). В текущем запуске старый daemon Java 17 был отклонён, затем выбран Java 21; сообщение JVM is incompatible касается отвергнутого daemon, не проекта. |
| Компиляция | Во всех десяти модулях явно jvmToolchain(21), Kotlin JVM_21; в androidApp Java source/targetCompatibility=21. JDK 21.0.2 доступен и обнаружен Gradle. Обе запрошенные целевые компиляции UP-TO-DATE: свежий процесс компилятора и его фактическое jdkHome этим запуском не наблюдались. |
| IDE project SDK и Gradle JDK | .idea/misc.xml задаёт SDK с именем 21 и languageLevel JDK_21; это не JVM самой IDE. В .idea/gradle.xml нет явного gradleJvm. Полная синхронизация из UI не запускалась; отдельную JVM запуска IDE Sync не выдаём за наблюдённую. Различия ролей описаны в [Android JDK guide](https://developer.android.com/build/jdks). |
| CI | release.yml объявляет Temurin 21; фактический CI в этом аудите не запускался. |

## Совместимость

Версии оставлены без изменений. Источники проверены 24 октября 2026.

| Компонент / сочетание | Было и оставлено | Основание |
| --- | --- | --- |
| Android Studio ↔ AGP | Rabbit 1 2026.2.1 ↔ 9.4.1 | Studio поддерживает AGP 7.1–9.4: [официальная таблица](https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility). |
| AGP ↔ Gradle / JDK / SDK | AGP 9.4.1, Gradle 9.7.0, daemon JDK 21, compileSdk 37 | Для ветки AGP 9.4 указаны минимум Gradle 9.6.0, JDK 17 и максимум API 37; требования минимумов выполнены: [AGP release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes). Это не универсальная сертификация всех плагинов. |
| Gradle ↔ Java | 9.7.0 ↔ launcher 25 / daemon 21 | Обе Java входят в диапазон; таблица указывает поддержку Java 21 с Gradle 8.5, Java 25 с 9.1.0: [матрица именно Gradle 9.7.0](https://docs.gradle.org/9.7.0/userguide/compatibility.html). |
| KMP ↔ Gradle | 2.4.21 ↔ 9.7.0 | В диапазоне 7.6.3–9.7.0: [KMP compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#version-compatibility). |
| KMP ↔ AGP | 2.4.21 ↔ 9.4.1 | ВНЕ опубликованного полного диапазона 8.5.2–9.3.1 той же матрицы. Это подтверждённое расхождение с матрицей, но сбой на этой паре не воспроизведён. |
| Compose ↔ Kotlin / compiler | Compose 1.12.2, Kotlin и Compose compiler 2.4.21 | Версии Kotlin/compiler совпадают; актуальный Compose совместим с актуальным Kotlin. Минимум Desktop JDK 11, упаковки 17: [Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html). Это не расширяет диапазон AGP в матрице KMP. |
| Матрица тестирования самого Gradle | 9.7.0 ↔ AGP 9.4.1 / KGP 2.4.21 | Страница Gradle 9.7.0 перечисляет протестированные AGP до 9.4.0-alpha03 и Kotlin до 2.4.20-Beta1. Она не даёт отдельного подтверждения конкретной установленной пары; для KMP используется также специализированная матрица JetBrains выше. |

Не предлагается автоматически понижать AGP: compileSdk 37 и требования проекта нужно сопоставлять в отдельной задаче изменения сборки. До её постановки версии следует оставить, зафиксировав риск. Обновление IDE само по себе не требует Java target 25, изменения minSdk/targetSdk или всех библиотек.

## Фактически выполненные проверки

Рабочая папка всех команд: C:\Users\loki5\AndroidStudioProjects\cosplay-2. Полные команды Gradle перечислены без оболочки сохранения лога; логи содержат вывод и EXIT_CODE.

| Проверка / точная команда | Exit code | Результат / лог |
| --- | --- | --- |
| `.\gradlew.bat --version` в исходном окружении | 9009 | BLOCKED окружением, Java отсутствует; [лог](logs/wrapper-default.log). Лог содержит также вторичные ошибки batch error branch; они не доказывают несовместимость JVM/Gradle. |
| `$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'; & .\gradlew.bat --version` | 0 | PASS запуска Wrapper, [лог](logs/wrapper-jbr.log). Показанный Kotlin 2.4.0 — встроенный Kotlin Gradle, а не версия KMP проекта 2.4.21. |
| `$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'; & .\gradlew.bat help javaToolchains --console=plain --info` | 0 | PASS конфигурации всех проектов, 2 задачи executed; [лог](logs/configuration.log). Новое зеркало/init script не применялись. |
| `$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'; & .\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:compileAndroidMain --offline --console=plain --info` | 0 | PASS запуска задач, 106 actionable: 15 executed, 91 up-to-date; целевые компиляции UP-TO-DATE, свежая перекомпиляция НЕ подтверждена. [Лог](logs/compilation-offline.log). Offline не проверяет доступность репозиториев и загрузку на чистой машине. |
| `& 'C:/Program Files/Android/Android Studio/jbr/bin/jcmd.exe' 38004 VM.version` | 0 | JBR запущенной IDE, [лог](logs/ide-runtime.log). |
| `& 'C:/Program Files/Android/Android Studio/jbr/bin/jcmd.exe' 29576 VM.version` | 0 | Java daemon текущей проверки, [лог](logs/daemon-runtime.log). |
| `& 'C:/Users/loki5/.jdks/openjdk-21.0.2/bin/java.exe' -version` | 0 | Установленный JDK 21, [лог](logs/jdk21-version.log). |

Тесты: SKIPPED, выполнено 0; не требуются для ограниченного аудита без изменения приложения. Lint, APK, release, CI, полный IDE Sync и запуск приложения: SKIPPED, не входят в проведённую конфигурационную диагностику. Новых XML-результатов тестов нет.

Есть предупреждение Gradle о deprecated features, несовместимых с будущим Gradle 10. Это не ошибка текущего Gradle 9.7.0; причины каждого deprecation отдельно не исследовались. Перепроверка с --rerun-tasks и полный набор приложения намеренно не выполнялись.

## История и ограничения

Документ docs/build-upgrade/JDK21-UPGRADE.md описывает прежний переход с Java 17 на 21, прежние 47 тестов, APK, Lint, зеркало и сетевой timeout. Эти результаты не присваиваются сегодняшнему запуску. Число 47 не является числом выполненных здесь тестов. Историческое отсутствие toolchain 17 не является текущим блокером: сегодня есть и JDK 17, и JDK 21.

Существование каждой версии транзитивных библиотек и API-миграции отдельно не проверялись: библиотеки не обновлялись, конфигурация разрешила текущие плагины, compilation использовала локальные результаты. Официальная страница daemon для URL Gradle 9.7.0 не открылась через web-инструмент; приоритет критериев сверялся по актуальному Gradle manual и подтверждён текущим запуском. Checksum установленного дистрибутива отдельно не пересверялся с сервером: Wrapper не обновлялся и не перекачивался.

## Изменения и возврат

Исходники, настройки и версии не изменялись. SHA-256 25 tracked файлов сборки/Wrapper/каталогов/CI до и после совпали: [сводка](logs/hash-comparison.txt), [до](logs/hashes-before.json), [после](logs/hashes-after.json). Все собственные сохранённые материалы находятся в этой папке доказательств. Откат приложения не требуется; при необходимости удалить только эти материалы, не затрагивая чужие изменения и глобальные кэши. JAVA_HOME менялся только внутри завершившихся процессов.

Проверка whitespace tracked diff: git diff --check — exit 0, [лог](logs/diff-check.log). Она не проверяет содержимое untracked Markdown; ссылки новых материалов проверяются родителем отдельно.
