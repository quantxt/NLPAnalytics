package com.quantxt.nlp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quantxt.QTDocument.ENDocumentInfo;
import com.quantxt.QTDocument.QTDocument;
import com.quantxt.types.StringDouble;
import com.quantxt.types.StringDoubleComparator;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.apache.commons.io.IOUtils;
import org.datavec.api.util.ClassPathResource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Created by matin on 10/9/16.
 */
public class Speaker {
    final private static Logger logger = LoggerFactory.getLogger(Speaker.class);

    private static String QUOTE_CATEGORY;
    public static String ARTICLE_CATEGORY;

    private static Trie phraseTree = null;
    private static Trie nameTree = null;
    private static Trie verbTree = null;
    private static Trie titleTree = null;

    private static Map<String, Speaker> keyword2speaker = new HashMap<>();
    private static Map<String, String> verb2context = new HashMap<>();
    private static Map<String, double[]> TOPIC_MAP = new HashMap<>();


    private static TopicModel topic_model = null;

    private String name;
    private String affiliation;
    private boolean no_search;
    private List<String> search_phs = new ArrayList<>();
    private Set<String> tags = new HashSet<>();

    private Speaker(String entity, String affiliation, boolean ns) {
        this.name = entity;
        this.affiliation = affiliation;
        tags.add(name.replace("\"", ""));
        tags.add(affiliation.replace("\"", ""));
        no_search = ns;
    }

    public void addTag(String t) {
        tags.add(t);
    }

    public static TopicModel getTM() {
        return topic_model;
    }

    private List<String> getSearhTerm() {
        ArrayList<String> search_terms = new ArrayList<>();
        search_terms.add(affiliation);
        search_terms.addAll(search_phs);
        return search_terms;
    }

    public String getContextSearhTerm() {
        StringBuilder sb = new StringBuilder();
        for (String t : tags) {
            sb.append(t).append(" ");
        }
        sb.append(affiliation);
        return sb.toString();
    }

    private static QTDocument getQuoteDoc(QTDocument doc,
                                          String quote,
                                          String keyword) {
        quote = quote.replaceAll("^Advertisement\\s+", "").trim();

        QTDocument sDoc = new ENDocumentInfo("", quote);
        sDoc.setDate(doc.getDate());
        sDoc.setCategories(QUOTE_CATEGORY);
        sDoc.setDirectLink(doc.getDirectLink());
        sDoc.setLogo(doc.getLogo());
        sDoc.setSource(doc.getSource());
        StringDouble bt = getBestTag(quote);

        if (bt != null && bt.getVal() > .1) {
            sDoc.addTag(bt.getStr());
            sDoc.setLabel(bt.getStr());
            doc.addTag(bt.getStr());
        }

        Speaker speaker = keyword2speaker.get(keyword);
        if (speaker != null) {
            sDoc.setAuthor(speaker.name);
//            sDoc.addTags(new ArrayList<>(speaker.getTags()));
            sDoc.setEntity(speaker.affiliation.replace("\"", ""));
            doc.addTag(speaker.name.replace("\"", ""));
            sDoc.addTag(speaker.name.replace("\"", ""));
            doc.addTag(speaker.affiliation.replace("\"", ""));
            sDoc.addTag(speaker.affiliation.replace("\"", ""));
        }
        return sDoc;
    }

    private static boolean dist(Emit e1, Emit e2) {
        int e1_b = e1.getStart();
        int e1_e = e1.getEnd();
        int e2_b = e2.getStart();
        int e2_e = e2.getEnd();
        int diff = (e2_b - e1_e);
        // e1  e2
        //   if (e1_e < e2_b)
        return (diff >= 0 && diff < 10);
        // e2 e1
//        return ((e1_b - e2_e) < 10);
    }

    public static String getFact(String input) {
        Collection<Emit> verbs = verbTree.parseText(input);
        for (Emit e : verbs) {
            int v_e = e.getEnd() + 1;  // ?? why?
            String f = input.substring(v_e).trim();
            f = f.substring(0, 1).toUpperCase() + f.substring(1).toLowerCase();
            return f;
        }
        return null;
    }

