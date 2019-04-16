package com.mkotb.scheduler

import com.mkotb.scheduler.db.Course
import com.mkotb.scheduler.db.Section

data class Schedule (
    val name: String,
    val firstTermAmount: Int,
    val secondTermAmount: Int,
    val requiredFirstCourses: List<Course>,
    val requiredSecondCourses: List<Course>,
    val electives: MutableList<Course>,
    val schedulingFactors: List<SchedulingFactor>
) {
    var currentTerm = "1"
    val firstTermClasses = HashSet<Section>()
    val secondTermClasses = HashSet<Section>()
    val requiredCourses
        get() = when (currentTerm == "1") {
            true -> requiredFirstCourses
            false -> requiredSecondCourses
        }
    val classes
        get() = when (currentTerm == "1") {
            true -> firstTermClasses
            false -> secondTermClasses
        }
    val courseAmount
        get() = when (currentTerm == "1") {
            true -> firstTermAmount
            false -> secondTermAmount
        }
    val startOfDay
        get() = classes.map { it.startMinutes / 60 }.min()!!
    val endOfDay
        get() = classes.map { it.endMinutes / 60 }.max()!!

    fun score(section: Section): Double {
        return schedulingFactors.sumByDouble { it.score(this, section) }
    }

    fun filter(section: Section): Boolean {
        return schedulingFactors.any {
            it.filter(this, section)
        }
    }

    fun hasSelected(course: Course): Boolean {
        return requiredCourses.contains(course) || electives.contains(course)
    }
}