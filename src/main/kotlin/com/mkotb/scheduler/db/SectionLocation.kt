package com.mkotb.scheduler.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.and

object SectionLocations: IntIdTable() {
    val section = reference("section", Sections)
    val day = varchar("day", 3)
    val building = reference("building", Buildings)

    init {
        uniqueIndex(section, day)
    }
}

class SectionLocation(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<SectionLocation>(SectionLocations) {
        fun newOrUpdate(section: Section, day: String, building: Building) {
            val location = find(section, day)

            if (location != null) {
                location.building = building
            } else {
                SectionLocation.new {
                    this.section = section
                    this.dayRaw = day
                    this.building = building
                }
            }
        }

        fun find(section: Section, day: String): SectionLocation? {
            return find { (SectionLocations.section eq section.id) and (SectionLocations.day eq day) }.firstOrNull()
        }
    }

    var section by Section referencedOn SectionLocations.section
    // todo in search append "UBC" to it
    var building by Building referencedOn SectionLocations.building

    private var dayRaw by SectionLocations.day

    val day
        get() = daysMap[dayRaw.toLowerCase()]!!
}
