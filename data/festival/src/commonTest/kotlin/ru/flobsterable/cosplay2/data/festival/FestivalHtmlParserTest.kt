package ru.flobsterable.cosplay2.data.festival

import ru.flobsterable.cosplay2.model.FestivalActionLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FestivalHtmlParserTest {
    @Test
    fun readableContentRemovesExecutableAndVisualMarkup() {
        val content = extractReadableText(
            """<script>secretScript()</script><style>.hidden { color: red }</style>
                <h1>Фестиваль &amp; друзья</h1><p>Встреча&nbsp;в Омске</p><img src="poster.png">
                <ul><li>Косплей</li><li>Музыка</li></ul>""",
            "https://example.test"
        )
        assertTrue(content.contains("Фестиваль & друзья"))
        assertTrue(content.contains("Встреча в Омске"))
        assertTrue(content.contains("• Косплей"))
        assertTrue(content.contains("• Музыка"))
        assertFalse(content.contains("secretScript"))
        assertFalse(content.contains("color: red"))
        assertFalse(content.contains("poster.png"))
    }

    @Test
    fun contentContainerExcludesSurroundingNavigation() {
        val content = extractReadableText(
            """<nav>Навигация</nav><div class="content"><p>Описание фестиваля</p></div>
                <footer>Подвал</footer>""",
            "https://example.test"
        )
        assertEquals("Описание фестиваля", content)
    }

    @Test
    fun actionLinksResolvePathsIgnoreEmptyAnchorsAndDeduplicate() {
        val links = extractActionLinks(
            """<a href="#top">Вверх</a><a href="">Пусто</a><a href="/tickets">Билеты</a>
                <a href="/tickets">билеты</a><a href="rules">Правила</a>
                <a href="https://other.test">Сайт</a><a href="/image"><img src="x"></a>""",
            "https://example.test/"
        )
        assertEquals(
            listOf(
                FestivalActionLink("Билеты", "https://example.test/tickets"),
                FestivalActionLink("Правила", "https://example.test/rules"),
                FestivalActionLink("Сайт", "https://other.test")
            ),
            links
        )
    }

    @Test
    fun emptyPageHasNoContentOrActions() {
        assertEquals("", extractReadableText("", "https://example.test"))
        assertEquals(emptyList(), extractActionLinks("", "https://example.test"))
    }

    @Test
    fun inlineLinkKeepsItsLabelAndDestination() {
        val content = extractReadableText(
            """<a href="/tickets">Купить &amp; прийти</a>""",
            "https://example.test"
        )
        assertEquals("[[Купить & прийти|https://example.test/tickets]]", content)
    }
}
