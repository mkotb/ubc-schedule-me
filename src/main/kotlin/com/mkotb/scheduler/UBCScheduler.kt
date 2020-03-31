package com.mkotb.scheduler

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import com.mkotb.scheduler.db.*
import com.mkotb.scheduler.routes.CourseController
import com.mkotb.scheduler.routes.InstructorController
import com.mkotb.scheduler.routes.ScheduleController
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.json.FromJsonMapper
import io.javalin.json.JavalinJson
import io.javalin.json.ToJsonMapper
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.lang.reflect.Modifier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

var debug = System.getenv("DEBUG")?.startsWith("T") ?: false
val UPDATE_SEATS = System.getenv("UPDATE_SEATS")?.startsWith("T") ?: true
val CURRENT_YEAR = System.getenv("UBC_YEAR") ?: "2019"
val CURRENT_SESSION = System.getenv("UBC_SESSION") ?: "W"
val BASE_URL = "https://courses.students.ubc.ca"
val BASE_COURSE_URL = "$BASE_URL/cs/courseschedule?tname=subj-course&dept=%s&course=%s&pname=subjarea&sessyr=$CURRENT_YEAR&sesscd=$CURRENT_SESSION"
val BASE_SECTION_URL = "$BASE_URL/cs/courseschedule?pname=subjarea&tname=subj-section&dept=%s&course=%s&section=%s&sessyr=$CURRENT_YEAR&sesscd=$CURRENT_SESSION"
val ALL_SUBJECTS_URL = "$BASE_URL/cs/courseschedule?tname=subj-all-departments&sessyr=$CURRENT_YEAR&sesscd=$CURRENT_SESSION&pname=subjarea"
val DEPARTMENTS_URL = "$BASE_URL/cs/courseschedule?pname=subjarea&tname=subj-department&dept=%s&sessyr=$CURRENT_YEAR&sesscd=$CURRENT_SESSION"

val invalidBuildings = listOf (
    "to be announced",
    "no scheduled meeting"
)

fun main() {
    loadDatabase()

    // create our web server
    val app = Javalin.create()
        .port(System.getenv("SERVER_PORT")?.toIntOrNull() ?: 80)
        .enableCorsForAllOrigins()
        .start()

    // JSON deserializer (includes validation)
    val moshi = Moshi.Builder()
        .add(SchedulingFactor.jsonFactory)
        .add(KotlinJsonAdapterFactory())
        .build()

    // JSON serializer
    val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .excludeFieldsWithModifiers(Modifier.TRANSIENT)
        .create()

    // set mappers to use our serialiers/deserializers
    JavalinJson.toJsonMapper = object: ToJsonMapper {
        override fun map(obj: Any): String {
            return gson.toJson(obj)
        }
    }

    JavalinJson.fromJsonMapper = object: FromJsonMapper {
        override fun <T> map(json: String, targetClass: Class<T>): T {
            return moshi.adapter(targetClass).fromJson(json)!!
        }
    }

    val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    var count = 0

    scheduledExecutor.scheduleAtFixedRate({ try {
        if ((count % 24) == 0) {
            count = 1
            pullCourses()
            return@scheduleAtFixedRate
        }

        if (count > 1) {
            updateAllSeats()
        }

        count++
    } catch (e: Exception) { e.printStackTrace() }}, 0L, 2L, TimeUnit.HOURS)

    app.routes {
        path("courses") {
            get(CourseController::getAllCourses)
        }

        path("instructors") {
            get(InstructorController::getAllInstructors)
        }

        path("schedule") {
            post(ScheduleController::schedule)
        }
    }

    val googleKey = System.getenv("GOOGLE_KEY")

    // if the google key is provided, get all the times
    // from each building to another
    if (googleKey != null) {
        populateBuildingTravelTimes(googleKey)
    }
}

fun loadDatabase() {
    Database.connect(
        url = System.getenv("DB_URL"),
        driver = System.getenv("DB_DRIVER"),
        user = System.getenv("DB_USER") ?: "",
        password = System.getenv("DB_PASS") ?: ""
    )

    // create tables
    transaction {
        create(Courses)
        create(Sections)
        create(SectionLocations)
        create(Buildings)
        create(BuildingTravelTimes)
        create(Instructors)
        create(InstructorSectionAssociations)
    }
}

/**
 * finds building travel times and inserts it into database
 * makes nC2 requests to the Google Maps API where n is the
 * amount of buildings in the database
 */
