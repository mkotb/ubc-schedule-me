package com.mkotb.scheduler.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.and

object BuildingTravelTimes: IntIdTable() {
    val firstBuilding = reference("firstBuilding", Buildings)
    val secondBuilding = reference("secondBuilding", Buildings)
    val time = long("time")

    init {
        uniqueIndex(
            firstBuilding,
            secondBuilding
        )
    }
}

class BuildingTravelTime(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<BuildingTravelTime>(BuildingTravelTimes) {
        fun find(a: Building, b: Building): BuildingTravelTime? {
            fun attempt(first: Building, second: Building): BuildingTravelTime? {
                return find { (BuildingTravelTimes.firstBuilding eq first.id) and
                        (BuildingTravelTimes.secondBuilding eq second.id) }.firstOrNull()
            }

            return attempt(a, b) ?: attempt(b, a)
        }
    }

    var firstBuilding by Building referencedOn BuildingTravelTimes.firstBuilding
    var secondBuilding by Building referencedOn BuildingTravelTimes.secondBuilding
    var time by BuildingTravelTimes.time
}