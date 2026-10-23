# Результат обновления сборки

23 октября 2026, Cosplay 2.

| Компонент | До | После |
| --- | --- | --- |
| Gradle Wrapper | 9.0.0 | 9.7.0 |
| Kotlin / Compose compiler | 2.0.21 | 2.4.21 |
| Compose Multiplatform | 1.7.3 | 1.12.2 |
| Android Gradle Plugin | 8.5.2 | 9.3.3 |
| compileSdk | 35 | 37 |

minSdk 28, targetSdk 35, applicationId ru.flobsterable.cosplay2, versionName 1.0.2
и versionCode 4 сохранены. Прямые версии Ktor/serialization/datetime/coroutines
не менялись. AGP выбран в ветке 9.3, с исправлениями patch 9.3.3; официальный
диапазон полностью проверенных JetBrains версий заканчивается на 9.3.1.
9.3.3 исправляет известное падение Lint на JDK 17 из 9.3.1. Последний AGP 9.4
не выбран, поскольку он выходит за опубликованный диапазон KMP.

Источники: [матрица KMP](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html),
[исправления AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes),
[совместимость Compose](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html).

## Изменения

- Все девять KMP-модулей переведены на Android KMP library plugin и kotlin.android.
- Android entry point, manifest, launcher resources и signing находятся в androidApp;
  общий UI, state holders и Desktop entry point остаются в composeApp.
- Release workflow и инструкции используют :androidApp:assembleRelease и новый путь APK.
- BuildConfig в core/platform заменён генерацией Kotlin-константы из APP_VERSION_NAME.
  Фактический сгенерированный файл содержит строку 1.0.2, что проверено компиляцией.
- Для feature/update явно подключён material-icons-core, который перестал приходить
  транзитивно из новой Material 3. Иконки и UI не перерисовывались.
- MyCityScreenTest адаптирован к прокрутке нового Compose LazyColumn: до клика по
  фильтрам тест явно прокручивает к индексу 0. Проверки поведения не ослаблены;
  прежний поиск уже скомпонованного узла не гарантировал видимость области клика.
- Wrapper обновлён штатной Gradle-задачей, включая scripts/jar; SHA-256 дистрибутива
  закреплена в properties и проверена при загрузке.

## Проверки

JDK: отдельный Temurin 17.0.20.1 в рабочем каталоге. Android SDK установлен локально;
JAVA_HOME и ANDROID_HOME задавались только процессу, глобальное окружение не менялось.
Изначально Maven Central возвращал HTTP 403. Проверки выполнены с опциональным
maven-mirror.init.gradle.kts; repositories в settings.gradle.kts остаются стандартными.

| Проверка | Итог и доказательство |
| --- | --- |
| Конфигурация всех модулей | PASS: configure-mirror.log |
| Desktop + Android compilation и debug APK | PASS: final-compile-and-apk.log, exit 0 |
| Android Lint | PASS: final-lint.log, exit 0; 0 errors, 2 warnings |
| Wrapper update и запуск --version | PASS: wrapper-update.log, wrapper-version.log; exit 0 |
| ComposeApp desktopTest | 34 теста, 31 passed, 3 failed; verified-tests.log |
| Core/platform desktopTest | 12 тестов, 12 passed; test-summary.json и XML |
| UI-тест MyCityScreenTest после адаптации | PASS: city-test-adapted.log и полный повтор verified-tests.log |
| Обнаружение правил/skills/ролей после миграции | 48/48: discovery-checks.json, шесть контекстов |
| git diff --check | PASS; warnings LF/CRLF относятся к настройке Git, не к ошибкам пробелов |

Всего 46 тестов: 43 passed, 3 failed. Три сбоя — FestivalSearchTest, уже падавшие
на старом toolchain до миграции. Их причина и исходные результаты зафиксированы
в docs/ai-agent/v1/REPORT.md. Они сохранены для сравнения v1/v2; реализация, тесты
сервиса и его документация не менялись, SHA-256 сверены в v1-source-integrity.json.
Таким образом полный test task имеет exit 1; считать весь набор зелёным нельзя.

Debug APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk.
Подписанный release с секретами и запуск приложения на устройстве не проверялись.
Остаются предупреждения deprecated Compose/Gradle API и два предупреждения Lint:
OldTargetApi для сохранённого targetSdk 35 и ObsoleteSdkInt для папки launcher
resources v26 при minSdk 28. Они не скрывались и не исправлялись попутно.

Первоначальные неуспешные проверки оставлены в configure.log, configure-direct.log,
compile-and-apk.log, tests.log, lint.log. review.md содержит два исправленных
замечания независимого агента. final-results.json относится к проверке до адаптации
UI-теста (там было четыре сбоя); окончательный повтор — verified-tests.log,
wrapper-and-tests-results.json и test-summary.json.

## Повтор

```powershell
.\gradlew.bat -I docs/build-upgrade/maven-mirror.init.gradle.kts :composeApp:compileKotlinDesktop :composeApp:compileAndroidMain :androidApp:assembleDebug
.\gradlew.bat -I docs/build-upgrade/maven-mirror.init.gradle.kts :composeApp:desktopTest :core:platform:desktopTest --continue
.\gradlew.bat -I docs/build-upgrade/maven-mirror.init.gradle.kts :androidApp:lintDebug
```

При доступном Maven Central параметр -I не нужен. Не меняйте toolchain при сравнении
качества правил v1/v2 без явного учёта этой переменной.
Основания выбора версий и миграции — официальные источники в README.md этой папки.
