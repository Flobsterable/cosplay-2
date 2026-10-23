# Cosplay 2 — инструкции для AI-агента, v1

Проверено по исходникам и официальной документации 23 октября 2026 года.
Файл предназначен для Codex и действует на весь репозиторий. Это первая версия
правил. Конфигурация и результаты первого эксперимента хранятся отдельно в
`docs/ai-agent/v1/`; после эксперимента правила v1 не корректируются.

## Перед изменениями

- Прочитай `README.md`, `docs/architecture.md` и документы затрагиваемого сценария.
  Продуктовые контракты ревью находятся в `docs/review-rules.md`.
- Сверяй описание с исходниками и `build.gradle.kts`: документация может отставать.
  Например, фактический `AppRoute` уже содержит Favorites и возврат из деталей
  в избранное, хотя обзор архитектуры перечисляет не все маршруты.
- Проверь рабочие изменения; сохраняй чужую работу. Делай минимальные изменения
  по задаче, без попутной миграции стека, архитектуры и форматирования всего проекта.
- Не добавляй новые модули, библиотеки, платформы или инструменты качества без
  необходимости для задачи. Не коммить, не публикуй релиз и не создавай ветку
  без соответствующего запроса пользователя.
- Перед изменением файла читай вложенные AGENTS.md на пути к нему, даже если
  сессия запущена из корня. Для релевантной задачи загружай skill из `.agents/skills/`.

После отдельной миграции сборки AGP 9 сведения о версиях и Android-командах
актуализированы. Исходные инструкции эксперимента v1 сохранены в архиве
`docs/ai-agent/v1/configuration-snapshot.zip`; это не итерация улучшения правил.

## Фактический стек

Версии берутся из `gradle/libs.versions.toml`, а не из примеров свежей документации:

| Компонент | Версия / настройка |
| --- | --- |
| Kotlin Multiplatform и Compose compiler plugin | 2.4.21 |
| Compose Multiplatform / Material 3 | Compose 1.12.2; Material 3 через Compose dependencies |
| Ktor Client | 2.3.12; OkHttp для Android, CIO для Desktop |
| kotlinx.serialization JSON | 1.7.3 |
| kotlinx.datetime | 0.6.1 |
| kotlinx.coroutines | 1.8.0 |
| Android Gradle Plugin | 9.3.3 |
| Android | minSdk 28, compileSdk 37, targetSdk 35 |
| JVM | toolchain и bytecode target 17 |
| Gradle Wrapper | 9.7.0 в `gradle/wrapper/gradle-wrapper.properties` |

Цели — Android и Desktop JVM (`jvm("desktop")`). iOS и web не настроены.
Перечисление версий фиксирует конфигурацию, но не доказывает её совместимость
или успешную сборку. При ошибке toolchain сообщи точную причину; не обновляй
Gradle/AGP/Kotlin автоматически в рамках несвязанной задачи.

## Архитектура и расположение кода

Все десять модулей объявлены в `settings.gradle.kts`:

| Путь / модуль | Ответственность и внутренние зависимости |
| --- | --- |
| `androidApp` | Android MainActivity, manifest/resources, signing и APK; зависит от composeApp |
| `composeApp` | Desktop entry point, общий UI, тема, маршруты, state holders, composition root; подключает восемь core/data/feature модулей |
| `core/model` | DTO и общие модели; без зависимостей от других модулей проекта |
| `core/network` | Фабрика HttpClient; зависит от `core/platform` |
| `core/platform` | Хранилища, изображения, логирование, версия, back handler, запуск обновлений; зависит от `core/model` |
| `data/festival` | CosplayApi, HTML/JSON-LD парсинг, CosplayRepository; зависит от `core/model`, `core/network`, `core/platform` |
| `data/update` | GitHub Releases API и repository; зависит от `core/model`, `core/network` |
| `feature/festival` | Каталог, детали, избранное, фильтры и UiState; зависит от `core/model`, `core/platform` |
| `feature/update` | Баннер и экран обновления; зависит от `core/model` |
| `feature/settings` | Настройки, выбор города, UI поддержки; без внутренних project dependencies |

