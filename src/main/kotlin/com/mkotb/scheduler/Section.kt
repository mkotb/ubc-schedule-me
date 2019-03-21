package com.mkotb.scheduler

import java.time.DayOfWeek

val daysMap = run {
    val map = HashMap<String, DayOfWeek>()

    DayOfWeek.values().forEach {
        map[it.name.substring(0, 3).toLowerCase()] = it
    }

    map
}

class Section (
    val subjectCode: String,
    val courseNumber: String,
    // "Full" if full, otherwise empty
    val status: String,
    // name of the section
    val section: String,
    // link
    val href: String,
    val activity: String,
    val term: String,
    val daysRaw: String,
    val start: String,
    val end: String,
    val comments: String,
    val totalRemaining: Int,
    val currentlyRegistered: Int,
    val generalRemaining: Int,
    val restrictedRemaining: Int
) {
    val days: List<DayOfWeek>
        get() = daysRaw.split(" ").mapNotNull { daysMap[it.toLowerCase()] }
    // minutes into the day a class starts
    val startMinutes
        get() = parseTime(start)
    // minutes into the day a class ends
    val endMinutes
        get() = parseTime(end)
    // range of time from start to end
    val sectionRange
        get() = IntRange(startMinutes, endMinutes - 1)

    /**
     * Checks if another section intersects by
     *
     * - they run on the same term
     * - ensuring that they have a day where they both run by seeing if
     * the start or the end time of the other section is within
     * the duration of this section.
     */
    infix fun intersects(other: Section): Boolean {
        val otherDays = other.days

        return term == other.term && days.any { otherDays.contains(it) } &&
                (sectionRange.contains(other.startMinutes) || sectionRange.contains(other.endMinutes))
    }

    private fun parseTime(time: String): Int {
        val elements = time.split(":")

        return elements[0].toInt() * 60 + elements[1].toInt()
    }
}

