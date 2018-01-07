package com.quantxt.QTDocument;

import java.io.*;
import java.net.URL;
import java.util.*;

import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.trie.Emit;

import com.quantxt.trie.Trie;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tartarus.snowball.ext.SpanishStemmer;

public class ESDocumentInfo extends QTDocument {

	private static final Logger logger = LoggerFactory.getLogger(ESDocumentInfo.class);
	private static SentenceDetectorME sentenceDetector = null;
	private static POSTaggerME posModel = null;
	private static Analyzer analyzer;
	private static CharArraySet stopwords;
	private static HashSet<String> pronouns;
	private static Trie verbTree;

	public ESDocumentInfo (String body, String title) {
		super(body, title);
		language = Language.SPANISH;
	}

	public ESDocumentInfo (Elements body, String title) {
		super(body.html(), title);
		rawText = body.text();

	}

	public static boolean isStopWord(String p){
		return stopwords.contains(p);
	}

	public static void init(InputStream contextFile) throws Exception{
		if( sentenceDetector != null) return;

		pronouns = new HashSet<>();
		pronouns.add("él");
		pronouns.add("ella");

		analyzer = new SpanishAnalyzer();
		URL url = ESDocumentInfo.class.getClassLoader().getResource("en/en-sent.bin");
		SentenceModel sentenceModel = new SentenceModel(url.openStream());
		sentenceDetector = new SentenceDetectorME(sentenceModel);

		url = ESDocumentInfo.class.getClassLoader().getResource("es/es-pos-perceptron-500-0.bin");
		POSModel model = new POSModel(url.openStream());
		posModel = new POSTaggerME(model);

		stopwords = new CharArraySet(600, true);
		try {
			url = ESDocumentInfo.class.getClassLoader().getResource("es/stoplist.txt");
			List<String> sl = IOUtils.readLines(url.openStream());
			for (String s : sl){
				stopwords.add(s);
			}
		} catch (IOException e) {
			logger.equals(e.getMessage());
		}

		//verbs
		byte[] verbArr = contextFile != null ? IOUtils.toByteArray(contextFile) :
				IOUtils.toByteArray(ESDocumentInfo.class.getClassLoader().getResource("es/context.json").openStream());

		QTDocument doc = new ESDocumentInfo("", "");
		verbTree = doc.buildVerbTree(verbArr);

		logger.info("Spanish models initiliazed");
	}

	public static synchronized ArrayList<String> stemmer(String str){
		ArrayList<String> postEdit = new ArrayList<>();
		TokenStream stream  = analyzer.tokenStream(null, new StringReader(str));
		stream = new StopFilter(stream, stopwords);
		stream = new SnowballFilter(stream, new SpanishStemmer());
		CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);

