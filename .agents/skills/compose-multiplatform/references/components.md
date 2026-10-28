# Декомпозиция экрана и граница core UI

Целевые шаблоны v2, не существующие файлы. Имена и строки данных приходят извне.
Тексты интерфейса вызывающий UI получает из ресурсов, как описано в resources.md.
Компиляция этих фрагментов в рамках подготовки инструкций не выполнялась.

## Screen собирает компоненты

Предлагаемое размещение внутри feature/festival:

```text
ui/catalog/CatalogContent.kt
ui/catalog/components/CatalogHeader.kt
```

CatalogContent — содержимое успешного состояния, а не замена обработки
Loading/Error/Success в существующем Screen. Вход items — уже подготовленные данные.

```kotlin
package ru.flobsterable.cosplay2.feature.festival.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.flobsterable.cosplay2.feature.festival.ui.catalog.components.CatalogHeader
import ru.flobsterable.cosplay2.model.FestivalSummary

@Composable
internal fun CatalogContent(
    items: List<FestivalSummary>,
    heading: String,
    filtersLabel: String,
    listState: LazyListState,
    onOpenFilters: () -> Unit,
    onOpenFestival: (FestivalSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        CatalogHeader(heading, filtersLabel, onOpenFilters)
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(items = items, key = { it.id }) { festival ->
                TextButton(onClick = { onOpenFestival(festival) }) {
                    Text(text = festival.title)
                }
            }
        }
    }
}
```

Владелец создаёт listState через rememberLazyListState и сохраняет его в контексте
маршрута по существующим правилам. Пример предполагает ограниченную высоту контейнера.
Для реального каталога используй существующую карточку, сохраняя избранное и действия.

```kotlin
package ru.flobsterable.cosplay2.feature.festival.ui.catalog.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun CatalogHeader(
    heading: String,
    filtersLabel: String,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Text(
            text = heading,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge
        )
        TextButton(onClick = onOpenFilters) { Text(text = filtersLabel) }
    }
}
```

Граница компонента — самостоятельный блок с одним назначением. Screen/Route
не передаёт в Header repository, use case или весь state holder.

## Общий компонент core/ui

Следующий файл допустим после выделения core/ui для реальных потребителей
разных feature: core/ui/.../core/ui/components/button/ActionButton.kt.

```kotlin
package ru.flobsterable.cosplay2.core.ui.components.button

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text = label)
    }
}
```

Тонкая обёртка оправдана, если централизует реальное соглашение дизайн-системы;
сам этот пример не требует оборачивать все Material-компоненты. В core/ui
не должно быть API вида FestivalCard(festival: FestivalSummary, repository: ...).
Карточка фестиваля остаётся в feature, а общая кнопка получает подпись и callback.
Проверь темы, длинный текст, масштаб шрифта и клавиатуру; сохранение состояния
проверяется на подключённом экране, а не только на изолированном компоненте.
