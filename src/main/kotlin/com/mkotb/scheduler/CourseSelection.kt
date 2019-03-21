package com.mkotb.scheduler

data class CourseSelection (
    val requiredCourses: List<Course>,
    val electives: List<Course>
)