package com.quantxt.nlp;

import com.google.gson.*;
import com.quantxt.QTDocument.ENDocumentInfo;
import com.quantxt.SearchConcepts.Entity;
import com.quantxt.SearchConcepts.NamedEntity;
import com.quantxt.doc.QTDocument;
import com.quantxt.nlp.types.TextNormalizer;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.apache.commons.io.IOUtils;
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

    private static Trie phraseTree = null;
    private static Trie nameTree   = null;
    private static Trie verbTree   = null;
    private static Trie titleTree  = null;

    private static List<String> SEARH_TEMRS = new ArrayList<>();

    private static QTDocument getQuoteDoc(QTDocument doc,
                                          String quote,
                                          NamedEntity namedEntity)
    {
        quote = quote.replaceAll("^Advertisement\\s+", "").trim();
        ENDocumentInfo sDoc = new ENDocumentInfo("", quote);
        sDoc.setDate(doc.getDate());
//        sDoc.setCategories(QUOTE_CATEGORY);
        sDoc.setLink(doc.getLink());
        sDoc.setLogo(doc.getLogo());
        sDoc.setSource(doc.getSource());

        /*
        StringDouble bt = getBestTag(quote);
        if (bt != null && bt.getVal() > .1) {
            sDoc.addTag(bt.getStr());
            doc.addTag(bt.getStr());
        }
        */

        sDoc.setAuthor(namedEntity.getName());
        sDoc.setEntity(namedEntity.getEntity().getName());
        doc.addTag(namedEntity.getName());
        sDoc.addTag(namedEntity.getName());
        doc.addTag(namedEntity.getEntity().getName());
        sDoc.addTag(namedEntity.getEntity().getName());

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
        return (diff >= 0 && diff < 5);
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

    public static List<String> getSummary(QTDocument doc){
        List<String> sents = doc.getSentences();
        int numSent = sents.size();
        ArrayList<String> summaries = new ArrayList<>();
        for (int i = 1; i < numSent; i++) {
            final String orig = sents.get(i);
            String normalized = TextNormalizer.normalize(orig);
            int numTokens = normalized.split("\\s+").length;
            if (numTokens < 6 || numTokens > 50) continue;
            Collection<Emit> verb_emit = verbTree.parseText(normalized);
            if (verb_emit.size() == 0) continue;
            summaries.add(normalized);
        }
        return summaries;
    }

    public static ArrayList<QTDocument> extractEntityMentions(QTDocument doc) {
        ArrayList<QTDocument> quotes = new ArrayList<>();
        List<String> sents = doc.getSentences();
        int numSent = sents.size();
        Set<String> entitiesFromNamedFound = new HashSet<>();
        Set<String> topEntitiesFound = new HashSet<>();

        Collection<Emit> ttl_names = nameTree.parseText(doc.getTitle());
        for (Emit emt : ttl_names){
            NamedEntity ne = (NamedEntity) emt.getCustomeData();
            if (ne.isParent()){
                topEntitiesFound.add(ne.getName());
            }
        }

        for (int i = 1; i < numSent; i++) {
            final String orig = sents.get(i);
            final String origBefore = sents.get(i - 1);
    //        String rawSent_before = TextNormalizer.normalize(origBefore);
            String rawSent_curr = TextNormalizer.normalize(orig);
           int numTokens = rawSent_curr.split("\\s+").length;
            if (numTokens < 6 || numTokens > 50) continue;
/*
            try {
                Files.write(Paths.get("snp500.txt"), (orig  +"\n").getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            }
*/

            Collection<Emit> name_match_curr = nameTree.parseText(orig);
            Collection<Emit> name_match_befr = nameTree.parseText(origBefore);

            if (name_match_curr.size() == 0 && name_match_befr.size() == 0) continue;

            Collection<Emit> titles_emet = titleTree.parseText(orig);

 //           int importance = 0;
 //           if (name_match_befr.size() > 0) importance++;

//            if (name_match_curr.size() > 1) continue;

            List<Emit> allemits = new ArrayList<>();
            allemits.addAll(name_match_curr);
            allemits.addAll(name_match_befr);
            for (Emit emt : allemits){
                NamedEntity ne = (NamedEntity) emt.getCustomeData();
                if (ne.isParent()){
                    topEntitiesFound.add(ne.getName());
                }
            }

            NamedEntity entity = null;

            if (name_match_curr.size() > 1) {
                Collection<Emit> verb_emit = verbTree.parseText(rawSent_curr);
                if (verb_emit.size() == 0) {
                    logger.debug("There are more than one entity and no verb: " + orig);
                    for (Emit e_n : name_match_curr) {
                        logger.debug(" --> " + e_n.getKeyword());
                    }
                    continue;
                } else {
                    for (Emit e_n : name_match_curr) {
                        for (Emit e_v : verb_emit) {
                            if (dist(e_n, e_v)) {
                                entity = (NamedEntity) e_n.getCustomeData();
                                break;
                            }
                        }
                    }
                }
            }

            if (name_match_curr.size() == 1) {
                entity = (NamedEntity) name_match_curr.iterator().next().getCustomeData();
            }

            if (entity == null) {
                logger.debug("Entity is still null : " + orig);
                continue;
            }

            entitiesFromNamedFound.add(entity.getEntity().getName());

            // if this entity is a child and parent hasn't been found then this entity should not be added
            if (!topEntitiesFound.contains(entity.getEntity().getName())){
                continue;
            }

            QTDocument newQuote = getQuoteDoc(doc, orig, entity);
            newQuote.setDocType(QTDocument.DOCTYPE.Phrase);
            newQuote.setBody(origBefore + " " + orig);
            quotes.add(newQuote);

            if (titles_emet.size()  == 0) continue;
            Iterator<Emit> it = titles_emet.iterator();
            while(it.hasNext()) {
                newQuote.addFacts("Title", it.next().getKeyword());
            }
        }
        return quotes;
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

            NamedEntity entity = null;
            if (name_match_curr.size() > 0) {
                importance += 2;
                int name_verb_pair = 0;
                for (Emit e_n : name_match_curr) {
                    for (Emit e_v : verb_emit) {
                        if (dist(e_n, e_v)) {
                            name_verb_pair++;
                            entity = (NamedEntity) e_n.getCustomeData();
                            break;
                        }
                    }
                }
                if (entity != null) {
                    QTDocument newQuote = getQuoteDoc(doc, orig, entity);
//                    Emit firstVerbEmit = (Emit) verb_emit.toArray()[0];
//                    newQuote.addTag(verb2context.get(firstVerbEmit.getKeyword()));
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

    public static void getPhsFromw(String input, String output) throws IOException {
        Trie.TrieBuilder phrase = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
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

    public static void init(InputStream speakerFile,
                            InputStream phraseFile,
                            InputStream contextFile) throws IOException, ClassNotFoundException {
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

        JsonParser parser = new JsonParser();

        //verbs
        Trie.TrieBuilder verbs = Trie.builder().onlyWholeWords().ignoreCase();

 //       byte[] contextArr = contextFile != null ? IOUtils.toByteArray(contextFile) :
 //       IOUtils.toByteArray(new ClassPathResource("/context.json").getInputStream());
        URL url = Speaker.class.getClassLoader().getResource("context.json");

        byte[] contextArr = contextFile != null ? IOUtils.toByteArray(contextFile) :
                IOUtils.toByteArray(url.openStream());


        JsonElement jsonElement = parser.parse(new String(contextArr, "UTF-8"));
        JsonObject contextJson = jsonElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : contextJson.entrySet()) {
            String context_key = entry.getKey();
            JsonArray context_arr = entry.getValue().getAsJsonArray();
            for (JsonElement e : context_arr) {
                String verb = TextNormalizer.normalize(e.getAsString());
                verbs.addKeyword(verb, context_key);
            }
        }
        verbTree = verbs.build();

        //titles
        Trie.TrieBuilder titles = Trie.builder().onlyWholeWords().ignoreCase();
 //       byte[] commonArr = IOUtils.toByteArray(new ClassPathResource("/common.json").getInputStream());
        byte[] commonArr = IOUtils.toByteArray(Speaker.class.getClassLoader().getResource("common.json").openStream());
        JsonElement commonosnElement = parser.parse(new String(commonArr, "UTF-8"));
        JsonObject commonosnObject = commonosnElement.getAsJsonObject();
        JsonElement titles_elems = commonosnObject.get("titles");
        if (titles_elems != null) {
            for (JsonElement elem : titles_elems.getAsJsonArray()) {
         //       String ttl = lp.normalize(elem.getAsString());
                String ttl = elem.getAsString();
                titles.addKeyword(ttl);
            }
        }
        titleTree = titles.build();

        //names
        if (speakerFile == null){
            byte[] subjectArr = IOUtils.toByteArray(Speaker.class.getClassLoader().getResource("subject.json").openStream());
//            byte[] subjectArr = IOUtils.toByteArray(new ClassPathResource("/subject.json").getInputStream());
            jsonElement = parser.parse(new String(subjectArr, "UTF-8"));
        } else {
            jsonElement = parser.parse(new InputStreamReader(speakerFile, "UTF-8"));
        }

        JsonArray speakerJson = jsonElement.getAsJsonArray();
        Trie.TrieBuilder names = Trie.builder().onlyWholeWords().ignoreOverlaps();


        Gson gson = new Gson();
        for (JsonElement spj : speakerJson) {
            final Entity entity = gson.fromJson(spj, Entity.class);
            String entity_name = entity.getName();
            NamedEntity entityNamedEntity = new NamedEntity(entity_name, null);
            entityNamedEntity.setEntity(entity);
            entityNamedEntity.setParent(true);
            SEARH_TEMRS.add(entity_name);

            names.addKeyword(entity_name, entityNamedEntity);
            names.addKeyword(entity_name.toUpperCase(), entityNamedEntity);

            String [] alts = entity.getAlts();
            if (alts != null) {
                for (String alt : alts) {
                    names.addKeyword(alt, entityNamedEntity);
                    names.addKeyword(alt.toUpperCase(), entityNamedEntity);

                }
            }

            List<NamedEntity> namedEntities = entity.getNamedEntities();
            if (namedEntities == null) continue;
            for (NamedEntity namedEntity : namedEntities) {
                namedEntity.setEntity(entity);
                String p_name = namedEntity.getName();
                names.addKeyword(p_name, namedEntity);
                names.addKeyword(p_name.toUpperCase(), namedEntity);
                for (String alt : namedEntity.getAlts()) {
         //           alt = lp.normalize(alt);
                    names.addKeyword(alt, namedEntity);
                    names.addKeyword(alt.toUpperCase(), namedEntity);
                }
            }
        }
        nameTree = names.build();
    }

    public static List<String> getSearhTemrs(){
        return SEARH_TEMRS;
    }

    public static void main(String[] args) throws Exception {
        ENDocumentInfo.init();
        Speaker.init(new FileInputStream("/Users/matin/git/QTdatacollect/src/main/resources/CentralBanks.json") , null, null);

        QTDocument doc = new ENDocumentInfo("While March’s U.S. employment report wasn’t as bad as the payrolls number indicated, there are plenty of reasons why April’s figures will be brighter -- and continue to show a solid labor market.\n" +
                "\n" +
                "The first jobs report of the second quarter should show the economy moving past early-year seasonal quirks -- both of the methodological kind that have plagued the Labor Department data for several years, as well as the noise emanating from two unusually warm months followed by one that contained a powerful Northeast storm during the report’s survey week.\n" +
                "\n" +
                "That would make the underlying picture clearer: a steady job market with hiring settling into a more sustainable pace and wage growth strengthening, all helping to cushion household balance sheets and make the first quarter’s weak consumption figure look like a blip. The overall bright labor-market picture helped prompt Federal Reserve officials Wednesday to suggest they’re still on track for two more interest-rate increases this year, saying the “fundamentals underpinning the continued growth” of consumer spending remain solid.",
                "rican output after five years, says Bank of Canada’s Stephen Poloz");
        doc.processDoc();

        ArrayList<QTDocument> docs = Speaker.extractEntityMentions(doc);
        for (QTDocument dd : docs){
            logger.info(dd.getTitle());
        }
    }
}


