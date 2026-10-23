# Аудит Day 1 Rules и исправления материалов

23 октября 2026 года. Проверяется обновлённое рабочее состояние Cosplay 2;
результат первой генерации отдельно сохранён в v1/. Вторая генерация не запускалась.

## Исправления по аудиту

- Действующий AGENTS.md согласован с version catalog: AGP 9.4.1, Ktor 3.6.0,
  serialization 1.12.0, datetime 0.8.0, coroutines 1.11.0; Java toolchain,
  bytecode target и критерий Gradle daemon — 21. Добавлено упоминание androidApp
  в перечне локальных правил. Исходный снимок инструкций v1 сохранён.
- kmp-testing различает launcher, требуемую toolchain и критерий daemon;
  устаревшая рекомендация искать JDK 17 убрана.
- В configuration-snapshot.zip восстановлены пути четырёх локальных AGENTS.md.
  Все 11 файлов manifest и глобальные правила сохранены без изменения байтов.
  Первоначальный ZIP оставлен в configuration-snapshot.original.zip.
- Архивные Kotlin-файлы source/ восстановлены после нормализации переводов строк
  до точных SHA-256 generation-manifest.json. Снимок root-AGENTS.snapshot.md
  восстановлен до исходных байтов из ZIP. .gitattributes сохраняет байты
  архивных исходников, снимка инструкций и журналов при следующих checkout.
- Сохранён полный исходный rollout генерации, включая финальный ответ и task_complete.
  Повреждённый CLI-экспорт и его exit code 1 оставлены без изменений; причина кода
  не установлена. Новый rollout не является реконструкцией формата CLI events.
- Исторические отчёты уточняют последующие проверки и исправление рабочих тестов.
  Оценка влияния отдельных правил обозначена как качественное наблюдение.

Контрольные суммы и происхождение восстановленных материалов:
[artifact-recovery.json](v1/artifact-recovery.json).
Сравнение текущих исходников с первой генерацией:
[current-source-integrity.json](current-source-integrity.json).
Поле currentTextMatchesOriginal сравнивает текст с учётом возможного различия LF/CRLF;
поля SHA-256 и currentMatchesOriginal сравнивают исходные байты.

## Первая генерация и последующие исправления

Первоначальная генерация создала FestivalSearch, шесть тестов и документацию.
Сервис использует поиск подстрок: «омск» совпадает и с «Омск», и с «Томск».
Три исходных теста ошибочно исключали «Томск»; после подготовки JDK 17 это дало
три падения среди 34 тестов composeApp. Статическое ревью ошибочных ожиданий не заметило.
Обе компиляции на том этапе прошли, Android Lint блокировался HTTP 403 Maven Central.
Это исторический результат v1, сохранённый в [REPORT.md](v1/REPORT.md).

Позднее рабочие отрицательные примеры заменены на «Казань» и добавлен седьмой тест,
явно проверяющий подстроки. Реализация поиска не менялась. Изменение тестов описано
в [TOOLCHAIN-FIX.md](../build-upgrade/TOOLCHAIN-FIX.md).
Исторический v1-source-integrity.json из docs/build-upgrade относится к этапу
до этого исправления и не является проверкой нынешних рабочих файлов.

## Проверки обновлённого проекта

В ходе аудита до правок документации выполнено:

```powershell
.\gradlew.bat -I docs/build-upgrade/maven-mirror.init.gradle.kts :composeApp:compileKotlinDesktop :composeApp:compileAndroidMain :androidApp:assembleDebug :composeApp:desktopTest :core:platform:desktopTest :androidApp:lintDebug --continue
.\gradlew.bat -I docs/build-upgrade/maven-mirror.init.gradle.kts :composeApp:desktopTest --rerun :core:platform:desktopTest --rerun --continue
```

JAVA_HOME процесса указывал на установленный OpenJDK 21.0.2, ANDROID_HOME — на
локальный Android SDK. Использовано существующее зеркало Maven; стандартные
repositories и глобальное окружение не менялись.

| Проверка | Результат |
| --- | --- |
| Android/Desktop compilation и debug APK | exit 0, инкрементальная сборка |
| Повторное исполнение composeApp desktopTest | 35 тестов, 0 failures/errors/skips |
| Повторное исполнение core/platform desktopTest | 12 тестов, 0 failures/errors/skips |
| Android Lint | exit 0, 0 errors, 2 warnings; инкрементальная проверка |

Сохранённые журналы аудита: [сборка и Lint](audit-checks.log),
[повтор тестов](audit-tests.log). Предупреждения Lint: OldTargetApi для targetSdk 35
и ObsoleteSdkInt для ресурсов v26 при minSdk 28. ktlint/Detekt/Spotless не настроены.
Чистая сборка без кэша, доступность Maven без зеркала, запуск на устройстве,
подписанный release и GitHub Actions не проверялись.

После исправления материалов проверены уникальность путей и CRC ZIP,
все 11 SHA-256 конфигурации и три SHA-256 архивных исходников,
валидность 185 JSONL-записей восстановленного rollout и наличие task_complete.
Обнаружение обновлённых правил, четырёх skills и двух ролей проверяется через
verify-discovery.ps1; результаты — [current-discovery-checks.json](current-discovery-checks.json).
Синтаксис skills проверяется quick_validate.py; результаты —
[current-skill-validation.json](current-skill-validation.json).

Рабочий код, зависимости, тесты и конфиги ролей при исправлении материалов не менялись.
Сборка приложения после этих правок повторно не требуется: изменения относятся
к инструкциям, отчётности и сохранению исторических артефактов.
