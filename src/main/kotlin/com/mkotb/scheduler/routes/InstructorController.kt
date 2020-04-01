package com.mkotb.scheduler.routes

import com.mkotb.scheduler.db.InstructorSectionAssociations
import com.mkotb.scheduler.db.Instructors
import com.mkotb.scheduler.db.Sections
import io.javalin.Context
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object InstructorController {
    fun getAllInstructors(context: Context) {
        transaction {
            val courseToInstructors = InstructorSectionAssociations.innerJoin(Instructors).innerJoin(Sections)
                .select {
                    (InstructorSectionAssociations.instructor eq Instructors.id) and
                            (InstructorSectionAssociations.section eq Sections.id)
                }
                .groupBy (
                    { it[Sections.name].split(" ").subList(0, 2).joinToString(" ") },
                    { ExportedInstructor(it[Instructors.fullName], it[Instructors.firstName], it[Instructors.lastName]) }
                )
                .mapValues { it.value.toSet() }

            context.json(AllInstructorsResponse(
                courseToInstructors
            ))
        }
    }
}

class AllInstructorsResponse (
    val courseToInstructors: Map<String, Set<ExportedInstructor>>
): SuccessfulResponse()

data class ExportedInstructor (
    val fullName: String,
    val firstName: String,
    val lastName: String
)
