package xyz.kfdykme.ktor.snmpwatcher.bean

/**

 * @author  wimkf

 * @create 2018-08-20 10:14

 * @desc      交换机连接状态

        */

class SwitchStatus{

    var ip :String =""
    var status :String = ""


    companion object {
        val STATUS_CONNECTED = "connected"
        val STATUS_DISCONNECTED = "disconnected"
        val STATUS_CONNECTING = "connecting"
    }
}