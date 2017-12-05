package com.quantxt.QTDocument;

import java.io.*;
import java.net.URL;
import java.util.*;

import com.google.gson.*;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.nlp.Speaker;
import com.quantxt.nlp.types.ExtInterval;
import com.quantxt.trie.Emit;
import com.quantxt.trie.Trie;

import com.quantxt.types.NamedEntity;
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

import static com.quantxt.QTDocument.QTHelper.removePrnts;

public class ESDocumentInfo extends QTDocument {

	private static final Logger logger = LoggerFactory.getLogger(ESDocumentInfo.class);
	private static final Analyzer analyzer = new SpanishAnalyzer();

	private static CharArraySet stopwords = null;

	private static Trie verbTree   = null;

	private String rawText;

	private static SentenceDetectorME sentenceDetector = null;
	private static POSTaggerME posModel = null;

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
		JsonParser parser = new JsonParser();
		Trie.TrieBuilder verbs = Trie.builder().onlyWholeWords().ignoreCase();

		url = Speaker.class.getClassLoader().getResource("en/context.json");

		byte[] contextArr = contextFile != null ? IOUtils.toByteArray(contextFile) :
				IOUtils.toByteArray(url.openStream());

		ESDocumentInfo tmpDoc = new ESDocumentInfo("", "");
		JsonElement jsonElement = parser.parse(new String(contextArr, "UTF-8"));
		JsonObject contextJson = jsonElement.getAsJsonObject();
		for (Map.Entry<String, JsonElement> entry : contextJson.entrySet()) {
			String context_key = entry.getKey();
			JsonArray context_arr = entry.getValue().getAsJsonArray();
			for (JsonElement e : context_arr) {
				//	String verb = TextNormalizer.normalize(e.getAsString());
				String verb = tmpDoc.normalize(e.getAsString());
				verbs.addKeyword(verb, context_key);
			}
		}
		verbTree = verbs.build();

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
	private List<ExtInterval> getNounAndVerbPhrases(String orig,
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
			if (tag.startsWith("N")){
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

	protected DOCTYPE getVerbType(String verbPhs) {
		TokenStream result = analyzer.tokenStream(null, verbPhs);

		try {
			result = new SnowballFilter(result, "Spanish");
			CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
			result.reset();
			List<String> tokens = new ArrayList<>();
			while (result.incrementToken()) {
				tokens.add(resultAttr.toString());
			}
			result.close();
			if (tokens.size() == 0) return null;
			Collection<Emit> emits = verbTree.parseText(String.join(" ", tokens));
			for (Emit e : emits) {
				DOCTYPE vType = (DOCTYPE) e.getCustomeData();
				if (vType == DOCTYPE.Aux) {
					if (emits.size() == 1) return null;
					continue;
				}
				return vType;
			}

		} catch (IOException e){
			logger.error("Error getVerbType: " + e.getMessage());
		}

		return DOCTYPE.Statement;
	}

	@Override
	public ArrayList<QTDocument> extractEntityMentions(QTExtract speaker) {
		ArrayList<QTDocument> quotes = new ArrayList<>();
		List<String> sents = getSentences();
		int numSent = sents.size();
		Set<String> entitiesFromNamedFound = new HashSet<>();

		for (int i = 0; i < numSent; i++) {
			final String orig = removePrnts(sents.get(i)).trim();
			final String origBefore = i == 0 ? title : removePrnts(sents.get(i - 1)).trim();

			String rawSent_curr = orig;
			String [] parts = rawSent_curr.split("\\s+");
			int numTokens = parts.length;
			if (numTokens < 6 || numTokens > 80) continue;
/*
            try {
                Files.write(Paths.get("snp500.txt"), (orig  +"\n").getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            }
*/

			Map<String, Collection<Emit>> name_match_curr = speaker.parseNames(orig);

			if (name_match_curr.size() == 0) {
				Map<String, Collection<Emit>> name_match_befr = speaker.parseNames(origBefore);
				for (Map.Entry<String, Collection<Emit>> entType : name_match_befr.entrySet()) {
					Collection<Emit> ent_set = entType.getValue();
					if (ent_set.size() != 1) continue;
					// simple co-ref for now
					if (parts[0].equalsIgnoreCase("él") || parts[0].equalsIgnoreCase("el") ||
							parts[0].equalsIgnoreCase("ella"))
					{
						Emit matchedName = ent_set.iterator().next();
						String keyword = matchedName.getKeyword();
						parts[0] = keyword;
						rawSent_curr = String.join(" ", parts);
						Emit shiftedEmit = new Emit(0, keyword.length() - 1, keyword, matchedName.getCustomeData());
						name_match_curr.put(entType.getKey(), ent_set);
						//		name_match_curr.add(shiftedEmit);
					}
				}
			}

			//if still no emit continue
			if (name_match_curr.size() == 0){
				continue;
			}


			QTDocument newQuote = getQuoteDoc(orig);
			//           if (name_match_curr.size() > 0) {
			List<ExtInterval> tagged = getNounAndVerbPhrases(rawSent_curr, parts);
			for (Map.Entry<String, Collection<Emit>> entType : name_match_curr.entrySet()){
				for (Emit matchedName : entType.getValue()) {
					//       Emit matchedName = name_match_curr.iterator().next();
					for (int j = 0; j < tagged.size(); j++) {
						ExtInterval ext = tagged.get(j);
						if (ext.overlapsWith(matchedName)) {
							//only if this is a noun type and next one is a verb!
							if (j + 1 < tagged.size()) {
								ExtInterval nextExt = tagged.get(j + 1);
								if (ext.getType().equals("N") && nextExt.getType().equals("V")) {
									DOCTYPE verbType = getVerbType(rawSent_curr.substring(nextExt.getStart(), nextExt.getEnd()));
									if (verbType == null) continue;
									NamedEntity ne = (NamedEntity) matchedName.getCustomeData();
									newQuote.addEntity(entType.getKey(), ne.getName());
									newQuote.setDocType(verbType);
								}
							} else {
								if (ext.getType().equals("N")) {
									DOCTYPE verbType = getVerbType(rawSent_curr);
									if (verbType == null) continue;
									NamedEntity ne = (NamedEntity) matchedName.getCustomeData();
									newQuote.addEntity(entType.getKey(), ne.getName());
									newQuote.setDocType(verbType);
								}
							}
						}
					}
				}
			}

			if (newQuote.getEntity() == null) {
				logger.debug("Entity is still null : " + orig);
				continue;
			}

			// if this entity is a child and parent hasn't been found then this entity should not be added
			//          if (!topEntitiesFound.contains(entity.getEntity().getName())){
			//              continue;
			//          }

			newQuote.setBody(origBefore + " " + orig);
			quotes.add(newQuote);
/*
			if (titles_emet.size()  != 0) {
				Iterator<Emit> it = titles_emet.iterator();
				while (it.hasNext()) {
					newQuote.addFacts("Title", it.next().getKeyword());
				}
			}
*/
		}
		return quotes;
	}
}
