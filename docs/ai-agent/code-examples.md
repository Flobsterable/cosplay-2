# Справочник примеров для агента

Примеры дополняют действующий AGENTS.md. Пути приведены от корня репозитория.
Существующий код не отменяет целевые правила организации новых изменений.

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

## Шаблон типового UI-файла

Предлагаемый шаблон, не существующий экран и не выполненная пробная генерация.
Размещать в `feature/festival/src/commonMain/kotlin/ru/flobsterable/cosplay2/feature/festival/ui/components/`
только когда задача действительно требует нового компонента. Использованы уже
доступные этому модулю зависимости; API/репозитория внутри UI нет.

```kotlin
package ru.flobsterable.cosplay2.feature.festival.ui.components

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
