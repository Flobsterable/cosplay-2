# Реализация экранов

Это техническое руководство: где находятся экраны, кто владеет состоянием,
как подключены переходы и как добавить новый UI. Описание продукта — что сделано,
какие экраны видит пользователь и какие флоу доступны — находится в [features.md](features.md).

## Карта реализации

| Экран | Composable и файл | Источник состояния |
| --- | --- | --- |
| Каталог | `FestivalsScreen`, `feature/festival/.../FestivalListScreen.kt` | `FestivalsRoute` в App.kt загружает каталог; поиск, категория и месяц находятся в UI |
| Детали | `FestivalDetailScreen`, `feature/festival/.../FestivalDetailScreen.kt` | `FestivalDetailRoute` вызывает `getFestivalDetail(festival)` по ключу `festival.id` |
| Обновление | `UpdateScreen`, `feature/update/.../UpdateScreen.kt` | `UpdateRoute` получает релиз и наблюдает `pendingUpdate`/`installState` платформенного launcher |
| Настройки | `SettingsScreen`, `feature/settings/.../SettingsScreen.kt` | Версия приходит из `AppVersion.versionName`; callbacks возврата и открытия поддержки передаёт `CosplayApp` |
| Поддержка | `SupportScreen`, `feature/settings/.../SupportScreen.kt` | `SupportChatState` создаётся в `CosplayApp`; черновик и флаги отправки — в UI |

Полный корень пакетов — `src/commonMain/kotlin/ru/flobsterable/cosplay2/` внутри
указанного модуля.

## Навигация и жизненный цикл

В `composeApp/.../App.kt` закрытый `AppRoute` содержит Festivals, FestivalDetails,
Update, Settings и Support. `AnimatedContent` выбирает экран. Экраны получают callbacks,
маршрут меняет `CosplayApp`. Кнопки возврата и Android `SystemBackHandler` ведут на
каталог; исключение — «Поддержка», откуда возврат идёт в «Настройки». Desktop-реализация
системного back handler не выполняет действий.

`CosplayApp` создаёт `rememberSaveableStateHolder()` и оборачивает экраны (кроме
`FestivalDetails`) в `SaveableStateProvider` с ключом по маршруту: `festivals`,
`update`, `settings`, `support`; для деталей ключ `details-<id>`, но сам экран
в holder не помещается. Благодаря этому `rememberSaveable`-состояние каталога
(поиск, категория, месяц, год) и черновик сообщения поддержки сохраняются при
переходах между экранами. `lastSuccessfulCatalog` в `FestivalsRoute` хранится
в `remember` и не переживает пересоздание composable, но фильтры восстанавливаются
из сохранённого состояния. Дискового кэша сетевых данных нет.

Загрузка каталога и деталей выполняется в `produceState`; смена выбранного года
перезапускает запрос каталога. `HttpClient` и репозитории создаются в `remember`,
клиент закрывается через `DisposableEffect`. Порядок запросов и преобразований
описан в [feature-flows.md](feature-flows.md).

## Порядок реализации экрана в Cosplay 2

Это руководство предлагает порядок добавления нового экрана по существующим образцам.
Новый экран ниже — пример, а не уже реализованная возможность приложения.

1. Выберите feature-модуль: интерфейс фестивалей находится в `feature/festival`,
   обновлений — в `feature/update`, настроек и поддержки — в `feature/settings`. Общий Compose-код пишется в `src/commonMain/kotlin`.
2. Определите входные данные экрана и callbacks пользовательских действий.
   Например, `FestivalDetailScreen` принимает `state: UiState<FestivalDetail>`,
   `client: HttpClient` и `onBack: () -> Unit`. Данные экрана и загрузка разделены:
   composable отображает переданное состояние, Route вызывает репозиторий.
3. Создайте composable `@Composable fun ...Screen(...)` и обработайте загрузку,
   ошибку, успех и пустой результат. `UiState` сейчас объявлен в `feature/festival`:
   `Loading`, `Success<T>(data)` и `Error(message)`. Это не общий тип из `core/model`.
4. Добавьте Route в `composeApp/.../App.kt`. По существующему образцу данные
   загружаются через `produceState`, затем передаются экрану. Задайте ключ загрузки
   по входному параметру: у деталей это `festival.id`, у списка — `selectedYear`.
5. Для отдельного перехода добавьте вариант в закрытый `AppRoute` и ветку `when`
   внутри `AnimatedContent`. Передавайте переходы callbacks, как `onFestivalSelected`
   или `onOpenSettings`. Проверьте поведение `SystemBackHandler`: возврат из поддержки
   ведёт в настройки, из остальных дочерних экранов — на список фестивалей.
6. Зависимости создавайте в `CosplayApp` и передавайте вниз. В текущем проекте
   нет обязательного ViewModel/DI/navigation framework для добавления экрана.
   HttpClient создаётся через `remember`, закрывается в `DisposableEffect`.
7. Проверьте Android и Desktop, состояния загрузки/ошибки/пустых данных и возврат.
   Команды компиляции приведены в [development.md](development.md).

