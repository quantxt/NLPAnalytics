package com.quantxt.nlp.types;

import com.quantxt.QTDocument.ENDocumentInfo;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static java.util.Arrays.asList;

/**
 * Created by matin on 4/29/17.
 */
public class TextNormalizer {

    final private static Logger logger = LoggerFactory.getLogger(TextNormalizer.class);
    final private static opennlp.tools.stemmer.PorterStemmer porterStemmer = new opennlp.tools.stemmer.PorterStemmer();
    private static Set<String> stopwords = null;

    public static void init() {
        if (stopwords != null) return;
        stopwords = new HashSet<>();
        List<String> sl = null;
        try {
            URL url = ENDocumentInfo.class.getClassLoader().getResource("en/stoplist.txt");
            sl = IOUtils.readLines(url.openStream());
            for (String s : sl){
                String [] tokens = TextNormalizer.normalize(s).split("\\s+");
                for (String t : tokens) {
                    stopwords.add(t);
                }
            }
        } catch (IOException e) {
            logger.equals(e.getMessage());
        }
        logger.info("Stop list loaded");
    }

    public static void init(String stoplist) {
        String line;
        try {
            BufferedReader br = new BufferedReader(new FileReader(stoplist));
            while ((line = br.readLine()) != null) {
                stopwords.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("Stop list loaded");
    }

    //porter stemmer is NOT thread safe :-/
    public static synchronized String normalize(String string) {

        string = string.replaceAll("\\\\\"","\"");
        string = string.replaceAll("\\\\n","");
        string = string.replaceAll("\\\\r","");
        string = string.replaceAll("\\\\t","");
        string = string.replaceAll("[\\&\\!\\“\\”\\$\\=\\>\\<_\\'\\’\\-\\—\"\\‘\\.\\/\\(\\),?;:\\*\\|\\]\\[\\@\\#\\s+]+", " ");
        string = string.replaceAll("\\b\\d+\\b", "");
        string = string.toLowerCase();
        List<String> list = asList(string.split("\\s+"));
        ArrayList<String> postEdit = new ArrayList<>();

        for (String l : list) {
            if (!stopwords.contains(l)) {
                l = porterStemmer.stem(l);
                postEdit.add(l);
            }
        }
       // string = StringUtils.join(postEdit, " ");
        return String.join(" " , postEdit);
    }

    public static void main(String[] args) throws Exception {
        logger.info(TextNormalizer.normalize("health care obamacare"));

    }
}
