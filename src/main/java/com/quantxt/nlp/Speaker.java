package com.quantxt.nlp;

import com.google.gson.*;
import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.nlp.types.Tagger;
import com.quantxt.trie.Emit;
import com.quantxt.trie.Trie;
import com.quantxt.types.Entity;
import org.apache.commons.io.IOUtils;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by matin on 10/9/16.
 */

public class Speaker implements QTExtract {
    final private static Logger logger = LoggerFactory.getLogger(Speaker.class);
    final public static String HIDDEH_ENTITY = "hidden";

    private Trie phraseTree = null;
    private Trie hidden_entities;
    private Map<String, Trie> nameTree = new HashMap<>();
    private List<String> search_terms = new ArrayList<>();
    private Tagger tagger = null;
    private ConcurrentHashMap<String, Double> tokenRank;

    public Map<String, Double> popTokenRank(List<String> tokens, int nums){
        word2vec w2v = tagger.getW2v();
        if (w2v == null) return null;
        tokenRank = new ConcurrentHashMap<>();
        for (String t : tokens) {
            Collection<String> closests = w2v.getClosest(t, nums);
            tokenRank.put(t, 1d);
            for (String c : closests){
                double r = w2v.getDistance(c, t);
                Double r_cur = tokenRank.get(c);
                if (r_cur == null){
                    r_cur = 0d;
                }
                tokenRank.put(c, r + r_cur);
            }
        }
        return tokenRank;
    }

    public WordVectors getw2v(){
        return tagger.getW2v().getW2v();
    }

    public double getSentenceRank(List<String> parts){
        double rank  = 0;
        for (String p : parts){
            Double d = tokenRank.get(p);
            if (d != null){
                rank += d;
            }
        }
        return rank;
    }

    private void loadEntsAndPhs(Map<String, Entity[]> entityMap,
                                InputStream phraseFile) throws IOException
    {
        if (phraseFile != null) {
            Trie.TrieBuilder phrase = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
            String line;
            int num = 0;
            BufferedReader br = new BufferedReader(new InputStreamReader(phraseFile, "UTF-8"));
            while ((line = br.readLine()) != null) {
                line = line.replaceAll("[_\\-]+", " ");
                line = line.replaceAll("[^A-Za-z\\s]+", "").trim();
                String[] parts = line.split("\\s+");
                if (parts.length > 4) continue;
                num++;
                phrase.addKeyword(line);
            }
            logger.info(num + " phrases loaded for tagging");
            phraseTree = phrase.build();
        }

        Gson gson = new Gson();

        //names
        if (entityMap == null){
            byte[] subjectArr = IOUtils.toByteArray(Speaker.class.getClassLoader().getResource("subject.json").openStream());
            entityMap = new HashMap<>();
            entityMap.put("Person", gson.fromJson(new String(subjectArr, "UTF-8"), Entity[].class));
        }

        for (Map.Entry<String, Entity[]> e : entityMap.entrySet()) {
            String entType = e.getKey();
            Extract ext = new Extract(entType, e.getValue());
            for (Entity entity : e.getValue()) {
                String entity_name = entity.getName();
                search_terms.add(entity_name);
            }
            if (entType.equals(HIDDEH_ENTITY)){
                hidden_entities = ext.getLookupTrie();
            } else {
                nameTree.put(entType, ext.getLookupTrie());
            }
        }
    }


    public Speaker(Map<String, Entity[]> entities,
                   String taggerDir,
                   InputStream phraseFile) throws IOException
    {
        loadEntsAndPhs(entities, phraseFile);

        if (taggerDir != null) {
            tagger = Tagger.load(taggerDir);
            logger.info("Speaker for " + taggerDir + " is created");
        }
    }


    public Speaker(Map<String, Entity[]> entities,
                   word2vec w2v,
                   InputStream phraseFile) throws IOException
    {
        loadEntsAndPhs(entities, phraseFile);
        if (w2v != null) {
            tagger = new Tagger();
            tagger.setW2v(w2v);
        }

        logger.info("word2vex is set");
    }

    @Override
    public double [] tag(String str){
        synchronized (tagger) {
            return tagger.getTextVec(str);
        }
    }

    @Override
    public double terSimilarity(String str1, String str2) {
        //TODO:
        return 0;
    //    return tagger.getTopic_model().cosineSimilarityTER(str1, str2);
    }

    @Override
    public Map<String, Collection<Emit>> parseNames(String str){
        HashMap<String, Collection<Emit>> res = new HashMap<>();
        if (hidden_entities != null){
            res.put(HIDDEH_ENTITY, hidden_entities.parseText(str));
        }

        for (Map.Entry<String, Trie> e : nameTree.entrySet()){
            Trie trie = e.getValue();
            res.put(e.getKey(), trie.parseText(str));
        }

        return res;
    }