Общий код: `<module>/src/commonMain/kotlin/ru/flobsterable/cosplay2/`.
Платформенные реализации: `src/androidMain` и `src/desktopMain`.
Тесты уже есть в `composeApp/src/desktopTest`, `core/platform/src/commonTest`
и `core/platform/src/desktopTest`. Документация — `docs/`, workflows — `.github/workflows/`.

Поток данных: API → repository → Route/state holder → Screen; события UI
возвращаются через callbacks. `CosplayApp` в `App.kt` вручную создаёт зависимости
через `remember`, закрывает общий HttpClient через `DisposableEffect` и управляет
`AppRoute`/`AnimatedContent`. Каталог и детали загружаются через `produceState`.
`FavoritesState` и `MyCityState` — существующие владельцы состояния.
ViewModel, DI-фреймворк и отдельный domain/use-case модуль сейчас не используются.
Не представляй MVVM/Clean Architecture как уже реализованную архитектуру.
Рекомендации Android о слоях и однонаправленном потоке применяй к этим владельцам
состояния; вводить ViewModel или use cases следует отдельным обоснованным изменением.

### Границы и продуктовые контракты

- Screen отображает данные и вызывает callbacks. Запросы конкретного API и
  преобразования DTO принадлежат data-модулям; общие модели — `core/model`.
  Существующая передача HttpClient для изображений не разрешает переносить
  загрузку каталога в UI. Не создавай циклические зависимости и не обращайся
  к `internal` объявлениям другого модуля.
- В `commonMain` используй API, доступные всем настроенным целям. Android SDK,
  файловые и JVM-интеграции размещай в платформенных source sets. Следуй
  существующим интерфейсам и expect/actual; для нового expect добавь actual
  на обеих платформах. Не создавай ручную иерархию source sets без необходимости.
- Выбранный год определяет запрос каталога; «Актуальные» используют текущий год.
  Месяц, категория и поиск фильтруют загруженные данные; смена года сбрасывает месяц.
  Сохраняй защиту от отображения старого года в `catalogStateForYear`.
- Демонстрационный fallback с `isFallback=true` не является последним успешным
  ответом сервера. Не сохраняй его как успешный каталог. Детали кэшируются
  по нормализованному URL; не предполагай наличие SQL-базы или собственного backend.
- Сохраняй Loading/Error/Success и отдельное отображение пустого результата.
  В UiState нет отдельного типа Empty: пустота определяется успешными данными.
  Учитывай возврат Support → Settings и Details → Favorites при `fromFavorites`.
- Android-обновление скачивает APK и открывает системную установку; Desktop
  открывает ссылку. Секреты подписи и значения локальных настроек не попадают в Git/логи.
  Версия приложения хранится в `gradle.properties`, релизный тег — `v<APP_VERSION_NAME>`.

## Kotlin и naming conventions

- Четыре пробела; `UpperCamelCase` для классов, интерфейсов и файлов главного типа;
  `lowerCamelCase` для функций/свойств; `UPPER_SNAKE_CASE` для констант.
  Composable-функции, возвращающие Unit, называй `UpperCamelCase`.
- Сохраняй пакет `ru.flobsterable.cosplay2` и фактические подпакеты: `model`,
  `network`, `platform`, `data.festival`, `data.update`, `feature.*`.
  Платформенные файлы следуют существующему шаблону `Name.android.kt`/`Name.desktop.kt`.
- Следуй существующим именам `*Api`/`*ApiImpl`, `*Repository`/`*RepositoryImpl`,
  `*Screen`, `*Route`, `*State`, `*Dto`, если они соответствуют роли объявления.
- Используй `val`, неизменяемые входные модели, точные типы и минимальную видимость.
  Изменяемое состояние закрывай через `private set` или приватное поле.
  Nullable значения обрабатывай явно; не вводи `!!` без доказанного инварианта.
