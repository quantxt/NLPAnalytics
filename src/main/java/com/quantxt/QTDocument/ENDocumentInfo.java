package com.quantxt.QTDocument;

import java.io.*;
import java.util.*;

import com.quantxt.nlp.TopicModel;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;

import opennlp.tools.util.Span;
import org.ahocorasick.trie.Trie;
import org.apache.commons.io.IOUtils;
import org.datavec.api.util.ClassPathResource;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ENDocumentInfo extends QTDocument {

	protected static final Logger logger = LoggerFactory.getLogger(ENDocumentInfo.class);

	private static Trie statementWords = null;
	private static Trie actionWords = null;
	final private static int NumOfTopics = 100;
	final private static double bodyWeight  = .3;
	final private static double titleWeight = .7;
	private static boolean initialized = false;

	private String rawText;

	private static SentenceDetectorME sentenceDetector = null;
//	private NameFinderME nameFinder = null;
//	private NameFinderME organizationFinder = null;
	private static Tokenizer tokenizer = null;
//	private static cc.mallet.topics.TopicInferencer inferencer;
//	private static cc.mallet.pipe.Pipe trainingPipe = null;
//	private static Set<String> stopWordList = null;
//	private static String wordTopicList = null;
//	private static Map<String, ArrayList<Integer>> topicWeight = null;
//	private List<Company> companyName = new ArrayList<>();
	
	public ENDocumentInfo (String body, String title) {
		super(body, title);
	}

	public ENDocumentInfo (Elements body, String title) {
		super(body.html(), title);
		rawText = body.text();
	}
	
	public static void init() throws Exception{
		if( initialized) return;
//		stopWordList = new HashSet<String>();
//		wordTopicList = "topWords." + NumOfTopics + ".en.txt";
//		private static InstanceList instances;
//		private static cc.mallet.topics.TopicInferencer inferencer;
		InputStream sentenceModellIn = new ClassPathResource("/en-sent.bin").getInputStream();
		SentenceModel sentenceModel = new SentenceModel(sentenceModellIn);
		sentenceDetector = new SentenceDetectorME(sentenceModel);

//		InputStream tokenizerModelIn = new FileInputStream("models/en-token.bin");
//		TokenizerModel tokenizerModel = new TokenizerModel(tokenizerModelIn);
//		tokenizer = new TokenizerME(tokenizerModel);

		int numTopics = 150;

//		InputStream nerPersonmodelIn = new FileInputStream("models/en-ner-person.bin");
//		InputStream nerOrganizationnmodelIn = new FileInputStream("models/en-ner-organization.bin");
//		InputStream tokenizerModelIn = new FileInputStream("models/en-token.bin");

		initialized = true;
		logger.info("english models initiliazed");
	}
	
	private String _tokenizedText(String text){
		String tokenizedText = text;
		tokenizedText = tokenizedText.toLowerCase();
		tokenizedText = tokenizedText.replaceAll("[\\,\\/\\\\)\\(\\^\\]\\[\\»\\«\\-\\|]+", " ");
		tokenizedText = tokenizedText.replaceAll("[\\'\\.\\?\"\\*\\=]+", "");
//		line = line.replaceAll("[^a-z0-9]+", " ");
		tokenizedText = tokenizedText.replaceAll("\\S{20,}", "");
		return tokenizedText;
	}
	
	public double[] getTopicVec (String text){
		if (text == null || text.isEmpty())
			return null;
		//////remobe this
		return null;
		//////
//		InstanceList testing = new InstanceList(instances.getPipe());
//		InstanceList testing = new InstanceList(trainingPipe);
//		testing.addThruPipe(new Instance(_tokenizedText(text),  null, "text", null));
//		return inferencer.getSampledDistribution(testing.get(0), 200, 10, 5);
		/*
		double[] topicVec = new double[NumOfTopics + 1];
		if (text == null || text.isEmpty())
			return topicVec;
		String normText = _tokenizedText(text);
		String [] words = normText.split("\\s+");
		final double wordPortion = 1.0 / (double) words.length;
		
		double  oov = 0;
		for (String w : words){
			ArrayList<Integer> t = topicWeight.get(w);
			if (t == null){ //oov
//				topicVec[NumOfTopics + 1] += wordPortion;
				oov += wordPortion;
			}
			else
			{
				final double tSize = (double)t.size();
				for (int i : t){
					topicVec[i] += wordPortion * (1.0 / tSize);
				}
			}	
		};
		double scale =  1 - oov -  topicVec[NumOfTopics];
		if (scale < .01)
			return topicVec;
		
		for (int i=0; i< NumOfTopics; i++)
		{
			topicVec[i] /= scale;			
		}
//		double val = 0;
//		for (int i=0; i < NumOfTopics; i++)
//			val += topicVec[i];
//		System.out.println(val);
//		System.out.println(topicVec[NumOfTopics] + " " + topicVec[NumOfTopics+1]);
		return topicVec;
		*/
	}

	@Override
	protected String[] getTokens(String s) {
		return tokenizer.tokenize(s);
	}
	
	@Override
	protected Span[] getSpan(String[] s, NameFinderME nf) {
		return nf.find(s);
	}
	
	private Map<Integer, Double> getSortedTopics(String body, String title, double bodyWeight, double titleWeight){
		double[] bodyProbabilities  = getTopicVec(body);
		double[] titleProbabilities = getTopicVec(title);
		Map<Integer, Double> tmp = new HashMap<Integer, Double>();
		for (int i=0; i < bodyProbabilities.length; i++){
			double val = bodyWeight * bodyProbabilities[i] + titleWeight * titleProbabilities[i];
//			System.out.print(i + ":" +val + " ");

		//	tmp.add(10 * i + (int)(10 * val));
			if ( i == (bodyProbabilities.length-1))
				addTopic(bodyProbabilities.length , val);
			else if (val > .1)
				tmp.put(i, val);
		}
		
		VC bvc =  new VC(tmp);
        TreeMap<Integer, Double> sorted_map = new TreeMap<Integer, Double>(bvc);
        sorted_map.putAll(tmp);
        return sorted_map;
	}

	@Override
	public void processDoc(){
		englishTitle = title;
//		findCompanies(title);
//		final String str2search = title + " " + body;
//		findCompanies(title);
		if (body == null || body.isEmpty())
			return;	

		String sentences[] = rawText == null ? getSentences(body) : getSentences(rawText);

		for (String s : sentences){
			this.sentences.add(s);
		}
//			getSentenceNER(sentences, null, null);


//		getSentenceNER(sentences, nameFinder, null);
	}

	public static String [] getSentences(String text){
		return sentenceDetector.sentDetect(text);
	}

	@Override
	protected boolean isStatement(String s) {
		return statementWords.containsMatch(s);
	}

}

class VC implements Comparator<Integer> {

    Map<Integer, Double> base;
    public VC(Map<Integer, Double> base) {
        this.base = base;
    }

    // Note: this comparator imposes orderings that are inconsistent with equals.    
    public int compare(Integer a, Integer b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        } // returning 0 would merge keys
    }
}

