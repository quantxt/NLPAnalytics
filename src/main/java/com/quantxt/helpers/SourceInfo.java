package com.quantxt.helpers;

import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class SourceInfo {
	private static Logger logger = Logger.getLogger(SourceInfo.class);
	private String language;
	private String region;
	private String sourceName;
	private String [] links;
	private String domain;
	private String dateField;
	private String dateFormat;
	private String bodyField;
	private String authorField;
	private int numberCrawler;
	private int crawlDepth;
	private String toVisit;
	private String shouldVisit;
	private String category;
	private List<String> tags;
	private List<BasicHeader> header;
	private HashMap<String, String> body;
	private String filters;
	
	public SourceInfo(String l, String r, String s, String [] li, String af, String bf) throws URISyntaxException {
		language = l;
		region   = r;
		sourceName = s;
		links = li;
		authorField = af;
		bodyField = bf;
	}

	public String [] getLinks(){
		return links;
	}
	
	public String getLang(){
		return language;
	}
	
	public String getAuthorField(){
		return authorField;
	}
	
	public String getBodyField(){
		return bodyField;
	}

	public String getHost() throws URISyntaxException {
		URI uri = new URI(links[0]);
		domain = uri.getHost();
		return domain;
	}

	public void setCategory(String s){
		category = s;
	}

	public void addTags(String s){
		if (tags == null){
			tags = new ArrayList<>();
		}
		tags.add(s);
	}

	public boolean toKeep(String link){
		if (toVisit == null) return true;
		Pattern p = Pattern.compile(toVisit);
		return p.matcher(link).find();
	}

	public boolean shoudVisit(String link){
		if (shouldVisit == null) return true;
		Pattern p = Pattern.compile(shouldVisit);
		return p.matcher(link).find();
	}

	public int getNumberCrawler(){
		return numberCrawler;
	}
	public int getCrawlDepth(){
		return crawlDepth;
	}
	public String getRegion(){
		return region;
	}
	public String getSourceName(){
		return sourceName;
	}
	public String getFilters(){return filters;}


	public String getCategory(){
		return category;
	}
	public List<String> getTags(){
		return tags;
	}

	public List getHader(){
		return header;
	}
	public HashMap getBody(){
		return body;
	}
	public String getDateField(){return  dateField;}
	public String getDateFormat(){return  dateFormat;}
}
