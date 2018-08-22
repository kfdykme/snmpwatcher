package xyz.kfdykme.ktor.snmpwatcher.bean;

public class HttpRes<T> {
    public boolean error = false;
    public int errcode = 0;
    public T body;


}
