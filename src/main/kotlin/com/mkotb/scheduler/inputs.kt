package com.mkotb.scheduler

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class InputStudentFile (
    val debug: Boolean,
    val students: List<InputCourseSelection>
)

data class InputCourseSelection (
    val name: String,
    val requiredFirstCourses: List<InputClass>,
    val requiredSecondCourses: List<InputClass>,
    val electives: List<InputClass>
) {
    fun toSchedule(): Schedule {
        System.out.println("$name's required courses...")

        val first = requiredFirstCourses.mapNotNull { it.toCourse() }
        val second = requiredSecondCourses.mapNotNull { it.toCourse() }

        System.out.println("Done!")
        System.out.println("$name's electives...")

        val pulledElectives = electives.mapNotNull { it.toCourse() }.toMutableList()

        System.out.println("Done!")

        return Schedule (
            name,
            first,
            second,
            pulledElectives
        )
    }
}

val courseCache = HashMap<InputClass, Course>()

data class InputClass (
    val subject: String,
    val course: String
) {
    fun toCourse(): Course {
        val cached = courseCache[this]

        if (cached != null) {
            return cached
        }

        val doc = Jsoup.connect(String.format(BASE_COURSE_URL, subject, course)).get()
        val content = doc.selectFirst(".content")
        // search for all sections
        val sections = content.select(".section1").apply {
            addAll(content.select(".section2"))
        }
        // retrieve the rest of their information and map by their name
        val mappedSections = sections
            .mapNotNull { resolveSection(subject, course, it) }
            .associateBy { it.section }.toMutableMap()
        val course = Course (
            mappedSections,
            subject,
            course
        )

        courseCache[this] = course

        return course
    }
}

object InputClassAdapter: TypeAdapter<InputClass>() {
    override fun write(out: JsonWriter, value: InputClass) {
        out.value(value.subject + " " + value.course)
    }

    override fun read(reader: JsonReader): InputClass {
        val input = reader.nextString().split(" ")
        return InputClass(input[0], input[1])
    }
}

fun resolveSection(subject: String, course: String, element: Element): Section? {
    // this element is under a table, so we are essentially reading the columns for info
    val elements = element.children()

    // find the name for this section, if non-existent, return null (invalid section)
    val sectionName = elements[1].selectFirst("a") ?: return null
    // find the link to the section
    val sectionLink = sectionName.attr("href")
    // find any comments
    val comments = element.selectFirst(".accordion-inner")?.text() ?: ""

    val status = elements[0].text()
    val activity = elements[2].text()
    val term = elements[3].text()
    val days = elements[5].text()
    val start = elements[6].text()
    val end = elements[7].text()

    // resolve the section info page
    val doc = Jsoup.connect(String.format(BASE_SECTION_URL, subject, course, sectionName.text())).get()
    val content = doc.selectFirst(".content")
    // find all tables in the content
    val tables = content.select("table")
    // get seating information if available (as an array of entries in a row of a table)
    val seatingTableEntries = tables.getOrNull(3)?.selectFirst("tbody")?.children()

    fun findNumber(element: Int): Int {
        return seatingTableEntries?.get(element)?.selectFirst("strong")?.text()?.toIntOrNull() ?: 0
    }

    val totalSeatsRemaining = findNumber(0)
    val seatsRegistered = findNumber(1)
    val generalSeatsRemaining = findNumber(2)
    val restrictedSeatsRemaining = findNumber(3)


    return Section (
        subject, course, status,
        sectionName.text(), sectionLink,
        activity, term, days, start,
        end, comments, totalSeatsRemaining,
        seatsRegistered, generalSeatsRemaining,
        restrictedSeatsRemaining
    )
}