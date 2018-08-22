package xyz.kfdykme.demo.snmpapp.core


import com.google.gson.Gson
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.TransportMapping
import org.snmp4j.event.ResponseEvent
import org.snmp4j.event.ResponseListener
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.*
import org.snmp4j.transport.DefaultUdpTransportMapping
import xyz.kfdykme.ktor.snmpwatcher.bean.SwitchStatus
import xyz.kfdykme.ktor.snmpwatcher.core.SnmpBase
import xyz.kfdykme.ktor.snmpwatcher.core.db.data.Watch

class SnmpClient:SnmpBase{

    val TAG = "SnmpClient"
    var target :CommunityTarget? = null
    var targetAddress:Address? = null
    var config:SnmpConfig? = null
    var initialized = false

    /*
     *  mlistener
     *  将snmp的请求和返回用logger打印出来
     *  并回调listener的onResponse
     */

    val mlistener :ResponseListener = ResponseListener {



        if(it.peerAddress!= null){

            lisenter?.onResponseGet(it)
        } else{
            lisenter?.onFail(it)
        }
    }

    interface SnmpListener {
        fun onResponseWalk(event:ResponseEvent)
        fun onResponseGet(event:ResponseEvent)
        fun onComplete()
        fun onWalkEnd(it:String)
        fun onFail(event: ResponseEvent)
    }

    var lisenter :SnmpListener ? = null
    lateinit var snmp :Snmp


    companion object {

        //懒加载的单例
        val instance: SnmpClient by lazy { SnmpClient() }


        /*
        * read 从设备总通过snmp 2c协议获取设备信息并导入到数据库
        * @param ip
        * @param type
        * @param community
        * */
        fun read(ip:String,type:String,community:String){

            instance.config!!.ip = ip
            instance.config!!.type = type
            instance.config!!.community = community
            instance.init(instance.config!!)

            if(checkConnection(ip,type,community).status == SwitchStatus.STATUS_DISCONNECTED) {
                println("该设备没有连接上,放弃抓取数据: -- ip = "+ip)
                return

            }

            for(o in instance.config!!.varargs){


                if(o.method.equals("GET"))
                {
                    instance.get(o.oid)
                }
                if(o.method.equals("WALK")){

                    instance.walk(o.oid)
                }
            }
        }



        //TODO:好像无法不依赖数据库完成该操作 暂时没有实现该方法
        /*
        * checkAllConnection 获取所有设备连接状态
        *
        * return <MutableList<SwitchStatus>>
        * */
        fun checkAllConnection():MutableList<SwitchStatus>{
            var status = mutableListOf<SwitchStatus>()

            //对所有已有设备发送get


            return status
        }

        /**
        * checkConnection 尝试对设备发出请求,并建立连接
         * @param ip
         * @param type
         * @param community
         *
         * return <SwitchStatus> status
        * */

        fun checkConnection(ip:String,type:String,community:String):SwitchStatus{
            var status = SwitchStatus()
            status.ip = ip

            //初始化
            instance.config!!.ip = ip
            instance.config!!.type = type
            instance.config!!.community = community
            instance.init(instance.config!!)

            //初始化失败返回连接不成功
            if(instance.config!!.varargs.size ==  0)
            {
                status.status = SwitchStatus.STATUS_DISCONNECTED
                return status
            }

            for(va  in instance.config!!.varargs){
                var pdu =PDU()
                pdu.add(VariableBinding(OID(va.oid)))
                pdu.type = PDU.GET
                var responseEvent = instance.snmp.send(pdu, instance.target)



                //仅作一次 返回状态
                if(va == instance.config!!.varargs[0]){
                    //发送get 没有返回 所以 连接不成功
                    if(responseEvent == null || responseEvent.response == null)
                    {
                        status.status = SwitchStatus.STATUS_DISCONNECTED
                        return status
                    }
                    //有返回,连接成功
                    println(responseEvent.peerAddress)
                    status.ip = responseEvent.peerAddress.toString()
                    status.status = SwitchStatus.STATUS_CONNECTED
                }

                transaction {
                    var q = Watch.select {
                        Watch.ip.eq(ip)
                    }.last()


                }

            }


            return status
        }


        //NOTE:弃用
        fun readAll(){

            for(o in instance.config!!.varargs){


                if(o.method.equals("GET"))
                {
                    instance.get(o.oid)
                }
                if(o.method.equals("WALK")){

                    instance.walk(o.oid)
                }
            }
        }
    }

    /*
     * init 初始化SnmpCLient 附上addr 和community,版本默认2c
     * @params <String> addr
     * @params <String> community
     *
     */

    fun init(config:SnmpConfig) {
        this.config = config
        target = CommunityTarget()
        target!!.community = OctetString(config.community)
        target!!.version = SnmpConstants.version2c

        targetAddress = GenericAddress.parse("udp:${config.ip}/161")

        target!!.address = targetAddress
        target!!.retries = 5
        target!!.timeout = 1000

        initialized = true
        for (o in instance.config!!.varargs) {

            this.config!!.lastOid = o.oid

        }
    }


    private constructor(){
        var transport = DefaultUdpTransportMapping()
        snmp = Snmp(transport)
        transport.listen()
    }

    fun get(oid: String){
        if(!initialized){
            return
        }

        var pdu =PDU()
        pdu.add(VariableBinding(OID(oid)))
        pdu.type = PDU.GET

        snmp.send(pdu, target,null,mlistener)
    }

    fun walk(oid:String){
        if(!initialized){
            return
        }

        var pdu =PDU()
        pdu.add(VariableBinding(OID(oid)))
        pdu.type = PDU.GETNEXT

        var matched = true

        while(matched){

            var responseEvent = snmp.send(pdu,target)

            if(responseEvent == null || responseEvent.response == null)
                break

            var res = responseEvent.response
            var nextOid = ""

            for(v in res.variableBindings) {
                nextOid = v.oid.toDottedString()
                if (!nextOid.startsWith(oid))
                {
                    matched = false
                    break
                }
            }

            if(!matched)
                break;

            lisenter?.onResponseWalk(responseEvent)
            pdu.clear()
            pdu.add( VariableBinding(OID(nextOid)))

        }

        lisenter?.onWalkEnd(oid)
    }



}