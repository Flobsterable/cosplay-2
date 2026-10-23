package ru.flobsterable.cosplay2.feature.festival

import ru.flobsterable.cosplay2.model.FestivalSummary

class FestivalSearch {
    fun search(festivals: List<FestivalSummary>, query: String): List<FestivalSummary> {
        val words = buildList {
            val word = StringBuilder()
            for (character in query) {
                if (character.isWhitespace()) {
                    if (word.isNotEmpty()) {
                        add(word.toString())
                        word.clear()
                    }
                } else {
                    word.append(character)
                }
            }
            if (word.isNotEmpty()) add(word.toString())
        }

        if (words.isEmpty()) return festivals.toList()

        return festivals.filter { festival ->
            words.all { word ->
                festival.title.contains(word, ignoreCase = true) ||
                    festival.city.contains(word, ignoreCase = true)
            }
        }
    }
}
