package xyz.kfdykme.ktor.snmpwatcher


import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.jwt
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.experimental.selects.select
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils.create

import org.jetbrains.exposed.sql.transactions.transaction
import org.json.simple.JSONObject
import org.snmp4j.event.ResponseEvent
import xyz.kfdykme.demo.snmpapp.core.SnmpClient
import xyz.kfdykme.demo.snmpapp.core.SnmpConfig
import xyz.kfdykme.demo.snmpapp.core.SnmpUtil
import xyz.kfdykme.ktor.snmpwatcher.bean.*
import xyz.kfdykme.ktor.snmpwatcher.core.db.SnmpDb
import xyz.kfdykme.ktor.snmpwatcher.core.db.data.Content
import xyz.kfdykme.ktor.snmpwatcher.core.db.data.EquInfo
import xyz.kfdykme.ktor.snmpwatcher.core.db.data.Watch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.collections.HashMap


open class SimpleJWT(val secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier = JWT.require(algorithm).build()
    fun sign(name: String): String = JWT.create().withClaim("name", name).sign(algorithm)
}

class InvalidCredentialsException(message: String) : RuntimeException(message)

fun checkDataTypeShouldBreakByName(name: String): Boolean {
    when (name) {
        "IfDescr" -> {
            return true
        }
        "IfType" -> {
            return true
        }
        "IfPhysAddress" -> {
            return true
        }
    }

    return false
}

