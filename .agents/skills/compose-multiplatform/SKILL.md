---
name: compose-multiplatform
description: Implement or review Compose UI, state ownership, navigation and side effects for Android and Desktop in Cosplay 2.
---

# Compose Multiplatform

Прочитай AGENTS.md корня и затрагиваемого модуля, docs/screens.md и ближайший
Screen. Проверь Compose 1.12.2 в version catalog перед использованием нового API.

Выбери владельца состояния: Route/state holder владеет загрузкой и ошибками,
Screen получает state/callbacks, локальный ввод допускает rememberSaveable.
Переиспользуемый компонент принимает Modifier для корня. Используй MaterialTheme,
явные пустые/ошибочные состояния и доступные подписи для кнопок/иконок.

Проверь ключи effects, отмену при смене параметров, disposal принадлежащих ресурсов,
возврат навигации и сохранение фильтров. Не запускай работу в теле рекомпозиции.
Для Flow сначала определи lifecycle, sharing и Main dispatcher Desktop;
не добавляй lifecycle dependency автоматически.

Проверь обе компиляции, composeApp desktopTest и Android lintDebug. Новый UI
проверь существующим Desktop Compose test harness, если поведение требует теста.
Сообщи отдельно о ручной проверке внешнего вида, если она не выполнялась.

Источники: https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html
и https://developer.android.com/develop/ui/compose/side-effects
