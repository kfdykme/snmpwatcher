package xyz.kfdykme.demo.snmpapp.core

import com.google.gson.Gson

import org.snmp4j.PDU

class SnmpUtil{

    companion object {

        //处理res
        fun convertRes(res: PDU) :ResPdu?{
            if(res.isResponsePdu){
                var src = Gson().toJson(res)

                var v = res.variableBindings

                var respdu = ResPdu()
                for(v in res.variableBindings){
                    respdu.vars.add(ResPdu.Var(v.oid.toString(),v.variable.toString()))
                }
                return  respdu

            }
            return null
        }
    }
}