---
name: kmp-architecture
description: Plan or change module boundaries, dependencies, source sets and platform contracts in Cosplay 2 KMP. Use for architecture or cross-platform changes.
---

# KMP architecture

При выделении бизнес-сценария читай [use case и подключение](references/use-case.md).
Это целевой шаблон v2 на существующих контрактах, а не описание выполненного переноса.

1. Найди корень через settings.gradle.kts. Прочитай корневой AGENTS.md,
   docs/architecture.md и build.gradle.kts затрагиваемых модулей.
2. Отдели фактическую архитектуру от целевых соглашений v2. Для core читай
   core/AGENTS.md, для use cases — composeApp/AGENTS.md, для UI — feature/AGENTS.md.
   composeApp связывает зависимости, data получает данные, feature отображает,
   core/platform изолирует платформу. Не описывай core/ui или use cases как уже
   реализованные без проверки исходников.
3. Проверь направление project dependencies и доступность API для Android/Desktop.
   Для expect найди обе actual. Не добавляй iOS/web и domain-модуль по шаблону.
4. Новый/изменяемый бизнес-сценарий выделяй в *UseCase по правилам composeApp:
   зависимости через конструктор, без Compose/UiState/навигации. Подключи его к
   действующему владельцу состояния; не оставляй параллельно старую реализацию.
   API/парсинг остаются в data, системные операции — в platform.
5. Общий UI нескольких feature выделяй в core/ui только при реальных потребителях
   и в рамках задачи на общий UI. Проверь отсутствие зависимостей от feature/data,
   настройку обоих targets и ресурсов. Компоненты одного feature остаются в нём.
6. При изменении контракта проследи всех потребителей и тесты, совместимость
   сохранённых данных и обе actual. Обнови конкретный затронутый документ.
   Ограничь перенос затронутым сценарием и сохрани поведение при рефакторинге.
7. Для изменений кода/сборки запусти обе компиляции из корневого AGENTS.md и
   релевантные тесты. Для правок только инструкций достаточно проверки diff и ссылок.
   Отчитайся о границах, проверках и ограничениях; не меняй версии ради документации.

Источник: https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
