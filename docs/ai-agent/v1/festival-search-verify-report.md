# FestivalSearch — проверка v1

Проверены готовые исходник и тесты без исправлений. Команды выполнялись 23 октября 2026 года из корня репозитория в PowerShell, последовательно. `java` отсутствует в исходном `PATH`; для процесса использован установленный Android Studio JBR (`JAVA_HOME=C:/Program Files/Android/Android Studio/jbr`, Java 21.0.10). Глобальное окружение и Gradle-конфигурация не изменялись.

| Команда | Exit code | Результат | Полный лог |
| --- | ---: | --- | --- |
| `.\gradlew.bat :composeApp:compileKotlinDesktop` | 1 | BLOCKED: Gradle не нашёл Java toolchain 17; JBR имеет версию 21 | [compile-desktop](festival-search-verify-compile-desktop.log) |
| `.\gradlew.bat :composeApp:compileDebugKotlinAndroid` | 1 | BLOCKED: Java toolchain 17 отсутствует | [compile-android](festival-search-verify-compile-android.log) |
| `.\gradlew.bat :composeApp:desktopTest :core:platform:desktopTest` | 1 | BLOCKED: Java toolchain 17 отсутствует; тестовые задачи не запустились | [desktop-tests](festival-search-verify-desktop-tests.log) |
| `.\gradlew.bat :composeApp:lintDebug` | 1 | BLOCKED: Android SDK не указан через `ANDROID_HOME` или `local.properties` | [android-lint](festival-search-verify-android-lint.log) |
| `git diff --check` | 0 | PASS: ошибок пробелов в отслеживаемом diff нет | [diff-check](festival-search-verify-diff-check.log) |

Машинный список команд, времени, кодов завершения и имён логов: [results.json](festival-search-verify-results.json).

Тестовые итоги: **0 выполненных, 0 подтверждённых passed/failed**, поскольку Gradle остановился до запуска `desktopTest`. Проверка компиляцией, Android Lint и поведение `FestivalSearch` остаются неподтверждёнными. `git diff --check` не включает новые файлы, пока они не добавлены в индекс. Кода реализации, тестов и конфигурации эта проверка не меняла.

## Повтор Android Lint с найденным SDK

По дополнительной проверке выполнена та же команда `.\gradlew.bat :composeApp:lintDebug` с `ANDROID_HOME=C:\Users\loki5\AppData\Local\Android\Sdk` и `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, заданными только в процессе. **Exit code 1, BLOCKED**: после обнаружения SDK Gradle остановился при определении `:core:model:compileDebugKotlinAndroid` из-за отсутствия Java toolchain 17. Lint не запустился. Полный [лог](festival-search-verify-sdk-retry-android-lint.log) и [запись команды с exit code](festival-search-verify-sdk-retry-results.json) сохранены отдельно; первоначальный лог не изменён.

Локальный JDK 17 не найден в обычных каталогах `C:\Program Files\Java`, `Eclipse Adoptium`, `Microsoft`, `Amazon Corretto`, `Zulu`, `BellSoft`, `JetBrains`, `Android`, `C:\Users\loki5\.gradle\jdks` и `C:\Users\loki5\AppData\Local\Programs` (проверены существующие каталоги и имена непосредственных подкаталогов). Скачивание и изменение toolchain не выполнялись.
