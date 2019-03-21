package com.mkotb.scheduler

import java.util.*

data class Course (
    val sections: Map<String, Section>,
    val subjectCode: String,
    val courseNumber: String
) {
    override fun equals(other: Any?): Boolean {
        return other is Course && subjectCode == other.subjectCode && courseNumber == other.courseNumber
    }

    override fun hashCode(): Int {
        return Objects.hash(subjectCode, courseNumber)
    }
}