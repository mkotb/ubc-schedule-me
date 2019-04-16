package com.mkotb.scheduler.db

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException

object Buildings: IntIdTable() {
    val name = varchar("name", 64)

    init {
        uniqueIndex(name)
    }
}

class Building(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Building>(Buildings) {
        fun findOrNew(name: String): Building {
            val formatted = name.toLowerCase()
            val previous = find(name)

            if (previous != null) {
                return previous
            }

            return try {
                Building.new {
                    this.name = formatted
                }
            } catch (ex: ExposedSQLException) {
                // the exception might have occurred because
                // the building now exists, thus let's try
                // and find it. If it's not there, the exception
                // occurred for another reason and we should continue
                // to throw the exception
                return find(name) ?: throw ex
            }
        }

        fun find(name: String): Building? {
            return find { Buildings.name eq name.toLowerCase() }.firstOrNull()
        }
    }

    var name by Buildings.name
}
