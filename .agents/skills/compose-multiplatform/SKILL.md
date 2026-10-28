---
name: compose-multiplatform
description: Implement or review Compose screens, component decomposition, shared UI, resources and state ownership for Android and Desktop in Cosplay 2.
---

# Compose Multiplatform

При выделении компонентов читай [пример Screen и core UI](references/components.md),
при работе со строками — [пример ресурсов](references/resources.md).
Это целевые шаблоны v2, а не уже реализованные файлы; загружай нужный пример.

Прочитай AGENTS.md корня и правила на пути к файлу, включая feature/AGENTS.md
или core/AGENTS.md, затем docs/screens.md и ближайший аналог. Документация
описывает существующий код; целевая структура v2 задана инструкциями.
Проверь версию Compose в version catalog перед использованием нового API.

Screen собирает каркас и ветви состояния. Выделяй самостоятельные блоки в
ui/<экран>/components/, общие внутри feature — в ui/components/. Один компонент —
отдельный файл; не увеличивай сборники *Components.kt и не дроби каждый Text.
Для независимых компонентов разных feature применяй правила core/ui, сохраняя
бизнес-модели и сценарии в feature/use cases. Не создавай модуль ради одного экрана.

Выбери владельца состояния: Route/state holder владеет загрузкой и ошибками,
Screen получает state/callbacks, локальный ввод допускает rememberSaveable.
Переиспользуемый компонент принимает Modifier для корня. Используй MaterialTheme,
явные пустые/ошибочные состояния и доступные подписи для кнопок/иконок.
Бизнес-логику передавай use case через владельца состояния; компонент получает
только нужные данные/callbacks. Изменяемые списки имеют стабильные уникальные key;
при возврате сохраняются фильтры и прокрутка согласно контракту сценария.

Новые/изменяемые тексты и подписи доступности вынеси в общие строковые ресурсы
по корневым правилам. Проверь параметры фраз и plurals; не используй R.string
в commonMain. При необходимости подключи ресурсы только затронутого UI-модуля.

Проверь ключи effects, отмену при смене параметров, disposal принадлежащих ресурсов,
возврат навигации и сохранение фильтров. Не запускай работу в теле рекомпозиции.
Для Flow сначала определи lifecycle, sharing и Main dispatcher Desktop;
не добавляй lifecycle dependency автоматически.
Проверь повторные действия у владельца, устаревшие ответы и сброс флагов при
отмене. При извлечении компонентов сохрани ключи effects и rememberSaveable.

Проверь обе компиляции, composeApp desktopTest и Android lintDebug. Новый UI
проверь существующим Desktop Compose test harness, если поведение требует теста.
Проверь тему, семантику и клавиатуру Desktop; для внешнего вида учитывай узкое
окно, длинный текст, увеличенный шрифт и обе темы. В отчёте отделяй выполненную
ручную проверку от непроверенного; компиляция не подтверждает внешний вид.
При изменении только инструкций выполняй проверки Markdown вместо сборки UI.

Источники: https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html
и https://developer.android.com/develop/ui/compose/side-effects
