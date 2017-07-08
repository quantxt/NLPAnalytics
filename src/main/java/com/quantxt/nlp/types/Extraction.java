package com.quantxt.nlp.types;

import com.quantxt.trie.Emit;

import java.util.*;

/**
 * Created by matin on 4/4/17.
 */
public class Extraction {

    public enum Extractions {
        SIMPLE, PHRASE, ENTITY
    }

    private Map<Extractions, List<Emit>> extractions;

    public Extraction() {
        extractions = new HashMap<>();
    }

    public void add(Extractions e, Emit emt){
        List<Emit> emits = extractions.get(e);
        if (emits == null){
            emits = new ArrayList<>();
            extractions.put(e, emits);
        }
        emits.add(emt);
    }

    public void add(Extractions e, Collection<Emit> emt){
        List<Emit> emits = extractions.get(e);
        if (emits == null){
            emits = new ArrayList<>();
            extractions.put(e, emits);
        }
        emits.addAll(emt);
    }

    public List<Emit> getExtractions(Extractions ext){
        return extractions.get(ext);
    }

    public Map<Extractions, List<Emit>> getExtractions(){return extractions;}
}
