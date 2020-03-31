package com.mkotb.scheduler.routes

import com.mkotb.scheduler.db.Instructor
import com.mkotb.scheduler.db.Section
import io.javalin.Context
import org.jetbrains.exposed.sql.transactions.transaction

object InstructorController {
    fun getAllInstructors(context: Context) {
        transaction {
            val courseToInstructors = Section.all().groupBy(
                { it.course.fullName },
                { it.instructorAssociations }
            ).mapValues { entry ->
                entry.value.flatten().map { ExportedInstructor(it.instructor) }.toSet()
            }

            context.json(AllInstructorsResponse(
                courseToInstructors
            ))
        }
    }
}

class AllInstructorsResponse (
    val courseToInstructors: Map<String, Set<ExportedInstructor>>
): SuccessfulResponse()

class ExportedInstructor(instructor: Instructor) {
    val fullName = instructor.fullName
    val firstName = instructor.firstName
    val lastName = instructor.lastName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExportedInstructor

        if (fullName != other.fullName) return false

        return true
    }

    override fun hashCode(): Int {
        return fullName.hashCode()
    }


}