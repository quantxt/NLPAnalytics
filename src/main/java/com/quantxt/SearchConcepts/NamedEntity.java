package com.quantxt.SearchConcepts;


import org.nd4j.shade.jackson.annotation.JsonIgnore;

import java.util.*;

/**
 * Created by matin on 3/31/17.
 */
public class NamedEntity {

    @JsonIgnore
    private transient Entity entity;
    private String name;
    private boolean isParent = false;
    private TreeSet<String> alts;

    public NamedEntity(String n , List<String> p){
        name = n;
        if (p != null) {
            alts = new TreeSet<>();
            alts.addAll(p);
        }
    }

    public void setEntity(Entity e){
        entity = e;
    }

    public void addAlts(Collection<String> newAlts){
        if (alts == null){
            alts = new TreeSet<>();
        }
        alts.addAll(newAlts);
    }

    public Entity getEntity(){return entity;}
    public String getName(){return name;}
    public Set<String> getAlts(){
        return alts;
    }
    public boolean isParent(){
        return isParent;
    }

    public void setParent(boolean s){
        isParent = s;
    }
}