- В файле: package → явные imports → основное объявление → связанные private helpers.
  В классе: свойства/init → дополнительные конструкторы → связанные методы → companion.
  Новые wildcard imports не добавляй; существующий порядок не переформатируй массово.
  Kotlin использует видимость объявлений, отдельного блока exports нет.

## Compose и Coroutines

- Поднимай состояние к ближайшему общему владельцу; передавай данные вниз,
  callbacks вверх. Для новых переиспользуемых компонентов предоставляй
  `modifier: Modifier = Modifier` и применяй его к корневому UI-элементу.
- `remember` хранит объекты между рекомпозициями; `rememberSaveable` используй
  для сохраняемого UI-состояния с поддерживаемым типом/Saver. Не обещай сохранение
  текущего AppRoute после уничтожения процесса: он хранится через remember.
- Запускай загрузки в `LaunchedEffect`/`produceState` с ключами входных параметров.
  Для действий пользователя используй `rememberCoroutineScope`; освобождай
  принадлежащие владельцу ресурсы в `DisposableEffect`. Не закрывай переданный извне клиент.
- Сохраняй structured concurrency и пробрасывай `CancellationException` перед
  обработкой остальных ошибок. `runCatching` сам по себе не сохраняет отмену.
- Блокирующие операции storage выноси с UI-потока через `withContext(Dispatchers.IO)`;
  при необходимости тестируемости передавай dispatcher зависимостью. Само `suspend`
  не делает блокирующую функцию неблокирующей. Ktor suspend-запросам не нужен
  дополнительный IO-dispatcher только из-за наличия сети.
- Не добавляй lifecycle API из свежих примеров без проверки совместимости с
  Compose 1.12.2 и текущими зависимостями. Если задача вводит Flow в UI,
  выбирай сбор с учётом lifecycle обеих платформ и наличия Main dispatcher на Desktop.
- Если нужен Flow, наружу отдавай Flow/StateFlow, сохраняя MutableStateFlow приватным.
  Сначала определи владельца, начальное состояние и политику sharing. Используй
  update для атомарной смены StateFlow; не превращай события в теряемые сообщения
  без явного контракта. Не запускай новый collect при каждой рекомпозиции.
  Flow.catch обрабатывает upstream; ошибки обработчика collect обрабатывай отдельно.

## Пять образцов из текущего проекта

Ниже короткие выдержки; полные реализации остаются в исходниках. Заимствуй
указанное свойство примера, а не все решения окружающего файла.

### 1. Явный контракт repository

`data/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/data/festival/CosplayRepository.kt`:

```kotlin
interface CosplayRepository {
    suspend fun getFestivalCatalog(year: Int): FestivalCatalog
    suspend fun getFestivalDetail(festival: FestivalSummary): FestivalDetail
}
```

Зависимость выражена интерфейсом, параметр года явный, результат типизирован.

### 2. Исчерпывающее состояние UI

