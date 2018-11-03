package com.quantxt.nlp;

import com.google.gson.*;
import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.helper.types.Extraction;
import com.quantxt.trie.Emit;
import com.quantxt.trie.Trie;
import com.quantxt.types.BaseNameAlts;
import com.quantxt.types.Entity;
import com.quantxt.types.NamedEntity;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Created by matin on 4/4/17.
 */
public class Extract {

    final private static Logger logger = LoggerFactory.getLogger(Extract.class);

    private Trie lookupTrie  = null;

    public Extract(String entType, Entity[] entities){
        Trie.TrieBuilder names = Trie.builder().onlyWholeWords().ignoreOverlaps();
        for (Entity entity : entities) {
            // include entity as a speaker?

            if (entity.isSpeaker()) {
                String entity_name = entity.getName();
                NamedEntity entityNamedEntity = new NamedEntity(entity_name, null);
                entityNamedEntity.setEntity(entType, entity);
                entityNamedEntity.setParent(true);

                String[] alts = entity.getAlts();
                if (alts != null) {
                    for (String alt : alts) {
                        names.addKeyword(alt, entityNamedEntity);
                        names.addKeyword(alt.toUpperCase(), entityNamedEntity);

                    }
                } else {
                    names.addKeyword(entity_name, entityNamedEntity);
                    names.addKeyword(entity_name.toUpperCase(), entityNamedEntity);
                }
            }

            List<NamedEntity> namedEntities = entity.getNamedEntities();
            if (namedEntities == null) continue;
            for (NamedEntity namedEntity : namedEntities) {
                namedEntity.setEntity(entType, entity);
                String p_name = namedEntity.getName();
                names.addKeyword(p_name, namedEntity);
                names.addKeyword(p_name.toUpperCase(), namedEntity);
                Set<String> nameAlts = namedEntity.getAlts();
                if (nameAlts != null) {
                    for (String alt : namedEntity.getAlts()) {
                        names.addKeyword(alt, namedEntity);
                        names.addKeyword(alt.toUpperCase(), namedEntity);
                    }
                }
            }
        }
        lookupTrie = names.build();
    }

    public Extract(){

    }

    /*
    public String getName(){
        return name;
    }
    */

    public Trie getLookupTrie(){
        return lookupTrie;
    }

    public Extract(Trie.TrieBuilder trie){
        lookupTrie = trie.build();
    }

    public void loadFlatTree(InputStream ins){
        Trie.TrieBuilder trie = Trie.builder().onlyWholeWords()
                .ignoreCase().ignoreOverlaps();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(ins));
        try {
            while ((line = br.readLine()) != null) {
                trie.addKeyword(line);
            }
            lookupTrie = trie.build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("flat trie loaded for tagging");

    }

    public <T> void loadCategorical(InputStream ins,
                                    Class<T> dataClass,
                                    Type customeType,
                                    boolean includeCategory){
        Trie.TrieBuilder trie = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
        Gson gson = new Gson();
        HashMap<String, List<BaseNameAlts<T>>> preTreeMap = new HashMap<>();
        try {
            byte[] contextArr = IOUtils.toByteArray(ins);
            String jsonString = new String(contextArr, "UTF-8");
            BaseNameAlts<T>[] array = gson.fromJson(jsonString, customeType);
            for (BaseNameAlts<T> bna : array) {
                if (includeCategory) {
                    String name = bna.getName();
                    List<BaseNameAlts<T>> list = preTreeMap.get(name);
                    if (list == null){
                        list = new ArrayList<>();
                    }
                    list.add(bna);
                    preTreeMap.put(name, list);
                }
                String [] alts = bna.getAlts();
                for (String alt : alts) {
                    List<BaseNameAlts<T>> list = preTreeMap.get(alt);
                    if (list == null){
                        list = new ArrayList<>();
                    }
                    list.add(bna);
                    preTreeMap.put(alt, list);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (Map.Entry<String, List<BaseNameAlts<T>>> e : preTreeMap.entrySet()){
            trie.addKeyword(e.getKey(), e.getValue());
        }

        lookupTrie = trie.build();

        logger.info("Categorical trie loaded");
    }

    public void loadCategorical(InputStream ins){
        Trie.TrieBuilder trie = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
        Gson gson = new Gson();
        try {
            byte[] contextArr = IOUtils.toByteArray(ins);
            String jsonString = new String(contextArr, "UTF-8");
            BaseNameAlts[] array = gson.fromJson(jsonString, BaseNameAlts[].class);
            for (BaseNameAlts bna : array) {
                String name = bna.getName();
                String [] alts = bna.getAlts();
                trie.addKeyword(name, bna);
                for (String alt : alts) {
                    trie.addKeyword(alt, bna);
                }
            }
            lookupTrie = trie.build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("Categorical trie loaded");
    }

    public void loadArrayTree(InputStream ins){
        Trie.TrieBuilder trie = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
        JsonParser parser = new JsonParser();
        /*
            {
                "key1" : [val11, val12]
                "key2" : [val21, val 22]
            }

         */

        try {
            byte[] contextArr = IOUtils.toByteArray(ins);
            JsonElement jsonElement = parser.parse(new String(contextArr, "UTF-8"));
            JsonObject contextJson = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : contextJson.entrySet()) {
                String category = entry.getKey();
                JsonArray context_arr = entry.getValue().getAsJsonArray();
                for (JsonElement e : context_arr) {
                    String keyword = e.getAsString();
                    trie.addKeyword(keyword, category);
                }
            }
            lookupTrie = trie.build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("array trie loaded for tagging");
    }

    public void loadEntityTrie(InputStream ins){
        Trie.TrieBuilder trie = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
        JsonParser parser = new JsonParser();

        try {
            byte[] bytearr = IOUtils.toByteArray(ins);
            JsonElement jsonElement = parser.parse(new String(bytearr, "UTF-8"));
            JsonArray speakerJson = jsonElement.getAsJsonArray();

            Gson gson = new Gson();
            for (JsonElement spj : speakerJson) {
                final Entity entity = gson.fromJson(spj, Entity.class);
                String entity_name = entity.getName();
                NamedEntity entityNamedEntity = new NamedEntity(entity_name, null);
                entityNamedEntity.setEntity(entity);

                trie.addKeyword(entity_name, entityNamedEntity);

                String[] alts = entity.getAlts();
                if (alts != null) {
                    for (String alt : alts) {
                        trie.addKeyword(alt, entityNamedEntity);
                    }
                }

                List<NamedEntity> namedEntities = entity.getNamedEntities();
                if (namedEntities == null) continue;
                for (NamedEntity namedEntity : namedEntities) {
                    namedEntity.setEntity(entity);
                    String p_name = namedEntity.getName();
                    trie.addKeyword(p_name, entity);
                    for (String alt : namedEntity.getAlts()) {
                        trie.addKeyword(alt, namedEntity);
                    }
                }
            }
            lookupTrie = trie.build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("entity trie loaded for tagging");
    }

    public Extraction extract(String str){
        Extraction res = new Extraction();
        Collection<Emit> emits = lookupTrie.parseText(str);
        if (emits.size() > 0) {
            res.add(Extraction.Extractions.PHRASE, emits);
        }

        return res;
    }

    private boolean dist(Emit e1, Emit e2) {
        int e1_b = e1.getStart();
        int e1_e = e1.getEnd();
        int e2_b = e2.getStart();
        int e2_e = e2.getEnd();
        int diff = (e2_b - e1_e);

        return (diff >= 0 && diff < 10);

    }
}
