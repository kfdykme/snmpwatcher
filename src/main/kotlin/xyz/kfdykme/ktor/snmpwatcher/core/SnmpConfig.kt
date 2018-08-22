package xyz.kfdykme.demo.snmpapp.core

class SnmpConfig{
    var ip = "0.0.0.0"

    var type = "default type"

    var community = ""

    var varargs : MutableList<OidVar> = mutableListOf()

    var lastOid= ""


    companion object {

        val OIDMETHOD_GET = "GET"
        val OIDMETHOD_WALK = "WALK"

    }
    class OidVar( var oid:String = "",var desc:String = "",var name:String = "",var method :String = "GET"){



    }

}