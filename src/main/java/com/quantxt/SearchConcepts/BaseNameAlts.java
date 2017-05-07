package com.quantxt.SearchConcepts;


/**
 * Created by matin on 4/6/17.
 */
public class BaseNameAlts<T>  {
    private String name;
    private String [] alts;
    private T data;

    public BaseNameAlts(String n, String [] a){
        name = n;
        alts = a;
    }

    public BaseNameAlts(String n, String [] a, T d){
        name = n;
        alts = a;
        data = d;
    }

    public String getName(){return name;}
    public String [] getAlts(){return alts;}

    public T getData(){return data;}
    public void setData(T o){data = o;}

}
