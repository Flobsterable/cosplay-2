package ru.flobsterable.cosplay2.feature.festival

import io.ktor.http.encodeURLParameter
import ru.flobsterable.cosplay2.model.FestivalDetail

internal fun typeTitle(typeId: Int): String = when (typeId) {
    1 -> "Фестиваль"
    2 -> "Вечеринка"
    3 -> "Маркет"
    5 -> "Dance"
    6 -> "Бал"
    8 -> "Фандом"
    else -> "Событие"
}

const val ACTUAL_FILTER_YEAR = -1

internal enum class TypeFilter(val title: String) {
    All("Все"),
    Cosplay("Косплей"),
    Dance("Танцевальные"),
    Convention("Конвенты"),
    Party("Вечеринки"),
    Fair("Ярмарки"),
    Misc("Разное");

    fun matches(eventTypeId: Int): Boolean = when (this) {
        All -> true
        Cosplay -> eventTypeId == 1
        Dance -> eventTypeId == 5
        Convention -> eventTypeId == 8
        Party -> eventTypeId == 2
        Fair -> eventTypeId == 3
        Misc -> eventTypeId !in setOf(1, 2, 3, 5, 8)
    }

    fun labelFor(eventTypeId: Int, fallback: String?): String = when {
        matches(eventTypeId) && this != All -> title
        else -> when (eventTypeId) {
            1 -> "Косплей"
            5 -> "Танцевальные"
            8 -> "Конвенты"
            2 -> "Вечеринки"
            3 -> "Ярмарки"
            else -> fallback ?: "Разное"
        }
    }
}

internal enum class MonthFilter(val title: String, private val monthNumber: String?) {
    All("Все месяцы", null),
    January("Январь", "01"),
    February("Февраль", "02"),
    March("Март", "03"),
    April("Апрель", "04"),
    May("Май", "05"),
    June("Июнь", "06"),
    July("Июль", "07"),
    August("Август", "08"),
    September("Сентябрь", "09"),
    October("Октябрь", "10"),
    November("Ноябрь", "11"),
    December("Декабрь", "12");

    fun matches(startTime: String): Boolean {
        if (monthNumber == null) return true
        return startTime.substringAfter("-").take(2) == monthNumber
    }
}

internal fun formatFestivalDate(start: String, end: String): String {
    val startDate = start.substringBefore(" ")
    val endDate = end.substringBefore(" ")
    val startParts = startDate.split("-")
    val endParts = endDate.split("-")
    if (startParts.size != 3 || endParts.size != 3) return startDate

    val startYear = startParts[0]
    val startMonth = startParts[1].toIntOrNull() ?: return startDate
    val startDay = startParts[2].toIntOrNull() ?: return startDate
    val endYear = endParts[0]
    val endMonth = endParts[1].toIntOrNull() ?: return startDate
    val endDay = endParts[2].toIntOrNull() ?: return startDate

    val startMonthName = monthName(startMonth)
    val endMonthName = monthName(endMonth)

    return when {
        startDate == endDate -> "$startDay $startMonthName $startYear"
        startMonth == endMonth && startYear == endYear -> "$startDay-$endDay $startMonthName $startYear"
        startYear == endYear -> "$startDay $startMonthName - $endDay $endMonthName $startYear"
        else -> "$startDay $startMonthName $startYear - $endDay $endMonthName $endYear"
    }
}

internal fun buildCalendarUrl(detail: FestivalDetail): String {
    val title = detail.summary.title.encodeURLParameter()
    val details = buildFestivalAboutText(detail).take(1500).encodeURLParameter()
    val location = listOf(detail.venueCity, detail.summary.country)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .encodeURLParameter()
    val dates = "${calendarDateTime(detail.startDate, "120000")}/${calendarDateTime(detail.endDate, "200000")}"
    return "https://calendar.google.com/calendar/render?action=TEMPLATE&text=$title&dates=$dates&details=$details&location=$location"
}

private fun calendarDateTime(raw: String, time: String): String {
    val date = raw.substringBefore(" ").replace("-", "")
    return "${date}T$time"
}

private fun monthName(month: Int): String = when (month) {
    1 -> "января"
    2 -> "февраля"
    3 -> "марта"
    4 -> "апреля"
    5 -> "мая"
    6 -> "июня"
    7 -> "июля"
    8 -> "августа"
    9 -> "сентября"
    10 -> "октября"
    11 -> "ноября"
    12 -> "декабря"
    else -> ""
}
