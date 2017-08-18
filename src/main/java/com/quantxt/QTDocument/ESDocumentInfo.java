package com.quantxt.QTDocument;

import java.io.*;
import java.net.URL;
import java.util.*;

import com.google.gson.*;
import com.quantxt.SearchConcepts.Entity;
import com.quantxt.SearchConcepts.NamedEntity;
import com.quantxt.doc.QTDocument;
import com.quantxt.nlp.Speaker;
import com.quantxt.nlp.types.ExtInterval;
import com.quantxt.nlp.types.Tagger;
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
import org.tartarus.snowball.ext.SpanishStemmer;

import static com.quantxt.QTDocument.QTHelper.removePrnts;

public class ESDocumentInfo extends QTDocument {

	private static final Logger logger = LoggerFactory.getLogger(ESDocumentInfo.class);
	private static final Analyzer analyzer = new StandardAnalyzer();

	private static Trie statementWords = null;
	private static boolean initialized = false;
	private static CharArraySet stopwords = null;

	private static Trie verbTree   = null;
	private static Trie phraseTree = null;
	private static Trie titleTree  = null;
	private static Tagger tagger = null;

	private String rawText;
	private double score;

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

	public static void initTries(Entity[] entities,
								 InputStream phraseFile,
								 InputStream contextFile) throws IOException, ClassNotFoundException {
		if (phraseFile != null) {
			Trie.TrieBuilder phrase = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
			String line;
			int num = 0;
			BufferedReader br = new BufferedReader(new InputStreamReader(phraseFile, "UTF-8"));
			while ((line = br.readLine()) != null) {
				line = line.replaceAll("[_\\-]+", " ");
				line = line.replaceAll("[^A-Za-z\\s]+", "").trim();
				String[] parts = line.split("\\s+");
				if (parts.length > 4) continue;
				num++;
				phrase.addKeyword(line);
			}
			logger.info(num + " phrases loaded for tagging");
			phraseTree = phrase.build();
		}

		JsonParser parser = new JsonParser();

		//verbs
		Trie.TrieBuilder verbs = Trie.builder().onlyWholeWords().ignoreCase();

		URL url = Speaker.class.getClassLoader().getResource("es/context.json");

		byte[] contextArr = contextFile != null ? IOUtils.toByteArray(contextFile) :
				IOUtils.toByteArray(url.openStream());


		JsonElement jsonElement = parser.parse(new String(contextArr, "UTF-8"));
		JsonObject contextJson = jsonElement.getAsJsonObject();
		for (Map.Entry<String, JsonElement> entry : contextJson.entrySet()) {
			String context_key = entry.getKey();
			JsonArray context_arr = entry.getValue().getAsJsonArray();
			for (JsonElement e : context_arr) {
				String verb = e.getAsString().toLowerCase();
				//String verb = TextNormalizer.normalize(e.getAsString());
				verbs.addKeyword(verb, context_key);
			}
		}
		verbTree = verbs.build();

		//titles
		Trie.TrieBuilder titles = Trie.builder().onlyWholeWords().ignoreCase();
		byte[] commonArr = IOUtils.toByteArray(Speaker.class.getClassLoader().getResource("en/common.json").openStream());
		JsonElement commonosnElement = parser.parse(new String(commonArr, "UTF-8"));
		JsonObject commonosnObject = commonosnElement.getAsJsonObject();
		JsonElement titles_elems = commonosnObject.get("titles");
		if (titles_elems != null) {
			for (JsonElement elem : titles_elems.getAsJsonArray()) {
				//       String ttl = lp.normalize(elem.getAsString());
				String ttl = elem.getAsString();
				titles.addKeyword(ttl);
			}
		}
		titleTree = titles.build();
		logger.info("Spanish tries were created");
	}

	public static void init() throws Exception{
		if( initialized) return;

//		statementWords = Pattern.compile("(?i)\\bdijo|dice|añadió\\b");

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
		tagger = Tagger.load("es");
		initialized = true;
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
	public double [] getVectorizedTitle(){
		synchronized (tagger) {
			return tagger.getTextVec(title);
		}
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
		return statementWords.containsMatch(s);
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

	private QTDocument getQuoteDoc(String quote,
								   NamedEntity namedEntity)
	{
		quote = quote.replaceAll("^Advertisement\\s+", "").trim();
		QTDocument sDoc = new ESDocumentInfo("", quote);
		sDoc.setDate(getDate());
		sDoc.setLink(getLink());
		sDoc.setLogo(getLogo());
		sDoc.setSource(getSource());

		sDoc.setAuthor(namedEntity.getName());
		sDoc.setEntity(namedEntity.getEntity().getName());
		this.addTag(namedEntity.getName());
		sDoc.addTag(namedEntity.getName());
		this.addTag(namedEntity.getEntity().getName());
		sDoc.addTag(namedEntity.getEntity().getName());

		return sDoc;
	}

	@Override
	public ArrayList<QTDocument> extractEntityMentions() {
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

			Collection<Emit> name_match_curr = ENDocumentInfo.nameTree.parseText(orig);

			if (name_match_curr.size() == 0){
				Collection<Emit> name_match_befr = ENDocumentInfo.nameTree.parseText(origBefore);
				if (name_match_befr.size() != 1) continue;
				// simple co-ref for now
				if (parts[0].equalsIgnoreCase("él") || parts[0].equalsIgnoreCase("el") ||
						parts[0].equalsIgnoreCase("ella")) {
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

			Collection<Emit> titles_emet = titleTree.parseText(orig);

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
							entity = (NamedEntity) matchedName.getCustomeData();
							newQuote = getQuoteDoc(orig, entity);
							newQuote.setDocType(QTDocument.DOCTYPE.Action);
							break;
						}
					}
				}
				if (newQuote != null) break;
			}

			if (newQuote == null) {
				//If it is not an action then it is an statement and just pick the first found entity
				Emit matchedName = name_match_curr.iterator().next();
				entity = (NamedEntity) matchedName.getCustomeData();
				newQuote = getQuoteDoc(orig, entity);
				newQuote.setDocType(QTDocument.DOCTYPE.Statement);
			}
			//          }

			if (newQuote == null) {
				logger.debug("Entity is still null : " + orig);
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

	public static void main(String[] args) throws Exception {
		ESDocumentInfo.init();
		ESDocumentInfo.initTries(null, null, null);
		ENDocumentInfo.init();
		Entity entity = new Entity("Lilian Tintori", null, false);

		entity.addPerson("Lilian Tintori", null);

		Entity[] entities = new Entity[] {entity};

		ENDocumentInfo.initTries(entities, null, null);

		String in = "Lilian Tintori anunció este sábado en la noche en su cuenta en Twitter que su esposo, el líder opositor venezolano Leopoldo López, fue trasladado de nuevo de regreso a su casa.";
		QTDocument es = QTDocumentFactory.createQTDoct(in , in);
		es.processDoc();
		String out = es.normalize(in);
		logger.info("normalize: " + out);

		String [] pos = es.getPosTags(in.split("\\s+"));
		logger.info(String.join(" " , pos));

		ArrayList<QTDocument> docs = es.extractEntityMentions();
	}
}
