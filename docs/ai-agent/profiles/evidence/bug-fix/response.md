# Bug Fix — попытка 1

Профиль применён без доработок. Это один независимый запуск; красная и зелёная
фазы регрессионного теста выполнены внутри него. Исходный запрос — [task.md](task.md).

## Что нашёл

`core/model/src/commonMain/kotlin/ru/flobsterable/cosplay2/model/Models.kt:134`:
`compareAppVersions` разбивал строку по точке, дефису и подчёркиванию, затем
удалял нечисловые элементы. Из `1.0.0-rc.1` получалось `1,0,0,1`:
предрелиз становился новее стабильной версии, обратное обновление исчезало.
Вызов: App.kt → isNewerVersion в UpdateBanner.kt → compareAppVersions.
Отдельных runtime-логов бага не предоставлено; причина подтверждена тестом.

До фикса: [red-before.log](red-before.log), exit 1, 5 тестов / 4 падения.

## Что починил

Сравнение отделяет основную версию, prerelease и build metadata. Сначала
сравнивает числовые компоненты основной версии, затем идентификаторы prerelease;
стабильный релиз выше prerelease с той же основой. Префикс `v`, числовое сравнение,
пропущенные нули и старый разделитель `_` основной версии сохранены. Метаданные
после `+` не влияют на порядок. Числа сравниваются без переполнения Int.

Изменены Models.kt, docs/feature-flows.md; добавлен
composeApp/src/desktopTest/kotlin/ru/flobsterable/cosplay2/AppVersionComparisonTest.kt
в существующей конфигурации тестов без новых зависимостей.

## Что проверил

- [green-after.log](green-after.log): exit 0, все 5 регрессионных тестов прошли;
  [XML результата](green-tests.xml).
- [verification.log](verification.log): exit 0, обе компиляции composeApp,
  composeApp:desktopTest, core:platform:desktopTest, androidApp:lintDebug.
  Desktop-компиляция была UP-TO-DATE после успешной зелёной фазы;
  Android-компиляция, обе тестовые задачи и Lint выполнены.
- [verification-tests.txt](verification-tests.txt): 40 тестов composeApp и
  12 core/platform, failures/errors/skipped = 0.
- [diff-check.log](diff-check.log): git diff --check, exit 0.

Во всех Gradle-командах использован существующий Maven mirror init script и
JAVA_HOME текущего процесса на установленный Android Studio JBR; Gradle daemon
и компиляция используют проектный критерий Java 21. Зависимости не обновлялись.

Ограничения: ручная установка APK и взаимодействие с реальным GitHub Release
не проверялись. Формат некорректных тегов не валидируется: задача касается порядка
корректных версий. Ни коммитов, ни веток не создавалось. Инструкция профиля не
менялась после попытки, повторного запуска профиля не потребовалось.
