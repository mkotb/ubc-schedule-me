package com.mkotb.scheduler.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Instructors: IntIdTable() {
    val fullName = varchar("full_name", 128).uniqueIndex()
    val firstName = varchar("first_name", 128)
    val lastName = varchar("last_name", 128)
}

class Instructor(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Instructor>(Instructors) {
        fun findOrNew(name: String): Instructor {
            return find { Instructors.fullName eq name }.firstOrNull()
                ?: Instructor.new {
                    val components = name.split(',')

                    fullName = name
                    firstName = components.getOrNull(1)?.trim()?.split(" ")?.first() ?: name
                    lastName = components[0].split(" ")[0]
                }
        }
    }

    var fullName by Instructors.fullName
    var firstName by Instructors.firstName
    var lastName by Instructors.lastName

    val sectionAssociations by InstructorSectionAssociation referrersOn InstructorSectionAssociations.instructor
}
