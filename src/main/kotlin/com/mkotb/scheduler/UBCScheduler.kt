package com.mkotb.scheduler

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.time.DayOfWeek
import java.util.*
import kotlin.collections.HashSet

var term = "1"
var debug = false
const val CURRENT_YEAR = "2018"
const val CURRENT_SESSION = "W"
const val BASE_URL = "https://courses.students.ubc.ca"
const val BASE_COURSE_URL = "$BASE_URL/cs/courseschedule?tname=subj-course&dept=%s&course=%s&pname=subjarea&sessyr=$CURRENT_YEAR&sesscd=$CURRENT_SESSION"
const val BASE_SECTION_URL = "$BASE_URL/cs/courseschedule?pname=subjarea&tname=subj-section&dept=%s&course=%s&section=%s&sessyr=$CURRENT_YEAR&sesscd=$CURRENT_SESSION"
val gson: Gson = GsonBuilder()
    .registerTypeAdapter(InputClass::class.java, InputClassAdapter)
    .registerTypeAdapter(SchedulingFactor::class.java, SchedulingFactorDeserializer)
    .create()

fun main(args: Array<String>) {
    val inputFile = gson.fromJson(
        InputStreamReader(FileInputStream(File("students.json"))),
        InputStudentFile::class.java
    )

    debug = inputFile.debug

    System.out.println("Pulling course data...")

    val schedules = inputFile.students.map { it.toSchedule() }.toMutableList()

    createSchedules(schedules)

    term = "2"

    createSchedules(schedules)
}

fun createSchedules(schedules: MutableList<Schedule>) {
    System.out.println("\nCreating Term $term Schedules\n")

    val courseSet = HashSet<Course>()

    schedules.forEach { schedule ->
        courseSet.addAll(schedule.requiredCourses)
        courseSet.addAll(schedule.electives)
    }

    courseSet.forEach { course ->
        course.sections.entries.removeIf { entry ->
            entry.value.status.toLowerCase().contains("cancelled") ||
                    schedules.all { schedule -> schedule.filter(entry.value) }
        }
    }

    fun safeDivision(dividend: Double, divisor: Double): Double {
        return when (divisor == 0.0) {
            true -> 0.0
            false -> dividend / divisor
        }
    }

    courseSet.sortedByDescending { course ->
        // do any of the schedules require this course?
        val required = when (schedules.any { it.requiredCourses.contains(course) }) {
            true -> 1
            false -> 0
        }

        // how many schedules have this course?
        val sharedBy = schedules.count {
            it.requiredCourses.contains(course) || it.electives.contains(course)
        }

        val longestSection = (course.sections.values.maxBy { it.startMinutes }?.duration ?: 0) / 60

        val sectionsByActivity = course.sections.values
            // ignore irrelevant activities
            .filter { !sectionFilters.contains(it.activity) }
            .groupBy { it.activity }

        // find the average sections per activity
        // which roughly tells us the "difficulty"
        // of scheduling this course
        val averageSectionsPerActivity = safeDivision(
            sectionsByActivity.entries.sumBy { it.value.size }.toDouble(),
            sectionsByActivity.size.toDouble()
        )
        // make this a value between 0-1 where a higher averageSectionsPerActivity
        // reduces the priority of the course being scheduled
        val weightedSections = safeDivision(1.0, averageSectionsPerActivity)

        // create a score to find the priority
        // of scheduling this course
        val score = required * .3 + (weightedSections * 2) + (sharedBy * .1) + (longestSection * .2)

        if (debug) {
            System.out.println("${course.subjectCode} ${course.courseNumber} - $score ($required,$weightedSections,$sharedBy,$longestSection)")
        }

        score
    }.forEach { course ->
        val applicableSchedules = schedules.filter { entry ->
            val registeredRequiredCourses = entry.requiredCourses.count { c ->
                entry.classes.any { it.subjectCode == c.subjectCode && it.courseNumber == c.courseNumber }
            }
            val requiredLeft = entry.requiredCourses.size - registeredRequiredCourses

            (entry.requiredCourses.contains(course) || entry.classes.groupBy { "${it.subjectCode} ${it.courseNumber}" }.size < (5 - requiredLeft)) &&
                    (entry.electives.contains(course) || entry.requiredCourses.contains(course))
        }

        scheduleCourse(course, applicableSchedules.toMutableList())
    }

    schedules.forEach { schedule ->
        System.out.println("${schedule.name}'s Term $term Schedule:")

        System.out.println("| TIME | MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY |")
        System.out.println("|------|--------|---------|-----------|----------|--------|")

        // start of day
        var time = schedule.classes.map { it.startMinutes / 60 }.min()!!
        // end of day
        val max = schedule.classes.map { it.endMinutes / 60 }.max()!!

        while (time <= max) {
            val builder = StringBuilder("|$time:00|")

            for (day in DayOfWeek.values()) {
                val section = schedule.classes.find { it.days.contains(day) && it.sectionRange.contains(time * 60) }

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

fun scheduleCourse(course: Course, schedules: MutableCollection<Schedule>) {
    val sectionsByActivity = course.sections.values.groupBy { it.activity }

    for (entry in sectionsByActivity) {
        if (scheduleActivity(course, entry.key, entry.value, schedules)) {
            return
        }
    }

    schedules.forEach {
        // remove the course from possible options if its an elective
        it.electives.remove(course)
    }

    if (debug) {
        System.out.println("Scheduled ${course.subjectCode} ${course.courseNumber}")
    }
}

// returns whether to stop attempting to schedule this course
fun scheduleActivity(course: Course, activity: String, sections: List<Section>, schedules: MutableCollection<Schedule>): Boolean {
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
        .filter { it.term.contains(term) && it.status != "STT" }
        // ensure the section does not intersect with any of the schedules
        .filter { section ->
            schedules.all { schedule -> schedule.classes.none { it intersects section } }
        }
        .sortedByDescending { section -> schedules.sumByDouble { schedule -> schedule.score(section) }}
    // find the one with the lowest proximity value
    val foundSection = foundSections.firstOrNull()

    if (foundSection == null && schedules.size > 1) {
        // try and schedule it without other people
        schedules.removeIf {
            scheduleActivity(course, activity, sections, Collections.singleton(it))
        }
        return false
    }

    // if we are unable to find an applicable required section for this activity
    // remove the course as a whole
    if (foundSection == null) {
        schedules.forEach { schedule ->
            schedule.classes.removeIf {
                it.subjectCode == course.subjectCode && it.courseNumber == course.courseNumber
            }
        }

        if (debug) {
            System.out.println("Had to remove ${course.subjectCode} ${course.courseNumber}")
        }
        return true
    }

    // add the section to schedules
    schedules.forEach {
        it.classes.add(foundSection)
    }

    return false
}
