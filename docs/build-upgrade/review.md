# Независимое ревью миграции

Агент Archimedes, id 01a16838-ee60-7a02-aa65-8dcaf0a1f374, получил read-only задачу
с инструкциями code-reviewer. Gradle параллельно не запускал. Это отдельное ревью
миграции; runtime-проверка двух настроенных custom roles для эксперимента v1
сохранена в docs/ai-agent/v1/subagent-spawns.json.

Найдены и исправлены два дефекта миграции:

1. Генератор AppBuildInfo экранировал dollar и создавал буквальный шаблон
   versionName.get() вместо номера версии. Убрано экранирование; значение
   подставляется Gradle при генерации Kotlin. Ошибка также подтверждена Android
   compiler в первоначальном lint.log.
2. feature/update использует Icons.AutoMirrored.Rounded.ArrowBack, но новая
   Material 3 больше не приносит material-icons-core транзитивно. Добавлена
   явная зависимость через version catalog. Ошибка подтверждена Desktop compiler
   в compile-and-apk.log и tests.log.

Исходники FestivalSearch и его тесты не относятся к миграции и не исправлялись.
Последующие результаты компиляции, APK, тестов и Lint — final-*.log/results.json.
