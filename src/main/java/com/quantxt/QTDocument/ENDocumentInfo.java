package com.quantxt.QTDocument;

import java.io.*;
import java.net.URL;
import java.util.*;

import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.nlp.Speaker;
import com.quantxt.trie.Trie;
import com.quantxt.types.Entity;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ENDocumentInfo extends QTDocument {

	private static final Logger logger = LoggerFactory.getLogger(ENDocumentInfo.class);
	private static SentenceDetectorME sentenceDetector = null;
	private static POSTaggerME posModel = null;
	private static Analyzer analyzer;
	private static CharArraySet stopwords;
	private static HashSet<String> pronouns;
	private static Trie verbTree;
	
	public ENDocumentInfo (String body, String title) {
		super(body, title);
		language = Language.ENGLISH;
	}

	public ENDocumentInfo (Elements body, String title) {
		super(body.html(), title);
		rawText = body.text();
	}

	public static boolean isStopWord(String p){
		return stopwords.contains(p);
	}

	public static void init(InputStream contextFile) throws Exception{
		//Already initialized
		if (sentenceDetector != null) return;

		pronouns = new HashSet<>();
		pronouns.add("he");
		pronouns.add("she");

		analyzer = new StandardAnalyzer();
		URL url = ENDocumentInfo.class.getClassLoader().getResource("en/en-sent.bin");
		SentenceModel sentenceModel = new SentenceModel(url.openStream());
		sentenceDetector = new SentenceDetectorME(sentenceModel);

		url = ENDocumentInfo.class.getClassLoader().getResource("en/en-pos-maxent.bin");
		POSModel model = new POSModel(url.openStream());
		posModel = new POSTaggerME(model);

		stopwords = new CharArraySet(800, true);
		try {
			url = ENDocumentInfo.class.getClassLoader().getResource("en/stoplist.txt");
			List<String> sl = IOUtils.readLines(url.openStream());
			for (String s : sl){
				stopwords.add(s);
			}
		} catch (IOException e) {
			logger.equals(e.getMessage());
		}

		//verbs

		byte[] verbArr = contextFile != null ? IOUtils.toByteArray(contextFile) :
				IOUtils.toByteArray(ENDocumentInfo.class.getClassLoader().getResource("en/context.json").openStream());
		QTDocument doc = new ENDocumentInfo("", "");
		verbTree = doc.buildVerbTree(verbArr);
		logger.info("English models initiliazed");
	}
	
	public double[] getTopicVec (String text){
		if (text == null || text.isEmpty())
			return null;
		//////remove this
		return null;
	}

	@Override
	public Trie getVerbTree(){
		return verbTree;
	}

	@Override
	public List<String> tokenize(String str) throws IOException {
		TokenStream result = analyzer.tokenStream(null, str);
		result = new SnowballFilter(result, "English");

		CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
		result.reset();
		List<String> tokens = new ArrayList<>();
		while (result.incrementToken()) {
			tokens.add(resultAttr.toString());
		}
		result.close();
		return tokens;
	}

	public static synchronized ArrayList<String> stemmer(String str){
		ArrayList<String> postEdit = new ArrayList<>();

		try {
			TokenStream stream  = analyzer.tokenStream(null, new StringReader(str));
			stream = new StopFilter(stream, stopwords);
			CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
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
	public double [] getVectorizedTitle(QTExtract speaker){
		return speaker.tag(title);
	}

	@Override
	public String normalize(String string)
	{
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

	//pos tagger is NOT thread safe  :-/

	public String [] getPosTags(String [] text)
	{
		String [] tags;
		synchronized (posModel)	{
			tags = posModel.tag(text);
		}
		return tags;
	}

	private boolean isTagDC(String tag){
		return tag.equals("IN") || tag.equals("TO") || tag.equals("CC") || tag.equals("DT");
	}

	@Override
    public boolean isStatement(String s) {
		return false;
	}


	//https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html

	@Override
	protected List<ExtInterval> getNounAndVerbPhrases(String orig,
													  String[] parts){
		int numTokens = parts.length;
		String [] taags = getPosTags(parts);
		List<String> tokenList= new ArrayList<>();
		List<ExtInterval> phrases = new ArrayList<>();
		String type = "X";
		for (int j=numTokens-1; j>=0; j--){
			final String tag = taags[j];
			final String word = parts[j];
			if ( isTagDC(tag) ) {
				int nextIdx = j - 1;
				if (nextIdx < 0) continue;
				String nextTag = taags[nextIdx];
				if ((tokenList.size() != 0) &&
						(isTagDC(tag) ) ||
						(type.equals("N") && nextTag.startsWith("NN") ) ||
						(type.equals("V") && nextTag.startsWith("VB") ))
				{
					tokenList.add(word);
				}
				continue;
			}
			if (tag.startsWith("NN") || tag.equals("PRP")){
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
			}  else if ( tag.startsWith("JJ")){
				if (tokenList.size() != 0){
					tokenList.add(word);
				}
			} else if (tag.startsWith("VB")){
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
			} else if (tag.startsWith("MD") || tag.startsWith("RB")){
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

	public static void main(String[] args) throws Exception {
		ENDocumentInfo.init(null);
		ESDocumentInfo.init(null);

		ArrayList<Entity> entityArray1 = new ArrayList<>();
		entityArray1.add(new Entity("We" , new String[]{"we" , "We", "Company", "company", "We did", "we did", "we've", "we have" , "we will" , "we'll" , "our", "we were"} , true));
		Map<String, Entity[]> entMap = new HashMap<>();
		entMap.put("Subject" , entityArray1.toArray(new Entity[entityArray1.size()]));
		Speaker enx = new Speaker(entMap, (String)null, null);

		String url = "https://www.sec.gov/Archives/edgar/data/1594337/000155837017009458/R15.htm";
		String out = Jsoup.connect(url)
				.followRedirects(true)
				.referrer("http://www.google.com")
				.ignoreContentType(true)
				.timeout(10000)
				.method(Connection.Method.GET)
				.get()
				.body()
				.text();

//		String out = "This is a book. To address this unmet medical need, we offer our PEER Online technology to analyze an individual’s digital Quantitative EEG , correlating the individual’s QEEG features with medication outcomes in our proprietary database of over 10,000 unique patients to predict the efficacy of psychotropic medications by class and individual medication .";
		QTDocument doc = QTDocumentFactory.createQTDoct(out, "MYnd Analytics, Inc.");
		doc.processDoc();

		ArrayList<QTDocument> qts = doc.extractEntityMentions(enx);
		for (QTDocument dc : qts){
			if (dc.getDocType() == null) continue;
			logger.info(dc.getDocType() + " / " + dc.getTitle());
		}


	}
}

