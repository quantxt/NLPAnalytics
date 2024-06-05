package com.quantxt.types;

import com.quantxt.model.Interval;
import com.quantxt.model.document.BaseTextBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.quantxt.types.QSpan.EXTBOXType.VERTICAL_MANY;

public class QCollection {

    final private static Logger logger = LoggerFactory.getLogger(QCollection.class);
    // line -> start
    Map<Integer, Map<Integer, Interval>> columns;
    Map<Integer, Map<Integer, Interval>> rows;

    Map<Integer, List<Interval>> grouped_vertical = new HashMap<>();
    int max_line = 0;

    public QCollection(){
        rows = new HashMap<>();
        columns = new HashMap<>();
    }

    public List<Interval> get(int line){
        Map<Integer, Interval> line_items = columns.get(line);
        if (line_items == null || line_items.size() == 0) return new ArrayList<>();
        return new ArrayList(line_items.values());
    }

    public Interval lookup(int line, int start){
        Map<Integer, Interval> line_items = columns.get(line);
        if (line_items == null || line_items.size() == 0) return null;
        return line_items.get(start);
    }

    public boolean add(Interval interval, boolean overwite){
        int line = interval.getLine();
        int start = interval.getStart();
        int end = interval.getEnd();
        Map<Integer, Interval> line_items = columns.get(line);
        if (line_items == null){
            line_items = new HashMap<>();
            columns.put(line, line_items);
        }
        // check if we already have something here
        Interval existing = line_items.get(start);
        if (existing != null) {
            if (existing.getEnd() == end) return false;
            if (!overwite) return false;
        }
        line_items.put(start, interval);
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
        for (Map.Entry<Integer, Map<Integer, Interval>> e : newItems.columns.entrySet()){
            Integer line = e.getKey();
            Map<Integer, Interval> line_map = e.getValue();
            for (Map.Entry<Integer, Interval> ee : line_map.entrySet()){
                Integer start = ee.getKey();
                Interval newItem = ee.getValue();
                //check if it is already in the list
                Interval item = lookup(line, start);
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

    public Map<Integer, List<Interval>> groupVertically(QCollection generic_matches,
                                                    Map<Integer, List<Interval>> lineLabelMap,
                                                    float heightMult1, // between header and first cell
                                                    float heightMult2){

        Iterator<Map.Entry<Integer, Map<Integer, Interval>>> iter1 = columns.entrySet().iterator();

        while (iter1.hasNext()) {
            Map.Entry<Integer, Map<Integer, Interval>> line_map1 = iter1.next();
            if (line_map1.getValue() == null || line_map1.getValue().size() == 0){
                continue;
            }
            int line1 = line_map1.getKey();

            Iterator<Map.Entry<Integer, Interval>> iter11 = line_map1.getValue().entrySet().iterator();
            while (iter11.hasNext()){

                Map.Entry<Integer, Interval> next = iter11.next();
                Interval etb1 = next.getValue();
                List<BaseTextBox> tb1List = etb1.getTextBoxes();
                if (tb1List == null) continue;
                BaseTextBox tb1 = tb1List.get(0);
                List<Interval> grouped = grouped_vertical.get(line1);
                grouped.add(etb1);
                float b1 = tb1.getBase();
                float l1 = tb1.getLeft();
                float r1 = tb1.getRight();
                float char_width = (r1 - l1) / etb1.getStr().length();

                for (int line2 = line1 + 1; line2 <= max_line; line2++) {
                    List<Interval> etbList2 = get(line2);
                    if (etbList2.size() == 0) {
                        // check if we are hitting a label
                        List<Interval> labels = lineLabelMap.get(line2);
                        if (labels == null) continue;
                        List<Interval> label_spans = new ArrayList<>();
                        for (Interval eit : labels){
                            label_spans.add(eit);
                        }
                        Interval alignedLabels = detectBestAlignedValue(tb1, label_spans, b1, char_width);
                        if (alignedLabels != null) break;
                        continue;
                    }

                    Interval alignedValues = detectBestAlignedValue(tb1, etbList2, b1, char_width);

                    if (alignedValues != null){
                        List<BaseTextBox> tb2List = alignedValues.getTextBoxes();
                        if (tb2List != null) {
                            BaseTextBox tb2 = tb2List.get(0);
                            float t2 = tb2.getTop();
                            float dist_from_prev = t2 - b1;
                            float h2 = tb2.getBase() - t2;
                            if (grouped.size() == 1) {
                                if (dist_from_prev > heightMult1 * h2) break;
                            } else {
                                if (dist_from_prev > heightMult2 * h2) break;
                            }
                        }

                        grouped.add(alignedValues);
                        // we add one value in every line
                        b1 = tb2List.get(0).getBase();
                    }
                }
            }
        }
    }

    public void markVM(){
        for (Map.Entry<Integer, Map<Integer, Interval>> e : columns.entrySet()){
            Map<Integer, Interval> line_map = e.getValue();
            if (line_map.size() < 2) continue;
            for (Map.Entry<Integer, Interval> ee : line_map.entrySet()) {
                Interval interval = ee.getValue();
                if (qSpan.getKeys().size() < 2) continue;
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
            List<BaseTextBox> tb2List = etb2.getTextBoxes();
            if (tb2List == null) continue;
            BaseTextBox tb2 = tb2List.get(0);
            float b2 = tb2.getBase();
            // check if the vertical values are not well spaces

            float b_diff = b2 - b1;
            if (b_diff < 0) {
                logger.debug("Distance is negative!!!");
                continue;
            }

            boolean isAligned = headerAlignedWithCell(tb1, tb2, char_width);
            if (isAligned) {
                return etb2;
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
