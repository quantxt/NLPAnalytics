package com.quantxt.nlp.types;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.quantxt.helper.types.Extraction;
import com.quantxt.nlp.LcText;
import com.quantxt.trie.Emit;
import com.quantxt.types.BaseNameAlts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.*;


/**
 * Created by matin on 4/6/17.
 */
public class SOVAttributes {

    final private static Logger logger = LoggerFactory.getLogger(SOVAttributes.class);

    private Integer sourceIndex;
    private Deque<Integer> targetIndices = new ArrayDeque<>();
    private Map<String, String> valueMappings;
    private boolean shouldProcess = true;
    private String defaultValue;
    private String name;
    private HashSet<String> overrides;

    private LcText lctext;

    //This class should NOT be instantiated without a type and order
    private SOVAttributes(){

    }
}
