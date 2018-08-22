package xyz.kfdykme.demo.snmpapp.core


class ResPdu{
    var vars :MutableList<Var> = mutableListOf()

    fun getValue(oid:String):String{
        for(v in vars){
            if(oid.equals(v.oid))
                return v.variable

        }
        return ""
    }

    class Var( var oid:String = "", var variable:String = ""){


    }


}