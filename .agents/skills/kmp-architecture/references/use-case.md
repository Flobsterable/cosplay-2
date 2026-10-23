# Use case и подключение к Route

Целевой шаблон v2. Файлы ниже ещё не созданы в приложении. Контракт
CosplayRepository и модели существуют; сортировка по startTime соответствует
текущей подготовке каталога в Route. При переносе убери дублирующую сортировку
из затронутого Route. Fallback, год и остальные поля каталога сохраняются.

## Бизнес-сценарий

Предлагаемый файл: composeApp/src/commonMain/kotlin/ru/flobsterable/cosplay2/
domain/festival/usecase/LoadFestivalCatalogUseCase.kt.

```kotlin
package ru.flobsterable.cosplay2.domain.festival.usecase

import ru.flobsterable.cosplay2.data.festival.CosplayRepository
import ru.flobsterable.cosplay2.model.FestivalCatalog

class LoadFestivalCatalogUseCase(
    private val repository: CosplayRepository
) {
    suspend operator fun invoke(year: Int): FestivalCatalog {
        val catalog = repository.getFestivalCatalog(year)
        return catalog.copy(festivals = catalog.festivals.sortedBy { it.startTime })
    }
}
```

Use case не создаёт клиент, scope, Compose-состояние и пользовательские сообщения.
Он не перехватывает ошибки: отмена распространяется естественно. Не оборачивай
вызов в runCatching с преобразованием CancellationException в ошибку/fallback.
Если сценарий не требует suspend, не добавляй suspend ради суффикса UseCase.

## Создание и вызов

Фрагмент внутри существующего composition root, где repository уже создан:

```kotlin
val loadCatalog = remember(repository) { LoadFestivalCatalogUseCase(repository) }
```

Передай loadCatalog в существующий FestivalsRoute вместо прямого вызова repository.
В его существующем produceState замени получение и сортировку каталога на:

```kotlin
val catalog = loadCatalog(requestYear)
```

Фрагменты предполагают импорты androidx.compose.runtime.remember и класса use case.
Это точки интеграции, а не полная замена App.kt: сохрани ключ года (и нового
экземпляра зависимости при её замене), catalogStateForYear, правила lastSuccessfulCatalog,
обработку отмены, UiState и возврат навигации. isFallback=true не записывается
как успешный серверный каталог. Не оставляй use case неиспользуемым рядом со старым кодом.

Чистое выделение должно сохранять поведение; новую валидацию года, фильтрацию
по текущей дате или изменение fallback не добавляй попутно. Проверки: оба target,
существующие тесты каталога и содержательный тест перенесённого правила сортировки.