		try {
			stream.reset();
			while (stream.incrementToken()) {
				String term = charTermAttribute.toString();
				postEdit.add(term);
			}
			stream.close();
		} catch (Exception e){

		}
		return postEdit;
	}

	@Override
	public Trie getVerbTree(){
		return verbTree;
	}

	@Override
	public List<String> tokenize(String str) throws IOException {
		TokenStream result = analyzer.tokenStream(null, str);
		result = new SnowballFilter(result, "Spanish");

		CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
		result.reset();
		List<String> tokens = new ArrayList<>();
		while (result.incrementToken()) {
			tokens.add(resultAttr.toString());
		}
		result.close();
		return tokens;
	}

	@Override
	public double [] getVectorizedTitle(QTExtract speaker){
		return speaker.tag(title);
	}

	@Override
	public String normalize(String string)
	{
		string = string.replaceAll("\\\\\"","\"");
		string = string.replaceAll("\\\\n","");
		string = string.replaceAll("\\\\r","");
		string = string.replaceAll("\\\\t","");
		string = string.replaceAll("[\\&\\!\\“\\”\\$\\=\\>\\<_\\'\\’\\-\\—\"\\‘\\.\\/\\(\\),?;:\\*\\|\\]\\[\\@\\#\\s+]+", " ");
		string = string.replaceAll("\\b\\d+\\b", "");
		string = string.toLowerCase();

		//This is thread safe
		ArrayList<String> postEdit = stemmer(string);
		return String.join(" ", postEdit);
	}

	@Override
	public void processDoc(){
		englishTitle = title;
		if (body == null || body.isEmpty())
			return;

		String sentences[] = rawText == null ? getSentences(body) : getSentences(rawText);
		this.sentences = Arrays.asList(sentences);
	}

	@Override
	public String Translate(String text, Language inLang, Language outLang) {
		logger.error("Translation is not supported at this time");
		return null;
	}

	//sentence detc is NOT thread safe  :-/
	public static synchronized String [] getSentences(String text){
		return sentenceDetector.sentDetect(text);
	}


	@Override
	public boolean isStatement(String s) {
		return false;
	}

	public String [] getPosTags(String [] text)
	{
		String [] tags;
		synchronized (posModel)	{
			tags = posModel.tag(text);
		}
		return tags;
	}

	private static boolean isTagDC(String tag){
		return tag.equals("CS") || tag.equals("CC") || tag.startsWith("D");
	}

	//https://github.com/slavpetrov/universal-pos-tags/blob/master/es-eagles.map
	@Override
	protected List<ExtInterval> getNounAndVerbPhrases(String orig,
													String [] parts){
		int numTokens = parts.length;
		String [] taags = getPosTags(parts);
		List<String> tokenList= new ArrayList<>();
		List<ExtInterval> phrases = new ArrayList<>();
		String type = "X";
		for (int j=numTokens-1; j>=0; j--){
			String tag = taags[j];
			String word = parts[j];
			if ( isTagDC(tag) ) {
				int nextIdx = j - 1;
				if (nextIdx < 0) continue;
				String nextTag = taags[nextIdx];
				if ((tokenList.size() != 0) &&
						(isTagDC(tag) ) ||
						(type.equals("N") && nextTag.startsWith("N") ) ||
						(type.equals("V") && nextTag.startsWith("V") ))
				{
					tokenList.add(word);
				}
				continue;
			}
			if ( (tag.startsWith("N") || (tag.startsWith("P"))) ){
				if (!type.equals("N") && tokenList.size() >0){
					Collections.reverse(tokenList);
					String str = String.join(" ", tokenList);
					int start = orig.indexOf(str);
					if (start == -1){
						logger.error("NOT FOUND 1 " + str);
					}
					ExtInterval eit = new ExtInterval(start, start+str.length());
					eit.setType(type);
					phrases.add(eit);
					tokenList.clear();
				}
				type = "N";
				tokenList.add(word);
			}  else if ( tag.startsWith("A")){
				if (tokenList.size() != 0){
					tokenList.add(word);
				}
			} else if (tag.startsWith("V")){
				if (!type.equals("V") && tokenList.size() >0){
					Collections.reverse(tokenList);
					String str = String.join(" ", tokenList);
					int start = orig.indexOf(str);
					if (start == -1){
						logger.error("NOT FOUND 2 " + str);
					}
					ExtInterval eit = new ExtInterval(start, start+str.length());
					eit.setType(type);
					phrases.add(eit);
					tokenList.clear();
				}
				type = "V";
				tokenList.add(word);
			} else if (tag.startsWith("S") || tag.startsWith("R")){
				if (tokenList.size() != 0){
					tokenList.add(word);
				}
			}  else {
				if (!type.equals("X") && tokenList.size() >0){
					Collections.reverse(tokenList);
					String str = String.join(" ", tokenList);
					int start = orig.indexOf(str);
					if (start == -1){
						logger.error("NOT FOUND 3 " + str);
					}
					ExtInterval eit = new ExtInterval(start, start+str.length());
					eit.setType(type);
					phrases.add(eit);
					tokenList.clear();
				}
				type = "X";
			}
		}

		if (!type.equals("X") && tokenList.size() >0){
			Collections.reverse(tokenList);
			String str = String.join(" ", tokenList);
			int start = orig.indexOf(str);
			ExtInterval eit = new ExtInterval(start, start+str.length());
			eit.setType(type);
			phrases.add(eit);
		}

		Collections.reverse(phrases);
		return phrases;
	}
}
