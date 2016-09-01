package com.quantxt.QTDocument;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import com.quantxt.helpers.Company;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;

import opennlp.tools.util.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ENDocumentInfo extends QTDocument {

	protected static final Logger logger = LoggerFactory.getLogger(ENDocumentInfo.class);

	final private static Pattern statementWords = Pattern.compile("(?i)\\bsaid|told|stated|announced|says|added\\b");
	final private static int NumOfTopics = 100;
	final private static double bodyWeight  = .3;
	final private static double titleWeight = .7;

	private SentenceDetectorME sentenceDetector = null;
	private NameFinderME nameFinder = null;
	private NameFinderME organizationFinder = null;
	private Tokenizer tokenizer = null;
//	private static cc.mallet.topics.TopicInferencer inferencer;
//	private static cc.mallet.pipe.Pipe trainingPipe = null;
//	private static Set<String> stopWordList = null;
//	private static String wordTopicList = null;
//	private static Map<String, ArrayList<Integer>> topicWeight = null;
	private List<Company> companyName = new ArrayList<>();
	
	public ENDocumentInfo (String body, String title) throws IOException {
		super(body, title);
/*		InputStream sentenceModellIn = new FileInputStream("models/en-sent.bin");
		InputStream nerPersonmodelIn = new FileInputStream("models/en-ner-person.bin");
		InputStream nerOrganizationnmodelIn = new FileInputStream("models/en-ner-organization.bin");
		InputStream tokenizerModelIn = new FileInputStream("models/en-token.bin");
		SentenceModel sentenceModel = new SentenceModel(sentenceModellIn);
		sentenceDetector = new SentenceDetectorME(sentenceModel);
		TokenNameFinderModel nerPersonModel = new TokenNameFinderModel(nerPersonmodelIn);
		nameFinder = new NameFinderME(nerPersonModel);
		TokenNameFinderModel nerOrganizationModel = new TokenNameFinderModel(nerOrganizationnmodelIn);
		organizationFinder = new NameFinderME(nerOrganizationModel);
		TokenizerModel tokenizerModel = new TokenizerModel(tokenizerModelIn);
		tokenizer = new TokenizerME(tokenizerModel);
		logger.info("English model initialized");
		*/
	}
	
	public static void init() throws Exception{
//		stopWordList = new HashSet<String>();
//		wordTopicList = "topWords." + NumOfTopics + ".en.txt";
//		private static InstanceList instances;
//		private static cc.mallet.topics.TopicInferencer inferencer;
		InputStream sentenceModellIn = new FileInputStream("models/en-sent.bin");
		InputStream nerPersonmodelIn = new FileInputStream("models/en-ner-person.bin");
		InputStream nerOrganizationnmodelIn = new FileInputStream("models/en-ner-organization.bin");
		InputStream tokenizerModelIn = new FileInputStream("models/en-token.bin");
/*		InstanceList instances   = InstanceList.load(new File("models/NewsWireTopic."+ NumOfTopics +".market.en.instance"));
		trainingPipe = instances.getPipe();
		ParallelTopicModel model = null;
		model = new cc.mallet.topics.ParallelTopicModel(NumOfTopics);
		model = ParallelTopicModel.read(new File("models/NewsWireTopic." + NumOfTopics + ".market.en.state.gz"));
		File writer = new File(wordTopicList);
		model.printTopWords(writer, 30, false);
		inferencer = model.getInferencer();

	
		String line;
		topicWeight = new HashMap<>();
		BufferedReader br = new BufferedReader(new FileReader("/home/matin/Downloads/dictionary-builder-master/output/stoplist.txt.top1000.fromCorpus.en"));
		//do stop words
		while ( (line = br.readLine()) != null)
		{
			ArrayList<Integer> tList = topicWeight.get(line);
			if (tList == null)
			{
				topicWeight.put(line, new ArrayList<Integer>());
				tList = topicWeight.get(line);
			}
			tList.add(NumOfTopics);
		//	stopWordList.add(line);
		}
		br.close();
*/		/*
		br = new BufferedReader(new FileReader(wordTopicList));
		//do stop words
		while ( (line = br.readLine()) != null)
		{
			String [] parts = line.split("\\s+");
			for (int i = 2; i<parts.length; i++){
				final String w = parts[i];
				ArrayList<Integer> tList = topicWeight.get(w);
				if (tList == null)
				{
					topicWeight.put(w, new ArrayList<Integer>());
					tList = topicWeight.get(w);
				}
				tList.add(Integer.parseInt(parts[0]));
			}
		//	stopWordList.add(line);
		}
		br.close();
		*/
/*		SentenceModel sentenceModel = new SentenceModel(sentenceModellIn);
		sentenceDetector = new SentenceDetectorME(sentenceModel);
		TokenNameFinderModel nerPersonModel = new TokenNameFinderModel(nerPersonmodelIn);
		nameFinder = new NameFinderME(nerPersonModel);
		TokenNameFinderModel nerOrganizationModel = new TokenNameFinderModel(nerOrganizationnmodelIn);
		organizationFinder = new NameFinderME(nerOrganizationModel);
		TokenizerModel tokenizerModel = new TokenizerModel(tokenizerModelIn);
		tokenizer = new TokenizerME(tokenizerModel);
*/
		// Company List
		/*
		
		br = new BufferedReader(new FileReader("models/Companies.list"));
		while ( (line = br.readLine()) != null)
		{
			String [] parts = line.split("\",\"");
			String ticker   = parts[0].replaceAll("^\"", "");
			String name 	= parts[1].replaceAll("&#39;", "'");
			String sector 	= parts[6];
			String industry = parts[7];
		
			try {
				Double mktCap = Double.parseDouble(parts[3]);
				if (mktCap < 1500000000)
					continue;
				Company c = new Company(name, ticker, mktCap, sector, industry);
				companyName.add(c);
			} catch (NumberFormatException nfe) {
		         System.out.println("NumberFormatException: " + nfe.getMessage());
		    }
		}
		br.close();
*/
		System.out.println("english models initiliazed");
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
	
	private void findCompanies(String t){
		for (Company c : companyName) {
			final String compName = c.getName();
			if (c.isMatch(t)){
				this.addOrganization(compName);
				this.setSector(c.getSector());
				this.setIndustry(c.getIndustry());
				this.addTicker(c.getTicker());
			}
		}
	}
	
	@Override
	public void processDoc(){
		englishTitle = title;
//		findCompanies(title);
//		final String str2search = title + " " + body;
//		findCompanies(title);
		if (body == null || body.isEmpty())
			return;	
//		Map<Integer, Double> sorted_map = getSortedTopics(body, title, bodyWeight, titleWeight);
//        int u = 3;
//		for (Map.Entry<Integer, Double> e : sorted_map.entrySet()){
//			if ( u <= 0)
//				break;
//			addTopic(e.getKey(), e.getValue());
//			u--;
//		}
			
		String sentences[] = sentenceDetector.sentDetect(body);
		getSentenceNER(sentences, nameFinder, null);
	}
	
	@Override
	protected boolean isStatement(String s) {
		Matcher m = statementWords.matcher(s);
		if (m.find()){
			findCompanies(s);
			return true;
		}
		return false;
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
