package xyz.kfdykme.ktor.snmpwatcher.bean

data class SwitchInfo(
        val ip:String,
        val type:String,
        val sysDesc:String,
        val sysUptime:String,
        val sysName:String,
        val sysService:String,
        val ifNumber:String,
        val startTime:String,
        val endTime:String
        ){
    var totalNumber = 0
    var hasNext = false
    var page = 0
    var pageSize = 0

    var ifInfos:MutableList<IfInfo> = mutableListOf()//12

    var times:MutableList<Long> = mutableListOf()

    data class IfInfo(val oid:String,val desc:String,val name:String){

        var values:MutableList<MutableList<String>> = mutableListOf()//37<N>
    }

}