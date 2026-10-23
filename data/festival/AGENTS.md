# Festival data

- Перед изменением контракта прочитай docs/festival-api.md и docs/data-model.md.
- Выбранный год передаётся в getFestivalCatalog; UI-фильтры не создают новых запросов.
- Сохраняй нормализацию URL, значение isFallback и разделение ошибки списка/типов.
- catchingFestivalRequest приватен своему файлу и всегда пробрасывает отмену;
  не заменяй его обычным runCatching вокруг suspend-запросов.
- Проверяй CancellationTest и CatalogCacheTest из composeApp desktopTest,
  обе компиляции приложения; обновляй конкретный изменённый API-контракт в docs.
