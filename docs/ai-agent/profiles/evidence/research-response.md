# Результат Research, попытка 1

Источник: финальный ответ независимого агента `/root/research_trial` в этой сессии
24 октября 2026 года. Ниже сохранена содержательная запись результата;
это отчёт ответа, не полный машинный журнал вызовов инструментов.
Пути относительно корня проекта.

## Краткий ответ

Приложение получает фестивали с cosplay2.ru, сведения об обновлении — из GitHub
Releases. Дополнительно загружаются изображения и Android APK. В исследованных
тестах найдено косвенное покрытие отмены запросов фестивалей через подставной
CosplayApi; прямых тестов HTTP-контрактов найденных эндпоинтов не обнаружено.
Динамическое покрытие не измерялось.

## Файлы и эндпоинты

| Контракт | Файл:строка | Назначение |
| --- | --- | --- |
| POST `https://cosplay2.ru/api/events/filter_list` | `data/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/data/festival/CosplayApi.kt:41` | JSON-тело `wid`, `active_year` (45–46), список каталога |
| GET `https://cosplay2.ru/api/events/get_types` | `CosplayApi.kt:54` в том же пакете | Категории, связываемые через eventTypeId |
| GET `https://cosplay2.ru/archive/{year}` | `data/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/data/festival/CosplayRepositoryImpl.kt:21`, `:80`; GET в `CosplayApi.kt:63` | HTML с JSON-LD для каталога и деталей |
| GET `https://cosplay2.ru/` | `CosplayRepositoryImpl.kt:81` | JSON-LD для деталей, если год не распознан; кэш корневых схем в `CosplayApi.kt:59`, `:74` |
| GET `{festival.url}/get_pages/home` | `CosplayApi.kt:82`, вызов из `CosplayRepositoryImpl.kt:83` | Текст и ссылки страницы фестиваля |
| GET `https://api.github.com/repos/{owner}/{repo}/releases/latest` | `data/update/src/commonMain/kotlin/ru/flobsterable/cosplay2/data/update/GitHubReleaseApi.kt:17` | Accept/User-Agent на 18–19; owner/repo из `composeApp/src/commonMain/kotlin/ru/flobsterable/cosplay2/App.kt:89` |
| GET `{imageUrl}` | `core/platform/src/androidMain/kotlin/ru/flobsterable/cosplay2/platform/NetworkImageLoader.android.kt:14`, аналогичный `desktopMain/.../NetworkImageLoader.desktop.kt:14` | URL из JSON-LD либо `/files/{eventId}/logo.png` (`CosplayRepositoryImpl.kt:166`) |
| Загрузка `{apkUrl}` через DownloadManager | `core/platform/src/androidMain/kotlin/ru/flobsterable/cosplay2/platform/AppUpdateLauncher.android.kt:221`, `:230` | URL GitHub asset, отдельный путь вне Ktor |

Внешние страницы отделены от HTTP API: Desktop открывает APK/релиз браузером
(`core/platform/src/desktopMain/kotlin/ru/flobsterable/cosplay2/platform/AppUpdateLauncher.desktop.kt:28`).
Экран деталей открывает ссылки, календарь и сайт фестиваля
(`feature/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/feature/festival/FestivalDetailScreen.kt:93`,
`:103`, `:112`). URL формы календаря создаётся в `FestivalFormatting.kt:104`
того же пакета; интеграции Calendar API нет.
Сервис поддержки не подключён: `App.kt:95`,
`feature/settings/src/commonMain/kotlin/ru/flobsterable/cosplay2/feature/settings/SupportScreen.kt:44`.

## Связи

- `CosplayApp` создаёт общий клиент и `CosplayRepositoryImpl(CosplayApiImpl(client))`
  (`App.kt:83–85`).
- Каталог: `FestivalsRoute → FestivalCatalogCache → getFestivalCatalog(year) →`
  JSON-LD архива → список → типы → `FestivalCatalog → FestivalsScreen`.
  Подтверждения: `App.kt:297`, `:304`, `:314`; `CosplayRepositoryImpl.kt:21`, `:26`, `:38`.
  Ошибка списка даёт демонстрационный fallback (`:28`); ошибка схем или типов
  не препятствует использованию основного ответа.
- Детали: `FestivalDetailRoute → getFestivalDetail →` схема архива/главной
  страницы + `/get_pages/home → FestivalDetailScreen` (`App.kt:389`, `:391`,
  `:399`; `CosplayRepositoryImpl.kt:75`).
- Обновление: `GitHubReleaseApiImpl → GitHubUpdateRepository →` выбор APK →
  `AppUpdateInfo →` баннер/экран → launcher. Выбор asset —
  `data/update/src/commonMain/kotlin/ru/flobsterable/cosplay2/data/update/UpdateRepository.kt:15`,
  `:18`, `:29`; потребители — `App.kt:289`, `:329`, `:359`, `:366`, `:371`.
- Android — OkHttp (`core/network/src/androidMain/kotlin/ru/flobsterable/cosplay2/network/HttpClientFactory.android.kt:17`),
  Desktop — CIO (`desktopMain/.../HttpClientFactory.desktop.kt:16`).

## Покрытие и выводы

| Контракт | Тест | Что действительно проверено |
| --- | --- | --- |
| Список, типы и схемы каталога | `composeApp/src/desktopTest/kotlin/ru/flobsterable/cosplay2/FestivalCancellationTest.kt:34` | Отмена стадий реального repository через подставной CosplayApi (`:17`), без HTTP |
| Схемы и страница деталей | тот же тест:41 | Отмена обеих стадий; URL, HTTP и HTML не проверяются; ветка нераспознанного года не проверяется отдельно |
| Кэш каталога | `composeApp/src/desktopTest/kotlin/ru/flobsterable/cosplay2/FestivalCatalogCacheTest.kt:22`, `:38`, `:51`, `:64`, `:86` | Повторное использование, смена года, отмена, fallback, ошибки; подставная загрузочная функция |
| GitHub Releases | Прямого теста не найдено | URL, заголовки, десериализация, выбор APK не подтверждены HTTP-тестами |
| Изображения и APK | Прямых сетевых тестов не найдено | Создание клиента UI-тестом само по себе не проверка HTTP-контракта |

Например, `FavoriteIconTest.kt:37` передаёт экрану Loading, карточка `:66` не содержит
изображения. Следующие полезные проверки: метод/URL/тело/заголовки, десериализация,
JSON-LD и HTML на фиксированных примерах, выбор APK. Это отдельная задача на тесты.

Найдено расхождение документации: раздел навигации `docs/feature-flows.md` описывает
отсутствие сохранения фильтров и повторную загрузку после возврата. Фактический
`App.kt:85`, `:94`, `:294`, `:301` использует кэш и сохраняемое состояние;
существующие тесты кэша это учитывают. Research документ не исправлял.

## Ограничения

Исследованы Android/Desktop, Gradle, тесты всех десяти модулей и относящиеся
документы. Исключены build, архив `docs/ai-agent/v1`, генерируемые данные.
Упоминаний MockEngine, MockWebServer, WireMock, testApplication, JaCoCo и Kover
в исходниках, конфигурации и workflows не найдено. Это статическое исследование,
а не динамическое измерение покрытия или доступности серверов.

Сборка, тесты, генераторы, сетевые запросы не запускались; файлы исследованием
не менялись. В начале уже были новые профили/установщик, к концу появились
AppVersionComparisonTest.kt и evidence от других агентов. Полную неизменность
общего дерева при параллельной записи утверждать нельзя; эти изменения
Research себе не приписывает.