fun Application.main() {

    init()

    val simpleJwt = SimpleJWT("snmp-secret-for-jwt")
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(Authentication) {
        jwt {
            verifier(simpleJwt.verifier)
            validate {
                UserIdPrincipal(it.payload.getClaim("name").asString())
            }
        }
    }
    install(StatusPages) {
        exception<InvalidCredentialsException> { exception ->
            call.respond(HttpStatusCode.Unauthorized, mapOf("OK" to false, "error" to (exception.message ?: "")))
        }
    }
    install(DefaultHeaders)
    install(CallLogging)
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        allowCredentials = true
        anyHost()
    }
    install(Routing) {

        route("/switch") {
            get {


                var ip = call.request.queryParameters["ip"] ?: "192.168.2.1"
                println(ip)
                var endTime: Long = call.request.queryParameters["endTime"]?.toLong() ?: System.currentTimeMillis()
                var startTime: Long = call.request.queryParameters["startTime"]?.toLong() ?: (endTime - 60 * 60 * 1000)
                var page: Int = call.request.queryParameters["page"]?.toInt() ?: 1
                var pageSize: Int = call.request.queryParameters["pageSize"]?.toInt() ?: 999

                var startIndex = pageSize * (page - 1)
                var endIndex = pageSize * page

                var totalNumber = 0
                lateinit var info: SwitchInfo
                transaction {
                    var query = Watch.select {
                        Watch.ip.eq(ip)
//                        Watch.time.between(startTime, endTime)
                    }


                    totalNumber = query.count()

                    if (totalNumber != 0) {

                        var first =
                                query.first()

                        info = getWatchInfoFromFirst(
                                first = first,
                                ip = ip,
                                type = first[Watch.type],
                                startTime = startTime,
                                endTime = endTime
                        )

                        var walk = Gson().fromJson(first[Watch.walk], Content::class.java)

                        for (w in walk.list) {

                            var ifinfo: SwitchInfo.IfInfo = SwitchInfo.IfInfo(
                                    oid = Gson().fromJson(w["oid"], String::class.java),
                                    desc = Gson().fromJson(w["desc"], String::class.java),
                                    name = Gson().fromJson(w["name"], String::class.java)
                            )



                            for (l in w["value"] as JsonArray) {


                                var datas = mutableListOf<String>()
                                ifinfo.values.add(datas)

                            }
                            info.ifInfos.add(ifinfo)

                        }


//
                        var pIndex = startIndex
                        query.forEach {
                            if (pIndex >= startIndex && pIndex < endIndex) {

                                //NOTE:增加时间戳
                                var time = it[Watch.time]
                                info.times.add(time)

                                var fwalk = Gson().fromJson(it[Watch.walk], Content::class.java)

                                var index = 0
                                for (fw in fwalk.list) {

                                    var wName = Gson().fromJson(fw["name"], String::class.java)
                                    if (checkDataTypeShouldBreakByName(wName) && pIndex > startIndex) {
                                        //println("break "+wName)
                                        index++
                                        continue
                                    }


                                    var ifinfo = info.ifInfos.get(index = index)
                                    var index2 = 0
                                    for (l in fw["value"] as JsonArray) {
                                        var oid = l.asJsonObject["oid"]
                                        var value = l.asJsonObject["value"]

                                        var datas = ifinfo.values.get(index2)
                                        datas.add(Gson().fromJson(value, String::class.java))
                                        index2++


                                    }

                                    index++


                                }

                            }
                            pIndex++

                        }

                    } else {
                        info = SwitchInfo(
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                startTime.toString(),
                                endTime.toString())
                    }
                }

                info.totalNumber = totalNumber
                info.hasNext = totalNumber > endIndex
                info.page = page
                info.pageSize = pageSize


                var res: HttpRes<SwitchInfo> = HttpRes()
                res.body = info

                call.respondText(Gson().toJson(res), ContentType.Application.Json)

            }


        }
        route("/equ") {
            get {

                var httpResponse = HttpResponse()

                var act = call.request.queryParameters["act"] ?: "insert"
                print(call.request.queryParameters["act"] )
                print(act)

                var rip = call.request.queryParameters["ip"]
                if (rip == null) {
                    httpResponse.errcode = "无效ip"
                    httpResponse.result = 0
                    call.respondText(Gson().toJson(httpResponse), ContentType.Application.Json)
                }
                if(act == "insert"){
                    var rtype = call.request.queryParameters["type"]
                    if (rtype == null) {
                        httpResponse.errcode = "请输入type"
                        httpResponse.result = 0
                        call.respondText(Gson().toJson(httpResponse), ContentType.Application.Json)
                    }

                    var rcommunity = call.request.queryParameters["community"]
                    if (rcommunity == null) {
                        httpResponse.errcode = "请输入community"
                        httpResponse.result = 0
                        call.respondText(Gson().toJson(httpResponse), ContentType.Application.Json)
                    }

                    transaction {
                        //NOTE:插入数据库
                        create(EquInfo)

                        var query = EquInfo.select {
                            EquInfo.ip.eq(rip!!)
                        }

                        if (query.count() != 0) {
                            EquInfo.update(
                                    {
                                        EquInfo.ip.eq(rip!!)
                                    },
                                    1,
                                    {
                                        it[ip] = rip!!
                                        it[type] = rtype!!
                                        it[community] = rcommunity!!
                                    }
                            )
                        } else {
                            EquInfo.insert {

                                it[ip] = rip!!
                                it[type] = rtype!!
                                it[community] = rcommunity!!

                            }
                        }
                    }
                } else if(act == "delete"){

                    transaction {

                        EquInfo.deleteWhere {
                            EquInfo.ip.eq(rip!!)
                        }

                    }
                }



                call.respondText(Gson().toJson(httpResponse), ContentType.Application.Json)

            }
            post{
                var a = call.request.headers
                println(Gson().to(call.request)
                )
                call.respondText("",ContentType.Application.Json)
            }
        }
        /*
        * 获取所有设备的连接状态
        * */
        route("/status"){
            get{

                var rip = call.request.queryParameters["ip"]
                if(rip != null ){
                    println(rip+"----------------------------")

                    var res2 = HttpRes<SwitchStatusWithIp>()
                    var withIp :SwitchStatusWithIp?= null
                    transaction {

                        var equ = EquInfo.select {  EquInfo.ip.eq(rip) }.first()
                        var ip = equ[EquInfo.ip]
                        var type = equ[EquInfo.type]
                        var community = equ[EquInfo.community]

                        //NOTE:查询设备连接状态及其他
                        var query = Watch.select { Watch.ip.eq(rip) }


                        var first:ResultRow? = null

                        if(query.count()!=0) first = query.first()
                        var info= getWatchInfoFromFirst(first,
                                ip,
                                type,
                                System.currentTimeMillis(),
                                System.currentTimeMillis())
                        var status = SnmpClient.checkConnection(
                                ip, type, community
                        )
                        withIp = SwitchStatusWithIp(status,info)

                    }
                    res2.body = withIp
                    call.respondText(text = Gson().toJson(res2), contentType = ContentType.Application.Json)


                } else {
                    var res = HttpRes<MutableList<SwitchStatus>>()
                    res.body = mutableListOf()
                    transaction {
                        var query = EquInfo.selectAll()

                        if(query.count() > 0){

                            query.forEach {
                                var ip = it[EquInfo.ip]
                                var type = it[EquInfo.type]
                                var community = it[EquInfo.community]


                                var status = SwitchStatus()
                                status.status = SwitchStatus.STATUS_CONNECTING
                                status.ip = ip
                                res.body.add(status )
                            }


                        }
                    }
                    call.respondText(Gson().toJson(res),ContentType.Application.Json)
                }




            }
        }
    }


}

