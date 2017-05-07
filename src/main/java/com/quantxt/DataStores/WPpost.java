package com.quantxt.DataStores;

import com.quantxt.doc.QTDocument;
import org.joda.time.DateTime;

import java.util.ArrayList;

/**
 * Created by matin on 10/9/16.
 */

public class WPpost {

    private static ArrayList<QTDocument> ALL_POSTS = new ArrayList<>();

    private String post_content;
    private String post_title;
    private DateTime post_date;
    private Long logo_id;
    private String id;
    private String link;

    private ArrayList<String> similars = new ArrayList<>();

    public WPpost(String pt, String pc, DateTime pd){
        post_title   = pt;
        post_content = pc;
        post_date    = pd;
    }

    @Override
    public int hashCode()
    {
        if (id != null){
            return id.hashCode();
        }
        return post_title.hashCode() * post_date.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        WPpost p = (WPpost) o;
        if (p.post_date == null || post_title == null) return false;

        if (this.id != null && p.id != null){
            return this.id.equals(p.id);
        }
        long d1 = this.post_date.getMillis();
        long d2 = p.post_date.getMillis();

        return this.post_title.equals(p.post_title) &&
                d1 == d2;
    }

    public void setId(long i ){id = String.valueOf(i);}
    public void setId(String i ){id = i;}
    public void setLink(String i ){link = i;}
    public void addSimilars(String s ){similars.add(s);}

    public String getPost_title(){return post_title;}
    public String getPost_content(){return post_content;}
    public DateTime getPost_date(){return post_date;}
    public Long getId(){return Long.parseLong(id);}
    public String getStrId(){return id;}
    public String getLink(){return link;}
    public ArrayList<String> getSimilars(){return similars;}

    public void addPost(){
        /*ALL_POSTS.add(this);*/
    }

    public static int getNumPosts(){
        return ALL_POSTS.size();
    }
    public static void clear(){ALL_POSTS.clear();}

    public static ArrayList<QTDocument> getAllPosts(){
        /*return ALL_POSTS;*/
        return null;
    }
}
