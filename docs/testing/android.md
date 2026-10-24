# Android smoke через Mobile MCP

Используется локальный [Mobile MCP](https://github.com/mobile-next/mobile-mcp)
1.0.12. Нужны Node.js 20+, Android SDK и запущенный эмулятор в `adb devices`.
Инструмент устанавливается вне репозитория; Gradle-зависимости приложения не добавляются.

## Однократная настройка

Из корня проекта в PowerShell:

```powershell
.\.codex\install-android-testing.ps1
node scripts/android-mcp.mjs --probe
```

Установщик закрепляет версии Mobile MCP и клиентского SDK в
`%LOCALAPPDATA%/Cosplay2/testing/mobile-mcp-1.0.12`, отключает телеметрию сервера,
регистрирует stdio MCP `cosplay_android` через `codex mcp add` и обновляет личный skill.
Остальные MCP-конфигурации не затрагиваются. Для нестандартного SDK есть параметр
`-SdkDirectory`. Для другого RuntimeDirectory передайте этот же путь в
`connectAndroidMcp({runtimeDirectory: ...})` при использовании моста.

Откройте новый чат Codex, чтобы нативные инструменты cosplay_android загрузились.
Проверка `--probe` уже сейчас выполняет MCP handshake, получает tools и вызывает
mobile_list_available_devices; это проверка подключения, не UI smoke.
[Конфигурация MCP общая для клиентов Codex](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

## Полный запуск

Промпт:

> Используй $two-level-testing для Android. Выбери тестовый эмулятор, запусти кодовые
> проверки, собери и установи изолированный smoke APK. Через cosplay_android MCP
> пройди все пять сценариев, включая системный BACK и клавиатуру. Сохрани скриншоты
> каждого шага и единый отчёт. Не публикуй изменения.

Команды подготовки, которые выполняет агент (serial заменяется фактическим):

```powershell
.\scripts\verify.ps1 -TargetPlatform Android -OutputDirectory build/verification/android-001
.\scripts\prepare-android-smoke.ps1 -OutputDirectory build/verification/android-001 -Device emulator-5554 -ResetSmokeData -SyncClock
```

Exit 2 первой команды при успешном коде означает ожидающий UI; при exit 1 сначала
диагностируйте код/окружение. Нужен совместимый Java launcher; daemon/toolchain —
Java 21. Существующее зеркало при необходимости включается через
`-GradleInitScript docs/build-upgrade/maven-mirror.init.gradle.kts`.

Android-режим дополнительно собирает `:androidApp:assembleDebug -PandroidSmoke=true`.
Только debug получает applicationId `ru.flobsterable.cosplay2.smoke`; обычные debug
и release сохраняют `ru.flobsterable.cosplay2`. APK устанавливается параллельно
обычному приложению. Подготовка проверяет package через aapt и допускает только
явно выбранный эмулятор. Сбрасывается только `.smoke` и только при
`-ResetSmokeData`. Имя/иконка у двух установок одинаковые: запускайте точный package.

Перед установкой проверяется расхождение часов эмулятора с компьютером. При
разнице более пяти минут запуск останавливается: устаревшая дата может ломать TLS.
Явный -SyncClock синхронизирует часы только выбранного тестового эмулятора.

Подготовка пишет android-device.json: serial ADB, имя AVD для MCP, API, package,
SHA-256 APK, commit и хеш исходников. MCP может возвращать имя Pixel_9, а ADB —
emulator-5554. Используйте правильный идентификатор для каждого инструмента.
Затем агент запускает .smoke через mobile_launch_app и работает через MCP:
list elements → tap/type/swipe/BACK → screenshot → проверка ожидания.
Успешный вызов ввода сам по себе не доказывает, что поле содержит нужный текст.

Для проверки обычной QWERTY IME на эмуляторе с аппаратной клавиатурой сначала
сохраните текущие значения `show_ime_with_hard_keyboard` и
`stylus_handwriting_enabled` через `adb -s <serial> shell settings get secure <key>`.
Затем включите экранную клавиатуру и отключите handwriting только на тестовом эмуляторе:

```powershell
adb -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1
adb -s emulator-5554 shell settings put secure stylus_handwriting_enabled 0
```

После прогона восстановите сохранённые значения; если исходное значение было
`null`, используйте `settings delete secure <key>`, иначе `settings put secure <key> <value>`.
Настройки сохраняйте внутри run (например keyboard-before.json). Плавающая панель
рукописного ввода отличается от QWERTY: BACK может сразу вызвать навигацию приложения.
Не выдавайте её проверку за проверку полноразмерной клавиатуры и resize.

План — [smoke.json](smoke.json); для Android предпочитайте androidAction, если оно
задано. В S05 снимите открытую клавиатуру; первый системный BACK должен скрыть её,
сохранив черновик, второй — открыть Настройки. Сохраните оба снимка. Вводите только
тестовый текст: клавиатура может попасть в скриншот.

В smoke.json вместе с обычными revision/sourceHash/scenarios сохраните поля
platform (начинается с Android), transport, device, mcpDevice, packageName и
apkSha256. Последние четыре берутся из android-device.json. Затем:

```powershell
.\scripts\verify.ps1 -OutputDirectory build/verification/android-001 -ReportOnly
```

Генератор не принимает Desktop за Android: проверяются платформа, serial, MCP device,
package и hash APK. PASS/FAIL/BLOCKED и снимки — в report.md. При сбое записывайте
ожидание, факт и вероятное место проблемы, продолжайте независимые сценарии.
Hosted CI пока запускает только кодовый уровень; этот workflow — локальный MCP.

## Текущий чат до перезагрузки tools

Запустите `node scripts/android-mcp.mjs --session` через терминальный инструмент.
Каждая строка stdin — JSON: `{"method":"tools"}` получает схемы;
`{"name":"mobile_list_available_devices","arguments":{}}` вызывает tool.
Для screenshot добавьте `"imagePath":"<абсолютный путь PNG внутри run>"`:
изображение, полученное от MCP, сохраняется в файл и не печатается base64 в журнал.
Каждый ответ — одна строка JSON. Завершение: `{"method":"close"}`.
Схемы аргументов берите из tools, снимки проверяйте визуально. Мост использует
тот же stdio MCP, не прямые ADB-клики и не имитацию UI. Экспорт connectAndroidMcp()
доступен также обычному Node.js-клиенту; sandbox node_repl может не иметь process.

## Фактическая проверка 24 октября 2026

Mobile MCP handshake и управление подтверждены на Pixel_9 / emulator-5554,
Android 16 / API 36. Выполнены 69 кодовых тестов, обе компиляции, сборка изолированного
APK и Android Lint; отдельно обычный debug собран с прежним applicationId.
Из пяти Android smoke-сценариев четыре PASS, поддержка FAIL: при обычной QWERTY
IME текст черновика визуально пропадает из сжатого поля, хотя сохраняется в semantics
и возвращается после скрытия клавиатуры. Повторено дважды. BACK/навигация и сохранение
черновика прошли. Вероятное место для отдельного Bug Fix — SupportScreen.kt,
сочетание Scaffold padding/imePadding с adjustResize; причина требует проверки.

[Сохранённый отчёт](evidence/android-2026-10-24/report.md) содержит все 21 шаг,
31 снимок и диагностику. Полные журналы, JUnit XML и APK остаются локально в
build/verification/android-ready-20261024 и игнорируются Git.
Первый запуск упирался в неверную дату эмулятора (июль вместо октября): TLS
CertificateNotYetValid исправлен синхронизацией времени, без ослабления TLS.
Удалённый Android CI, физический телефон и другие версии Android не проверялись.