fun getWatchInfoFromFirst(first:ResultRow?,ip:String,type:String,startTime:Long,endTime:Long):SwitchInfo{

    var sysDesc: String = ""
    var sysUptime: String = "0"
    var sysName: String = ""
    var sysService: String = ""
    var ifNumber: String = "0"
    if(first!=null)
    {
        var get = Gson().fromJson(first[Watch.get], Content::class.java)
        for (g in get.list) {

            var name = Gson().fromJson(g["name"], String::class.java)

            when (name) {
                "SysDesc" -> {
                    sysDesc = Gson().fromJson(g["value"], String::class.java)
                }
                "sysUptime" -> {

                    sysUptime = Gson().fromJson(g["value"], String::class.java)
                }
                "SysName" -> {

                    sysName = Gson().fromJson(g["value"], String::class.java)
                }
                "SysService" -> {

                    sysService = Gson().fromJson(g["value"], String::class.java)
                }
                "IfNumber" -> {

                    ifNumber = Gson().fromJson(g["value"], String::class.java)
                }
            }
        }
    }



    return SwitchInfo(
            ip = ip,
            type = type,
            sysDesc = sysDesc,
            sysName = sysName,
            sysService = sysService,
            sysUptime = sysUptime,
            ifNumber = ifNumber,
            startTime = startTime.toString(),
            endTime = endTime.toString()
    )
}

/*
 * readConfig 获取config文件的字符串
 *
 * return <String> text
 */
fun readConfig(): String {


    var reader = BufferedInputStream(
            FileInputStream(
                    File(
                            SnmpClient.javaClass.classLoader.getResource("baseSnmpConfig.json").toURI()
                    )
            )).reader()
    return reader.readText()
}


/*
 * init 初始化除app外其他
 */
