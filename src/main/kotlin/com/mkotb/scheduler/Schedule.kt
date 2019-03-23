package com.mkotb.scheduler

data class Schedule (
    val name: String,
    val requiredFirstCourses: List<Course>,
    val requiredSecondCourses: List<Course>,
    val electives: MutableList<Course>,
    val schedulingFactors: List<SchedulingFactor>
) {
    val firstTermClasses = HashSet<Section>()
    val secondTermClasses = HashSet<Section>()
    val requiredCourses
        get() = when (term == "1") {
            true -> requiredFirstCourses
            false -> requiredSecondCourses
        }
    val classes
        get() = when (term == "1") {
            true -> firstTermClasses
            false -> secondTermClasses
        }
    val startOfDay
        get() = classes.map { it.startMinutes / 60 }.min()!!
    val endOfDay
        get() = classes.map { it.endMinutes / 60 }.max()!!

    fun score(section: Section): Double {
        return schedulingFactors.sumByDouble { it.score(this, section) }
    }

    fun filter(section: Section): Boolean {
        return schedulingFactors.all { it.filter(this, section) }
    }

    fun hasSelected(course: Course): Boolean {
        return requiredCourses.contains(course) || electives.contains(course)
    }
}