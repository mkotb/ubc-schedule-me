package com.mkotb.scheduler

data class Schedule (
    val name: String,
    val requiredFirstCourses: List<Course>,
    val requiredSecondCourses: List<Course>,
    val electives: MutableList<Course>
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
}