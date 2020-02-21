package com.mkotb.scheduler.routes

import com.mkotb.scheduler.*
import com.mkotb.scheduler.db.Course
import com.mkotb.scheduler.db.Section
import io.javalin.Context
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.DayOfWeek

object ScheduleController {
    fun schedule(context: Context) {
        val request = context.body<ScheduleRequest>()

        // ensure for every schedule
        // they do not request more classes than they provide options for
        request.schedules.forEach {
            val allRequested = it.firstTermAmount + it.secondTermAmount
            val allAvailable = it.requiredFirstCourses.size + it.requiredSecondCourses.size + it.electives.size

            if (allRequested > allAvailable) {
                context.json(InvalidCourseAmountsError(it.name))
                return
            }
        }

        transaction {
            // parse the schedules
            val parsedSchedules = try {
                request.schedules.map(InputSchedule::toSchedule).toMutableList()
            } catch (ex: Exception) {
                context.json(InvalidCoursesError)
                return@transaction
            }

            // schedule term 1
            createSchedules(parsedSchedules)

            // update to term 2
            parsedSchedules.forEach {
                it.currentTerm = "2"
            }

            // schedule term 2
            createSchedules(parsedSchedules)

            // export and respond
            context.json(SchedulingResponse(
                parsedSchedules.map { ExportedSchedule(it) }
            ))

            if (debug) {
                print(parsedSchedules, "1")
                print(parsedSchedules, "2")
            }
        }
    }
}

fun print(schedules: List<Schedule>, term: String) {
    schedules.forEach { schedule ->
        println("${schedule.name}'s Term $term Schedule:")
        schedule.currentTerm = term

        println("| TIME | MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY |")
        println("|------|--------|---------|-----------|----------|--------|")

        var time = schedule.startOfDay
        val max = schedule.endOfDay

        while (time <= max) {
            val builder = StringBuilder("|$time:00|")

            for (day in DayOfWeek.values()) {
                val section = schedule.classes.find { it.days.contains(day) && (time * 60) in it.startMinutes..(it.endMinutes - 1) }

                if (section != null) {
                    builder.append(section.name)
                }

                builder.append("|")
            }

            println(builder.toString())
            time++
        }
    }
}

class ScheduleRequest (
    val schedules: List<InputSchedule>
)

class InputSchedule (
    val name: String,
    var firstTermAmount: Int = 5,
    var secondTermAmount: Int = 5,
    val requiredFirstCourses: List<String>,
    val requiredSecondCourses: List<String>,
    val electives: List<String>,
    val schedulingFactors: List<SchedulingFactor>
) {
    fun toSchedule(): Schedule {
        fun mapCourseList(input: List<String>): List<Course> {
            return input.map(Course.Companion::findByName).requireNoNulls()
        }

        return Schedule (
            name,
            firstTermAmount,
            secondTermAmount,
            mapCourseList(requiredFirstCourses),
            mapCourseList(requiredSecondCourses),
            mapCourseList(electives).toMutableList(),
            schedulingFactors
        )
    }
}

object InvalidCoursesError: ErrorResponse("Invalid courses provided")
class InvalidCourseAmountsError (
    val incorrectSchedule: String
): ErrorResponse("$incorrectSchedule's schedule has too many courses requested for the amount they have chosen")

class SchedulingResponse (
    val schedules: List<ExportedSchedule>
): SuccessfulResponse()

class ExportedSchedule (schedule: Schedule) {
    val name = schedule.name
    val firstTermClasses = schedule.firstTermClasses.map { ExportedSection(it) }
    val secondTermClasses = schedule.secondTermClasses.map { ExportedSection(it) }
}

class ExportedSection (section: Section) {
    val course = section.course.fullName
    val name = section.name
    val status = section.status
    val activity = section.activity
    val term = section.term
    val link = section.link
    val days = section.days
    val startMinutes = section.startMinutes
    val endMinutes = section.endMinutes
    val instructor = section.instructor
    val generalRemaining = section.generalRemaining
}