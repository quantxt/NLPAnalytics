package com.quantxt.QTDocument;

import java.io.*;
import java.net.URL;
import java.util.*;

import com.google.gson.*;
import com.quantxt.SearchConcepts.NamedEntity;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.nlp.Speaker;
import com.quantxt.nlp.types.ExtInterval;
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
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.quantxt.QTDocument.QTHelper.removePrnts;

public class ENDocumentInfo extends QTDocument {

	private static final Logger logger = LoggerFactory.getLogger(ENDocumentInfo.class);
	private static final Analyzer analyzer = new StandardAnalyzer();

	private String rawText;

	private static SentenceDetectorME sentenceDetector = null;
	private static POSTaggerME posModel = null;
	private static CharArraySet stopwords = null;

	private static Trie verbTree   = null;
	
	public ENDocumentInfo (String body, String title) {
		super(body, title);
		language = Language.ENGLISH;
	}

	public ENDocumentInfo (Elements body, String title) {
		super(body.html(), title);
		rawText = body.text();
		language = Language.ENGLISH;
	}

	public static boolean isStopWord(String p){
		return stopwords.contains(p);
	}

	public static void init(InputStream contextFile) throws Exception{
		//Already initialized
		if (sentenceDetector != null) return;

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
		JsonParser parser = new JsonParser();
		Trie.TrieBuilder verbs = Trie.builder().onlyWholeWords().ignoreCase();

		url = Speaker.class.getClassLoader().getResource("en/context.json");

		byte[] contextArr = contextFile != null ? IOUtils.toByteArray(contextFile) :
				IOUtils.toByteArray(url.openStream());


		JsonElement jsonElement = parser.parse(new String(contextArr, "UTF-8"));
		JsonObject contextJson = jsonElement.getAsJsonObject();
		for (Map.Entry<String, JsonElement> entry : contextJson.entrySet()) {
			String context_key = entry.getKey();
			DOCTYPE verbTybe = DOCTYPE.Statement;
			switch (context_key) {
				case "Speculation" : verbTybe = DOCTYPE.Speculation;
					break;
				case "Action" : verbTybe = DOCTYPE.Action;
					break;
				case "Partnership" : verbTybe = DOCTYPE.Partnership;
					break;
				case "Legal" : verbTybe = DOCTYPE.Legal;
					break;
				case "Acquisition" : verbTybe = DOCTYPE.Acquisition;
					break;
				case "Production" : verbTybe = DOCTYPE.Production;
					break;
				case "Aux" : verbTybe = DOCTYPE.Aux;
					break;
			}
			JsonArray context_arr = entry.getValue().getAsJsonArray();
			for (JsonElement e : context_arr) {
				String verb = e.getAsString();
				TokenStream result = analyzer.tokenStream(null, verb);
				result = new SnowballFilter(result, "English");
				CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
				result.reset();

				List<String> tokens = new ArrayList<>();
				while (result.incrementToken()) {
					tokens.add(resultAttr.toString());
				}
				result.close();
				verbs.addKeyword(String.join(" ", tokens), verbTybe);
			}
		}
		verbTree = verbs.build();
		logger.info("English models initiliazed");
	}
	
	public double[] getTopicVec (String text){
		if (text == null || text.isEmpty())
			return null;
		//////remove this
		return null;
	}

