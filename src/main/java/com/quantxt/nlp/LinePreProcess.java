package com.quantxt.nlp;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.datavec.api.util.ClassPathResource;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultStreamTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * Created by matin on 5/3/2016.
 */
public class LinePreProcess extends Pipe implements TokenizerFactory {

    final private static Logger logger = Logger.getLogger(LinePreProcess.class);
    private TokenPreProcess tokenPreProcess;
    private static Set<String> stopwords = null;
    private final static opennlp.tools.stemmer.PorterStemmer porterStemmer = new opennlp.tools.stemmer.PorterStemmer();

    public LinePreProcess() {
        if (stopwords != null) return;
        String line;
        stopwords = new HashSet<>();
        List<String> sl = null;
        try {
            sl = IOUtils.readLines(new ClassPathResource("/stopwords.txt").getInputStream());
            for (String s : sl){
                String [] tokens = normalize(s).split("\\s+");
                for (String t : tokens) {
                    stopwords.add(t);
                }
            }
        } catch (IOException e) {
            logger.equals(e.getMessage());
        }
        logger.info("Stop list loaded");
    }

    public LinePreProcess(String stoplist) {
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

    public String normalize(String string) throws IOException {
//        Analyzer analyzer = new EnglishAnalyzer();

 //       TokenStream tokenStream = analyzer.tokenStream("", string);
//        string = porterStemmer.stem(string);


        /*
        StringBuilder sb = new StringBuilder();
        CharTermAttribute charTermAttr = tokenStream.getAttribute(CharTermAttribute.class);
        try{
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(charTermAttr.toString());
            }
        }
        catch (IOException e){
            System.out.println(e.getMessage());
        }
        */

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
        string = StringUtils.join(postEdit, " ");
        return string;
    }

    public Instance pipe (Instance carrier)
    {
        String string = (String) carrier.getData();
        try {
            string = normalize(string);
        } catch (IOException e) {
            e.printStackTrace();
        }
        carrier.setData(string);
        return carrier;
    }

    public Tokenizer create(String toTokenize) {
        try {
            toTokenize = normalize(toTokenize);
        } catch (IOException e) {
            e.printStackTrace();
        }
        DefaultTokenizer t = new DefaultTokenizer(toTokenize.trim());

        t.setTokenPreProcessor(this.tokenPreProcess);
        return t;
    }

    public Tokenizer create(InputStream toTokenize) {
        DefaultStreamTokenizer t = new DefaultStreamTokenizer(toTokenize);
        t.setTokenPreProcessor(this.tokenPreProcess);
        return t;
    }

    public void setTokenPreProcessor(TokenPreProcess preProcessor) {
        this.tokenPreProcess = preProcessor;
    }

    @Override
    public TokenPreProcess getTokenPreProcessor() {
        return null;
    }


    public static void main(String[] args) throws Exception {

        LinePreProcess lp = new LinePreProcess();
        logger.info(lp.normalize("health care obamacare"));

    }
}
