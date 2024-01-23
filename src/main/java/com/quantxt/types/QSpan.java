package com.quantxt.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.quantxt.model.ExtInterval;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class QSpan extends ExtInterval {

    public enum EXTBOXType {HORIZENTAL_ONE, HORIZENTAL_MANY, VERTICAL_ONE_BELOW, VERTICAL_ONE_ABOVE, VERTICAL_MANY}

    protected EXTBOXType spanType;
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
    protected transient List<BaseTextBox> childs = new ArrayList<>();
    protected String line_str;

    private List<ExtIntervalTextBox> extIntervalTextBoxes = new ArrayList<>();

    public QSpan(){
        super();
    }

    public QSpan(ExtIntervalTextBox e){
        super();
        this.start = e.getExtInterval().getStart();
        this.end = e.getExtInterval().getEnd();
        this.line = e.getExtInterval().getLine();
        this.str = e.getExtInterval().getStr();
        this.setDict_id(e.getExtInterval().getDict_id());
        this.setDict_name(e.getExtInterval().getDict_name());
        this.setCategory(e.getExtInterval().getCategory());
        add(e);
        if (e.getTextBox() != null) {
            top = e.getTextBox().getTop();
            left = e.getTextBox().getLeft();
            right = e.getTextBox().getRight();
            base = e.getTextBox().getBase();
            childs = e.getTextBox().getChilds();
            line_str = e.getTextBox().getLine_str();
        }
    }

    public List<ExtIntervalTextBox> getExtIntervalTextBoxes() {
        return extIntervalTextBoxes;
    }

    public void add(ExtIntervalTextBox e){
        extIntervalTextBoxes.add(e);
    }

    public int size(){
        return extIntervalTextBoxes.size();
    }

    public void process(String content){
        StringBuilder sb = new StringBuilder();
        extIntervalTextBoxes.sort(Comparator.comparingInt(o -> o.getExtInterval().getStart()));

        HashSet<Integer> lines = new HashSet<>();
        for (ExtIntervalTextBox ext : extIntervalTextBoxes){
            sb.append(ext.getExtInterval().getStr()).append(" ");
            start = Math.min(ext.getExtInterval().getStart(), start);
            end = Math.max(ext.getExtInterval().getEnd(), end);
            lines.add(ext.getExtInterval().getLine());
            BaseTextBox baseTextBox = ext.getTextBox();
            if (baseTextBox == null) continue;
            //get the largest fitting box
            top = Math.min(baseTextBox.getTop(), top);
            left = Math.min(baseTextBox.getLeft(), left);
            right = Math.max(baseTextBox.getRight(), right);
            base = Math.max(baseTextBox.getBase(), base);
        }
        num_lines = lines.size();
        str = sb.toString().trim();
        if (content != null) {
            LineInfo lineInfo = new LineInfo(content, extIntervalTextBoxes.get(0).getExtInterval());
            line = lineInfo.getLineNumber();
        }
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
        tb.setChilds(childs);
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

    public List<BaseTextBox> getChilds() {
        return childs;
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

    public void setExtIntervalTextBoxes(List<ExtIntervalTextBox> extIntervalTextBoxes) {
        this.extIntervalTextBoxes = extIntervalTextBoxes;
    }

    public void setChilds(List<BaseTextBox> childs) {
        this.childs = childs;
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
}
