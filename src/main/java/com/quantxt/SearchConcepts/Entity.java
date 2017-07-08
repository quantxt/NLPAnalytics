package com.quantxt.SearchConcepts;

import com.quantxt.trie.Trie;

import java.util.*;

/**
 * Created by matin on 3/22/17.
 */

public class Entity {

    final private String name;
    final private boolean isSpeaker;
    private String [] alts;

    private List<NamedEntity> namedEntities;

    private String[] context;
    private Trie contextTree;

    /*
    public Entity(String en, String [] enAlts){
        name = en;
        alts = enAlts;
        isSpeaker = true;
    }
    */

    public Entity(String en, String [] enAlts, boolean isSpeaker){
        name = en;
        alts = enAlts;
        this.isSpeaker = isSpeaker;

    }

    public void addNameEntity(List<NamedEntity> nes){
        if (namedEntities == null){
            namedEntities = new ArrayList<>();
            namedEntities.addAll(nes);
        } else {
            // 1st
            HashMap<String, NamedEntity> map1 = new HashMap<>();
            HashMap<String, NamedEntity> map2 = new HashMap<>();
            for (NamedEntity ne : namedEntities){
                map1.put(ne.getName(), ne);
            }
            for (NamedEntity ne : nes){
                map2.put(ne.getName(), ne);
            }
            for (Map.Entry<String, NamedEntity> entry : map2.entrySet()){
                String nes_name = entry.getKey();
                NamedEntity fromMap1 = map1.get(nes_name);
                if (fromMap1 == null) { // it's a new nameEntity
                    namedEntities.add(entry.getValue());
                } else { // combine alts
                    Set<String> alts = entry.getValue().getAlts();
                    if (alts != null) {
                        fromMap1.addAlts(alts);
                    }
                }
            }
        }
    }

    public void addPerson(String en, List<String> enAlts){
        NamedEntity namedEntity = new NamedEntity(en, enAlts);
        namedEntity.setEntity(this);
        if (namedEntities == null) {
            namedEntities = new ArrayList<>();
            namedEntities.add(namedEntity);
        } else {
            boolean exists = false;
            for (NamedEntity ne : namedEntities) {
                if (en.equals(ne.getName())) {
                    ne.addAlts(enAlts);
                    exists = true;
                    break;
                }
            }
            if (!exists){
                namedEntities.add(namedEntity);
            }
        }
    }

    public void addContext(String [] cs){
        if (cs != null) {
            context = cs;
            Trie.TrieBuilder w = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
            for (String c : cs) {
                w.addKeyword(c);
            }
            contextTree = w.build();
        }
    }
    public String getName(){return name;}
    public String [] getAlts(){return alts;}
    public List<NamedEntity> getNamedEntities(){return namedEntities;}
    public String [] getContext(){return context;}
    public boolean isSpeaker(){return isSpeaker;}
    public boolean isContextMatch(String s){
        if (contextTree == null) return false;
        return contextTree.containsMatch(s);
    }
}
