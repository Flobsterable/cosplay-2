# Исправление импорта Gradle после миграции

23 октября 2026.

В присланном журнале `Failed building KotlinMPPGradleModel` вызван отсутствием
локальной JDK 17 и репозитория для автоматического получения Java toolchain.
Миграция на Kotlin 2.4.21, Compose 1.12.2, Gradle 9.7.0 и AGP 9.3.3 уже находилась
в рабочем дереве. Она сохранена; версии библиотек Ktor и kotlinx не менялись.

В settings.gradle.kts добавлен Foojay resolver convention 1.0.0 по
[документации Gradle](https://docs.gradle.org/current/userguide/toolchains.html).
Он позволяет загрузить JDK 17, когда подходящая локальная установка отсутствует.
Различие Java для запуска Gradle и toolchain компиляции описано в development.md;
диагностика импорта добавлена в troubleshooting.md.

В этой среде загрузка JDK через resolver не завершилась: после получения URL
файл оставался нулевого размера. Этот проверочный процесс остановлен.
Уже имеющаяся Temurin 17.0.20.1 скопирована в стандартную папку
`~/.jdks/temurin-17.0.20.1`. Финальная проверка из нового Gradle daemon
подтвердила автоматическое обнаружение этой JDK без параметра пути.
Для запуска Wrapper использовалась JBR 25 из Android Studio; JAVA_HOME и
ANDROID_HOME задавались процессу, глобальное окружение не менялось.

Три сбоя FestivalSearchTest вызваны отрицательными примерами с городом «Томск»:
он содержит подстроку «омск», поэтому сервис правильно возвращал лишний
относительно ожиданий элемент. Эти примеры заменены на «Казань».
Добавлен отдельный тест совпадения подстрок в названии и городе.
Реализация поиска и архив эксперимента v1 не изменялись; прежние отчёты сохраняют
исторические результаты, а актуальный результат приведён ниже.

## Фактические проверки

- `:composeApp:compileKotlinDesktop :composeApp:compileAndroidMain :androidApp:assembleDebug`
  — exit 0; журнал `build/toolchain-local-compile.log`.
- Финальный запуск `--no-daemon` с обеими компиляциями,
  `:composeApp:desktopTest :core:platform:desktopTest :androidApp:lintDebug --continue`
  — exit 0; журнал `build/toolchain-final-checks.log`.
- Всего 47 тестов: 35 в composeApp и 12 в core/platform, без failures/errors/skips.
- Android Lint: 0 errors, 2 warnings (OldTargetApi и ObsoleteSdkInt).
- `git diff --check` — exit 0; предупреждения LF/CRLF не являются ошибками пробелов.

Проверки использовали существующий `maven-mirror.init.gradle.kts` из этой папки.
Попытка offline без зеркала завершилась ошибкой кэша зависимостей:
артефакты зеркала не считаются кэшем другого Maven-репозитория.
Полная сборка без зеркала и автоматическое скачивание JDK не подтверждены.
Ручная синхронизация Android Studio, запуск на устройстве и подписанный release
не проверялись. Сохранены предупреждения deprecated API и локального NDK.

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
