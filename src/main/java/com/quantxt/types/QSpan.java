package com.quantxt.types;

import com.quantxt.model.ExtInterval;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;

import java.util.*;

public class QSpan extends ExtInterval {

    public enum EXTBOXType {HORIZENTAL_ONE, HORIZENTAL_MANY, VERTICAL_ONE_BELOW, VERTICAL_ONE_ABOVE, VERTICAL_MANY}

    protected EXTBOXType spanType;

    protected float top = 100000;   // starty
    protected float base = -1;  // endy
    protected float left = 100000;  // startx
    protected float right = -1; // endx
    protected transient List<BaseTextBox> childs = new ArrayList<>();
    protected String line_str;

    private Map<BaseTextBox, Double> neighbors = new HashMap<>();
    private List<ExtIntervalTextBox> extIntervalTextBoxes = new ArrayList<>();

    public QSpan(ExtIntervalTextBox e){
        super();
        this.start = e.getExtInterval().getStart();
        this.end = e.getExtInterval().getEnd();
        this.line = e.getExtInterval().getLine();
        this.str = e.getExtInterval().getStr();
        this.setDict_id(e.getExtInterval().getDict_id());
        this.setDict_name(e.getExtInterval().getDict_name());
        this.setCategory(e.getExtInterval().getCategory());
        extIntervalTextBoxes.add(e);
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

    public void setExtIntervalTextBoxes(List<ExtIntervalTextBox> extIntervalTextBoxes) {
        this.extIntervalTextBoxes = extIntervalTextBoxes;
    }

    public void add(ExtIntervalTextBox e){
        extIntervalTextBoxes.add(e);
    }

    public int size(){
        if (extIntervalTextBoxes == null) return 0;
        return extIntervalTextBoxes.size();
    }


    public void process(String content){
        StringBuilder sb = new StringBuilder();

        for (ExtIntervalTextBox ext : extIntervalTextBoxes){
            sb.append(ext.getExtInterval().getStr()).append(" ");
            start = Math.min(ext.getExtInterval().getStart(), start);
            end = Math.max(ext.getExtInterval().getEnd(), end);

            BaseTextBox baseTextBox = ext.getTextBox();
            if (baseTextBox == null) continue;
            //get the largest fitting box
            top = Math.min(baseTextBox.getTop(), top);
            left = Math.min(baseTextBox.getLeft(), left);
            right = Math.max(baseTextBox.getRight(), right);
            base = Math.max(baseTextBox.getBase(), base);
        }

        str = sb.toString().trim();
        if (content != null) {
            LineInfo lineInfo = new LineInfo(content, extIntervalTextBoxes.get(0).getExtInterval());
            line = lineInfo.getLineNumber();
        }
    }

    public ExtInterval  getExtInterval(boolean useLocalLineStart){
        ExtInterval extInterval = new ExtInterval();
        extInterval.setDict_name(extIntervalTextBoxes.get(0).getExtInterval().getDict_name());
        extInterval.setDict_id(extIntervalTextBoxes.get(0).getExtInterval().getDict_id());
        extInterval.setStr(str);
        extInterval.setCategory(extIntervalTextBoxes.get(0).getExtInterval().getCategory());
        if (useLocalLineStart){
            extInterval.setStart(start);
            extInterval.setEnd(end);
        } else {
            extInterval.setStart(extIntervalTextBoxes.get(0).getExtInterval().getStart());
            extInterval.setEnd(extIntervalTextBoxes.get(0).getExtInterval().getEnd());
        }
        extInterval.setLine(line);
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


    public Map<BaseTextBox, Double> getNeighbors() {
        return neighbors;
    }

    public void setNeighbors(Map<BaseTextBox, Double> neighbors) {
        this.neighbors = neighbors;
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
}
