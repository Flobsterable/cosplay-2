# App shell

- App.kt — composition root и владелец маршрутов. Screen получает данные и callbacks.
- Сохраняй ключи produceState по году/фестивалю, защиту catalogStateForYear,
  SaveableStateHolder и маршруты возврата из Favorites/Support.
- FavoritesState/MyCityState обновляют видимое состояние после успешной записи;
  сохраняй обработку ошибки и снятие флагов загрузки/записи при отмене.
- Проверки: обе компиляции composeApp и :composeApp:desktopTest;
  при изменении UI также :androidApp:lintDebug. Workflow: $compose-multiplatform,
  $kmp-testing. Не добавляй ViewModel или DI-фреймворк для локальной задачи.
