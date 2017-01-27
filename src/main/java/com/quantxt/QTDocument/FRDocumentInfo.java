package com.quantxt.QTDocument;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import com.memetix.mst.language.Language;

public class FRDocumentInfo extends QTDocument {

	private static Pattern statementWords;
	private static int NumOfTopics;
	private static double bodyWeight  = .4;
	private static double titleWeight = .6;
	private static InstanceList instances;
	private static cc.mallet.topics.TopicInferencer inferencer;
	
	public FRDocumentInfo (String body, String title){
		super(body, title);
	}
	
	public static void init() throws Exception{
		NumOfTopics    = 100;
//		instances   = InstanceList.load(new File("models/topic."+ NumOfTopics +"news.fr.instance"));
//		cc.mallet.topics.ParallelTopicModel model = null;
//		model = new cc.mallet.topics.ParallelTopicModel(NumOfTopics);
//		model = ParallelTopicModel.read(new File("models/topic." + NumOfTopics + "news.fr.state.gz"));
		File writer = new File("topWords.fr.txt");
//		model.printTopWords(writer, 30, false);
//		inferencer = model.getInferencer();
	}
	
	private String _tokenizedText(String text){
		String tokenizedText = text;
		tokenizedText = tokenizedText.toLowerCase();
		tokenizedText = tokenizedText.replaceAll("[^a-z0-9]", " ");
		return tokenizedText;
	}
	
	private double[] getTopicVec (String text){
		if (text == null || text.isEmpty())
			return new double[NumOfTopics];
		InstanceList testing = new InstanceList(instances.getPipe());
		testing.addThruPipe(new Instance(_tokenizedText(text),  null, "text", null));
		return inferencer.getSampledDistribution(testing.get(0), 200, 10, 5);
	}
	
	public void processDoc() {
		englishTitle = Translate(title, Language.FRENCH, Language.ENGLISH);
		/*
		double[] bodyProbabilities  = getTopicVec(body);
		double[] titleProbabilities = getTopicVec(title);
		
		List<Integer> tmp = new ArrayList<Integer>();
		for (int i=0; i < NumOfTopics; i++){
			double val = bodyWeight * bodyProbabilities[i] + titleWeight * titleProbabilities[i];
			if (val < .1)
				continue;
//			tmp.add(10 * i + (int)(10 * val));
			tmp.add(i);
		}
		Collections.sort(tmp, Collections.reverseOrder());
		for (int i=0; i < tmp.size(); i++){
	//		addTopic(i, (double)tmp.get(i));
			addTopic(tmp.get(i), 1.0);
		}
		*/
	}

	@Override
	protected boolean isStatement(String s) {
		Matcher m = statementWords.matcher(s);
		if (m.find())
			return true;
		return false;
	}

}