`feature/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/feature/festival/UiState.kt`:

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```

Используй исчерпывающий `when`, чтобы новое состояние не осталось без UI.

### 3. Ошибка запроса не поглощает отмену

`data/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/data/festival/CosplayRepositoryImpl.kt`:

```kotlin
private inline fun <T> catchingFestivalRequest(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
```

В исходнике импортирован `kotlin.coroutines.cancellation.CancellationException`.
Helper приватный: не вызывай его из другого файла. Поведение покрывает
`composeApp/src/desktopTest/kotlin/ru/flobsterable/cosplay2/FestivalCancellationTest.kt`.

### 4. Ресурс освобождается владельцем

`composeApp/src/commonMain/kotlin/ru/flobsterable/cosplay2/App.kt`, фрагменты CosplayApp:

```kotlin
val client = remember { createHttpClient() }
```

```kotlin
DisposableEffect(Unit) {
    onDispose {
        client.close()
    }
}
```

Клиент здесь стабилен на время композиции. Для заменяемого ресурса ключ эффекта
должен отслеживать сам ресурс.

### 5. Запись перед обновлением видимого состояния

`composeApp/src/commonMain/kotlin/ru/flobsterable/cosplay2/MyCityState.kt`, метод:

```kotlin
suspend fun save(value: String) = mutex.withLock {
    isSaving = true
    try {
        val normalized = normalizeCity(value)
        withContext(Dispatchers.IO) { storage.write(normalized) }
        city = normalized
    } finally {
        isSaving = false
    }
}
```

Записи сериализованы Mutex, UI обновляется после успешного сохранения, флаг
сбрасывается также при ошибке или отмене. Поля и импорты смотри в исходном классе.

## Пять запрещённых антипаттернов для нового кода

1. `catch (Exception)` или `runCatching` вокруг suspend-операции с превращением
   отмены в Error/fallback. Сначала пробрасывай CancellationException.
2. `GlobalScope.launch`, бесхозный CoroutineScope или `runBlocking` в UI/рабочем
   suspend-коде. Привязывай работу к владельцу; runBlocking допустим в существующих JVM-тестах.
3. API-запрос каталога, запись storage или создание HttpClient прямо в теле Screen
   при рекомпозиции. Используй data-слой, владельца состояния и Compose effects.
4. Android/JVM API в общем коде, обход internal-видимости или циклическая
   зависимость модулей. Сохраняй платформенные границы и существующее направление связей.
5. `println`/`printStackTrace` как прикладное логирование, секреты или чувствительные
   данные в логах. Используй AppLogger с безопасным содержимым; существующий
   `LogLevel.ALL` в HttpClientFactory не является образцом для логирования секретов.

## Шаблон типового UI-файла

Предлагаемый шаблон, не существующий экран и не выполненная пробная генерация.
Размещать в `feature/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/feature/festival/`
только когда задача действительно требует нового компонента. Использованы уже
доступные этому модулю зависимости; API/репозитория внутри UI нет.

```kotlin
package ru.flobsterable.cosplay2.feature.festival

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FestivalActionCard(
    title: String,
    actionLabel: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = title)
        Button(onClick = onOpen) {
            Text(text = actionLabel)
        }
    }
}
```

## Проверка изменений

Запускай команды из корня через Wrapper. В PowerShell:

```powershell
.\gradlew.bat :composeApp:compileKotlinDesktop
.\gradlew.bat :composeApp:compileAndroidMain
.\gradlew.bat :composeApp:desktopTest :core:platform:desktopTest
.\gradlew.bat :androidApp:lintDebug
```

Компиляция проверяет обе настроенные платформы; выбирай релевантные тесты
по изменению. `lintDebug` — Android Lint, а не проверка Kotlin formatting.
Detekt/ktlint/Spotless в просмотренных Gradle-файлах не настроены: не заявляй,
что они прошли. Для теста отдельного класса используй `--tests` у desktopTest.
Существующие тесты проверяют отмену запросов, кэш, избранное, город и Compose UI.
Проверяй ошибку, пустой ответ, смену входных параметров и отмену для затронутого сценария.
UI-тестам Desktop может понадобиться графическая среда.

В отчёте укажи фактически выполненные проверки и причины пропусков. При изменении
поведения обнови конкретный затронутый документ из `docs/`. Изменение только
этого Markdown-файла не требует сборки приложения. Не выдавай наличие команд
в инструкции за успешный запуск.

## Глобальные, проектные и локальные правила (конфигурация v1)

- Личный глобальный `~/.codex/AGENTS.md`: язык общения, общие предпочтения,
  работа с секретами и формат отчёта. Не помещай туда версии и контракты Cosplay 2.
  В рамках настройки v1 файл создан; версионируемая копия находится в
  `docs/ai-agent/global/AGENTS.md`. Стек конкретного приложения остаётся в корне проекта.
- Корневой `AGENTS.md` (этот файл): стек, связи модулей, единые conventions,
  источники и проверки. Он хранится в Git вместе с проектом.
- Вложенный `<module>/AGENTS.md` при необходимости: только дополнительные правила
  соответствующего поддерева. Более глубокие правила уточняют родительские;
  прямые инструкции пользователя и системные ограничения имеют приоритет.
  Локальные правила установлены в `composeApp`, `data/festival`, `core/platform`
  и `feature/festival`.
- Предлагаемые места при росте правил: `data/festival/AGENTS.md` для контрактов
  API/парсинга; `core/platform/AGENTS.md` для actual и storage;
  `feature/festival/AGENTS.md` для UI/фильтров.
  Не дублируй весь корневой документ. Для ревью учитывай действующую версию
  `docs/review-rules.md` согласно описанному там порядку изменения правил.

## Субагенты и skills v1

Рабочие роли объявлены в `.codex/agents/code-reviewer.toml` и
`.codex/agents/test-engineer.toml`. Формат проверяется установленным Codex;
при запуске новой сессии роли обнаруживаются автоматически. Для небольшой
задачи достаточно одного агента. При независимых работах основной агент может
разделить чтение и проверку по следующим ролям:

| Роль | Область | Ожидаемый результат |
| --- | --- | --- |
| KMP reviewer | Gradle, core/network, core/platform | Границы source sets, обе actual, риски сборки |
| Data reviewer | data/festival, data/update, модели | Контракты API, парсинг, fallback, cancellation |
| Compose reviewer | composeApp, feature/* | Владение состоянием, effects, навигация, UI-состояния |
| Verification reviewer | Существующие тесты и docs | Регрессии, реальные результаты команд, расхождения документации |

Основной агент задаёт границы работы, сверяет выводы и интегрирует результат.
Параллельное редактирование одних файлов не назначать; результаты другого агента
проверять по diff и тестам. Если инструмент субагентов недоступен, выполнять эти
проверки последовательно и не заявлять, что субагенты запускались.

Установлены `.agents/skills/<name>/SKILL.md`: `kmp-architecture`,
`kotlin-code-style`, `compose-multiplatform`, `kmp-testing`. Выбирай их по описанию
задачи; skill задаёт workflow и ссылки на фактические файлы, а не копию архитектуры.
Общие личные skills остаются вне проекта. `code-reviewer` выполняет независимое
ревью без редактирования; `test-engineer` проверяет тесты и команды с явным
разрешением основной задачи на добавление тестов. Оба наследуют разрешения родителя.
Упомянутый в `docs/dev-assistant.md` внешний cosplay-dev-assistant — отдельный
инструмент; его конфигурация здесь не исследована и не изменяется.

## Официальные источники и их применение

- [Codex: AGENTS.md](https://developers.openai.com/codex/guides/agents-md)
  — глобальные и вложенные инструкции, порядок обнаружения.
- [Codex: skills](https://developers.openai.com/codex/skills)
  — repo skills в `.agents/skills`, обязательные name/description.
- [Codex: subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents)
  — project agents в `.codex/agents/*.toml`.

Актуальные страницы просмотрены 23 октября 2026 года. Свежие примеры могут
использовать более новые версии, чем этот проект; проверяй доступность каждого API.

- [JetBrains: KMP source set hierarchy](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html)
  — общий код и платформенные source sets, стандартная иерархия.
- [JetBrains: Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
  — naming, форматирование, структура объявлений.
- [Android Developers: architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
  — слои, repository, однонаправленный поток; рекомендации адаптируются к текущей архитектуре.
- [JetBrains: Compose Multiplatform lifecycle](https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html)
  — общий lifecycle и особенности Main dispatcher на Desktop при внедрении lifecycle API.
- [Android Developers: Compose side effects](https://developer.android.com/develop/ui/compose/side-effects)
  — ключи effects, запуск работы и освобождение ресурсов.
- [JetBrains: Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html)
  — scope и structured concurrency.
- [JetBrains: Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html)
  — распространение ошибок и отмены.
