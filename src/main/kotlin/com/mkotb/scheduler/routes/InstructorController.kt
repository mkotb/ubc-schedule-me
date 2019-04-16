package com.mkotb.scheduler.routes

import com.mkotb.scheduler.db.Section
import io.javalin.Context
import org.jetbrains.exposed.sql.transactions.transaction

object InstructorController {
    fun getAllInstructors(context: Context) {
        transaction {
            val courseToInstructors = Section.all().groupBy(
                { it.course.fullName },
                { it.instructor }
            ).mapValues { it.value.filterNotNull().toSet() }

            context.json(AllInstructorsResponse(
                courseToInstructors
            ))
        }
    }
}

class AllInstructorsResponse (
    val courseToInstructors: Map<String, Set<String>>
): SuccessfulResponse()