fun populateBuildingTravelTimes(googleKey: String) {
    transaction {
        val apiContext = GeoApiContext.Builder()
            .apiKey(googleKey)
            .build()
        val buildings = Building.all().toList()
        val entries = ArrayList<String>()

        // iterate through every building
        for (i in buildings.indices) {
            val buildingA = buildings[i]

            // iterate through every building after it in the list
            for (j in (i + 1) until buildings.size) {
                val buildingB = buildings[j]

                // if the travel time has already been calculated, ignore it
                if (BuildingTravelTime.find(buildingA, buildingB) != null) {
                    continue
                }

                // call Google Map's API to find the walking travel time
                val route = DirectionsApi.getDirections (
                    apiContext,
                    "${buildingA.name} UBC",
                    "${buildingB.name} UBC"
                ).mode(TravelMode.WALKING).awaitIgnoreError()?.routes?.getOrNull(0)

                // notify manual input if there was an error from the API
                if (route == null) {
                    println("${buildingA.name}->${buildingB.name} needs to be manually inputted")
                    continue
                }

                // calculate walk time by adding every duration in every "direction" along the way
                val walkTime = route.legs.map { it.duration.inSeconds }.sum()

                // insert into db
                BuildingTravelTime.new {
                    firstBuilding = buildingA
                    secondBuilding = buildingB
                    time = walkTime
                }
            }

            println("Finished ${buildingA.name}")
        }

        println("Done! ${entries.size}")
    }
}

fun updateAllSeats() {
    if (!UPDATE_SEATS) {
        return
    }

    println("Updating all seat information...")
    val sections = transaction { Section.all().toList() }
    val sectionThreadPool = Executors.newFixedThreadPool(
        System.getenv("UBC_POOL_SIZE")?.toIntOrNull() ?: 5
    )
    val count = AtomicInteger(0)

    sections.forEach { section ->
        sectionThreadPool.submit {
            transaction {
                updateSection(section)
            }

            val position = count.incrementAndGet()

            if (position >= sections.size) {
                println("Completed updating all seat information!")
            }
        }
    }
}

/**
 * Pulls all subject data from UBC's site
 */
fun pullCourses() {
    println("Pulling all subjects")

    // connect to the directory of the site
    val doc = Jsoup.connect(ALL_SUBJECTS_URL).get()
    // find all subjects by selecting the text of all links
    // in the main table
    val subjects = doc
        .selectFirst("#mainTable")
        .selectFirst("tbody")
        .select("a").map { it.text() }
    // thread pool where the extraction per subject will be performed
    // any number above 20 will result in the site blocking the requests
    val subjectThreadPool = Executors.newFixedThreadPool(
        System.getenv("UBC_POOL_SIZE")?.toIntOrNull() ?: 10
    )
    // how many subjects have been finished being pulled
    val position = AtomicInteger(0)

    subjects.forEach { subject ->
        subjectThreadPool.submit {
            // connect to it's department URL
            val departmentDoc = Jsoup.connect(String.format(DEPARTMENTS_URL, subject)).get()
            // find all courses by selecting the text of all links in the main table
            val courses = departmentDoc
                .selectFirst("#mainTable")
                .selectFirst("tbody")
                .select("a")

            courses.forEach { courseElement ->
                try {
                    resolveCourse(subject, courseElement)
                } catch (ex: Exception) {
                    // catch the exception so extraction can continue
                    ex.printStackTrace()
                }
            }

            val p = position.incrementAndGet()
            println("Finished $subject $p/${subjects.size}")
        }
    }
}

fun resolveCourse(subject: String, courseElement: Element) {
    val fullName = courseElement.text()
    // find the course name by taking
    // the second half of the text
    val courseName = fullName.split(" ")[1]
    // connect to the course page
    val courseDoc = Jsoup.connect(String.format(BASE_COURSE_URL, subject, courseName)).get()
    val content = courseDoc.selectFirst(".content")

    val course = transaction {
        if (debug) {
            addLogger(StdOutSqlLogger)
        }

        Course.findOrNew(subject, courseName)
    }

    // find all sections and resolve them
    content.select(".section1").apply {
        addAll(content.select(".section2"))
    }.forEach {
        resolveSection(subject, course, it)
    }
}

fun updateSection(section: Section) {
    val course = section.course
    val sectionDoc = Jsoup.connect(String.format(BASE_SECTION_URL,
        course.subjectCode,
        course.courseNumber,
        section.name.split(" ").last()
    )).get()
    val content = sectionDoc.selectFirst(".content")
    // find all tables in the content
    val tables = content.select("table")

    updateSeatInformation(section, tables)
}

