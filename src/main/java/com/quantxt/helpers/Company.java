package com.quantxt.helpers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class Company {
	
	public enum MarketCap {
		LARGECAP, MIDCAP, SMALLCAP
	}
	private static String [] commons = {"Inc.", "Corporation.", "Corporation", "Fund", "Trust", 
		"Group", "Inc" , "Holdings" ,"Ltd.", "Corp.", "Limited", 
		"Corp", "CO." , "corp", "llc", "LLC", "L.L.C.", 
		"L.P.", "S.A.", "N.V.", "LP" , "plc", "C.V.", "inc.", "Ltd" , 
		"SE" , "Company" , "p.l.c", "NV", "A/S", "Systems" , "S.P.A", "Incorporated",
		"Holding" , "INC" , "FUND" , "INCOME", "GP", "INC.", "PLC", "Co", "p.l.c.", "SA",
		"LTD", "Co.", "plc.", "AG", "LP.", " Plc"};
	private static HashMap<String, String> manualList = new HashMap(){{
		put("Ace", "Ace Limited");
		put("AOS" , "AO Smith");
		put("ARMH", "ARM Holdings");
		put("AXE", "Anixter");
		put("BAX" , "Baxter");
		put("BK", "Mellon");
		put("BPOP","Popular, Inc.");
		put("CA","CA Inc.");
		put("COST", "Costco");
		put("DAL", "Delta");
		put("DAN", "Dana Holding");
		put("DATA", "Tableau");
		put("DDD", "3D Systems");
		put("DWA", "Dreamworks");
		put("ENI", "Enersis");
		put("F", "Ford");
		put("FMX", "Fomento Economico Mexicano");
		put("MFG", "Mizuho");
		put("NWSA", "News Corporation");
		put("NWS", "News Corporation");
		put("POST", "Post Holdings");
		put("Pool" , "Pool Corporation");
		put("RUK", "Elsevier");
		put("SNE", "Sony");
		put("TMUS", "T-Mobile");
		put("TSLA", "Tesla");
		put("UNT" , "Unit Corporation");
		put("WSTC", "West Corporation");
		put("WUBA", "58.com");
		put("WWWW", "Web.com");
		put("YHOO", "Yahoo");
		put("HTZ", "Hertz");
		put("HTZ", "Hertz");
		put("HTZ", "Hertz");
		put("HTZ", "Hertz");
		}};
	private static final Set<String> commonSet = new HashSet<String>(Arrays.asList(commons));
//	private static final Set<String> manualSet = new HashSet<String>(Arrays.asList(manualList));
	private String company;
	private String ticker;
	private String sector;
	private String industry;
	private String searchableName;
	private Pattern comRegex;
	private double marketCap;
	public Company(String n, String t, double m, String sc, String in){
		company = n.trim();
		ticker = t.trim();
		sector = sc.trim();
		industry  = in.trim();
		marketCap = m;
		
//		System.out.println("before: " + company);
		if (manualList.containsKey(ticker)){
			searchableName = manualList.get(ticker);
		} else {
			searchableName = getCompName(company);
		}
		System.out.println(ticker + "\t" + company + "\t ---> " + searchableName);
//		comRegex = Pattern.compile("\\b" + name +"\\b");
	}
	
	private String getCompName(String n){
		n = n.replaceAll(",[^,]+$", "");
		n = n.replaceAll("and Company", "");
		n = n.replaceAll("\\& Company", "");
		n = n.replaceAll("\\& Co", "");
		n = n.replaceAll("\\.com", "");
		n = n.replaceAll("\\([^\\)]+\\)", "");
		StringBuilder sb = new StringBuilder();
		String[] tokens = n.replaceAll("\\([^\\)]+\\)", "").split("\\s+");
//		for (String p : commons){
//			String pattern = "[\\.\\,\\s]+" + p + "\\s*$";
//			n = n.replaceAll(pattern, "");
		for (String t:tokens){
			if (commonSet.contains(t))
				continue;
			sb.append(t).append(" ");
		}
//		}
		String name = sb.toString().trim();
		name = name.replaceAll("[\\.\\,\\']$", "");
		return name;
//		return n;
		
	}
	public Pattern getRegex(){
		return comRegex;
	}
	
	public String getName(){
		return company;
	}
	
	public Boolean isMatch(String s){
		return s.indexOf(searchableName) != -1;
	}
	
	public String getTicker(){
		return ticker;
	}
	
	public String getSector(){
		return sector;
	}
	
	public String getIndustry(){
		return industry;
	}
	
	public MarketCap type(){
		if (marketCap > 50000000000d)
			return MarketCap.LARGECAP;
		if (marketCap > 1000000000d)
			return MarketCap.MIDCAP;
		return MarketCap.SMALLCAP;
	}
}
