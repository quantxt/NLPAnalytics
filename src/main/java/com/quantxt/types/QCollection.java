package com.quantxt.types;

import com.quantxt.model.Interval;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.quantxt.types.QSpan.EXTBOXType.VERTICAL_MANY;

public class QCollection {

    final private static Logger logger = LoggerFactory.getLogger(QCollection.class);
    // line -> start
    Map<Integer, Map<Integer, QSpan>> columns;
    Map<Integer, Map<Integer, QSpan>> rows;
    int max_line = 0;

    public QCollection(){
        rows = new HashMap<>();
        columns = new HashMap<>();
    }

    public List<QSpan> get(int line){
        Map<Integer, QSpan> line_items = columns.get(line);
        if (line_items == null || line_items.size() == 0) return new ArrayList<>();
        return new ArrayList(line_items.values());
    }

    public QSpan lookup(int line, int start){
        Map<Integer, QSpan> line_items = columns.get(line);
        if (line_items == null || line_items.size() == 0) return null;
        return line_items.get(start);
    }

    public boolean add(QSpan qSpan, boolean overwite){
        int line = qSpan.getLine();
        int start = qSpan.getStart();
        int end = qSpan.getEnd();
        Map<Integer, QSpan> line_items = columns.get(line);
        if (line_items == null){
            line_items = new HashMap<>();
            columns.put(line, line_items);
        }
        // check if we already have something here
        QSpan existing = line_items.get(start);
        if (existing != null) {
            if (existing.getEnd() == end) return false;
            if (!overwite) return false;
        }
        line_items.put(start, qSpan);
        if (line > max_line){
            max_line = line;
        }
        return true;
    }

    public int getMax_line() {
        return max_line;
    }

    public QCollection combine(QCollection newItems, boolean overwrite){
        QCollection uniqNewItems = new QCollection();
        for (Map.Entry<Integer, Map<Integer, QSpan>> e : newItems.columns.entrySet()){
            Integer line = e.getKey();
            Map<Integer, QSpan> line_map = e.getValue();
            for (Map.Entry<Integer, QSpan> ee : line_map.entrySet()){
                Integer start = ee.getKey();
                QSpan newItem = ee.getValue();
                //check if it is already in the list
                QSpan item = lookup(line, start);
                if ((item != null) && (item.getEnd() == newItem.getEnd())) {
                    continue;
                }
                boolean added = add(newItem, overwrite);
                if (added) {
                    uniqNewItems.add(newItem, overwrite);
                }
            }
        }
        return uniqNewItems;
    }

    public int size(){
        return columns.size();
    }

    public void groupVertically(QCollection generic_matches,
                                Map<Integer, List<Interval>> lineLabelMap,
                                float heightMult1, // between header and first cell
                                float heightMult2){

        Iterator<Map.Entry<Integer, Map<Integer, QSpan>>> iter1 = columns.entrySet().iterator();

        while (iter1.hasNext()) {
            Map.Entry<Integer, Map<Integer, QSpan>> line_map1 = iter1.next();
            if (line_map1.getValue() == null || line_map1.getValue().size() == 0){
                continue;
            }
            int line1 = line_map1.getKey();

            Iterator<Map.Entry<Integer, QSpan>> iter11 = line_map1.getValue().entrySet().iterator();
            while (iter11.hasNext()){

                Map.Entry<Integer, QSpan> next = iter11.next();
                QSpan etb1 = next.getValue();
                BaseTextBox tb1 = etb1.getTextBox();
                if (tb1 == null) continue;
                int numv = etb1.getKeys().size();
                float b1 = tb1.getBase();
                float l1 = tb1.getLeft();
                float r1 = tb1.getRight();
                float char_width = (r1 - l1) / etb1.getExtInterval(true).getStr().length();

                for (int line2 = line1 + 1; line2 <= max_line; line2++) {
                    List<QSpan> etbList2 = get(line2);
                    if (etbList2.size() == 0) {
                        // check if we are hitting a label
                        List<Interval> labels = lineLabelMap.get(line2);
                        if (labels == null) continue;
                        List<QSpan> label_spans = new ArrayList<>();
                        for (Interval eit : labels){
                            label_spans.add(new QSpan(eit));
                        }
                        ExtIntervalTextBox alignedLabels = detectBestAlignedValue(tb1, label_spans, b1, char_width);
                        if (alignedLabels != null) break;
                        continue;
                    }

                    ExtIntervalTextBox alignedValues = detectBestAlignedValue(tb1, etbList2, b1, char_width);

                    if (alignedValues != null){
                        BaseTextBox tb2 = alignedValues.getTextBox();
                        if (tb2 != null) {
                            float t2 = tb2.getTop();
                            float dist_from_prev = t2 - b1;
                            float h2 = tb2.getBase() - t2;
                            if (numv == 1) {
                                if (dist_from_prev > heightMult1 * h2) break;
                            } else {
                                if (dist_from_prev > heightMult2 * h2) break;
                            }
                        }

                        etb1.add(alignedValues);
                        // we add one value in every line
                        b1 = alignedValues.getTextBox().getBase();
                        numv++;
                    }
                }
            }
        }
    }