    private static boolean dist(Emit e1, Emit e2) {
        int e1_b = e1.getStart();
        int e1_e = e1.getEnd();
        int e2_b = e2.getStart();
        int e2_e = e2.getEnd();
        int diff = (e2_b - e1_e);
        // e1  e2
        //   if (e1_e < e2_b)
        return (diff >= 0 && diff < 5);
        // e2 e1
//        return ((e1_b - e2_e) < 10);
    }

    /*
    public String getFact(String input) {
        Collection<Emit> verbs = verbTree.parseText(input);
        for (Emit e : verbs) {
            int v_e = e.getEnd() + 1;  // ?? why?
            String f = input.substring(v_e).trim();
            f = f.substring(0, 1).toUpperCase() + f.substring(1).toLowerCase();
            return f;
        }
        return null;
    }

    public List<String> getSummary(QTDocument doc){
        List<String> sents = doc.getSentences();
        int numSent = sents.size();
        ArrayList<String> summaries = new ArrayList<>();
        for (int i = 1; i < numSent; i++) {
            final String orig = sents.get(i);
            String normalized = doc.normalize(orig);
            int numTokens = normalized.split("\\s+").length;
            if (numTokens < 6 || numTokens > 50) continue;
            Collection<Emit> verb_emit = verbTree.parseText(normalized);
            if (verb_emit.size() == 0) continue;
            summaries.add(normalized);
        }
        return summaries;
    }
*/

    private static String removePrnts(String str){
        str = str.replaceAll("\\([^\\)]+\\)", " ");
        str = str.replaceAll("([\\.])+$", " $1");
        str = str.replaceAll("\\s+", " ");
        return str;
    }

    public ArrayList<String> phraseMatch(String str) {
        if (phraseTree == null) return null;
        ArrayList<String> matches = new ArrayList<>();
        Collection<Emit> emits = phraseTree.parseText(str);
        for (Emit e : emits) {
            matches.add(e.getKeyword());
        }
        return matches;
    }

    public static Trie getTirefromFile(String filename) throws IOException {
        BufferedReader br;
        Trie.TrieBuilder w = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();

        br = new BufferedReader(new FileReader(filename));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                w.addKeyword(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            br.close();
        }
        return w.build();
    }


    public void getPhsFromw(String input, String output) throws IOException {
        Trie.TrieBuilder phrase = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
        String line;
        int num = 0;
        Trie ww = getTirefromFile(input);
        BufferedReader br = new BufferedReader(new FileReader("wiki.phs"));

        int num2 = 0;
        try {
            while ((line = br.readLine()) != null) {
                if ((num++ % 100000) == 0) {
                    logger.info(num + " " + " " + num2 + " loaded");
                }
                if (!line.matches("^([A-Z]).*")) continue;
                if (line.length() > 50) continue;
                String[] parts = line.replaceAll("[_\\-]+", " ").split("\\s+");

                Collection<Emit> matches = ww.parseText(line);
                if ((matches.size() == parts.length) || (matches.size() > 1)) {
                    String link = "https://en.wikipedia.org/wiki/" + line;
                    try {
                        byte[] cont = Jsoup.connect(link)
                                .userAgent("Mozilla")
                                .ignoreContentType(true).execute().bodyAsBytes();
                        Document jsoupDoc = Jsoup.parse(new String(cont), "UTF-8");
                        String content = jsoupDoc.body().text();
                        QTDocument qt = new ENDocumentInfo(content, jsoupDoc.title());
                        List<String> sents = qt.getSentences();
                        for (String s : sents) {
                            Files.write(Paths.get(output), (s + "\n").getBytes(), StandardOpenOption.APPEND);
                        }
                    } catch ( Exception  e) {
                        logger.error(e.getMessage());
                    }
                    logger.info(line);
                } else {
                    continue;
                }

//
//                if (parts.length > 4) continue;

/*
                Files.write(Paths.get(phraseFileName), (line + "\n").getBytes(), StandardOpenOption.APPEND);
                line = line.replaceAll("[_\\-]+", " ");
                line = line.replaceAll("[^A-Za-z\\s]+", "").trim();
                String[] parts = line.split("\\s+");
                if (parts.length > 4) continue;
                //             logger.info(bb + " --> " + line + " --> " + tag);


                phrase.addKeyword(line);
*/                num2++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        phraseTree = phrase.build();
        logger.info("Phrases loaded");
    }

    public List<String> getSearhTemrs(){
        return search_terms;
    }

    public Map<String, Trie> getNameTree() {
        return nameTree;
    }

    @Override
    public boolean hasEntities() {
        return nameTree.size() > 0;
    }

    public Tagger getTagger() {
        return tagger;
    }

}


