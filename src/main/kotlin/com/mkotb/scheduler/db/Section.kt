package com.mkotb.scheduler.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.and
import java.time.DayOfWeek

val daysMap = run {
    val map = HashMap<String, DayOfWeek>()

    DayOfWeek.values().forEach {
        map[it.name.substring(0, 3).toLowerCase()] = it
    }

    map
}

object Sections: IntIdTable() {
    val course = reference("course", Courses)
    val status = varchar("status", 32)
    val name = varchar("name", 16)
    val activity = varchar("activity", 32)
    val link = varchar("link", 128)
    val term = varchar("term", 3)
    val days = varchar("days", 32)
    val start = integer("start")
    val end = integer("end")
    val comments = varchar("comments", 4096)
    val instructor = varchar("instructor", 512).nullable()
    val totalRemaining = integer("total_remaining")
    val currentlyRegistered = integer("currently_registered")
    val generalRemaining = integer("general_remaining")
    val restrictedRemaining = integer("restricted_remaining")

    init {
        uniqueIndex(course, name)
    }
}

class Section(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Section>(Sections) {
        fun updateOrNew(course: Course, name: String, operation: Section.() -> Unit): Section {
            var new = false
            val section = find { (Sections.course eq course.id) and (Sections.name eq name) }.firstOrNull()
                // if there is no section, create one with the following operation
                ?: Section.new {
                    this.course = course
                    this.name = name

                    operation(this)
                    new = true
                }

            if (!new) {
                // perform the update operation on the section
                operation(section)
            }

            return section

        }
    }

    var course by Course referencedOn Sections.course
    var name by Sections.name
    var status by Sections.status
    var activity by Sections.activity
    var term by Sections.term
    var link by Sections.link
    var daysRaw by Sections.days
    var startMinutes by Sections.start
    var endMinutes by Sections.end
    var comments by Sections.comments
    var instructor by Sections.instructor
    var totalRemaining by Sections.totalRemaining
    var currentlyRegistered by Sections.currentlyRegistered
    var generalRemaining by Sections.generalRemaining
    var restrictedRemaining by Sections.restrictedRemaining

    val duration
        get() = endMinutes - startMinutes
    val days: List<DayOfWeek>
        get() = daysRaw.split(" ").mapNotNull { daysMap[it.toLowerCase()] }

    /**
     * Checks if another section intersects by
     *
     * - they run on the same term
     * - ensuring that they have a day where they both run by seeing if
     * the start or the end time of the other section is within
     * the duration of this section.
     */
    infix fun intersects(other: Section): Boolean {
        val otherDays = other.days
        val ours = other.startMinutes in startMinutes..(endMinutes - 1)
        val theirs = startMinutes in other.startMinutes..(other.endMinutes - 1)

        return term == other.term && days.any { otherDays.contains(it) } && (ours || theirs)
    }
}
