# Platform integrations

- Общие контракты находятся в commonMain; actual — androidMain и desktopMain.
  Проверяй обе реализации при изменении expect или storage-контракта.
- FavoritesStorage содержит блокирующие операции. Владелец вызывает их вне UI-потока.
  Сохраняй формат StoredFavorites, дедупликацию id, backup при reset и атомарность записи.
- Для ошибок storage не подменяй повреждённые данные успешным пустым результатом.
  Не обходи системное подтверждение Android-установки APK.
- Новую общую чистую логику тестируй в commonTest; файловую Desktop-логику — desktopTest.
  Запускай :core:platform:desktopTest и компиляции composeApp для Android/Desktop.
