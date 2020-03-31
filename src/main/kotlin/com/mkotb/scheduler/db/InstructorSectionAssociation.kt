package com.mkotb.scheduler.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.and

object InstructorSectionAssociations : IntIdTable() {
    val section = reference("section", Sections)
    val instructor = reference("instructor", Instructors)

    init {
        uniqueIndex(section, instructor)
    }
}

class InstructorSectionAssociation(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<InstructorSectionAssociation>(InstructorSectionAssociations) {
        fun findOrNew(section: Section, instructor: Instructor): InstructorSectionAssociation {
            val association = find {
                (InstructorSectionAssociations.section eq section.id) and
                        (InstructorSectionAssociations.instructor eq instructor.id)
            }.firstOrNull()

            return association ?: InstructorSectionAssociation.new {
                this.section = section
                this.instructor = instructor
            }
        }
    }

    var section by Section referencedOn InstructorSectionAssociations.section
    var instructor by Instructor referencedOn InstructorSectionAssociations.instructor
}