package com.quantxt.nlp;

import com.google.gson.*;
import com.quantxt.QTDocument.ENDocumentInfo;
import com.quantxt.SearchConcepts.Entity;
import com.quantxt.SearchConcepts.NamedEntity;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.helper.ArticleBodyResolver;
import com.quantxt.helper.DateResolver;
import com.quantxt.nlp.types.Tagger;
import com.quantxt.trie.Emit;
import com.quantxt.trie.Trie;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Created by matin on 10/9/16.
 */

public class Speaker implements QTExtract {
    final private static Logger logger = LoggerFactory.getLogger(Speaker.class);

    private Trie phraseTree = null;
    private Trie nameTree   = null;
    private Trie titleTree  = null;
    private List<String> SEARH_TEMRS = new ArrayList<>();
    private Tagger tagger = null;


    public Speaker(Entity[] entities,
                   String taggerCode,
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

        JsonParser parser = new JsonParser();


        //titles
        Trie.TrieBuilder titles = Trie.builder().onlyWholeWords().ignoreCase();
        byte[] commonArr = IOUtils.toByteArray(Speaker.class.getClassLoader().getResource("en/common.json").openStream());
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
        Gson gson = new Gson();

        //names
        if (entities == null){
            byte[] subjectArr = IOUtils.toByteArray(Speaker.class.getClassLoader().getResource("en/subject.json").openStream());
            entities = gson.fromJson(new String(subjectArr, "UTF-8"), Entity[].class);
        }

        Trie.TrieBuilder names = Trie.builder().onlyWholeWords().ignoreOverlaps();

        for (Entity entity : entities){
            //        final Entity entity = gson.fromJson(spj, Entity.class);
            String entity_name = entity.getName();
            NamedEntity entityNamedEntity = new NamedEntity(entity_name, null);
            entityNamedEntity.setEntity(entity);
            entityNamedEntity.setParent(true);
            SEARH_TEMRS.add(entity_name);

            // include entity as a speaker?
            if (entity.isSpeaker()) {
                names.addKeyword(entity_name, entityNamedEntity);
                names.addKeyword(entity_name.toUpperCase(), entityNamedEntity);

                String[] alts = entity.getAlts();
                if (alts != null) {
                    for (String alt : alts) {
                        names.addKeyword(alt, entityNamedEntity);
                        names.addKeyword(alt.toUpperCase(), entityNamedEntity);

                    }
                }
            }

            List<NamedEntity> namedEntities = entity.getNamedEntities();
            if (namedEntities == null) continue;
            for (NamedEntity namedEntity : namedEntities) {
                namedEntity.setEntity(entity);
                String p_name = namedEntity.getName();
                names.addKeyword(p_name, namedEntity);
                names.addKeyword(p_name.toUpperCase(), namedEntity);
                Set<String> nameAlts = namedEntity.getAlts();
                if (nameAlts != null) {
                    for (String alt : namedEntity.getAlts()) {
                        //           alt = lp.normalize(alt);
                        names.addKeyword(alt, namedEntity);
                        names.addKeyword(alt.toUpperCase(), namedEntity);
                    }
                }
            }
        }
        nameTree = names.build();
        tagger = Tagger.load(taggerCode);
        logger.info("Speaker for " + taggerCode + " is created");
    }


    @Override
    public double [] tag(String str){
        synchronized (tagger) {
            return tagger.getTextVec(str);
        }
    }

    @Override
    public Collection<Emit> parseNames(String str){
        return nameTree.parseText(str);
    }

    @Override
    public Collection<Emit> parseTitles(String str){
        return titleTree.parseText(str);
    }

