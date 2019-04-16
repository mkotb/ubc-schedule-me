package com.mkotb.scheduler

import com.mkotb.scheduler.db.Course
import com.mkotb.scheduler.db.Section
import com.mkotb.scheduler.db.cachedSections
import com.mkotb.scheduler.db.clearCache
import java.util.*

fun createSchedules(schedules: MutableList<Schedule>) {
    val courseSet = HashSet<Course>()
    val requiredCourses = HashSet<Course>()
    val electives = HashSet<Course>()

    schedules.forEach { schedule ->
        courseSet.addAll(schedule.requiredCourses)
        courseSet.addAll(schedule.electives)

        requiredCourses.addAll(schedule.requiredCourses)
        electives.addAll(schedule.electives)
    }

    courseSet.forEach { course ->
        course.cachedSections.removeIf { entry ->
            entry.status.toLowerCase().contains("cancelled") ||
                    schedules.any { schedule -> schedule.filter(entry) }
        }
    }

    // performs division but returns 0 when divisor == 0
    fun safeDivision(dividend: Double, divisor: Double): Double {
        return if (divisor == 0.0) 0.0 else dividend / divisor
    }

    // finds all schedules which can register this course
    fun findApplicableSchedules(course: Course): MutableList<Schedule> {
        return schedules.filter { entry ->
            // how many required courses has this schedule registered?
            val registeredRequiredCourses = entry.requiredCourses.count { c ->
                entry.classes.any { it.course == c }
            }
            // how many of them are left?
            val requiredLeft = entry.requiredCourses.size - registeredRequiredCourses
            val electiveSpaces = (entry.courseAmount - requiredLeft)
            val spaceForElectives = entry.classes.groupBy { it.course.fullName }.size < electiveSpaces

            // A. Have this schedule selected this course?
            // B. is this a required course? or do we have spaces for electives?
            (entry.requiredCourses.contains(course) || spaceForElectives) &&
                    (entry.hasSelected(course))
        }.toMutableList()
    }

    // first register our required courses
    requiredCourses.sortedByDescending { course ->
        // how many schedules have this course?
        val sharedBy = schedules.count {
            it.hasSelected(course)
        }

        // what is the longest section (in hours)
        val longestSection = (course.cachedSections.maxBy { it.startMinutes }?.duration ?: 0) / 60

        val sectionsByActivity = course.cachedSections
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
        val score = (weightedSections) + (sharedBy * .15) + (longestSection * .05)

        if (debug) {
            System.out.println("${course.fullName} - $score ($weightedSections,$sharedBy,$longestSection)")
        }

        score
    }.forEach { course ->
        scheduleCourse(course, findApplicableSchedules(course))
    }

    // register electives
    electives.sortedByDescending { course ->
        val relevantSchedules = findApplicableSchedules(course)
        // how many schedules have this course?
        val sharedBy = relevantSchedules.size
        // the average score for each section for each schedule
        val averageScore = relevantSchedules.sumByDouble { schedule ->
            schedule.classes.sumByDouble { section ->
                val factors = schedule.schedulingFactors
                    .associateWith { it.score(schedule, section) }
                    .filter { it.value != 0.0 }

                factors.values.sum() / factors.size
            } / schedule.classes.size
        } / relevantSchedules.size

        val score = (sharedBy * .2) + averageScore

        if (debug) {
            System.out.println("${course.fullName} - $score ($sharedBy,$averageScore)")
        }

        score
    }.forEach { course ->
        scheduleCourse(course, findApplicableSchedules(course))
    }

    courseSet.forEach { it.clearCache() }
}

val sectionFilters = listOf("waiting list", "web-oriented course", "distance education")

fun scheduleCourse(course: Course, schedules: MutableCollection<Schedule>) {
    if (schedules.isEmpty()) {
        return
    }

    val sectionsByActivity = course.cachedSections.groupBy { it.activity }

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
        System.out.println("Scheduled ${course.fullName}")
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
        .filter { it.term.contains(schedules.first().currentTerm) && it.status != "STT" }
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
                it.course == course
            }
        }

        if (debug) {
            System.out.println("Had to remove ${course.fullName} $activity")
        }
        return true
    }

    // add the section to schedules
    schedules.forEach {
        it.classes.add(foundSection)
    }

    return false
}
