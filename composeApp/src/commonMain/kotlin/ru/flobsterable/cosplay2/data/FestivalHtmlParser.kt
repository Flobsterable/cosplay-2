package ru.flobsterable.cosplay2.data

internal fun extractReadableText(html: String, baseUrl: String): String {
    val focusedHtml = extractPrimaryContent(html)

    val withoutScripts = focusedHtml
        .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<link[^>]*>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE), " ")

    val adapted = withoutScripts
        .replace(Regex("""<a[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val href = resolveUrl(it.groupValues[1].trim(), baseUrl)
            val label = htmlToPlainText(it.groupValues[2]).trim()
            if (label.isBlank() || href.isBlank()) "" else "[[$label|$href]]"
        }
        .replace(Regex("""<(br|br/)\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            "\n${htmlToPlainText(it.groupValues[1])}\n\n"
        }
        .replace(Regex("""<h2[^>]*>(.*?)</h2>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val title = htmlToPlainText(it.groupValues[1]).trim().trimEnd(':')
            if (title.isBlank()) "\n" else "\n$title:\n"
        }
        .replace(Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val paragraph = htmlToPlainText(it.groupValues[1]).trim()
            if (paragraph.isBlank()) "\n" else "$paragraph\n\n"
        }
        .replace(Regex("""<li[^>]*>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val item = htmlToPlainText(it.groupValues[1]).trim()
            if (item.isBlank()) "" else "• $item\n"
        }
        .replace(Regex("""</(ul|ol|div|section|article|center)>""", RegexOption.IGNORE_CASE), "\n")

    return normalizeReadableContent(adapted)
}

internal fun extractActionLinks(html: String, baseUrl: String): List<FestivalActionLink> {
    return ANCHOR_REGEX.findAll(html)
        .mapNotNull { match ->
            val href = match.groupValues[1].trim()
            val label = htmlToPlainText(match.groupValues[2]).trim()
            if (href.isBlank() || label.isBlank()) return@mapNotNull null
            if (href.startsWith("#")) return@mapNotNull null

            FestivalActionLink(
                title = label,
                url = resolveUrl(href, baseUrl)
            )
        }
        .distinctBy { "${it.title.lowercase()}|${it.url.lowercase()}" }
        .toList()
}

internal fun decodeHtml(raw: String): String = raw
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .replace("&laquo;", "«")
    .replace("&raquo;", "»")
    .replace("&mdash;", "—")
    .replace("&ndash;", "–")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace(Regex("""\s*\(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+00:00\)"""), "")

private fun extractPrimaryContent(html: String): String {
    val mainContainer = Regex(
        """<div[^>]+id=["']maincontainer["'][^>]*>([\s\S]*?)</div>\s*</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(html)?.groupValues?.getOrNull(1)

    if (!mainContainer.isNullOrBlank()) return mainContainer

    val contentBlock = Regex(
        """<div[^>]+class=["'][^"']*content[^"']*["'][^>]*>([\s\S]*?)</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(html)?.groupValues?.getOrNull(1)

    if (!contentBlock.isNullOrBlank()) return contentBlock

    return html
}

private fun htmlToPlainText(fragment: String): String = decodeHtml(
    fragment
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""[ \t\r]+"""), " ")
        .replace(Regex("""\n\s+"""), "\n")
        .trim()
)

private fun normalizeReadableContent(raw: String): String {
    val lines = raw
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""[ \t\r]+"""), " ")
        .replace(Regex("""\n\s+"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .lines()
        .map { decodeHtml(it).trim() }
        .filter { it.isNotBlank() }

    val result = buildList {
        var previous: String? = null
        lines.forEach { line ->
            val normalized = line
                .trimEnd(':')
                .replace(Regex("""\s+"""), " ")

            if (normalized.isBlank()) return@forEach
            if (previous.equals(normalized, ignoreCase = true)) return@forEach
            if (normalized.matches(Regex("""\d{2}\.\d{2}\.\d{4}"""))) return@forEach

            add(line)
            previous = normalized
        }
    }

    return result.joinToString("\n").trim()
}

private fun resolveUrl(href: String, baseUrl: String): String {
    val base = baseUrl.trimEnd('/')
    return when {
        href.isBlank() -> ""
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> "$base$href"
        else -> "$base/$href"
    }
}

private val ANCHOR_REGEX =
    Regex("""<a[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