    public static ArrayList<QTDocument> extractQuotes(QTDocument doc) {
        ArrayList<QTDocument> quotes = new ArrayList<>();
        List<String> sents = doc.getSentences();
        int numSent = sents.size();
        for (int i = 1; i < numSent; i++) {
            final String orig = sents.get(i);
            String rawSent_before = sents.get(i - 1).replaceAll("[^a-zA-Z0-9\\-]+", " ");
            String rawSent_curr = orig.replaceAll("[^a-zA-Z0-9\\-]+", " ");
            //           String normStatement = s.replaceAll("[^a-zA-Z]+", " ");
            //           normStatement = s.replaceAll("\\s+", " ");
            //           String normStatement_curr = Normalizer.normalize(rawSent_curr);
            int numTokens = rawSent_curr.split("\\s+").length;
            if (numTokens < 6 || numTokens > 50) continue;
/*
            try {
                Files.write(Paths.get("snp500.txt"), (orig  +"\n").getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            }
*/
            Collection<Emit> verb_emit = verbTree.parseText(rawSent_curr);

            if (verb_emit.size() == 0) continue;


            Collection<Emit> name_match_curr = nameTree.parseText(rawSent_curr);
            Collection<Emit> name_match_befr = nameTree.parseText(rawSent_before);

            if (name_match_curr.size() == 0 && name_match_befr.size() == 0) continue;

            int importance = 0;
            if (name_match_befr.size() > 0) importance++;

//            if (name_match_curr.size() > 1) continue;

            String keyword = null;
            if (name_match_curr.size() > 0) {
                importance += 2;
                int name_verb_pair = 0;
                for (Emit e_n : name_match_curr) {
                    for (Emit e_v : verb_emit) {
                        if (dist(e_n, e_v)) {
                            name_verb_pair++;
                            keyword = e_n.getKeyword();
                            break;
                        }
                    }
                }
                if (keyword != null) {
                    QTDocument newQuote = getQuoteDoc(doc, orig, keyword);
                    Emit firstVerbEmit = (Emit) verb_emit.toArray()[0];
                    newQuote.addTag(verb2context.get(firstVerbEmit.getKeyword()));
                    quotes.add(newQuote);
                }

                if (name_match_curr.size() == name_verb_pair) importance += 2;

                Collection<Emit> titles_emet = titleTree.parseText(rawSent_curr);
                if (titles_emet.size() > 0) {
                    importance++;
                    name_verb_pair = 0;
                    for (Emit e_t : titles_emet) {
                        for (Emit e_v : verb_emit) {
                            if (dist(e_t, e_v)) {
                                name_verb_pair++;
                                break;
                            }
                        }
                    }
                    if (name_match_curr.size() == name_verb_pair) importance += 2;
                }

            }
        }
        return quotes;
    }

