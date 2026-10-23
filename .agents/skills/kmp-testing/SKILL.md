---
name: kmp-testing
description: Design and run focused Kotlin or Compose regression tests and Android/Desktop build checks in Cosplay 2; diagnose environment failures without hiding them.
---

# KMP testing

Найди изменённый контракт и существующие тесты: composeApp/src/desktopTest,
core/platform/src/commonTest и desktopTest. Используй kotlin.test и имеющиеся
fakes/Compose harness; новые библиотеки для локального теста не нужны.

Проверь значимые ветви: успех, пустой ввод, ошибка, отмена suspend-операции,
смена ключа/повторный вызов — по применимости. Для Flow проверяй наблюдаемое
состояние и контролируй жизнь collection; не используй Thread.sleep.
Не обращайся к реальной сети в unit tests. Не тестируй только собственные mock-настройки.

Запускай Wrapper из корня. В Windows используй .\gradlew.bat:
- :composeApp:compileKotlinDesktop и :composeApp:compileAndroidMain;
- :composeApp:desktopTest и :core:platform:desktopTest (или --tests для класса);
- :androidApp:lintDebug для Android Lint, git diff --check для whitespace.

Сохраняй команду, exit code и полный log. Различай FAIL кода и BLOCKED окружением.
При отсутствии Java сначала используй установленный JDK 17/Android Studio JBR
через JAVA_HOME процесса; не меняй глобальное окружение или Gradle-версии.
Не выдавай отсутствие ktlint/detekt за успешный lint и не исправляй несвязанные
toolchain-проблемы. Результат: команды, реальные totals тестов, дефекты и ограничения.

Источники: https://developer.android.com/topic/architecture/recommendations
и https://kotlinlang.org/docs/exception-handling.html
