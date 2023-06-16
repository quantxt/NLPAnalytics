package com.quantxt.doc.helper.textbox;

import com.quantxt.model.document.BaseTextBox;
import com.quantxt.types.LineInfo;
import com.quantxt.types.QSpan;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TextBox extends BaseTextBox implements Comparable<TextBox> {
    final private static Logger logger = LoggerFactory.getLogger(TextBox.class);

    final private static float spc_lower = 2.0f;
    final private static float spc_upper = 4.0f;
    final private static float avg_char_width_unit = 50f; // this is the width for an `e`

    @Override
    public int compareTo(TextBox that) {

        if (this.getBase() == that.getBase() && this.getTop() == that.getTop()) return 0;

        //primitive numbers follow this form
        if (this.getBase() < that.getBase()) return -1;
        if (this.getBase() > that.getBase()) return +1;

        return 0;
    }

    @Override
    public String toString(){
        if (childs.size() == 0) return getStr();
        StringBuilder sb = new StringBuilder();
        for (BaseTextBox tb : childs){
            sb.append(tb.getStr()).append(" ");
        }
        return sb.toString().trim();
    }

    public static class SortByBaseLine implements Comparator<BaseTextBox> {
        public int compare(BaseTextBox a, BaseTextBox b) {
            float a_base = a.getBase();
            float b_base = b.getBase();
            if (a_base == b_base){
                float a_left = a.getLeft();
                float b_left = b.getLeft();
                return Float.compare(a_left, b_left);
            }

            return Float.compare(a_base, b_base);
        }
    }

    private static String [] getLinesfromLineBoxes(List<BaseTextBox> textBoxes, float avg_w, float avg_h)
    {
        float limit = .1f;
        List<String> lines = new ArrayList<>();
        if (textBoxes.size() == 0) return new String []{};
        textBoxes.sort(new SortByBaseLine());
        int numLines = textBoxes.size();
        int line_number = 0; // zero-based line numbers
        for (int k=0; k < numLines; k++) {
            BaseTextBox lineBox = textBoxes.get(k);
            List<BaseTextBox> lineTextBoxList = lineBox.getChilds();
            lineTextBoxList.sort(new SortByStartWord());
            int total_textboxes_per_line = lineTextBoxList.size();

            StringBuilder sb = new StringBuilder();
            float num_units_in_line = 0;
            float covered_area_in_pxl = 0;
             for (int i = 0; i < total_textboxes_per_line; i++) {
                BaseTextBox textBox = lineTextBoxList.get(i);
                float tb_h = textBox.getBase() - textBox.getTop();
                float r = tb_h/avg_h;
                num_units_in_line += getSingleSpaceEstimate(textBox) / avg_char_width_unit;
                covered_area_in_pxl += (textBox.getRight() - textBox.getLeft() ) / r;
            }

            float avg_char_unit_length_in_pxl = (covered_area_in_pxl / num_units_in_line);

            float start_pad = 0;
            for (int i = 0; i < total_textboxes_per_line; i++) {
                BaseTextBox textBox = lineTextBoxList.get(i);
                textBox.setPage(lineBox.getPage());
                String str = textBox.getStr();
                float end_pad = textBox.getLeft();
                boolean isCloseToNext = false;
                float dist_to_prev = Math.abs(end_pad - start_pad);
                float tb_h = textBox.getBase() - textBox.getTop();
                float r = tb_h/avg_h;
                float norm_dist_to_prev = Math.abs(end_pad - start_pad) / r;

                float local_avg_char_unit_lngth_in_pxl = avg_char_unit_length_in_pxl;
                if (str.length() > 1){
                    float local_num_units = getSingleSpaceEstimate(textBox) / avg_char_width_unit;
                    float local_w = (textBox.getRight() - textBox.getLeft() );
                    local_avg_char_unit_lngth_in_pxl = local_w/local_num_units;
                    norm_dist_to_prev = Math.abs(end_pad - start_pad);
                }
                float estimated_num_space = norm_dist_to_prev / local_avg_char_unit_lngth_in_pxl;
                if (estimated_num_space < spc_lower){
                    isCloseToNext = true;
                }

                if (dist_to_prev < limit) {
                    sb.append(str);
                } else if (isCloseToNext){
                    sb.append(" ").append(str);
                } else {
                    if (estimated_num_space < spc_upper){

                        int left_pad_length =  (int) estimated_num_space;

                        if (left_pad_length > 0){
                            String left_pad = String.format("%1$" + left_pad_length + "s", "");
                            sb.append(left_pad);
                        }

                        sb.append(str);
                        int right_pad_length = (int) (end_pad / avg_w) - sb.length() - left_pad_length;

                        if (right_pad_length > 0) {
                            String right_pad = String.format("%1$" + right_pad_length + "s", "");
                            sb.append(right_pad);
                        }
                    } else {
                        int pad_length_int = (int) (end_pad / avg_w) - sb.length();
                        if (pad_length_int < 2) {
                            pad_length_int = 2;
                        }
                        String white_pad = String.format("%1$" + pad_length_int + "s", "");
                        sb.append(white_pad).append(str);
                    }
                }
                start_pad = textBox.getRight();
            }

            lineBox.setLine(line_number);
            lineBox.setLine_str(sb.toString());
            lines.add(sb.toString());
            line_number++;

            if ( k < numLines - 1) {
                float cur_base = lineBox.getBase();
                float cur_top  = lineBox.getTop();
                float next_top  = textBoxes.get(k+1).getTop();
                float next_base  = textBoxes.get(k+1).getBase();
                float cur_h  = cur_base - cur_top;
                float next_h  = next_base - next_top;
                // very short lines
                float base_to_base_dist = next_base - cur_base;
                float base_to_top_dist = next_top - cur_base;
                int num_empty_lines_to_add = 0;
                if (cur_h> 1 && base_to_top_dist > cur_h && base_to_top_dist > next_h){
                    num_empty_lines_to_add = (int) (base_to_base_dist/cur_h)- 1;
                }
                if (num_empty_lines_to_add > 10){
                    num_empty_lines_to_add = 10;
                }
                if (num_empty_lines_to_add > 0) {
                    for (int j = 0; j < num_empty_lines_to_add; j++) {
                        lines.add("");
                        line_number++;
                    }
                }
            }
        }

        return lines.toArray(new String[lines.size()]);
    }

    public static List<BaseTextBox> process(List<BaseTextBox> textBoxes) {

        MeanVar meanVar = new MeanVar(textBoxes);
        meanVar.calc(true);
        float avg_w = meanVar.avg_w;
        float avg_h = meanVar.avg_h;

        List<BaseTextBox> processedTextBoxes = mergeNeighbors(textBoxes);

        for (float overlap = .9f; overlap > .4f; overlap -= .1) {
            mergeTextBoxes(processedTextBoxes, overlap);
        }

        getLinesfromLineBoxes(processedTextBoxes, avg_w, avg_h);
        return processedTextBoxes;
    }

    public static List<BaseTextBox> correctSkew(List<BaseTextBox> lineBoxes, List<BaseTextBox> textBoxes, double thresh){
        SimpleRegression simpleRegression = new SimpleRegression(true);
        double sum = 0;
        double c = 0;
        for (BaseTextBox lineTb : lineBoxes){
            if (lineTb.getChilds().size() < 2) continue;
            simpleRegression.clear();
            for (BaseTextBox tb : lineTb.getChilds()) {
                simpleRegression.addData(tb.getLeft(), -1 * tb.getBase());

            }
            double slope = simpleRegression.getSlope();
            sum += slope;
            c +=1;
        }

        double avgSlope = sum/c;
        //correction

        if (Math.abs(avgSlope) < thresh) return textBoxes;
        //correct skew
        List<BaseTextBox> corrected_textBoxes = new ArrayList<>();
        for (BaseTextBox btb : textBoxes) {

            BaseTextBox baseTextBox = new BaseTextBox();

            float base = btb.getBase();
            float left = btb.getLeft();
            baseTextBox.setLeft(left);

            float top = btb.getTop();
            float right = btb.getRight();
            baseTextBox.setRight(right);

            double shift_base = left * avgSlope;
            double shift_top = right * avgSlope;

            double corrected_top = top + shift_top;
            baseTextBox.setTop((float) corrected_top);
            double corrected_base = base + shift_base;
            baseTextBox.setBase((float) corrected_base);

            baseTextBox.setBase((float) corrected_base);
            baseTextBox.setStr(btb.getStr());
            corrected_textBoxes.add(baseTextBox);
        }

        return corrected_textBoxes;

    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {

        Path path = Paths.get("/Users/matin/Downloads/abdd88d9-3583-4345-b083-1b03435aba7d.textbox");
        byte[] bytes = Files.readAllBytes(path);

        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInput in = new ObjectInputStream(bis);
        List<List<BaseTextBox>> textBoxes = (List<List<BaseTextBox>>) in.readObject();
        in.close();

        for (List<BaseTextBox> tbList : textBoxes) {
            List<BaseTextBox> processed = process(tbList);

        }
    }

    private static void addToClosest(List<BaseTextBox> textBoxes,
                                     List<BaseTextBox> leftOvers)
    {
        if (textBoxes.size() == 0) return;
        for (BaseTextBox tb : leftOvers){
            float base = tb.getBase();
            BaseTextBox bestLine = textBoxes.get(0);
            float bestDistance = 10000f;
            for (BaseTextBox lb : textBoxes){
                float lineBase = lb.getBase();
                float d = Math.abs(lineBase - base);
                if (d < bestDistance){
                    // check if the box has horizental overlap
                    boolean hasHorizentalOverlap = false;
                    for (BaseTextBox tbb : lb.getChilds()) {
                        hasHorizentalOverlap = hasHorizentalOverlap(tb, tbb);
                        if (hasHorizentalOverlap) break;
                    }
                    if (!hasHorizentalOverlap) {
                        bestDistance = d;
                        bestLine = lb;
                    }
                }
            }
            bestLine.getChilds().add(tb);
        }
    }

    private static class SortByStartWord implements Comparator<BaseTextBox> {
        public int compare(BaseTextBox a, BaseTextBox b) {
            float a_left = a.getLeft();
            float b_left = b.getLeft();
            if (a_left == b_left){
                float a_base = a.getBase();
                float b_base = b.getBase();
                return Float.compare(a_base , b_base);
            }
            return Float.compare(a_left , b_left); }
    }

    private static int mergeTextBoxes(List<BaseTextBox> textBoxes,
                                      float vertical_overlap_ratio) {

        HashSet<Integer> processedTbs = new HashSet<>();
        textBoxes.sort(new SortByStartWord());
        // find character space estimate

        int numMerges = 0;
        for (int i = 0; i < textBoxes.size(); i++) {
            if (processedTbs.contains(i)) continue;
            BaseTextBox textBox1 = textBoxes.get(i);

            float top1   = textBox1.getTop();
            float base1  = textBox1.getBase();
            float left1  = textBox1.getLeft();
            float right1 = textBox1.getRight();

            List<BaseTextBox> tbList1 = new ArrayList<>();
            for (int j = 0; j < textBoxes.size(); j++) {
                if (i == j) continue;
                if (processedTbs.contains(j)) continue;

                BaseTextBox textBox2 = textBoxes.get(j);
                List<BaseTextBox> childs2 = textBox2.getChilds();

                float top2    = textBox2.getTop();
                float base2   = textBox2.getBase();
                float left2   = textBox2.getLeft();
                float right2  = textBox2.getRight();

                float vertical_overlap = getVerticalOverlap(textBox1, textBox2);
                if (vertical_overlap < vertical_overlap_ratio) continue;

                // check overlap of child
                boolean hasHorizentalOverlap = false;
                for (BaseTextBox tb2 : textBox2.getChilds()) {
                    for (BaseTextBox tb1 : textBox1.getChilds()){
                        hasHorizentalOverlap = getHorizentalOverlap(tb1, tb2) >0;
                        if (hasHorizentalOverlap) break;
                    }
                    if (hasHorizentalOverlap) break;

                    for (BaseTextBox tb1 : tbList1){
                        hasHorizentalOverlap = getHorizentalOverlap(tb1, tb2) >0;
                        if (hasHorizentalOverlap) break;
                    }
                    if (hasHorizentalOverlap) break;
                }
                if (hasHorizentalOverlap) continue;

                if (childs2.isEmpty()){
                    tbList1.add(textBox2);
                } else {
                    tbList1.addAll(childs2);
                }

                processedTbs.add(j);
                numMerges++;
                top1 = Math.min(top1, top2);
                base1 = Math.max(base1, base2);
                left1 = Math.min(left1, left2);
                right1 = Math.max(right1, right2);
            }

            List<BaseTextBox> childs = textBox1.getChilds();
            if (childs.isEmpty()) {
                BaseTextBox tCopy = new BaseTextBox(textBox1.getTop(), textBox1.getBase(),
                        textBox1.getLeft(), textBox1.getRight(), textBox1.getStr());
                tCopy.setPage(textBox1.getPage());
                childs.add(tCopy);
            }
            childs.addAll(tbList1);
            // modify TextBox dimentions. A box that covers all the childs
            textBox1.setTop(top1);
            textBox1.setBase(base1);
            textBox1.setLeft(left1);
            textBox1.setRight(right1);
        }

        ListIterator<BaseTextBox> iter = textBoxes.listIterator();
        int i=0;
        while (iter.hasNext()){
            iter.next();
            if (processedTbs.contains(i++)) {
                iter.remove();
            }
        }

        return numMerges;
    }

    private static boolean hasHorizentalOverlap(BaseTextBox textBox1, BaseTextBox textBox2){
        if (textBox1.getLeft() < textBox2.getRight() && textBox1.getLeft() >= textBox2.getLeft()) {
            return true;
        }
        if (textBox1.getRight() <= textBox2.getRight() && textBox1.getRight() > textBox2.getLeft()) {
            return true;
        }
        return false;
    }


    private static List<BaseTextBox> mergeNeighbors(List<BaseTextBox> textBoxes) {

        List<BaseTextBox> processedTextboxes = new ArrayList<>();
        for (BaseTextBox tb : textBoxes){
            BaseTextBox ptb = new BaseTextBox(tb.getTop(), tb.getBase(),
                    tb.getLeft(), tb.getRight(), tb.getStr());
            processedTextboxes.add(ptb);
        }

        processedTextboxes.sort(new SortByStartWord());
        // find character space estimate

        HashSet<Integer> processedTbs = new HashSet<>();
        for (int i = 0; i < processedTextboxes.size(); i++) {
            if (processedTbs.contains(i)) continue;

            BaseTextBox textBox1 = processedTextboxes.get(i);

            float top1   = textBox1.getTop();
            float base1  = textBox1.getBase();
            float left1  = textBox1.getLeft();
            float right1 = textBox1.getRight();
            if (textBox1.getStr().isEmpty()) continue;
            float avg_c = (right1 - left1) / textBox1.getStr().length();

            /*
            List<BaseTextBox> tbList1 = new ArrayList<>();
            BaseTextBox master_tb = textBox1;
            for (int j = i+1; j < processedTextboxes.size(); j++) {
        //        if (i == j) continue;
                BaseTextBox textBox2 = processedTextboxes.get(j);
                if (processedTbs.contains(j)) continue;

                float d = textBox2.getLeft() - master_tb.getRight();
                if (d < -.1) continue;
                if (d > 5 * avg_c) break; // if distance is mor than 5 character they cant be neighbours
                float vertical_overlap = getVerticalOverlap(master_tb, textBox2);
                if (vertical_overlap <= .6f) continue;

                boolean isNeighbour = isNeighbour(master_tb, textBox2);
                if (!isNeighbour) continue;
                master_tb = textBox2;

        //        List<BaseTextBox> childs2 = textBox2.getChilds();
                tbList1.add(textBox2);
        //        if (childs2.isEmpty()){
        //            tbList1.add(textBox2);
        //        } else {
        //            tbList1.addAll(childs2);
        //        }

                processedTbs.add(j);
        //        top1 = top2;
        //        base1 = base2;
        //        left1 = left2;
        //        right1 = right2;
            //    top1 = Math.min(top1, top2);
            //    base1 = Math.max(base1, base2);
            //    left1 = Math.min(left1, left2);
            //    right1 = Math.max(right1, right2);
            }


             */
            List<BaseTextBox> childs = textBox1.getChilds();
            if (childs.isEmpty()) {
                BaseTextBox tCopy = new BaseTextBox(textBox1.getTop(), textBox1.getBase(),
                        textBox1.getLeft(), textBox1.getRight(), textBox1.getStr());
                tCopy.setPage(textBox1.getPage());
                childs.add(tCopy);
            }

       //     childs.addAll(tbList1);
            // modify TextBox dimentions. A box that covers all the childs
    //        textBox1.setTop(top1);
    //        textBox1.setBase(base1);
    //        textBox1.setLeft(left1);
    //        textBox1.setRight(right1);
        }

        List<BaseTextBox> resudedProcessedTextboxes = new ArrayList<>();
        for (int i=0; i<processedTextboxes.size(); i++){
            if (processedTbs.contains(i)) continue;
            BaseTextBox btb = processedTextboxes.get(i);
            if (btb.getChilds().isEmpty()) continue;
            resudedProcessedTextboxes.add(btb);
        }

        return resudedProcessedTextboxes;
    }


    private static boolean isNeighbour(BaseTextBox tbBefore,
                                       BaseTextBox tbCur){
        float d = tbCur.getLeft() - tbBefore.getRight();
        float h1 = tbCur.getBase() - tbCur.getTop();
        float h2 = tbBefore.getBase() - tbBefore.getTop();
        if (h2 > 0 && h1 > 0) {
            float r = h1 > h2 ? h2/h1 : h1/h2;
            float w = .8f * (h1 + h2);
            if (r > .4 && d <= h1){
                return true;
            }
        }
        return false;
    }

    private static float getSingleSpaceEstimate(BaseTextBox tb){
        String str = tb.getStr();
        if (str.length() == 0) return 0;
        float length = 0;
        for (int i=0; i< str.length(); i++){
            Character c = str.charAt(i);
            Float f = ratios.get(c);
            float s = f == null ? avg_char_width_unit : f;
            length += s;
        }
        return length;
    }

    private static float getSpaceEstimate(TextBox textBox){
        float total_pixel_length = 0;
        float total_char_length = 0;
        List<BaseTextBox> textBoxList = textBox.getChilds();
        for (BaseTextBox tb : textBoxList) {
            total_char_length += getSingleSpaceEstimate(tb);
            total_pixel_length += (tb.getRight() - tb.getLeft());
        }

        float space_estimate = 5f;

        if (total_char_length > 0 ) {
            space_estimate = 100 * total_pixel_length / total_char_length;
        }
        return space_estimate;
    }

    private static class MeanVar{
        public float avg_w;
        public float avg_h;
        public float std_w;
        public float std_h;
        public List<BaseTextBox> textBoxes;

        public MeanVar(List<BaseTextBox> textBoxes){
            this.textBoxes = textBoxes;
        }

        public void calc(boolean removeEmptyStr){
            double powerSumW1 = 0;
            double powerSumW2 = 0;

            double powerSumH1 = 0;
            double powerSumH2 = 0;

            List<Float> heights = new ArrayList<>();
            Iterator<BaseTextBox> it = textBoxes.listIterator();
            while (it.hasNext()) {
                BaseTextBox textBox = it.next();
                if (removeEmptyStr) {
                    String str = textBox.getStr();
                    if (str == null || str.trim().isEmpty()) {
                        it.remove();
                        continue;
                    }
                }
                float tb_h = textBox.getBase() - textBox.getTop();
                powerSumH1 += tb_h;
                powerSumW2 += Math.pow(tb_h, 2);
                heights.add(tb_h);
            }
            // tkae 90th perc to account for 90th tallest box
            Collections.sort(heights);

            int nintith_perc = (int)( .2f * (float)heights.size());
            float ref_height = heights.get(nintith_perc);

            it = textBoxes.listIterator();
            while (it.hasNext()){
                BaseTextBox textBox = it.next();
                float tb_h = textBox.getBase() - textBox.getTop();
                float r = tb_h/ref_height;
                float num_units_in_line = getSingleSpaceEstimate(textBox) / avg_char_width_unit;
                double w = ((textBox.getRight() - textBox.getLeft() ) / r ) / num_units_in_line;

                /*
                double w = (textBox.getRight() - textBox.getLeft() ) / textBox.getStr().length();

                 */
                powerSumW1 += w;
                powerSumW2 += Math.pow(w, 2);
            }

            float n = textBoxes.size();
            avg_w = (float) powerSumW1 / n;
            avg_h = (float) powerSumH1 / n;
            std_w = (float) Math.sqrt(n * powerSumW2 - Math.pow(powerSumW1, 2)) / n;
            std_h = (float) Math.sqrt(n * powerSumH2 - Math.pow(powerSumH1, 2)) / n;
        }

        public float sigma(){
            float ww = avg_w - std_w;
            if (ww < 1f) ww = 1f;
            return ww;
        }
    }

    public static BaseTextBox findAssociatedTextBox(Map<Integer, BaseTextBox> lineTextBoxMap,
                                                    String str,
                                                    LineInfo lineInfo,
                                                    boolean ignorePrefixBoxes)
    {
        if (lineTextBoxMap == null) return null;
        BaseTextBox line_text_boxes = lineTextBoxMap.get(lineInfo.getLineNumber());
        if (line_text_boxes == null) return null;

        String line_str = line_text_boxes.getLine_str();
        //TODO: Ideally we should pass the analyzer here
        String norm_line_str = line_str.replaceAll("[\\p{Punct}]", " ");
        String norm_str = str.replaceAll("[\\p{Punct}]", " ");
        BaseTextBox surronding_box = new BaseTextBox();

    //    int str_start_1 = line_str.indexOf(str);
        int str_start_1 = norm_line_str.indexOf(norm_str);
        if (str_start_1 < 0) {
            logger.warn("{} was not found in line_str {}", str, line_str);
            return null;
        }

        List<BaseTextBox> childs = line_text_boxes.getChilds();
        if (childs.size() == 0) return null;

        //align textbox lefts with string indices
        int index = 0;
        int[][] childIdx2StrIdx = new int[childs.size()][2];

        for (int i = 0; i < childs.size(); i++) {
            BaseTextBox baseTextBox = childs.get(i);
            String child_str = baseTextBox.getStr();
            String norm_child_str = child_str.replaceAll("[\\p{Punct}]", " ");
            index = norm_line_str.indexOf(norm_child_str, index);
        //    index = line_str.indexOf(child_str, index);
            childIdx2StrIdx[i][0] = index;
            childIdx2StrIdx[i][1] = index + child_str.length();
            index += child_str.length();
        }

        List<BaseTextBox> components = new ArrayList<>();
        int first_box_idx = -1;
        int last_box_idx = childs.size() - 1;
        int spanStart = lineInfo.getLocalStart();
        int spanEnd = lineInfo.getLocalEnd();
        for (int i = 0; i < childIdx2StrIdx.length; i++) {
            int current_start = childIdx2StrIdx[i][0];
            if (current_start > spanEnd) break;
            int current_end = childIdx2StrIdx[i][1];
            BaseTextBox current_box = childs.get(i);
            if ((spanStart >= current_start && spanStart <= current_end) ||
                    (spanEnd >= current_start && spanEnd <= current_end)) {
                if (spanEnd < current_end || spanStart > current_start){
                    // in this case we adjust left and right
                    float current_num_str = (float) (current_box.getStr().length());
                    float current_box_length = current_box.getRight() - current_box.getLeft();
                    float new_right = current_box.getRight();
                    float new_left = current_box.getLeft();
                    if (spanEnd < current_end){
                        float diff = (float) (current_end - spanEnd);
                        new_right = current_box.getRight() - diff/current_num_str * current_box_length;
                    }

                    if (spanStart > current_start){
                        float diff = (float) (spanStart - current_start);
                        new_left = current_box.getLeft() + diff/current_num_str * current_box_length;

                    }
                    BaseTextBox new_current_box = new BaseTextBox(current_box.getTop(), current_box.getBase(),
                            new_left, new_right, current_box.getStr());
                    components.add(new_current_box);
                } else {
                    components.add(current_box);
                }
                if (first_box_idx == -1) {
                    first_box_idx = i;
                }
                last_box_idx = i;
            }
        }

        if (components.size() == 0) {
            logger.debug("Didn't find the associating texboxes {}", str);
            return null;
        } else {
            float s_left = components.get(0).getLeft();
            float s_right = components.get(components.size()-1).getRight();
            float s_top = 100000;
            float s_base = -100000;
            for (BaseTextBox s : components){
                s_top = Math.min(s.getTop(), s_top);
                s_base = Math.max(s.getBase(), s_base);
            }
            surronding_box = new BaseTextBox(s_top, s_base, s_left, s_right, null);
        }

        if (ignorePrefixBoxes) {

            // check if first box has token that is the beginining of the str
            String first_matched_box_str = childs.get(first_box_idx).getStr();
            // so non number and alphabet charcters at the begining are not prefix: like ( ) %
            first_matched_box_str = first_matched_box_str.replaceAll("^[^A-Za-z0-9]+", "");
            if (!str.startsWith(first_matched_box_str)) {
                if (!first_matched_box_str.startsWith(str)) {
                    logger.debug("{} is a prefix", str);
                    return null;
                }
            }
            BaseTextBox textbox_before = first_box_idx > 0 ? childs.get(first_box_idx - 1) : null;

            if (textbox_before != null) {
                float gap_left = surronding_box.getLeft() - textbox_before.getRight();
                float w2 = (textbox_before.getRight() - textbox_before.getLeft()) / (textbox_before.getStr().length());
                if (gap_left < w2 * .8 ) {
                    String textbox_before_str = textbox_before.getStr();
                    if (textbox_before_str.length() != 0) {
                        String lastChar = textbox_before_str.substring(textbox_before_str.length() - 1);
                        if (lastChar.replaceAll("[A-Za-z0-9\"']", "").length() == 0) {
                            logger.debug("{} is a left-prefix", str);
                            return null;
                        }
                    }
                }
            }

            // This is English so if the phrase ends with colon (:) no need to look the right side
            BaseTextBox textbox_after = last_box_idx < childs.size()-1 ? childs.get(last_box_idx + 1) : null;
            BaseTextBox textbox_curr = childs.get(last_box_idx);
            String last_char_textbox_after = textbox_after == null ? "" : textbox_curr.getStr().substring(textbox_curr.getStr().length() - 1);
            last_char_textbox_after = last_char_textbox_after.replaceAll("[^\\p{L}]", "");
            if (textbox_after != null && !last_char_textbox_after.isEmpty()) {
                float gap_right =  textbox_after.getLeft() - surronding_box.getRight();
                float w2 = (textbox_after.getRight() - textbox_after.getLeft()) / (textbox_after.getStr().length());
                if (gap_right < w2 * .8 ) {
                    String textbox_after_str = textbox_after.getStr();
                    if (textbox_after_str.length() > 0) {
                        String firstChar = textbox_after_str.substring(0, 1);
                        if (firstChar.replaceAll("[\\p{L}]", "").length() == 0) {
                            logger.debug("{} is a right-prefix", str);
                            return null;
                        }
                    }
                }
            }

        }

        surronding_box.setChilds(components);
        surronding_box.setLine_str(line_str);

        return surronding_box;
    }

    public static float extendToNeighbours(Map<Integer, BaseTextBox> lineTextBoxMap,
                                          QSpan label){
        // find closest textboxes on the right side
        BaseTextBox tb = label.getTextBox();
        BaseTextBox newTb = new BaseTextBox(tb.getTop(), tb.getBase(), tb.getRight(), 10000, "");
        for (Map.Entry<Integer, BaseTextBox> e : lineTextBoxMap.entrySet()){
            int lineBoxLine = e.getKey();
            if (e.getValue() == null) continue;
            if (Math.abs(lineBoxLine - label.getLine()) > 3) continue;
            List<BaseTextBox> childs = e.getValue().getChilds();
            if (childs == null || childs.size() == 0) continue;
            for (BaseTextBox bt : childs) {
                float vOcerlap = getVerticalOverlap(bt, tb);
                if (vOcerlap  <= 0) continue;
                float hOverlap = getHorizentalOverlap(bt, newTb, false);
                if (hOverlap > 0 && bt.getLeft() > tb.getRight()) {
                    newTb.setRight(bt.getLeft());
                }
            }
        }

        return newTb.getRight();
    }

    public static float extendToLeftNeighbours(Map<Integer, BaseTextBox> lineTextBoxMap,
                                           QSpan label){
        // find closest textboxes on the right side
        BaseTextBox tb = label.getTextBox();
        BaseTextBox newTb = new BaseTextBox(tb.getTop(), tb.getBase(), 0, tb.getLeft(), "");
        for (Map.Entry<Integer, BaseTextBox> e : lineTextBoxMap.entrySet()){
            int lineBoxLine = e.getKey();
            if (e.getValue() == null) continue;
            if (Math.abs(lineBoxLine - label.getLine()) > 3) continue;
            List<BaseTextBox> childs = e.getValue().getChilds();
            if (childs == null || childs.size() == 0) continue;
            for (BaseTextBox bt : childs) {
                float vOcerlap = getVerticalOverlap(bt, tb);
                if (vOcerlap  <= 0) continue;
                float hOverlap = getHorizentalOverlap(bt, newTb, false);
                if (hOverlap > 0 && bt.getRight() < tb.getLeft()) {
                    newTb.setLeft(bt.getRight());
                }
            }
        }

        return newTb.getLeft();
    }

    public static float getHorizentalOverlap(BaseTextBox textBox1,
                                             BaseTextBox textBox2,
                                             boolean useChilds){
        float l1 = useChilds ? textBox1.getChilds().get(0).getLeft() : textBox1.getLeft();
        float r1 = useChilds ? textBox1.getChilds().get(textBox1.getChilds().size() -1).getRight() : textBox1.getRight();

        float l2 = useChilds ? textBox2.getChilds().get(0).getLeft() : textBox2.getLeft();
        float r2 = useChilds ? textBox2.getChilds().get(textBox2.getChilds().size() -1).getRight() : textBox2.getRight();

        float w1 = r1 - l1;
        float w2 = r2 - l2;


        if (w1 == 0 || w2 == 0) return 0;

        // h1 100% covers h2
        if (l2 >= l1 && r2 <= r1) {
            return 1;
        }

        // h2 100% covers h1
        if (l2 <= l1 && r2 >= r1) {
            return 1;
        }

        float overlap = 0;

        if (l1 <= r2 && l1 >= l2) {
            overlap = (r2 - l1 );
        }

        if (r1 <= r2 && r1 >= l2) {
            overlap = (r1 - l2);
        }
        return overlap / Math.min(w1, w2);
    }

    public static float getHorizentalOverlap(BaseTextBox textBox1,
                                             BaseTextBox textBox2){
        float l1 = textBox1.getLeft();
        float r1 = textBox1.getRight();

        float l2 = textBox2.getLeft();
        float r2 = textBox2.getRight();

        float w1 = r1 - l1;
        float w2 = r2 - l2;


        if (w1 == 0 || w2 == 0) return 0;

        // w1 100% covers w2
        if (l2 >= l1 && r2 <= r1) {
            return 1;
        }

        // w2 100% covers w1
        if (l2 <= l1 && r2 >= r1) {
            return 1;
        }

        float overlap = 0;

        if (l1 >= l2) {
            overlap = Math.max(0, Math.min(r1, r2) - l1);
        } else {
            overlap = Math.max(0, Math.min(r1, r2) - l2);
        }
        return overlap / Math.min(w1, w2);
    }

    public static float getVerticalOverlap(BaseTextBox textBox1, BaseTextBox textBox2)
    {
        float t1 = textBox1.getTop();  //l=t
        float b1 = textBox1.getBase(); //r=b

        float t2 = textBox2.getTop();
        float b2 = textBox2.getBase();

        float h1 = b1 - t1;     //w=h
        float h2 = b2 - t2;

        if (h2 == 0 || h1 == 0) return 0;

        if (t2 >= t1 && b2 <= b1) {
            return 1;
        }

        // h2 100% covers h1
        if (t2 <= t1 && b2 >= b1) {
            return 1;
        }

        float overlap = 0;
        if (t1 >= t2) {
            overlap = Math.max(0, Math.min(b1, b2) - t1);
        } else {
            overlap = Math.max(0, Math.min(b1, b2) - t2);
        }

        return overlap / Math.min(h1, h2);
    }

    private static Map<Character, Float> ratios = new HashMap<>() {{
        put('.', 25f);
        put('#', 85f);
        put('&', 75f);
        put(',', 25f);
        put(';', 25f);
        put(':', 25f);
        put('\"', 25f);
        put('/', 25f);
        put('1', 25f);
        put('2', 60f);
        put('3', 60f);
        put('4', 75f);
        put('5', 75f);
        put('6', 80f);
        put('7', 70f);
        put('8', 80f);
        put('9', 80f);
        put('@', 80f);
        put('a', 60f);
        put('b', 60f);
        put('c', 52f);
        put('d', 60f);
        put('e', 60f);
        put('f', 30f);
        put('g', 60f);
        put('h', 60f);
        put('i', 25f);
        put('j', 25f);
        put('k', 52f);
        put('l', 25f);
        put('m', 87f);
        put('n', 60f);
        put('o', 60f);
        put('p', 60f);
        put('q', 60f);
        put('r', 35f);
        put('s', 52f);
        put('t', 30f);
        put('u', 60f);
        put('v', 52f);
        put('w', 77f);
        put('x', 52f);
        put('y', 52f);
        put('z', 52f);
        put('A', 70f);
        put('B', 70f);
        put('C', 77f);
        put('D', 77f);
        put('E', 70f);
        put('F', 65f);
        put('G', 82f);
        put('H', 77f);
        put('I', 30f);
        put('J', 55f);
        put('K', 70f);
        put('L', 60f);
        put('M', 87f);
        put('N', 77f);
        put('O', 82f);
        put('P', 70f);
        put('Q', 82f);
        put('R', 77f);
        put('S', 70f);
        put('T', 65f);
        put('U', 77f);
        put('V', 70f);
        put('W', 100f);
        put('X', 70f);
        put('Y', 70f);
        put('Z', 65f);
        put(' ', 100f);
    }};
}