fun init() {


    println("--------------------initial snmpclient----------------------------")

    SnmpDb()

//    println(readConfig())
    var config = Gson().fromJson(
            readConfig(), SnmpConfig::class.java)

    SnmpClient.instance.init(config!!)


    var getResult = Content()
    var walkResult = Content()
    var childList: MutableList<Map<String, String>> = mutableListOf()


    //设置lisenter
    var lisenter = object : SnmpClient.SnmpListener {

        override fun onComplete() {

            //插入到数据库
            transaction {
                create(Watch)

                Watch.insert {
                    it[time] = System.currentTimeMillis()
                    it[type] = config!!.type
                    it[ip] = SnmpClient.instance.config!!.ip
                    it[get] = Gson().toJson(getResult)
                    it[walk] = Gson().toJson(walkResult)
                }
            }
            getResult.list = mutableListOf()
            walkResult.list = mutableListOf()
            println("---------------------  insert ----------------------")

        }

        override fun onResponseGet(it: ResponseEvent) {


            //1 初始化变量
            var responOid = it.response.variableBindings.get(0).oid.toString()
            var res = SnmpUtil.convertRes(it.response)


            //2 找到当前responseevent对应的oid是属于Config中的哪一个
            lateinit var curOid: SnmpConfig.OidVar
            for (o in SnmpClient.instance.config!!.varargs)
                if (o.method.equals(SnmpConfig.OIDMETHOD_GET) && o.oid.equals(responOid))
                    curOid = o

            //3 根据找到的OidVar 获取对应数据的其他属性，填充入map中
            var map = JsonObject()
            map.addProperty("oid", curOid.oid)
            map.addProperty("desc", curOid.desc)
            map.addProperty("value", res!!.getValue(curOid.oid))
            map.addProperty("name", curOid.name)

            //4 将map数据add到list中
            getResult.list.add(map)


            //5 当前是否是此次处理的最后一个response,如果是，则onComplete()
            if (responOid.equals(SnmpClient.instance.config!!.lastOid)) {
                this.onComplete()
            }


        }

        override fun onWalkEnd(it: String) {

            //1 找到当前responseevent对应的oid是属于Config中的哪一个
            lateinit var curParOid: SnmpConfig.OidVar
            for (o in SnmpClient.instance.config!!.varargs)
                if (it.startsWith(o.oid))
                    curParOid = o


            //2 当前是否是此次walk处理的最后一个response,如果是，则写入这一次walk的数据
            var value = Gson().toJson(childList)

            var map2 = JsonObject()
            map2.addProperty("oid", curParOid.oid)
            map2.addProperty("desc", curParOid.desc)
            map2.add("value", Gson().toJsonTree(childList))
            map2.addProperty("name", curParOid.name)
            walkResult.list.add(map2)


            //3 重置childList
            childList = mutableListOf()


            //4 当前是否是此次处理的最后一个response,如果是，则onComplete()
            //println("${curParOid.oid} \n${SnmpClient.instance.config!!.lastOid}")
            if (curParOid.oid.equals(SnmpClient.instance.config!!.lastOid)) {
                this.onComplete()
            }

        }

        override fun onResponseWalk(it: ResponseEvent) {
            //1 初始化变量
            var responOid = it.response.variableBindings.get(0).oid.toString()
            var res = SnmpUtil.convertRes(it.response)

            //2将这一次walk的数据插入的总的这个walk的数据小表内
            var map = HashMap<String, String>()
            map.put("oid", responOid)
            map.put("value", res!!.getValue(responOid))

            childList.add(map)


        }

        override fun onFail(event: ResponseEvent) {
            for (o in event.request.variableBindings)
                if (o.oid.equals(SnmpClient.instance.config!!.varargs.last().oid.toString())) {

                    SnmpClient.instance.log("error")

                }
        }
    }

    SnmpClient.instance.lisenter = lisenter

    //设置无限循环读取数据
    Thread(Runnable {

        while (true) {

            Thread.sleep(30 * 60 * 1000)

            lateinit var query: Query

            //TODO:未经过测试的修改,改为所有都可以抓取
            transaction {
                create(EquInfo)
                query = EquInfo.selectAll()
                query.forEach {

                    var ip = it[EquInfo.ip]
                    var type = it[EquInfo.type]
                    var community = it[EquInfo.community]

                    SnmpClient.read(ip, type, community)
                }
            }
//            SnmpClient.readAll()

        }

    }).start()

    //表示完成
    println("------------------Snmp Client init successfully-------------------")
}
