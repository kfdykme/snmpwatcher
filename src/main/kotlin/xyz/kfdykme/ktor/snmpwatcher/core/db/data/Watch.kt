package xyz.kfdykme.ktor.snmpwatcher.core.db.data

import org.jetbrains.exposed.sql.Table


object Watch:Table(){

    val id =  integer("id").autoIncrement().primaryKey() // Column<Int>
    val ip =  varchar("ip", 20) // Column<String>
    val type =  varchar("type",8)
    val time = long("time")
    val walk = text("walk")
    val get = text("get") // is json


}