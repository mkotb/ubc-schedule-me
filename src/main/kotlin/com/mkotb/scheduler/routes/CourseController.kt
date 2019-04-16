package com.mkotb.scheduler.routes

import com.mkotb.scheduler.db.Course
import io.javalin.Context
import org.jetbrains.exposed.sql.transactions.transaction

object CourseController {
    fun getAllCourses(context: Context) {
        transaction {
            val courses = Course.all().groupBy(
                { it.subjectCode },
                { it.courseNumber }
            )

            context.json(AllCoursesResponse(courses))
        }
    }
}

class AllCoursesResponse (
    val courses: Map<String, List<String>>
): SuccessfulResponse()
