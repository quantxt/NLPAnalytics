package com.quantxt.SearchConcepts;

/**
 * Created by matin on 3/31/17.
 */
public class NamedEntity {
    private Entity entity;
    private String name;
    private boolean isParent = false;
    private String [] alts;

    public NamedEntity(String n , String [] p){
        name = n;
        alts = p;
    }

    public void setEntity(Entity e){
        entity = e;
    }

    public Entity getEntity(){return entity;}
    public String getName(){return name;}
    public String [] getAlts(){return alts;}
    public boolean isParent(){
        return isParent;
    }

    public void setParent(boolean s){
        isParent = s;
    }
}
