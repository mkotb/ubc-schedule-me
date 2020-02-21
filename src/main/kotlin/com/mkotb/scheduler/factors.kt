package com.mkotb.scheduler

import com.mkotb.scheduler.db.BuildingTravelTime
import com.mkotb.scheduler.db.Section
import com.mkotb.scheduler.db.SectionLocation
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import java.time.DayOfWeek

val factors = mapOf (
    Pair("days_filter", DayFilter::class.java),
    Pair("days_preference", DaysPreference::class.java),
    Pair("proximity", ProximityFactor::class.java),
    Pair("distance", DistanceFactor::class.java),
    Pair("early_preference", EarlyClassesPreference::class.java),
    Pair("late_preference", LateClassesPreference::class.java),
    Pair("early_filter", EarlyFilter::class.java),
    Pair("late_filter", LateFilter::class.java),
    Pair("short_day", ShortDayPreference::class.java),
    Pair("no_torture", NoTorturePreference::class.java),
    Pair("instructor_filter", InstructorFilter::class.java),
    Pair("instructor_preference", InstructorPreference::class.java)
)

abstract class SchedulingFactor {
    companion object {
        val jsonFactory = run {
            var f = PolymorphicJsonAdapterFactory.of(SchedulingFactor::class.java, "name")

            factors.forEach { (key, clazz) ->
                f = f.withSubtype(clazz, key)
            }

            f
        }
    }

    var weight = 1

    open fun filter(schedule: Schedule, section: Section): Boolean = false
    // scores a section between 0-1
    open fun score(schedule: Schedule, section: Section): Double = 0.0
}

// removes any sections that are part of the days list
class DayFilter (
    val days: List<DayOfWeek>
): SchedulingFactor() {
    override fun filter(schedule: Schedule, section: Section): Boolean {
        return section.days.any { days.contains(it) }
    }
}

// holds a preference for days mentioned
class DaysPreference (
    val days: List<DayOfWeek>
): SchedulingFactor() {
    override fun score(schedule: Schedule, section: Section): Double {
        return section.days.count { days.contains(it) } / section.days.size.toDouble()
    }
}

// prefers sections which make the day as short as possible
class ShortDayPreference: SchedulingFactor() {
    companion object {
        const val UPPER_BOUND = (9 * 5)
    }

    override fun score(schedule: Schedule, section: Section): Double {
        var dayLength = 0.0

        DayOfWeek.values().forEach { day ->
            val sections = schedule.classes.filter { it.days.contains(day) }
            var start = sections.map { it.startMinutes / 60 }.min() ?: 0
            var end = sections.map { it.endMinutes / 60 }.max() ?: 0

            if (section.days.contains(day)) {
                if (section.startMinutes < start) {
                    start = section.startMinutes
                }

                if (section.endMinutes > end) {
                    end = section.endMinutes
                }
            }

            dayLength += (end - start)
        }

        return (dayLength / 60) / UPPER_BOUND
    }
}

// prefers classes that are close to each other
class ProximityFactor (
    val punishHourBreaks: Boolean = false
): SchedulingFactor() {
    companion object {
        const val UPPER_BOUND = 1000.0
    }

    init {
        weight = 3
    }

    override fun score(schedule: Schedule, section: Section): Double {
        val classes = schedule.classes
        var proximity = 0

        section.days.forEach { day ->
            // find other sections on that day
            val otherSections = classes.filter { it.days.contains(day) }
            val (earlierSection, laterSection) = findClosestClasses(otherSections, section)

            // the time between the end of the earlier section and the start of the input
            val earlyDistance = when (earlierSection == null) {
                true -> 0
                false -> section.startMinutes - earlierSection.endMinutes
            }
            // the time between the end of the input and start of the later section
            val laterDistance = when (laterSection == null) {
                true -> 0
                false -> laterSection.startMinutes - section.endMinutes
            }
            val sum = earlyDistance + laterDistance

            proximity += sum

            if (sum == 0) {
                proximity -= 120
            }

            if ((earlyDistance == 60 || laterDistance == 60) && punishHourBreaks) {
                proximity += 90
            }
        }

        if (proximity < 0) {
            return 0.0
        }

        return Math.max(0.0, (UPPER_BOUND - proximity) / UPPER_BOUND)
    }
}

// prefers classes that are physically closer to each other
// (where the walk time is shorter)
class DistanceFactor: SchedulingFactor() {
    companion object {
        const val UPPER_BOUND = (15 * 60).toDouble()
    }

