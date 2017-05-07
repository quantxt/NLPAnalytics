package com.quantxt.SearchConcepts;

import org.ahocorasick.trie.Trie;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by matin on 3/22/17.
 */

public class Entity {

    private String name;
    private String [] alts;
    private List<NamedEntity> namedEntities;

    private String[] context;
    private Trie contextTree;

    public Entity(String en, String [] enAlts){
        name = en;
        alts = enAlts;
    }

    public void addPerson(String en, String [] enAlts){
        NamedEntity namedEntity = new NamedEntity(en, enAlts);
        namedEntity.setEntity(this);
        if (namedEntities == null) namedEntities = new ArrayList<>();
        namedEntities.add(namedEntity);
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

    public boolean isContextMatch(String s){
        if (contextTree == null) return false;
        return contextTree.containsMatch(s);
    }
}
