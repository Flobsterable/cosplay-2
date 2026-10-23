---
name: kotlin-code-style
description: Write or review Kotlin declarations, naming, imports, null handling and cancellation-safe error handling in Cosplay 2.
---

# Kotlin code style

Прочитай Kotlin/naming, образцы и шаблон в корневом AGENTS.md и ближайший
аналогичный исходник. Используй минимальную видимость, val, точные типы,
четыре пробела, явные imports и существующие package/suffix conventions.
Группируй связанные объявления; не форматируй несвязанные файлы.

Проверь nullable-ветви, исключения и отмену. В suspend-пути CancellationException
должен распространяться до владельца; не копируй runCatching без анализа отмены.
Исключения домена не заменяй Any/строками там, где существует тип модели.

Проверь diff на новый !!, wildcard imports, бесхозные scope и прикладной println.
Это ревью, а не настроенный линтер. Выполни git diff --check и релевантную
компиляцию; Android Lint не называй ktlint. В проекте ktlint/detekt не установлены.

Источник: https://kotlinlang.org/docs/coding-conventions.html
