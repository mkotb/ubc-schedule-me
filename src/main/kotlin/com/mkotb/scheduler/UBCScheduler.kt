package com.mkotb.scheduler

import java.time.DayOfWeek
import java.util.*
import kotlin.collections.HashSet
import kotlin.system.exitProcess

var TERM = "1"
const val CURRENT_YEAR = "2018"
const val CURRENT_SESSION = "W"
const val BASE_URL = "https://courses.students.ubc.ca"
const val BASE_COURSE_URL = "$BASE_URL/cs/courseschedule?tname=subj-course&dept=%s&course=%s&pname=subjarea&sessyr=$CURRENT_YEAR&sesscd=$CURRENT_SESSION"
const val BASE_SECTION_URL = "$BASE_URL/cs/courseschedule?pname=subjarea&tname=subj-section&dept=%s&course=%s&section=%s&sessyr=$CURRENT_YEAR&sesscd=$CURRENT_SESSION"

fun main(args: Array<String>) {
    if (args.size < 5) {
        System.out.println("You need at least two course selections to form a schedule!")
        exitProcess(-127)
    }

    TERM = args[0]

    // convert input classes into course selections (pulling data from online)
    val firstSelection = InputCourseSelection (
        parseClasses(args[1]),
        parseClasses(args[2])
    ).toCourseSelection()
    val secondSelection = InputCourseSelection (
        parseClasses(args[3]),
        parseClasses(args[4])
    ).toCourseSelection()

    val firstSchedule = HashSet<Section>()
    val secondSchedule = HashSet<Section>()

    // map their selections to their schedule
    val selectionsMap = mapOf (
        Pair(firstSelection, firstSchedule),
        Pair(secondSelection, secondSchedule)
    )

    val courseSet = HashSet<Course>()

    selectionsMap.forEach { selection, _ ->
        courseSet.addAll(selection.requiredCourses)
        courseSet.addAll(selection.electives)
    }

    courseSet.sortedByDescending { course ->
        val required = when (selectionsMap.keys.any { it.requiredCourses.contains(course) }) {
            true -> 1
            false -> 0
        }
        val sharedBy = selectionsMap.keys.count {
            it.requiredCourses.contains(course) || it.electives.contains(course)
        }
        val sectionsByActivity = course.sections.values.groupBy { it.activity }
        val averageSectionsPerActivity = when (sectionsByActivity.isEmpty()) {
            true -> 0
            false -> sectionsByActivity.entries.sumBy { it.value.size } / sectionsByActivity.size
        }
        val weightedSections = when (averageSectionsPerActivity == 0) {
            true -> 0.0
            false -> (1.0 / averageSectionsPerActivity)
        }
        val score = required * .4 + weightedSections + (sharedBy * .125)

        System.out.println("${course.subjectCode} ${course.courseNumber} - $score ($required,$averageSectionsPerActivity,$sharedBy)")

        score
    }.forEach { course ->
        val schedules = selectionsMap.filter { entry ->
            (entry.key.requiredCourses.contains(course) || entry.value.groupBy { "${it.subjectCode} ${it.courseNumber}" }.size < 5) &&
            (entry.key.electives.contains(course) || entry.key.requiredCourses.contains(course))
        }

        scheduleCourse(course, schedules.values.toMutableList())
    }

    selectionsMap.forEach { _, schedule ->
        System.out.println("Schedule:")

        System.out.println("| TIME | MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY |")
        System.out.println("|------|--------|---------|-----------|----------|--------|")

        // start of day
        var time = 7
        // end of day
        val max = 17

        while (time <= max) {
            val builder = StringBuilder("|$time:00|")

            for (day in DayOfWeek.values()) {
                val section = schedule.find { it.days.contains(day) && it.sectionRange.contains(time * 60) }

                if (section != null) {
                    builder.append(section.section)
                }

                builder.append("|")
            }

            System.out.println(builder.toString())
            time++
        }
    }
}

val sectionFilters = listOf("waiting list", "web-oriented course", "distance education")

fun scheduleCourse(course: Course, schedules: MutableCollection<MutableSet<Section>>) {
    val sectionsByActivity = course.sections.values.groupBy { it.activity }

    for (entry in sectionsByActivity) {
        if (scheduleActivity(course, entry.key, entry.value, schedules)) {
            return
        }
    }

    System.out.println("Scheduled ${course.subjectCode} ${course.courseNumber}")
}

// returns whether to stop attempting to schedule this course
fun scheduleActivity(course: Course, activity: String, sections: List<Section>, schedules: MutableCollection<MutableSet<Section>>): Boolean {
    // ignore any irrelevant activities
    if (sectionFilters.contains(activity.toLowerCase())) {
        return false
    }

    // ignore an activity that is specifically for Vantage College
    // (e.g. first-year MATH discussions)
    if (sections.all { it.comments.contains("Vantage College") }) {
        return false
    }

    val foundSections = sections
        // search only for the correct term and when it's not in the Standard Time Table
        .filter { it.term.contains(TERM) && it.status != "STT" }
        // ensure the section does not intersect with any of the schedules
        .filter { section ->
            schedules.all { schedule -> schedule.none { it intersects section } }
        }
        // sort by a "proximity" value that encourages classes to be close to each other
        // experiment with this, sometimes it produces better/worse results
        .sortedBy { section -> schedules.map { schedule -> calculateProximity(section, schedule) }.sum() }
    // find the one with the lowest proximity value
    val foundSection = foundSections.firstOrNull()

    if (foundSection == null && schedules.size > 1) {
        // try and schedule it without other people
        schedules.forEach {
            if (scheduleActivity(course, activity, sections, Collections.singleton(it))) {
                schedules.remove(it)
            }
        }
        return false
    }

    // if we are unable to find an applicable required section for this activity
    // remove the course as a whole
    if (foundSection == null) {
        schedules.forEach { schedule ->
            schedule.removeIf {
                it.subjectCode == course.subjectCode && it.courseNumber == course.courseNumber
            }
        }

        System.out.println("Had to remove ${course.subjectCode} ${course.courseNumber}")
        return true
    }

    // add the section to schedules
    schedules.forEach {
        it.add(foundSection)
    }

    return false
}

/**
 * Calculates how close are this section is to its neighbours
 */
fun calculateProximity(section: Section, schedule: Set<Section>): Int {
    var proximity = 0

    section.days.forEach { day ->
        // find other sections on that day
        val otherSections = schedule.filter { it.days.contains(day) }
        var earlierSection: Section? = null
        var laterSection: Section? = null

        // if there are no sections, reduce proximity to give it a "bonus"
        if (otherSections.isEmpty()) {
            proximity -= 150
            return@forEach
        }

        otherSections.forEach {
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

        // the time between the end of the earlier section and the start of the input
        val earlyDistance = when (earlierSection == null) {
            true -> 0
            false -> section.startMinutes - earlierSection!!.endMinutes
        }
        // the time between the end of the input and start of the later section
        val laterDistance = when (laterSection == null) {
            true -> 0
            false -> laterSection!!.startMinutes - section.endMinutes
        }

        proximity += earlyDistance + laterDistance
    }

    return proximity
}

/**
 * Takes a format of (subject),(course);(subject),(course) and converts to a list of InputClass
 *
 * e.g. ENGL,100;MATH,100;CPSC,110
 */
fun parseClasses(input: String): List<InputClass> {
    return input.split(";").mapNotNull {
        val elements = it.split(",")

        if (elements.size != 2) {
            return@mapNotNull null
        }

        InputClass(elements[0], elements[1])
    }
}