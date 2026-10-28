---
name: kotlin-code-style
description: Write or review Kotlin declarations, naming, imports, null handling and cancellation-safe error handling in Cosplay 2.
---

# Kotlin code style

Прочитай Kotlin/naming в корневом AGENTS.md, применимые вложенные инструкции
и ближайший аналогичный исходник. Если нужен пример, выбери соответствующий
раздел docs/ai-agent/code-examples.md. Используй минимальную видимость, val, точные типы,
четыре пробела, явные imports и существующие package/suffix conventions.
Группируй связанные объявления; не форматируй несвязанные файлы.
Самостоятельный тип/компонент размещай в файле по его имени; небольшие private
helpers оставляй рядом. Пакеты соответствуют подпапкам по правилам feature/core.
Не создавай сборники несвязанных Utils/Components и не расширяй public API
ради переноса файла. Для use cases следуй контракту из composeApp/AGENTS.md.

Проверь nullable-ветви, исключения и отмену. В suspend-пути CancellationException
должен распространяться до владельца; не копируй runCatching без анализа отмены.
Исключения домена не заменяй Any/строками там, где существует тип модели.

Проверь diff на новый !!, wildcard imports, бесхозные scope и прикладной println.
Это ревью, а не настроенный линтер. Выполни git diff --check и релевантную
компиляцию при изменении Kotlin; Android Lint не называй ktlint. В проекте
ktlint/detekt не установлены. Для правки только инструкций сборка не требуется.

Источник: https://kotlinlang.org/docs/coding-conventions.html