    private QTDocument getQuoteDoc(QTDocument doc,
                                   String quote,
                                   NamedEntity namedEntity)
    {
        quote = quote.replaceAll("^Advertisement\\s+", "").trim();
        QTDocument sDoc = new ENDocumentInfo("", quote);
        sDoc.setDate(doc.getDate());
        sDoc.setLink(doc.getLink());
        sDoc.setLogo(doc.getLogo());
        sDoc.setSource(doc.getSource());

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
    private static boolean isTagDC(String tag){
        return tag.equals("IN") || tag.equals("TO") || tag.equals("CC") || tag.equals("DT");
    }

    private static String removePrnts(String str){
        str = str.replaceAll("\\([^\\)]+\\)", " ");
        str = str.replaceAll("([\\.])+$", " $1");
        str = str.replaceAll("\\s+", " ");
        return str;
    }

    /*
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

//            try {
//                Files.write(Paths.get("snp500.txt"), (orig  +"\n").getBytes(), StandardOpenOption.APPEND);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

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
    */

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
/*
    public Speaker(Entity [] entities,
                   InputStream phraseFile,
                   InputStream contextFile) throws IOException, ClassNotFoundException
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
        Gson gson = new Gson();

        //names
        if (entities == null){
            byte[] subjectArr = IOUtils.toByteArray(Speaker.class.getClassLoader().getResource("subject.json").openStream());
            entities = gson.fromJson(new String(subjectArr, "UTF-8"), Entity[].class);
        }

        Trie.TrieBuilder names = Trie.builder().onlyWholeWords().ignoreOverlaps();

        for (Entity entity : entities){
    //        final Entity entity = gson.fromJson(spj, Entity.class);
            String entity_name = entity.getName();
            NamedEntity entityNamedEntity = new NamedEntity(entity_name, null);
            entityNamedEntity.setEntity(entity);
            entityNamedEntity.setParent(true);
            SEARH_TEMRS.add(entity_name);

            // include entity as a speaker?
            if (entity.isSpeaker()) {
                names.addKeyword(entity_name, entityNamedEntity);
                names.addKeyword(entity_name.toUpperCase(), entityNamedEntity);

                String[] alts = entity.getAlts();
                if (alts != null) {
                    for (String alt : alts) {
                        names.addKeyword(alt, entityNamedEntity);
                        names.addKeyword(alt.toUpperCase(), entityNamedEntity);

                    }
                }
            }

            List<NamedEntity> namedEntities = entity.getNamedEntities();
            if (namedEntities == null) continue;
            for (NamedEntity namedEntity : namedEntities) {
                namedEntity.setEntity(entity);
                String p_name = namedEntity.getName();
                names.addKeyword(p_name, namedEntity);
                names.addKeyword(p_name.toUpperCase(), namedEntity);
                Set<String> nameAlts = namedEntity.getAlts();
                if (nameAlts != null) {
                    for (String alt : namedEntity.getAlts()) {
                        //           alt = lp.normalize(alt);
                        names.addKeyword(alt, namedEntity);
                        names.addKeyword(alt.toUpperCase(), namedEntity);
                    }
                }
            }
        }
        nameTree = names.build();
    }
*/

    public List<String> getSearhTemrs(){
        return SEARH_TEMRS;
    }

    public static void main(String[] args) throws Exception {
        ENDocumentInfo.init(null);

        Entity entity = new Entity("House of Representatives", null, false);
        //    List<String> nes = new ArrayList<>();
        //    nes.add("Tammy Baldwin");
        entity.addPerson("Tammy Baldwin", null);

        Entity[] entities = new Entity[] {entity};
        Speaker spk = new Speaker(entities, null, null);

       // "http://milwaukeecourieronline.com/index.php/2017/05/27/u-s-senator-tammy-baldwin-statement-on-cbo-score-of-house-passed-health-care-bill/"
        String link = "http://milwaukeecourieronline.com/index.php/2017/05/27/u-s-senator-tammy-baldwin-statement-on-cbo-score-of-house-passed-health-care-bill/";
     //   "https://insurancenewsnet.com/oarticle/u-s-senator-tammy-baldwin-to-president-trump-on-proposed-medicaid-cuts-americas-veterans-deserve-better";
        Document jsoupDoc = Jsoup.connect(link).get();
        DateTime document_date = DateResolver.resolveDate(jsoupDoc);
        ArticleBodyResolver adr = new ArticleBodyResolver(jsoupDoc);

        List<Element> bodyElems = adr.getText();
        String body = "";
        for (Element e : bodyElems){
            body += e.text() + " ";
        }

        String title = jsoupDoc.title();
        ENDocumentInfo doc = new ENDocumentInfo(body, title);
        doc.setDate(document_date);
        doc.processDoc();
    //    ArrayList<QTDocument> docs = spk.extractEntityMentions(doc);
    //    logger.info(document_date + " " + docs.size());
    }
}


