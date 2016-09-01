package com.quantxt.nlp;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultStreamTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by u6014526 on 5/3/2016.
 */
public class LinePreProcess extends Pipe implements TokenizerFactory {

    final private static Logger logger = Logger.getLogger(LinePreProcess.class);
    private TokenPreProcess tokenPreProcess;

    public LinePreProcess() {
    }

    private String normalize(String string) throws IOException {
//        org.apache.lucene.analysis.Tokenizer tokenizer  = new LowerCaseTokenizer(new StringReader(string));
        Analyzer analyzer = new EnglishAnalyzer();
//        TokenStream tokenStream = new PorterStemFilter(tokenizer);
        TokenStream tokenStream = analyzer.tokenStream("", string);

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
        string = string.replaceAll("[\\>\\<_'\\-\"\\/\\(\\),?;:]+", " ");
        string = string.replaceAll("\\b\\d+\\b", "");
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
}
