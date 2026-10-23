# Управляемая отмена запроса

Целевой шаблон для composeApp/src/desktopTest. Он предполагает внедрение
LoadFestivalCatalogUseCase из [примера use case](../../kmp-architecture/references/use-case.md).
Класс use case пока не существует в приложении; этот Markdown не является
исполняемым тестом. Используются имеющиеся kotlin.test и kotlinx.coroutines,
без kotlinx-coroutines-test и Thread.sleep.

```kotlin
package ru.flobsterable.cosplay2

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.flobsterable.cosplay2.data.festival.CosplayRepository
import ru.flobsterable.cosplay2.domain.festival.usecase.LoadFestivalCatalogUseCase
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalDetail
import ru.flobsterable.cosplay2.model.FestivalSummary

class LoadFestivalCatalogUseCaseTest {
    @Test
    fun cancelledRequestDoesNotReturnFallbackAndNextYearCanLoad() = runBlocking {
        withTimeout(5_000) {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val firstFinished = CompletableDeferred<Unit>()
            val requestedYears = mutableListOf<Int>()
            val repository = object : CosplayRepository {
                override suspend fun getFestivalCatalog(year: Int): FestivalCatalog {
                    requestedYears += year
                    if (year == 2025) {
                        firstStarted.complete(Unit)
                        try {
                            releaseFirst.await()
                        } finally {
                            firstFinished.complete(Unit)
                        }
                    }
                    return FestivalCatalog(emptyList(), emptyMap(), year, false)
                }

                override suspend fun getFestivalDetail(
                    festival: FestivalSummary
                ): FestivalDetail = error("Detail request is outside this test")
            }
            val loadCatalog = LoadFestivalCatalogUseCase(repository)
            var firstReturned = false
            val first = async {
                loadCatalog(2025).also { firstReturned = true }
            }
            firstStarted.await()
            first.cancelAndJoin()
            firstFinished.await()

            val second = loadCatalog(2026)
            assertTrue(first.isCancelled)
            assertFalse(firstReturned)
            assertEquals(listOf(2025, 2026), requestedYears)
            assertEquals(2026, second.year)
            assertFalse(second.isFallback)
        }
    }
}
```

Deferred управляет порядком событий; timeout только ограничивает зависший тест.
firstReturned проверяет, что use case не проглотил отмену и не вернул fallback.
runBlocking допустим здесь в JVM-тесте, а не в UI. Это проверка use case,
она не доказывает защиту состояния Route от устаревшего результата.

## Проверка гонки у реального владельца состояния

При доработке Route/state holder используй его настоящий API, а не тестовую
копию реализации. Управляемая зависимость должна позволять такой порядок:

1. Начать запрос A и дождаться сигнала startedA.
2. Сменить год/фестиваль и начать B, дождаться startedB.
3. Завершить B, проверить отображение данных B.
4. Разрешить поздний ответ или ошибку A, если контракт допускает их доставку;
   проверить, что данные/ошибка A не заменили B и finally A не сбросил флаги B.

Для отменяемой зависимости отдельно проверь, что A отменён при смене ключа.
Не подавляй CancellationException в production ради воспроизведения теста.
Добавь отдельный случай завершения A, пока B ещё загружается, для проверки флага B.
UI-вариант запускай через существующий Desktop Compose harness с контролируемой
зависимостью. Тест storage использует временные файлы и проверяет старый формат,
сбой записи и сохранность backup по реальному контракту, а не текст исключения.