    public void groupHorizontally(QCollection generic_matches,
                                Map<Integer, List<ExtIntervalTextBox>> lineLabelMap,
                                float heightMult1, // between header and first cell
                                float heightMult2){

        Iterator<Map.Entry<Integer, Map<Integer, QSpan>>> iter1 = columns.entrySet().iterator();

        while (iter1.hasNext()) {
            Map.Entry<Integer, Map<Integer, QSpan>> line_map1 = iter1.next();
            if (line_map1.getValue() == null || line_map1.getValue().size() == 0){
                continue;
            }
            int line1 = line_map1.getKey();

            Iterator<Map.Entry<Integer, QSpan>> iter11 = line_map1.getValue().entrySet().iterator();
            while (iter11.hasNext()){

                Map.Entry<Integer, QSpan> next = iter11.next();
                QSpan etb1 = next.getValue();
                BaseTextBox tb1 = etb1.getTextBox();
                int numv = etb1.getExtIntervalTextBoxes().size();
                float b1 = tb1.getBase();
                float l1 = tb1.getLeft();
                float r1 = tb1.getRight();
                float char_width = (r1 - l1) / etb1.getExtInterval(true).getStr().length();

                for (int line2 = line1 + 1; line2 <= max_line; line2++) {
                    List<QSpan> etbList2 = get(line2);
                    if (etbList2.size() == 0) {
                        // check if we are hitting a label
                        List<ExtIntervalTextBox> labels = lineLabelMap.get(line2);
                        if (labels == null) continue;
                        List<QSpan> label_spans = new ArrayList<>();
                        for (ExtIntervalTextBox eit : labels){
                            label_spans.add(new QSpan(eit));
                        }
                        ExtIntervalTextBox alignedLabels = detectBestAlignedValue(tb1, label_spans, b1, char_width);
                        if (alignedLabels != null) break;
                        continue;
                    }

                    ExtIntervalTextBox alignedValues = detectBestAlignedValue(tb1, etbList2, b1, char_width);

                    if (alignedValues != null){
                        BaseTextBox tb2 = alignedValues.getTextBox();
                        if (tb2 != null) {
                            float t2 = tb2.getTop();
                            float dist_from_prev = t2 - b1;
                            float h2 = tb2.getBase() - t2;
                            if (numv == 1) {
                                if (dist_from_prev > heightMult1 * h2) break;
                            } else {
                                if (dist_from_prev > heightMult2 * h2) break;
                            }
                        }

                        etb1.add(alignedValues);
                        // we add one value in every line
                        b1 = alignedValues.getTextBox().getBase();
                        numv++;
                    }
                }
            }
        }
    }

    public void markVM(){
        for (Map.Entry<Integer, Map<Integer, QSpan>> e : columns.entrySet()){
            Map<Integer, QSpan> line_map = e.getValue();
            if (line_map.size() < 2) continue;
            for (Map.Entry<Integer, QSpan> ee : line_map.entrySet()) {
                QSpan qSpan = ee.getValue();
                if (qSpan.getExtIntervalTextBoxes().size() < 2) continue;
                qSpan.setSpanType(VERTICAL_MANY);
            }
        }
    }

    private Interval detectBestAlignedValue(BaseTextBox tb1,
                                                      List<Interval> etbList2,
                                                      float b1,
                                                      float char_width)
    {
        ListIterator<Interval> iter2 = etbList2.listIterator();
        while (iter2.hasNext()) {
            Interval etb2 = iter2.next();
            BaseTextBox tb2 = etb2.getTextBox();
            if (tb2 == null) continue;
            float b2 = tb2.getBase();
            // check if the vertical values are not well spaces

            float b_diff = b2 - b1;
            if (b_diff < 0) {
                logger.debug("Distance is negative!!!");
                continue;
            }

            boolean isAligned = headerAlignedWithCell(tb1, tb2, char_width);
            if (isAligned) {
                ExtIntervalTextBox candidateEtb = new ExtIntervalTextBox(etb2.getExtInterval(false), etb2.getTextBox());
                return candidateEtb;
            }
        }
        return null;
    }

    private boolean headerAlignedWithCell(BaseTextBox tb1,
                                          BaseTextBox tb2,
                                          float char_width){

        float l1 = tb1.getLeft();
        float l2 = tb2.getLeft();
        float ld = Math.abs(l1-l2);
        if (ld < char_width) return true;

        float r1 = tb1.getRight();
        float r2 = tb2.getRight();
        float rd = Math.abs(r1-r2);
        if ( rd < char_width) return true;

        float c1 = (r1 + l1 ) / 2;
        float c2 = (r2 + l2 ) / 2;
        float cd = Math.abs(c1-c2);
        if (cd < char_width) return true;

        return false;
    }
}