## Компоненты экрана списка фестивалей

Образец — `feature/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/feature/festival/FestivalListScreen.kt`.
`FestivalsScreen` использует `Scaffold` с `TopAppBar` (заголовок «Фестивали» и кнопка
«Настройки», вызывающая `onOpenSettings`) и `LazyColumn`. Поиск представлен
`OutlinedTextField`, выбор года и категории — `FilterChip`, месяца — `MonthDropdown`.
Результаты показываются через `FestivalCard`. Состояния отображают `LoadingBlock`,
`ErrorBlock` и `EmptyBlock`; дополнительный верхний блок передаётся через `topContent`.
Запрос, категория и месяц хранятся через `rememberSaveable`; год приходит снаружи.
Фильтры работают над загруженным каталогом. Смена года запускает загрузку в Route.

Справа от поиска находится кнопка со значком фильтра. По умолчанию панель города,
категории, года и месяца скрыта; нажатие плавно раскрывает её вниз, повторное —
сворачивает. Выбранные параметры продолжают действовать при скрытой панели.
Состояние раскрытия, поиск, категория и месяц остаются у Screen в `rememberSaveable`;
смена года сбрасывает месяц. Компоненты `FestivalSearchBar` и `FestivalFilters`
находятся в `feature/festival/.../ui/catalog/components/`, получают данные и callbacks.
Подписи поиска, кнопки фильтра, выбора города и «Актуальные» берутся из Compose Resources
модуля `feature/festival`.

## Компоненты экрана деталей фестиваля

Образец — `feature/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/feature/festival/FestivalDetailScreen.kt`.
`FestivalDetailScreen` разбирает `UiState<FestivalDetail>`. Для успешного результата
используется `LazyColumn`, блок метаданных `MetaCard` и раскрываемый текст
`ExpandableSectionCard`. Кнопка возврата вызывает `onBack`; интерфейс использует
цвета и типографику `MaterialTheme`. Сам экран не вызывает `getFestivalDetail`:
это делает `FestivalDetailRoute` в `App.kt`.

## Переиспользование компонентов

В `FestivalComponents.kt` есть `InfoSectionCard`, `MetaLine`, `FilterChip`,
`MonthDropdown`, `TypeChip`, `LoadingBlock`, `ErrorBlock`, `EmptyBlock`, а также
компоненты карточек фестиваля. Многие объявлены `internal`: их можно использовать
внутри `feature/festival`, но нельзя напрямую импортировать в другой feature-модуль.
Для нового модуля сначала решите, нужен ли публичный API или выделение общего UI;
общего модуля дизайн-системы сейчас нет. `MonthDropdown` привязан к `MonthFilter`,
а карточки — к моделям фестивалей; это не универсальные виджеты для любых данных.

## Минимальный шаблон нового экрана

Пример для файла внутри `feature/festival` в пакете `ru.flobsterable.cosplay2.feature.festival`.
Это шаблон UI, а не готовый маршрут: источник `state` и переход нужно подключить в `App.kt`.

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ExampleScreen(state: UiState<String>, onBack: () -> Unit) {
    Column {
        TextButton(onClick = onBack) { Text("Назад") }
        when (state) {
            UiState.Loading -> LoadingBlock()
            is UiState.Error -> ErrorBlock(state.message)
            is UiState.Success -> {
                if (state.data.isBlank()) EmptyBlock() else Text(state.data)
            }
        }
    }
}
```

Модели и поля описаны в [data-model.md](data-model.md), загрузка и переходы —
в [feature-flows.md](feature-flows.md), зависимости модулей — в [architecture.md](architecture.md).

## Настройки и поддержка

`feature/settings` — отдельный KMP-модуль (android + desktop, Compose, зависимость
`kotlinx-coroutines-core`), подключённый в `composeApp`. `SettingsScreen` принимает
`versionName`, `onBack` и `onOpenSupport`; использует `Scaffold`/`TopAppBar`, карточку
«Поддержка» и строку версии. `SupportScreen` принимает `SupportChatState`, `onBack`
и suspend-callback `onSendMessage`; отображает сообщения `User`/`Assistant`,
ожидание ответа, ошибку и поле ввода с лимитом 4000 символов. Используются `Scaffold`,
`TopAppBar`, `LazyColumn`, `Card` и `OutlinedTextField`; автоскролл к последнему
сообщению выполняется через `snapshotFlow`.

`SupportChatState` содержит `messages`, `isAvailable`, `isSending` и `error`;
`SupportMessage` — `id`, `text`, `author` (`SupportAuthor.User`/`Assistant`).
В `CosplayApp` состояние создаётся с `isAvailable=false`, а `onSendMessage` — пустой
колбэк, поэтому отправка недоступна и экран показывает сообщение о недоступности
сервиса. Черновик хранится через `rememberSaveable` и сохраняется при навигации
благодаря `SaveableStateHolder`; при ошибке отправки текст остаётся для повтора.
Навигация и `SaveableStateHolder` находятся в `CosplayApp`.
