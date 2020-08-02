package com.quantxt.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.types.ExtIntervalSimple;
import com.quantxt.types.MapSort;

public class NLPUtil {

    private static Logger logger = LoggerFactory.getLogger(NLPUtil.class);

    private static final String EMPTY_STRING  = "";

    private static ExtIntervalSimple findSpanHelper(String str,
                                                    int startPos,
                                                    List<String> tokenList)
    {
        if (tokenList.size() == 0) {
            return null;
        }
        int strLength = str.length();
        if (strLength == 0) return null;
        int end = startPos;
        for (int c = 0; c < tokenList.size(); c++) {
            String t = tokenList.get(c);
            if (t.length() == 0) continue;
            int pos = str.indexOf(t, end);
            if (pos < 0) {
                return null;
            }
            end = pos + t.length();
            if (end > strLength) return null;
        }

        return new ExtIntervalSimple(startPos, end);
    }

    public static ExtIntervalSimple findSpan(String str,
                                             List<String> tokenList) {
        if (tokenList == null || tokenList.size() == 0)
            return null;
        str = str.trim();

        Map<ExtIntervalSimple, Integer> allMatahces = new HashMap<>();
        String firstToken = tokenList.get(0);
        int cursor = 0;
        while (cursor >=0){
            int pos = str.indexOf(firstToken, cursor);
            if (pos < 0) break;
            ExtIntervalSimple ext = findSpanHelper(str, pos, tokenList);
            if (ext != null){
                allMatahces.put(ext, ext.getEnd() - ext.getStart());
            }
            cursor = pos + firstToken.length();
        }
        if (allMatahces.size() == 0){
            return null;
        }
        //find shortest ..
        // if there multiple shorts then we pick the last one (the one closer to the end of utterance)
        Map<ExtIntervalSimple, Integer> sorted = MapSort.sortByValue(allMatahces);
        int shortestLnegth = sorted.entrySet().iterator().next().getValue();
        ExtIntervalSimple res = sorted.entrySet().iterator().next().getKey();

        for (Map.Entry<ExtIntervalSimple, Integer> e : sorted.entrySet()){
            int length = e.getValue();
            if (length != shortestLnegth) break;
            ExtIntervalSimple key = e.getKey();
            if (key.getStart() > res.getStart()){
                res = key;
            }
        }
        return res;
    }

    public static ExtIntervalSimple[] findAllSpans(String str, String[] tokens){
        ExtIntervalSimple [] allSpans = new ExtIntervalSimple[tokens.length];

        int cursor = 0;
        for (int i=0; i<tokens.length; i++){
            String t = tokens[i];
            int pos = str.indexOf(t, cursor);
            if (pos < 0) {
                logger.warn("Token {} as not found in the string {}", t , str);
                continue;
            }
            ExtIntervalSimple exi = new ExtIntervalSimple(pos, pos + t.length());
            allSpans[i] = exi;
            cursor = exi.getEnd();

        }
        return allSpans;
    }

    public static boolean isEmpty(String value) {
        return value == null || value.replaceAll("\\s*", EMPTY_STRING).equals(EMPTY_STRING);
    }

}
