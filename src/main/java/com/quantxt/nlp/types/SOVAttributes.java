package com.quantxt.nlp.types;

import com.quantxt.types.MapSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.quantxt.nlp.types.SOVAttributes.Type.STRING;


/**
 * Created by matin on 4/6/17.
 */
public class SOVAttributes {

    final private static Logger logger = LoggerFactory.getLogger(SOVAttributes.class);
    public enum Type {INTEGER, DOUBLE, STRING, DATE, BOOLEAN, SEQ, PERCENT, CAT};


    private Type type = STRING;
    private Integer sourceIndex;
    private Deque<Integer> targetIndices = new ArrayDeque<>();
    private Map<String, String> valueMappings;
    private boolean shouldProcess = true;
    private String defaultValue;
    private String name;
    private HashSet<String> overrides;

    //This class should NOT be instantiated without a type and order
    private SOVAttributes(){

    }

    public SOVAttributes(String n, Type t, String dv){
        name = n;
        type = t;
        defaultValue = dv;
    }

    public String getDefault(){
        return defaultValue;
    }

    public SOVAttributes(SOVAttributes sov){
        this.type = sov.type;
        //      this.sourceIndex = sov.sourceIndex;
        if (sourceIndex != null) {
            this.sourceIndex = sov.sourceIndex;
        }
        if (sov.targetIndices != null) {
            this.targetIndices = new ArrayDeque<>(sov.targetIndices);
        }
        if (valueMappings != null) {
            this.valueMappings = new HashMap<>(sov.valueMappings);
        }
        this.shouldProcess = sov.shouldProcess;
        this.defaultValue  = sov.defaultValue;
    }

    public SOVAttributes(int o){
        sourceIndex = o;
    }

    public void addToMappingColumns(int i){
        targetIndices.add(i);
    }

    public void setMappingColumnsAndLock(int i) {
        targetIndices.addFirst(i);
    }

    public String getName(){
        return name;
    }

    public void addName(String n){
        name = n;
    }

    public Type getType(){
        return type;
    }

    public void setType(Type t){
        type = t;
    }

    public void setSourceIndex(int o){
        sourceIndex = o;
    }

    public Integer getSourceIndex(){
        return sourceIndex;
    }

    public Deque<Integer> getTargetIndices(){
        return targetIndices;
    }

    public String getMappedValue(String key){
        if (key == null){
            return null;
        }
        if (valueMappings == null){
            return null;
        }
        key = key.toLowerCase().trim();
        String mapped =  valueMappings.get(key);
        if (mapped != null) return mapped;
        return defaultValue;
    }

    //   public void setValueMappings(Map<String, String> map){
    //       valueMappings = map;
    //   }
    //   public Map<String, String> getvalueMappings(){
    //       return valueMappings;
    //   }

    public String getCategorical(String str){
        // TODO: need a classifier here
        if (str == null || str.isEmpty()){
            return defaultValue;
        }
        str = str.toLowerCase().trim();
        //first do whole look up
        String fullyMapped = valueMappings.get(str);
        if (fullyMapped != null){
            return fullyMapped;
        }
        str = str.replaceAll("[\\/\\-\\(\\)\\']" , " ").trim();
        fullyMapped = valueMappings.get(str);
        if (fullyMapped != null){
            return fullyMapped;
        }

        String [] parts = str.split("\\s+");
        Map<String, Integer> allMapped = new HashMap<>();
        for (String p : parts){
            String mm = valueMappings.get(p);
            if (mm == null) continue;
            Integer c = allMapped.get(mm);
            if (c == null){
                c = 0;
            }
            allMapped.put(mm, c);
        }

        if (allMapped.size() == 0){
            return defaultValue;
        }
        Map<String, Integer> sorted = MapSort.sortdescByValue(allMapped);
        String mapped = sorted.entrySet().iterator().next().getKey();
        //    logger.info("Mapping " + name + " / "  + " --> " + mapped);
        return mapped;
    }

    public boolean isOverrid(String str){
        if (overrides == null) return false;
        logger.info(str + " is an override");
        return overrides.contains(str);
    }

    public boolean hasOverris(){
        return (overrides != null);
    }

    public boolean shouldProcess(){
        return shouldProcess;
    }
}
