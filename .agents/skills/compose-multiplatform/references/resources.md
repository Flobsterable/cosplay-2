# Строковые ресурсы общего UI

Целевой шаблон v2. Зависимость compose.components.resources сейчас есть в
composeApp; для затронутого feature проверь и добавь её при задаче на ресурсы.
Файлы и generated Res ниже иллюстративные, пока не созданы в приложении.

В commonMain.dependencies нужного UI-модуля:

```kotlin
implementation(compose.components.resources)
```

Не обновляй версии библиотек. В том же модуле задай пакет генерации, чтобы
импорты примера были однозначны (пакет принадлежит feature/festival):

```kotlin
compose.resources {
    packageOfResClass = "ru.flobsterable.cosplay2.feature.festival.resources"
}
```

Не делай Res публичным для доступа из других модулей: строки компонента
принадлежат его модулю. Поддержку DSL проверь в установленном Compose-плагине.

src/commonMain/composeResources/values/strings.xml:

```xml
<resources>
    <string name="catalog_title">Фестивали</string>
    <string name="catalog_show_filters">Показать фильтры</string>
    <string name="catalog_city">Мой город: %1$s</string>
    <plurals name="catalog_result_count">
        <item quantity="one">Найден %1$d фестиваль</item>
        <item quantity="few">Найдено %1$d фестиваля</item>
        <item quantity="many">Найдено %1$d фестивалей</item>
        <item quantity="other">Найдено %1$d фестиваля</item>
    </plurals>
</resources>
```

Русские формы продублируй в values-ru/strings.xml: values — fallback,
а правила выбора quantity зависят от локали устройства. Для неподдерживаемой
локали с русским fallback корректность склонений не предполагается; проверь
реальный языковой контракт приложения перед миграцией существующего счётчика.
Не добавляй английский перевод без задачи.

Пример использования после генерации ресурсов:

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ru.flobsterable.cosplay2.feature.festival.resources.Res
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_city
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_result_count

@Composable
internal fun CatalogSummary(city: String, count: Int) {
    Text(text = stringResource(Res.string.catalog_city, city))
    Text(text = pluralStringResource(Res.plurals.catalog_result_count, count, count))
}
```

Первый count выбирает форму, второй подставляется в %1$d. Проверь 0, 1, 2, 5,
11, 21, 22 и длинное название города. Фраза хранится целиком; не собирай её
конкатенацией. Содержимое API не становится ресурсом. Core UI получает
сценарные тексты параметрами; собственные стандартные подписи хранит у себя.
После подключения ресурсов нужны обе компиляции и применимые UI-проверки.

API ресурсов сверены с [документацией Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html).
Совместимость с установленной версией подтверждается сборкой при внедрении.