fun updateSeatInformation(section: Section, tables: Elements) {
    section.apply {
        // get seating information if available (as an array of entries in a row of a table)
        val seatingTableEntries = tables.firstOrNull {
            it.firstOrNull()?.text() == "Seat Summary"
        }?.selectFirst("tbody")?.children()

        fun findNumber(element: Int): Int {
            return seatingTableEntries?.getOrNull(element)?.selectFirst("strong")?.text()?.toIntOrNull() ?: 0
        }

        // copy information from columns
        totalRemaining = findNumber(0)
        currentlyRegistered = findNumber(1)
        generalRemaining = findNumber(2)
        restrictedRemaining = findNumber(3)
    }
}

fun resolveSection(subjectName: String, course: Course, element: Element): Section? {
    // this element is under a table, so we are essentially reading the columns for info
    val elements = element.children()
    // find the name for this section, if non-existent, return null (invalid section)
    val sectionElement = elements[1].selectFirst("a") ?: return null
    // a map of day of week to the building the section is held in
    val dayToBuilding = HashMap<String, String>()

    val operation: Section.() -> Unit = {
        // find the link to the section
        link = sectionElement.attr("href")

        // find any comments
        comments = element.selectFirst(".accordion-inner")?.text() ?: ""

        // insert information from columns
        status = elements[0].text()
        activity = elements[2].text()
        term = elements[3].text()
        daysRaw = elements[5].text()

        // takes time like 9:30 and converts it into minutes into the day
        // uses 24 hour time
        fun textToTime(input: String): Int {
            val e = input.split(":")

            if (e.size != 2) {
                return 0
            }

            return e[0].toInt() * 60 + e[1].toInt()
        }

        startMinutes = textToTime(elements[6].text())
        endMinutes = textToTime(elements[7].text())

        // connect to the section page
        val doc = Jsoup.connect(String.format(BASE_SECTION_URL, subjectName, course.courseNumber, sectionElement.text().split(" ").last())).get()
        val content = doc.selectFirst(".content")
        // find all tables in the content
        val tables = content.select("table")

        // find the schedule entries for this section
        val dayEntries = content.select(".table-striped")
            .firstOrNull {
                it.firstOrNull()?.firstOrNull()?.firstOrNull()?.text() == "Term"
            }
            ?.selectFirst("tbody")
            ?.children()

        // map every day to the building the session is held in
        dayEntries?.forEach { element ->
            val columns = element.children()

            // ignore if not a section time (like a comment or something)
            if (columns.size < 6) {
                return@forEach
            }

            // find all days this portion is held in
            val entryDays = columns[1].text().split(" ")
            // find the text, ignore if there is no building
            val building = columns.getOrNull(4)?.text() ?: return@forEach

            // ignore this entry if the building is invalid (e.g. it's TBA)
            if (invalidBuildings.contains(building.toLowerCase())) {
                return@forEach
            }

            // map each day to the building
            entryDays.forEach {
                // remove any qualifiers
                val parsedDay = it.toLowerCase().replace("*", "").replace("^", "")

                if (parsedDay.isNotEmpty()) {
                    dayToBuilding[parsedDay] = building
                }
            }
        }

        val instructorValue = tables.firstOrNull {
            it.firstOrNull()?.firstOrNull()?.firstOrNull()?.text()?.contains("Instructor") ?: false
        }?.select("a")?.map { it.text() }/*?.joinToString(separator = ";") { it.text() } */

        instructorValue?.forEach { name ->
            InstructorSectionAssociation.findOrNew(
                this,
                Instructor.findOrNew(name)
            )
        }

        // set the instructor
        //if (instructorValue != null && instructorValue.length < 512) {
        // instructor = instructorValue
        //}

        updateSeatInformation(this, tables)
    }

    return transaction {
        if (debug) {
            addLogger(StdOutSqlLogger)
        }

        // create or update the section
        val section = Section.updateOrNew(course, sectionElement.text(), operation)

        // add a section location for every day -> building
        dayToBuilding.forEach { (day, building) ->
            val b = Building.findOrNew(building)

            SectionLocation.newOrUpdate(section, day, b)
        }

        section
    }
}

// shortcut for getting the first child element or null
private fun Element.firstOrNull(): Element? {
    return children().firstOrNull()
}