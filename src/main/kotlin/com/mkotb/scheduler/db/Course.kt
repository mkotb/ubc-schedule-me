package com.mkotb.scheduler.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.and
import java.util.*

object Courses: IntIdTable() {
    val subjectCode = varchar("subject_code", 4)
    val courseNumber = varchar("course_number", 4)

    init {
        uniqueIndex(subjectCode, courseNumber)
    }
}

class Course(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Course>(Courses) {
        fun findOrNew(subject: String, course: String): Course {
            return findByName(subject, course) ?: Course.new {
                subjectCode = subject
                courseNumber = course
            }
        }

        fun findByName(name: String): Course? {
            val elements = name.split(" ")

            if (elements.size != 2) {
                return null
            }

            return findByName(elements[0], elements[1])
        }

        fun findByName(subject: String, course: String): Course? {
            return find { (Courses.subjectCode eq subject) and (Courses.courseNumber eq course) }.firstOrNull()
        }
    }

    var subjectCode by Courses.subjectCode
    var courseNumber by Courses.courseNumber

    val fullName
        get() = "$subjectCode $courseNumber"

    val sections by Section referrersOn Sections.course

    override fun equals(other: Any?): Boolean {
        return other is Course && fullName == other.fullName
    }

    override fun hashCode(): Int {
        return Objects.hash(subjectCode, courseNumber)
    }
}

val cachedCourseSections = WeakHashMap<Course, MutableList<Section>>()

val Course.cachedSections: MutableList<Section>
    get() = cachedCourseSections.getOrPut(this) { sections.toMutableList() }

fun Course.clearCache() {
    cachedCourseSections.remove(this)
}