	public static synchronized ArrayList<String> stemmer(String str){
		ArrayList<String> postEdit = new ArrayList<>();

		try {
			TokenStream stream  = analyzer.tokenStream(null, new StringReader(str));
			stream = new StopFilter(stream, stopwords);
			//	stream = new SnowballFilter(stream, new SpanishStemmer());
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
//		string = string.replaceAll("\\\\\"","\"");
//		string = string.replaceAll("\\\\n","");
//		string = string.replaceAll("\\\\r","");
//		string = string.replaceAll("\\\\t","");
//		string = string.replaceAll("[\\&\\!\\“\\”\\$\\=\\>\\<_\\'\\’\\-\\—\"\\‘\\.\\/\\(\\),?;:\\*\\|\\]\\[\\@\\#\\s+]+", " ");
//		string = string.replaceAll("\\b\\d+\\b", "");
//		string = string.replaceAll(" " , " ");
	//	string = string.toLowerCase().trim();

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

	private static boolean isTagDC(String tag){
		return tag.equals("IN") || tag.equals("TO") || tag.equals("CC") || tag.equals("DT");
	}

	@Override
    public boolean isStatement(String s) {
		return false;
	}


	//https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
	protected List<ExtInterval> getNounAndVerbPhrases(String orig,
													  String[] parts){
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
						(type.equals("N") && nextTag.startsWith("NN") ) ||
						(type.equals("V") && nextTag.startsWith("VB") ))
				{
					tokenList.add(word);
				}
				continue;
			}
			if (tag.startsWith("NN")){
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

	private QTDocument getQuoteDoc(String quote,
								   NamedEntity namedEntity)
	{
		quote = quote.replaceAll("^Advertisement\\s+", "").trim();
		QTDocument sDoc = new ENDocumentInfo("", quote);
		sDoc.setDate(getDate());
		sDoc.setLink(getLink());
		sDoc.setLogo(getLogo());
		sDoc.setSource(getSource());

		if (namedEntity != null) {
			sDoc.setAuthor(namedEntity.getName());
			sDoc.setEntity(namedEntity.getEntity().getName());
			this.addTag(namedEntity.getName());
			sDoc.addTag(namedEntity.getName());
			this.addTag(namedEntity.getEntity().getName());
			sDoc.addTag(namedEntity.getEntity().getName());
		}

		return sDoc;
	}

	protected DOCTYPE getVerbType(String verbPhs) {
		TokenStream result = analyzer.tokenStream(null, verbPhs);

		try {
			result = new SnowballFilter(result, "English");
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


	public ArrayList<QTDocument> extractEntityMentionsV2(QTExtract speaker) {
		ArrayList<QTDocument> quotes = new ArrayList<>();
		List<String> sents = getSentences();
		int numSent = sents.size();
		Set<String> entitiesFromNamedFound = new HashSet<>();
		//       Set<String> topEntitiesFound = new HashSet<>();

 /*       Collection<Emit> ttl_names = nameTree.parseText(doc.getTitle());
        for (Emit emt : ttl_names){
            NamedEntity ne = (NamedEntity) emt.getCustomeData();
            if (ne.isParent()){
                topEntitiesFound.add(ne.getName());
            }
        }
*/
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

			Collection<Emit> name_match_curr = speaker.parseNames(orig);

			if (name_match_curr.size() == 0){
				Collection<Emit> name_match_befr = speaker.parseNames(origBefore);
				if (name_match_befr.size() != 1) continue;
				// simple co-ref for now
				if (parts[0].equalsIgnoreCase("he") || parts[0].equalsIgnoreCase("she")) {
					Emit matchedName = name_match_befr.iterator().next();
					String keyword = matchedName.getKeyword();
					parts[0] = keyword;
					rawSent_curr = String.join(" " , parts);
					Emit shiftedEmit = new Emit(0, keyword.length()-1, keyword, matchedName.getCustomeData());
					name_match_curr.add(shiftedEmit);
				}
			}

			//if still no emit continue
			if (name_match_curr.size() == 0){
				continue;
			}

			Collection<Emit> titles_emet = speaker.parseTitles(orig);

			List<Emit> allemits = new ArrayList<>();
			allemits.addAll(name_match_curr);

			NamedEntity entity = null;

			QTDocument newQuote = null;
			//           if (name_match_curr.size() > 0) {
			List<ExtInterval> tagged = getNounAndVerbPhrases(rawSent_curr, parts);
			for (Emit matchedName : name_match_curr) {
				//       Emit matchedName = name_match_curr.iterator().next();
				for (int j = 0; j < tagged.size(); j++) {
					ExtInterval ext = tagged.get(j);
					if (ext.overlapsWith(matchedName)) {
						//only if this is a noun type and next one is a verb!
						if (j == tagged.size() - 1) break;
						ExtInterval nextExt = tagged.get(j + 1);
						if (ext.getType().equals("N") && nextExt.getType().equals("V")) {
							DOCTYPE verbType = getVerbType(rawSent_curr.substring(nextExt.getStart(), nextExt.getEnd()));
							if (verbType == null) continue;
							entity = (NamedEntity) matchedName.getCustomeData();
							newQuote = getQuoteDoc(orig, entity);
							newQuote.setDocType(verbType);
							break;
						}
					}
				}
				if (newQuote != null) break;
			}

			/*
			TODO: enable this?
			if (newQuote == null) {
				//If it is not an action then it is an statement and just pick the first found entity
				Emit matchedName = name_match_curr.iterator().next();
				entity = (NamedEntity) matchedName.getCustomeData();
				newQuote = getQuoteDoc(orig, entity);
				newQuote.setDocType(QTDocument.DOCTYPE.Statement);
			}
			*/
			//          }

			if (newQuote == null) {
				logger.debug("Entity is still null or Verb type is not detected: " + orig);
				continue;
			}

			entitiesFromNamedFound.add(entity.getEntity().getName());

			// if this entity is a child and parent hasn't been found then this entity should not be added
			//          if (!topEntitiesFound.contains(entity.getEntity().getName())){
			//              continue;
			//          }

			newQuote.setBody(origBefore + " " + orig);
			quotes.add(newQuote);

			if (titles_emet.size()  != 0) {
				Iterator<Emit> it = titles_emet.iterator();
				while (it.hasNext()) {
					newQuote.addFacts("Title", it.next().getKeyword());
				}
			}
		}
		return quotes;
	}

	@Override
	public ArrayList<QTDocument> extractEntityMentions(QTExtract speaker) {
		ArrayList<QTDocument> quotes = new ArrayList<>();
		List<String> sents = getSentences();
		int numSent = sents.size();

		for (int i = 0; i < numSent; i++) {
			final String orig = removePrnts(sents.get(i)).trim();
			final String origBefore = i == 0 ? title : removePrnts(sents.get(i - 1)).trim();

			String rawSent_curr = orig;
			String [] parts = rawSent_curr.split("\\s+");
			int numTokens = parts.length;
			if (numTokens < 6 || numTokens > 80) continue;

			ArrayList<Emit> name_match_curr = new ArrayList<>(speaker.parseNames(orig));

			if (name_match_curr.size() == 0){
				Collection<Emit> name_match_befr = speaker.parseNames(origBefore);
				if (name_match_befr.size() == 1) {
					// simple co-ref for now
					if (parts[0].equalsIgnoreCase("he") || parts[0].equalsIgnoreCase("she")) {
						Emit matchedName = name_match_befr.iterator().next();
						String keyword = matchedName.getKeyword();
						parts[0] = keyword;
						rawSent_curr = String.join(" ", parts);
						Emit shiftedEmit = new Emit(0, keyword.length() - 1, keyword, matchedName.getCustomeData());
						name_match_curr.add(shiftedEmit);
					}
				}
			}

			Collection<Emit> titles_emet = speaker.parseTitles(orig);

			List<Emit> allemits = new ArrayList<>();
			allemits.addAll(name_match_curr);

			NamedEntity entity = (name_match_curr != null && name_match_curr.size() > 0) ?
					(NamedEntity) name_match_curr.get(0).getCustomeData() :
					null;

			QTDocument newQuote = null;
			List<ExtInterval> tagged = getNounAndVerbPhrases(rawSent_curr, parts);

			for (int j = 0; j < tagged.size(); j++) {
				ExtInterval ext = tagged.get(j);
				//only if this is a noun type and next one is a verb!
				if (j == tagged.size() - 1) break;
				ExtInterval nextExt = tagged.get(j + 1);
				if (ext.getType().equals("N") && nextExt.getType().equals("V")) {
					DOCTYPE verbType = getVerbType(rawSent_curr.substring(nextExt.getStart(), nextExt.getEnd()));
					if (verbType == null) continue;
					newQuote = getQuoteDoc(orig, entity);
					newQuote.setDocType(verbType);
					break;
				}
			}

			if (newQuote == null) {
				logger.debug("Entity is still null or Verb type is not detected: " + orig);
				continue;
			}

			newQuote.setBody(origBefore + " " + orig);
			quotes.add(newQuote);

			if (titles_emet.size()  != 0) {
				Iterator<Emit> it = titles_emet.iterator();
				while (it.hasNext()) {
					newQuote.addFacts("Title", it.next().getKeyword());
				}
			}
		}
		return quotes;
	}
}

