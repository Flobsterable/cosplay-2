---
name: kmp-architecture
description: Plan or change module boundaries, dependencies, source sets and platform contracts in Cosplay 2 KMP. Use for architecture or cross-platform changes.
---

# KMP architecture

1. Найди корень через settings.gradle.kts. Прочитай корневой AGENTS.md,
   docs/architecture.md и build.gradle.kts затрагиваемых модулей.
2. Сопоставь изменение с существующим владельцем: composeApp связывает зависимости,
   data получает данные, feature отображает, core/platform изолирует платформу.
3. Проверь направление project dependencies и доступность API для Android/Desktop.
   Для expect найди обе actual. Не добавляй iOS/web и domain-модуль по шаблону.
4. При изменении контракта проследи все вызовы и тесты; обнови затронутый документ.
5. Запусти обе компиляции из корневого AGENTS.md. Отчитайся о файлах, границах,
   результатах и неподтверждённых предположениях. Не меняй версии ради новой документации.

Источник: https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
