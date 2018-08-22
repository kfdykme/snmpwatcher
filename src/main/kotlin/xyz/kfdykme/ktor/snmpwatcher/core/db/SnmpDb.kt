package xyz.kfdykme.ktor.snmpwatcher.core.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import xyz.kfdykme.ktor.snmpwatcher.core.SnmpBase
import xyz.kfdykme.ktor.snmpwatcher.core.db.data.Watch
import java.sql.Blob

class SnmpDb:SnmpBase(){

    companion object {

    }

    init {

        Database.connect(
            user = "root",
            password = "Fangmunianhua2",
            url = "jdbc:mysql://localhost:3306/snmpwatcher?characterEncoding=utf8&useSSL=true&serverTimezone=UTC",
            driver = "com.mysql.jdbc.Driver"
        )
    }


}