    public static ArrayList<String> phraseMatch(String str) {
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
        Trie.TrieBuilder w = Trie.builder().onlyWholeWords().caseInsensitive().removeOverlaps();

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

    public static void getPhsFromw(String input, String output) throws IOException {
        Trie.TrieBuilder phrase = Trie.builder().onlyWholeWords().caseInsensitive().removeOverlaps();
        String line;
        int num = 0;
        Trie ww = getTirefromFile(input);
        BufferedReader br = new BufferedReader(new FileReader("/Users/matin/Downloads/enwiki-20161201-all-titles-in-ns0"));

        int num2 = 0;
        try {
            while ((line = br.readLine()) != null) {
                if ((num++ % 100000) == 0) {
                    logger.info(num + " " + " " + num2 + " loaded");
                }
                //      if (num > 10500000) break;
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
                        qt.processDoc();
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

    public static void getPhs(String phraseFileName) throws IOException {
        Trie.TrieBuilder phrase = Trie.builder().onlyWholeWords().caseInsensitive().removeOverlaps();
        String line;
        int num = 0;
        //      Trie ww = getTirefromFile("/Users/matin/git/quantxt/qtingestor/w");
        BufferedReader br = new BufferedReader(new FileReader("/Users/matin/Downloads/enwiki-20161201-all-titles-in-ns0"));

        int num2 = 0;
        try {
            while ((line = br.readLine()) != null) {
                if ((num++ % 100000) == 0) {
                    logger.info(num + " " + " " + num2 + " loaded");
                }
                //      if (num > 10500000) break;
                if (!line.matches("^([A-Z]).*")) continue;
                if (line.length() > 50) continue;

                StringDouble bestTag = getBestTag(line);

                if (bestTag == null || bestTag.getVal() < .5) continue;
                Files.write(Paths.get(phraseFileName), (line + "\n").getBytes(), StandardOpenOption.APPEND);
                line = line.replaceAll("[_\\-]+", " ");
                line = line.replaceAll("[^A-Za-z\\s]+", "").trim();
                String[] parts = line.split("\\s+");
                if (parts.length > 4) continue;
                //             logger.info(bb + " --> " + line + " --> " + tag);
                num2++;
                phrase.addKeyword(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        phraseTree = phrase.build();
        logger.info("Phrases loaded");
    }

    public static void init(String speakerFile,
                            String ruleTagFile,
                            String word2vec,
                            String[] categories) throws IOException, ClassNotFoundException {
        if (ruleTagFile != null) {
            Trie.TrieBuilder phrase = Trie.builder().onlyWholeWords().caseInsensitive().removeOverlaps();
            String line;
            int num = 0;
            BufferedReader br = new BufferedReader(new FileReader(ruleTagFile));
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

        if (categories != null) {
            QUOTE_CATEGORY = categories[1];
            ARTICLE_CATEGORY = categories[0];
        }

        JsonParser parser = new JsonParser();

        //verbs
        Trie.TrieBuilder verbs = Trie.builder().onlyWholeWords().caseInsensitive();
        byte[] contextArr = IOUtils.toByteArray(new ClassPathResource("/context.json").getInputStream());
        JsonElement jsonElement = parser.parse(new String(contextArr, "UTF-8"));
        JsonObject contextJson = jsonElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : contextJson.entrySet()) {
            String context_key = entry.getKey();
            JsonArray context_arr = entry.getValue().getAsJsonArray();
            for (JsonElement e : context_arr) {
                String verb = e.getAsString();
                verbs.addKeyword(verb);
                verb2context.put(verb, context_key);
            }
        }
        verbTree = verbs.build();

        //titles
        Trie.TrieBuilder titles = Trie.builder().onlyWholeWords().caseInsensitive();
        byte[] commonArr = IOUtils.toByteArray(new ClassPathResource("/common.json").getInputStream());
        JsonElement commonosnElement = parser.parse(new String(commonArr, "UTF-8"));
        JsonObject commonosnObject = commonosnElement.getAsJsonObject();
        JsonElement titles_elems = commonosnObject.get("titles");
        if (titles_elems != null) {
            for (JsonElement elem : titles_elems.getAsJsonArray()) {
                String ttl = elem.getAsString();
                titles.addKeyword(ttl);
            }
        }
        titleTree = titles.build();

        //names
        if (speakerFile == null){
            byte[] subjectArr = IOUtils.toByteArray(new ClassPathResource("/subject.json").getInputStream());
            jsonElement = parser.parse(new String(subjectArr, "UTF-8"));
        } else {
            jsonElement = parser.parse(new FileReader(speakerFile));
        }

        JsonArray speakerJson = jsonElement.getAsJsonArray();
        Trie.TrieBuilder names = Trie.builder().onlyWholeWords().caseInsensitive();


        for (JsonElement spj : speakerJson) {
            JsonObject jsonObject = spj.getAsJsonObject();
            String entity = jsonObject.get("entity").getAsString();

            names.addKeyword(entity.replace("\"", ""));
            Speaker speaker = new Speaker(entity.replace("\"", ""), entity, false);
            keyword2speaker.put(entity.toLowerCase().replace("\"", ""), speaker);

            JsonElement alts = jsonObject.get("alts");
            if (alts != null) {
                for (JsonElement elem : alts.getAsJsonArray()) {
                    String alt = elem.getAsString();
                    names.addKeyword(alt);
                    keyword2speaker.put(alt.toLowerCase(), speaker);
                }
            }

            JsonElement personElement = jsonObject.get("people");
            if (personElement == null) continue;
            JsonArray personArr = personElement.getAsJsonArray();
            for (JsonElement elem : personArr) {
                String person = elem.getAsString();
                names.addKeyword(person);
                speaker = new Speaker(person, entity, false);
                keyword2speaker.put(person.toLowerCase(), speaker);
                String[] person_parts = person.split("\\s+");
                String last_name = person_parts[person_parts.length - 1];
                names.addKeyword(last_name);
                keyword2speaker.put(last_name.toLowerCase(), speaker);
            }
        }

        nameTree = names.build();

        topic_model = new TopicModel();
        if (word2vec != null) {
            topic_model.loadInfererFromW2VFile(word2vec);
        }

        byte[] topicArr = IOUtils.toByteArray(new ClassPathResource("/topics.json").getInputStream());
        jsonElement = parser.parse(new String(topicArr, "UTF-8"));
        JsonObject topicJson = jsonElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : topicJson.entrySet()) {
            String topic = entry.getKey();
            String str = entry.getValue().getAsString();
            double[] tVector = topic_model.getSentenceVector(str);
            TOPIC_MAP.put(topic, tVector);
        }
    }

    public static void loadCategories(File file) throws FileNotFoundException {
        TOPIC_MAP.clear();
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(new FileReader(file));
        JsonObject topicJson = jsonElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : topicJson.entrySet()) {
            String topic = entry.getKey();
            String str = entry.getValue().getAsString();
            double[] tVector = topic_model.getSentenceVector(str);
            TOPIC_MAP.put(topic, tVector);
        }
    }

    public static StringDouble getBestTag(String str) {
        double[] tvec = topic_model.getSentenceVector(str);
        HashMap<String, Double> vals = new HashMap<>();
        double avg = 0;
        double numTopics = TOPIC_MAP.size();
        for (Map.Entry<String, double[]> r : TOPIC_MAP.entrySet()) {
            Double sim = TopicModel.cosineSimilarity(tvec, r.getValue());
            if (sim.isNaN()) {
                sim = 0d;
            }
            avg += sim / numTopics;
            vals.put(r.getKey(), sim);

            //         logger.info(r.getKey() + " " + sim);
        }
        //      if (avg < .1) return null;

        StringDoubleComparator bvc = new StringDoubleComparator(vals);
        TreeMap<String, Double> sorted_map = new TreeMap<>(bvc);
        sorted_map.putAll(vals);

        double max = sorted_map.firstEntry().getValue();
        if (max < .1 || max / avg < 1.3){
            logger.warn("All tags are likely!.. model is not sharp enough");
        }
        return new StringDouble(sorted_map.firstEntry().getKey(), sorted_map.firstEntry().getValue());
    }

    private boolean isNo_search() {
        return no_search;
    }

    public static List<String> getAllSearchableTerms(){
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, Speaker> e : keyword2speaker.entrySet()) {
            Speaker spk = e.getValue();
            if (spk.isNo_search()) continue;
            List<String> search_terms = spk.getSearhTerm();
            list.addAll(search_terms);
        }
        return list;
    }

    public static void main(String[] args) throws Exception {
        ENDocumentInfo.init();
        Speaker.getPhsFromw("/Users/matin/git/quantxt/qtingestor/models/ww.phtags", "wadaewandy.txt");
 //      Speaker.init(150, "/Users/matin/git/quantxt/qtingestor/models/Casual.json", "/Users/matin/git/quantxt/qtingestor/models/ww.phtags", "/Users/matin/git/quantxt/qtingestor/snp500.w2v", null);

    }
}


