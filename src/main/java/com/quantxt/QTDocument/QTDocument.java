package com.quantxt.QTDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.quantxt.nlp.CategoryDetection;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;

import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;
import org.apache.log4j.Logger;

abstract public class QTDocument {

	private static Gson gson = new Gson();
	private static Logger logger = Logger.getLogger(QTDocument.class);

	protected String body;
	protected Set<String> persons;
	protected List<String> organizations = null;
	private List<String> ticker = null;
	protected List<String> sentences = new ArrayList<>();
//	protected List<String> statements = new ArrayList<>();
//	protected List<String> actions = new ArrayList<>();
	protected Map<Integer, Integer> topics;
	protected String sector;
	protected String industry;
//	protected List<Integer> topics;
//	protected String topics = null;
//	protected int id;
	protected String directLink;
	protected String origLink;
	protected String title;
	protected String englishTitle;
	private String date;
	private String language;
	private String region;
	private String sourceName;
	private String categories;
	private String author;
	private Set<String> tags = new HashSet<>();
	private String excerpt;
	private String logo;

	private static Translate translator;

//	private static CategoryDetection categoryDetection = new CategoryDetection();
	private static CategoryDetection categoryDetection = null;
	
	public QTDocument(String b, String t){
		body = b;
		title = t;
//		date = dateFormat.format(Calendar.getInstance().getTime());
		persons 		= new HashSet<>();
//		organizations 	= new ArrayList<String>();
//		ticker			= new ArrayList<String>();
//		statements		= new ArrayList<>();
		topics          = new HashMap<>();
//		topics          = new ArrayList<Integer>();
//		id = Math.abs(title.hashCode());
	}
	
	protected String Translate(String text, Language inLang, Language outLang) throws Exception{
		return "";
//		return Translate.execute(text, inLang, outLang);
	}
	
	protected abstract boolean isStatement(String s);

	protected String[] getTokens(String s) {
		return s.split("\\s+");
	}
	
	protected Span[] getSpan(String[] s, NameFinderME nf) {
		return nf.find(s);
	}

	protected void setSector(String s){
		sector = s;
	}

	protected void setExcerpt(String s){
		excerpt = s;
	}

	public void setBody(String b){
		body = b;
	}

	public void setCategories (String s){
		categories = s;
	}

	public void addTags (List<String> taglist){
		tags.addAll(taglist);
	}

	public void addTag (String tag){
		tags.add(tag);
	}
	
	protected void setIndustry(String s){
		industry = s;
	}

	public void setLogo(String s){
		logo = s;
	}
	
	protected void getSentenceNER(final String[] sentences,
								  final NameFinderME nameFinder,
								  final NameFinderME organizationFinder)
	{
		if (sentences.length > 0){
			excerpt = sentences[0];
		} else {
			logger.info(" --> " + title);
		}

	    for(String sentence: sentences) {

	    	String tokens[]             = getTokens(sentence);
	    	if (nameFinder != null){
		    	Span nameSpans[] 			= getSpan(tokens, nameFinder);
		    	for (Span ns : nameSpans)
		    	{
		    		String p = "";
		    		for (int i= ns.getStart(); i < ns.getEnd(); i++)
		    			p += tokens[i] + " ";
		    		p = p.substring(0, p.length() -1 );
		    		addPerson(p);
		    	}
		    }
	    	
	    	if (organizationFinder != null) {
		    	Span organizationSpans[] 	= getSpan(tokens, organizationFinder);
		    	for (Span os : organizationSpans)
		    	{
		    		String p = "";
		    		for (int i= os.getStart(); i < os.getEnd(); i++)
		    			p += tokens[i] + " ";
		    		p = p.substring(0, p.length() -1 );
		    		addOrganization(p);
		    	}
	    	}
 	    }
	    if (nameFinder != null) 
	    	nameFinder.clearAdaptiveData();
	    
	    if (organizationFinder != null)
	    	organizationFinder.clearAdaptiveData();
	    
	}
	
	
	public void setDate(String d){
		date = d;
	}
	public void addPerson(String p){
		persons.add(p);
	}
	
	public static void resetTranslatCred(){
		Translate.setClientId("2b70575a-116a-40db-9cc8-4b5192659506");
		Translate.setClientSecret("g184U2+B7fwftaoGzBDyb59KzYEKtulZZQZsnW71wj4=");
	}
	
