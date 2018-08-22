package xyz.kfdykme.ktor.snmpwatcher.core.db.data

import org.jetbrains.exposed.sql.Table
import xyz.kfdykme.ktor.snmpwatcher.core.db.data.Watch.autoIncrement
import xyz.kfdykme.ktor.snmpwatcher.core.db.data.Watch.primaryKey

object EquInfo: Table(){

    val ip = EquInfo.varchar("ip", 20).primaryKey() // Column<String>
    val type = EquInfo.varchar("type", 8)
    val community = EquInfo.varchar("comunity",20)

}