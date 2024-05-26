package com.quantxt.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.quantxt.model.ExtInterval;
import com.quantxt.model.Interval;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class QSpan {

    public enum EXTBOXType {HORIZENTAL_ONE, HORIZENTAL_MANY, VERTICAL_ONE_BELOW, VERTICAL_ONE_ABOVE, VERTICAL_MANY}
    protected EXTBOXType spanType;

    private String category;
    private String dict_name;
    private String dict_id;
    protected float top = 100000;   // starty
    protected float base = -1;  // endy
    protected float left = 100000;  // startx
    protected float right = -1; // endx
    protected float area = -1; // endx
    /*
        occupied area exclduing white paddings
    */
    protected float occ_area = -1; // //occupied area exclduing white pads
    protected int num_lines = 1; // endx
//    protected transient List<BaseTextBox> childs = new ArrayList<>();
    protected String line_str;

    private List<Interval> keys = new ArrayList<>();
    private List<Interval> values = new ArrayList<>();

    public QSpan(){
    }

    public QSpan(ExtInterval e) {
        dict_name = e.getDict_name();
        dict_id = e.getDict_id();
        category = e.getCategory();
        add(e);
    }

    public void add(Interval e){
        keys.add(e);
        keys.sort(Comparator.comparingInt(o -> o.getStart()));
    }

    public int size(){
        return keys.size();
    }

    public void process(String content){

        HashSet<Integer> lines = new HashSet<>();
        for (Interval ext : keys){
            lines.add(ext.getLine());
            List<BaseTextBox> tbList = ext.getTextBoxes();
            if (tbList == null) continue;
            //get the largest fitting box
            for (BaseTextBox tb : tbList) {
                top = Math.min(tb.getTop(), top);
                left = Math.min(tb.getLeft(), left);
                right = Math.max(tb.getRight(), right);
                base = Math.max(tb.getBase(), base);
            }
        }
        num_lines = lines.size();
    }

    @JsonIgnore
    public ExtInterval getExtInterval(){
        List<BaseTextBox> tbList = new ArrayList<>();
        for (ExtIntervalTextBox eitb : extIntervalTextBoxes){
            tbList.add(eitb.getTextBox());
        }

        ExtInterval extInterval = getExtInterval(true);
        extInterval.setTextBoxes(tbList);
        return extInterval;
    }
    public ExtInterval getExtInterval(boolean useLocalLineStart){
        ExtInterval extInterval = new ExtInterval();
        extInterval.setDict_name(extIntervalTextBoxes.get(0).getExtInterval().getDict_name());
        extInterval.setDict_id(extIntervalTextBoxes.get(0).getExtInterval().getDict_id());
        extInterval.setStr(str);
        extInterval.setCategory(extIntervalTextBoxes.get(0).getExtInterval().getCategory());
        if (useLocalLineStart){
            extInterval.setStart(start);
            extInterval.setEnd(end);
        } else {
            // find unique lines
            HashSet<Integer> uLines = new HashSet<>();
            extInterval.setStart(extIntervalTextBoxes.get(0).getExtInterval().getStart());
            for (ExtIntervalTextBox etb : extIntervalTextBoxes){
                uLines.add(etb.getExtInterval().getLine());
            }
            if (uLines.size() == 1){
                extInterval.setEnd(extIntervalTextBoxes.get(extIntervalTextBoxes.size()-1).getExtInterval().getEnd());
            } else {
                extInterval.setEnd(extIntervalTextBoxes.get(0).getExtInterval().getEnd());
            }
        }
        extInterval.setLine(line);
        List<BaseTextBox> tbList = new ArrayList<>();
        for (ExtIntervalTextBox eitb : extIntervalTextBoxes){
            tbList.add(eitb.getTextBox());
        }
        extInterval.setTextBoxes(tbList);
        extInterval.setExtIntervalSimples(getExtIntervalSimples());

        return extInterval;
    }

    public BaseTextBox getTextBox(){
        if (base < 0 || right < 0) return null;
        BaseTextBox tb = new BaseTextBox(top, base, left, right, str);
        if (line != null) {
            tb.setLine(line);
        }
    //    tb.setChilds(childs);
        tb.setLine_str(line_str);
        return tb;
    }


    public float getTop() {
        return top;
    }

    public float getBase() {
        return base;
    }

    public float getLeft() {
        return left;
    }

    public float getRight() {
        return right;
    }

    public String getLine_str() {
        return line_str;
    }

    public void setLine_str(String line_str) {
        this.line_str = line_str;
    }

    public EXTBOXType getSpanType() {
        return spanType;
    }

    public void setSpanType(EXTBOXType spanType) {
        this.spanType = spanType;
    }

    public void setTop(float top) {
        this.top = top;
    }

    public void setBase(float base) {
        this.base = base;
    }

    public void setLeft(float left) {
        this.left = left;
    }

    public void setRight(float right) {
        this.right = right;
    }

    public float getArea() {
        return area;
    }

    public void setArea(float area) {
        this.area = area;
    }

    public int getNum_lines() {
        return num_lines;
    }

    public float getOcc_area() {
        return occ_area;
    }

    public void setOcc_area(float occ_area) {
        this.occ_area = occ_area;
    }

    public int getStart(){
        return keys.get(0).getStart();
    }

    public int getEnd(){
        return keys.get(keys.size() -1).getEnd();
    }

    public List<Interval> getKeys(){
        return keys;
    }

    public String getStr(){
        List<String> list = new ArrayList<>();
        for (Interval k : keys) {
            list.add(k.getStr());
        }
        return String.join(" ", list);
    }

    public int getLine(){
        return keys.get(0).getLine();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDict_name() {
        return dict_name;
    }

    public void setDict_name(String dict_name) {
        this.dict_name = dict_name;
    }

    public String getDict_id() {
        return dict_id;
    }

    public void setDict_id(String dict_id) {
        this.dict_id = dict_id;
    }

    public void setNum_lines(int num_lines) {
        this.num_lines = num_lines;
    }

    public void setKeys(List<Interval> keys) {
        this.keys = keys;
    }

    public List<Interval> getValues() {
        return values;
    }

    public void setValues(List<Interval> values) {
        this.values = values;
    }

}