    override fun score(schedule: Schedule, section: Section): Double {
        val classes = schedule.classes
        var proximity = 0

        section.days.forEach { day ->
            val formattedDay = day.name.toLowerCase().substring(0, 3)
            val otherSections = classes.filter { it.days.contains(day) }
            val sectionLocation = SectionLocation.find(section, formattedDay) ?: return@forEach

            val classesTravelTime = findClosestClasses(otherSections, section).toList()
                .filterNotNull()
                // only include classes that are adjacent to this section
                .filter { it.startMinutes == section.endMinutes || it.endMinutes == section.startMinutes }
                // map to the travel time of the two sections
                .mapNotNull {
                    val otherLocation = SectionLocation.find(it, formattedDay) ?: return@mapNotNull null

                    BuildingTravelTime.find(sectionLocation.building, otherLocation.building)?.time
                }

            proximity += classesTravelTime.average().toInt()
        }

        val averageProximity = (proximity / section.days.size)

        return Math.max(0.0, (UPPER_BOUND - averageProximity) / UPPER_BOUND)
    }
}

// given a list of sections and a section, it finds the classes
// that are above and below it
fun findClosestClasses(sections: List<Section>, section: Section): Pair<Section?, Section?> {
    var earlierSection: Section? = null
    var laterSection: Section? = null

    sections.forEach {
        // does this section run earlier than the input?
        val earlier = it.startMinutes < section.startMinutes

        // is it closer to the input than our previous value?
        if (earlier && (earlierSection == null || it.startMinutes > earlierSection!!.startMinutes)) {
            earlierSection = it
        }

        // is it closer to the input than our previous laterSection value?
        if (!earlier && (laterSection == null || it.startMinutes < laterSection!!.startMinutes)) {
            laterSection = it
        }
    }

    return Pair(earlierSection, laterSection)
}

// sets a preference for earlier classes
class EarlyClassesPreference: SchedulingFactor() {
    companion object {
        const val MINUTES_IN_DAY = (24 * 60).toDouble()
    }

    override fun score(schedule: Schedule, section: Section): Double {
        return (MINUTES_IN_DAY - section.startMinutes) / MINUTES_IN_DAY
    }
}

// filters any classes earlier than earliestHour
class EarlyFilter (
    val earliestHour: Int
): SchedulingFactor() {
    override fun filter(schedule: Schedule, section: Section): Boolean {
        return section.startMinutes < (earliestHour * 60)
    }
}

// prefers later classes
class LateClassesPreference: SchedulingFactor() {
    companion object {
        // the latest time a class starts, assuming it's 8 PM
        const val LATEST_CLASS = (20 * 60).toDouble()
    }

    override fun score(schedule: Schedule, section: Section): Double {
        return (section.startMinutes / LATEST_CLASS)
    }
}

// removes any classes later than latestHour
class LateFilter (
    val latestHour: Int
): SchedulingFactor() {
    override fun filter(schedule: Schedule, section: Section): Boolean {
        return section.startMinutes > (latestHour * 60)
    }
}

// attempts to avoid classes that cause the schedule
// to have 3 or more consecutive classes
class NoTorturePreference: SchedulingFactor() {
    companion object {
        const val UPPER_BOUND = (2 * 3)
    }

    override fun score(schedule: Schedule, section: Section): Double {
        var blocksSum = 0.0

        section.days.forEach { day ->
            // find all classes is in the day
            val classes = schedule.classes.filter { it.days.contains(day) }.toMutableList().apply {
                add(section)
                sortBy { it.startMinutes }
            }

            // find the furthest continuous class in a given direction
            // true means earlier direction, false means later direction
            fun findFurthest(direction: Boolean, index: Int): Section {
                // find the index of the next class
                val furtherIndex = index + if (direction) -1 else 1
                // get its element
                val further = classes.getOrNull(furtherIndex)
                val current = classes[index]

                // if it's null, we hit the end of the list
                // so the current index is the furthest
                if (further == null) {
                    return current
                }

                /*
                 * get the times to compare it with
                 *
                 * if we are going earlier, look at the junction when the further class ends
                 * and the current class begins
                 *
                 * if we are going later, look at the junction when the further class begins and
                 * the current class ends.
                 */
                val furtherTime = if (direction) further.endMinutes else further.startMinutes
                val currentTime = if (direction) current.startMinutes else current.endMinutes

                // check for continuity
                if (furtherTime != currentTime) {
                    return current
                }

                // keep moving in the direction with further being the new current
                return findFurthest(direction, furtherIndex)
            }

            val index = classes.indexOf(section)
            val upper = findFurthest(true, index)
            val lower = findFurthest(false, index)

            blocksSum += (lower.endMinutes - upper.startMinutes) / 60.0
        }

        blocksSum -= (3 * 3)
        return blocksSum.coerceAtLeast(0.0) / UPPER_BOUND
    }
}

// removes any instructors in the list
class InstructorFilter (
    val instructors: List<String>
): SchedulingFactor() {
    override fun filter(schedule: Schedule, section: Section): Boolean {
        return instructors.contains(section.instructor?.toLowerCase())
    }
}

// prefers the instructors provided
class InstructorPreference (
    val instructors: List<String>
): SchedulingFactor() {
    override fun score(schedule: Schedule, section: Section): Double {
        val instructor = section.instructor
        return if (instructor != null && instructors.contains(instructor.toLowerCase())) 1.0 else 0.0
    }
}
