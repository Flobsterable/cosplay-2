# App shell

- App.kt — composition root и владелец маршрутов. Screen получает данные и callbacks.
- Сохраняй ключи produceState по году/фестивалю, защиту catalogStateForYear,
  SaveableStateHolder и маршруты возврата из Favorites/Support.
- FavoritesState/MyCityState обновляют видимое состояние после успешной записи;
  сохраняй обработку ошибки и снятие флагов загрузки/записи при отмене.
- Проверки: обе компиляции composeApp и :composeApp:desktopTest;
  при изменении UI также :androidApp:lintDebug. Workflow: $compose-multiplatform,
  $kmp-testing. Не добавляй ViewModel или DI-фреймворк для локальной задачи.

## Целевая организация бизнес-сценариев

### Use cases

- Новый или изменяемый бизнес-сценарий оформляй отдельным классом `*UseCase`
  с одним основным действием через `operator fun invoke` (suspend при необходимости).
  Правила отбора и обработки данных, валидация, координация repository/storage
  принадлежат use case; Screen отображает результат, Route/state holder запускает
  сценарий и переводит его результат в состояние UI.
- Use cases размещай в `composeApp/src/commonMain/kotlin/ru/flobsterable/cosplay2/`
  под `domain/<сценарий>/usecase/`, например `domain/festival/usecase/`.
  Это размещение использует существующие зависимости composition root. Не добавляй
  domain-модуль автоматически и не подключай composeApp к feature/data-модулям.
  Если сценарий нужен вне composeApp, отдельно обоснуй перенос его контракта и кода.
- Зависимости передавай через конструктор, используй существующие интерфейсы
  repository/storage. Создавай use cases в composition root и передавай владельцам
  состояния. Use case не зависит от Compose, UiState, навигации, строковых ресурсов,
  конкретного HttpClient или Android API; не владеет CoroutineScope и UI-состоянием.
  Сохраняй отмену и правила выбора dispatcher из раздела Compose и Coroutines.
- Загрузка и парсинг API, DTO mapping и механика кэша остаются в data; работа
  с файлами и системными API — в platform. Use case задаёт правила сценария,
  используя эти контракты. Вёрстка, раскрытие панели, фокус и форматирование
  текста для отображения остаются в UI и не требуют отдельных use cases.
- При доработке существующего бизнес-сценария выноси затронутую логику из
  Route/Screen/state holder в use case, сохраняя поведение остальных сценариев.
  Не создавай классы для каждого callback или UI-helper. Новые бизнес-правила
  проверяй содержательными тестами через подставные зависимости в существующей
  тестовой конфигурации.