	public static void init () throws Exception {
		ENDocumentInfo.init();
		ESDocumentInfo.init();
		NLDocumentInfo.init();
		FRDocumentInfo.init();
//		resetTranslatCred();
	}
	
	public void addOrganization(String o){
		if (organizations == null){
			organizations 	= new ArrayList<String>();
		}
		organizations.add(o);
	}
	
	public void addTicker(String o){
		if (ticker == null){
			ticker 	= new ArrayList<String>();
		}
		ticker.add(o);
	}


	
	public void addTopic(int t, double v){
//		Topic topic = new Topic(t,v);
//		topics.add(topic);
//		topics.add(t);
//		StringBuilder sb = new StringBuilder();
//		if (topics != null)
//			sb = sb.append(topics);
//		sb.append(t).append(",");
//		topics = sb.toString();
		
//		topics.put(t, v);

//		Integer val = 10 * t + (int)(10 * v);
//		topics.put(topics.size(), val);
		topics.put(topics.size(), t);
	}	
	
	public void setOrigLink (String l){
		origLink = l;
	}
	
	public void setDirectLink (String l){
		directLink = l;
	}

	public void setAuthor(String a){
		author = a;
	}
	
	public void setRegion(String r){
		region = r;
	}
	
	public void setLanguage(String l){
		language = l;
	}
	
	public void setSource(String s) {
		sourceName = s;
	}

	
	public String getTokenizedBody(){
		String b = body;
		b = b.toLowerCase();
		b = b.replaceAll("[^a-z0-9]+", " ");
		return b;
	}
	
	public String getBody(){
		return body;
	}

	public String getExcerpt(){
		return excerpt;
	}
	
	public Set<String> getPersons(){
		return persons;
	}
	
	public String getLanguage(){
		return language;
	}
	
	public List<String> getOrganizations(){
		return organizations;
	}
	
	public String getOrigLink(){
		return origLink;
	}
	
	public String getDirectLink(){
		return directLink;
	}

	public String getTitle(){
		return title;
	}
	
	public String getEnglishTitle(){
		return englishTitle;
	}

	public String getLogo(){return logo;}

//	public int getID() {return id;}

	public String getCategories() {
		if (categories == null) {
			return String.valueOf(categoryDetection.detectCategory(title));
//		logger.info("Get categories.. " + );
		}
		return categories;
	}

	public Set<String> getTags(){ return tags;}

	public String getDate() {return date;}

	public String getAuthor() {return author;}

	public List<String> getSentences(){
		return sentences;
	}

//	public List<String> getStatements(){
//		return statements;
//	}

//	public List<String> getActions(){
//		return actions;
//	}

	//interface to process document
	public void processDoc() throws Exception{
	}
	
	public String toString(){
		return gson.toJson(this);
	}

	public String string(){
		StringBuilder out = new StringBuilder();
		out.append("Line: ").append(directLink)
			.append("\nTitle: ")
			.append(title)
			.append("\nDate: ")
			.append(date);
		if (sentences.size() > 0)
			out.append("\nStatements: ").append(sentences.toString());
		if (persons.size() > 0)
			out.append("\nPerson: ").append(persons.toString());
		if (organizations != null)
			out.append("\nOrganization: ").append(organizations.toString());
//		if (topics.size() > 0)
//		{
//			out.append("\nTopics: ");
//			for (Topic t : topics)
//				out.append("{").append(t.topic).append(":").append(t.val).append("}");
//			for (Map.Entry<Integer, Double> entry : topics.entrySet())
//				out.append("{").append(entry.getKey()).append(":").append(entry.getValue()).append("}");
//		}
		return out.toString();
	}

	public String getWPDocument(){
		JsonObject json = new JsonObject();
//		json.addProperty("ID", id);
//		json.addProperty("post_id", id);
		json.addProperty("post_date", date);
		json.addProperty("post_date_gmt", date);
		json.addProperty("post_title", title);
		json.addProperty("post_content", body);
		json.addProperty("post_status", "publish");
		json.addProperty("post_name", title);
		json.addProperty("post_type", "post");
		json.addProperty("permalink", directLink);
		return json.toString();
	}
}