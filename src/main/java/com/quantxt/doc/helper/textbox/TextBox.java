package com.quantxt.doc.helper.textbox;

import com.quantxt.model.document.BaseTextBox;

import java.util.*;

public class TextBox extends BaseTextBox implements Comparable<TextBox> {

    final private static float adj_distance_mult = 1.5f;

    List<BaseTextBox> childs;
    private int line;
    private String line_str;
    private boolean processed;

    public TextBox(){
        super();
    }

    public TextBox(float t, float b, float l, float r, String s) {
        super(t, b, l, r, s);
        childs = new ArrayList<>();
    }


    @Override
    public int compareTo(TextBox that) {

        if (this.getBase() == that.getBase() && this.getTop() == that.getTop()) return 0;

        //primitive numbers follow this form
        if (this.getBase() < that.getBase()) return -1;
        if (this.getBase() > that.getBase()) return +1;

        return 0;
    }

    public String getLine_str() {
        return line_str;
    }

    public int getLine() {
        return line;
    }

    public boolean isProcessed(){return processed;}

    public void setLine(int line) {
        this.line = line;
    }

    public void setLine_str(String line_str) {
        this.line_str = line_str;
    }

    public void setProcessed(boolean b){processed = b;}

    public List<BaseTextBox> getChilds() {
        return childs;
    }

    public void setChilds(List<BaseTextBox> childs) {
        this.childs = childs;
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

    private static String [] getLinesfromLineBoxes(List<TextBox> textBoxes, float avg_w)
    {
        float limit = .1f;
        List<String> lines = new ArrayList<>();
        if (textBoxes.size() == 0) return new String []{};
        textBoxes.sort(new SortByBaseLine());
        int numLines = textBoxes.size();
        int line_number = 0; // zero-based line numbers
        for (int k=0; k < numLines; k++) {
            TextBox lineBox = textBoxes.get(k);
            List<BaseTextBox> lineTextBoxList = lineBox.getChilds();
            lineTextBoxList.sort(new SortByStartWord());
            int total_textboxes_per_line = lineTextBoxList.size();
            float start_pad = 0;

            StringBuilder sb = new StringBuilder();
        //    float space_estimate = getSpaceEstimate(lineBox);
            for (int i = 0; i < total_textboxes_per_line; i++) {
                BaseTextBox textBox = lineTextBoxList.get(i);
                textBox.setPage(lineBox.getPage());
                float end_pad = textBox.getLeft();
                boolean isCloseToNext = i > 0 ? isNeighbour(lineTextBoxList.get(i-1), textBox) : false;

                String str = textBox.getStr();
                float space_estimate = (getSingleSpaceEstimate(textBox) / str.length() ) / 50;

                float dist_to_prev = Math.abs(end_pad - start_pad) / space_estimate;
                String white_pad = "";
                if (dist_to_prev < limit) {
                    white_pad = "";
                } else if (isCloseToNext || (dist_to_prev >= limit && dist_to_prev < adj_distance_mult)){
                    white_pad = " ";
                } else {
                    int pad_length_int = (int) (end_pad / avg_w) - sb.length();
                    if (pad_length_int < 2){
                        //               log.debug("Squished text {}", textBox.getStr());
                        pad_length_int = 2;
                    }
                    white_pad = String.format("%1$" + pad_length_int + "s", "");
                }

                sb.append(white_pad).append(str);
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

    public static List<TextBox> process(List<BaseTextBox> textBoxes) {

        MeanVar meanVar = new MeanVar(textBoxes);
        meanVar.calc(true);
        float sigma = meanVar.sigma();

        List<TextBox> processedTextBoxes = mergeNeighbors(textBoxes);
        List<TextBox> leftOvers = new ArrayList<>();
        for (float overlap = .9f; overlap > .4f; overlap -= .1) {
            textBoxes.addAll(leftOvers);
            mergeTextBoxes(processedTextBoxes, leftOvers, overlap);
        }

        addToClosest(processedTextBoxes, leftOvers);
        getLinesfromLineBoxes(processedTextBoxes, sigma);
        return processedTextBoxes;
    }

    private static void addToClosest(List<TextBox> textBoxes,
                                     List<TextBox> leftOvers)
    {
        if (textBoxes.size() == 0) return;
        for (TextBox tb : leftOvers){
            float base = tb.getBase();
            TextBox bestLine = textBoxes.get(0);
            float bestDistance = 10000f;
            for (TextBox lb : textBoxes){
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

    private static int mergeTextBoxes(List<TextBox> textBoxes,
                               List<TextBox> leftOvers,
                               float vertical_overlap_ratio) {

        Iterator<TextBox> it = textBoxes.listIterator();
        while (it.hasNext()){
            TextBox tb = it.next();
            tb.setProcessed(false);
        }

        textBoxes.sort(new SortByStartWord());
        // find character space estimate

        int numMerges = 0;
        for (int i = 0; i < textBoxes.size(); i++) {
            TextBox textBox1 = textBoxes.get(i);
            if (textBox1.isProcessed()) continue;

            float top1   = textBox1.getTop();
            float base1  = textBox1.getBase();
            float left1  = textBox1.getLeft();
            float right1 = textBox1.getRight();

            List<BaseTextBox> tbList1 = new ArrayList<>();
            for (int j = 0; j < textBoxes.size(); j++) {
                if (i == j) continue;
                TextBox textBox2 = textBoxes.get(j);

                if (textBox2.isProcessed()) continue;

                float top2    = textBox2.getTop();
                float base2   = textBox2.getBase();
                float left2   = textBox2.getLeft();
                float right2  = textBox2.getRight();

                float vertical_overlap = getOverlap(top1, base1, top2, base2, vertical_overlap_ratio);
                if (vertical_overlap <= 0) continue;

                boolean hasHorizentalOverlap = hasHorizentalOverlap(textBox1, textBox2);
                if (hasHorizentalOverlap) continue;
                for (BaseTextBox tb1 : textBox1.getChilds()){
                    hasHorizentalOverlap = hasHorizentalOverlap(tb1, textBox2);
                    if (hasHorizentalOverlap) break;
                }
                if (hasHorizentalOverlap) continue;

                for (BaseTextBox tb1 : tbList1){
                    hasHorizentalOverlap = hasHorizentalOverlap(tb1, textBox2);
                    if (hasHorizentalOverlap) break;
                }
                if (hasHorizentalOverlap) continue;

                List<BaseTextBox> childs2 = textBox2.getChilds();

                if (childs2.isEmpty()){
                    tbList1.add(textBox2);
                } else {
                    tbList1.addAll(childs2);
                }

                textBox2.setProcessed(true);
                numMerges++;
                top1 = Math.min(top1, top2);
                base1 = Math.max(base1, base2);
                left1 = Math.min(left1, left2);
                right1 = Math.max(right1, right2);
            }

            List<BaseTextBox> childs = textBox1.getChilds();
            if (childs.isEmpty()) {
                BaseTextBox tCopy = new TextBox(textBox1.getTop(), textBox1.getBase(),
                        textBox1.getLeft(), textBox1.getRight(), textBox1.getStr());
                //        tCopy.setLine(textBox1.getLine());
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

        it = textBoxes.listIterator();
        leftOvers.clear();
        while (it.hasNext()) {
            TextBox tb = it.next();
            List<BaseTextBox> childTextBoxes = tb.getChilds();
            if (tb.isProcessed()) {
                it.remove();
            } else if (childTextBoxes.isEmpty()){
                leftOvers.add(tb);
                it.remove();
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


    private static List<TextBox> mergeNeighbors(List<BaseTextBox> textBoxes) {

        List<TextBox> processedTextboxes = new ArrayList<>();
        for (BaseTextBox tb : textBoxes){
            TextBox ptb = new TextBox(tb.getTop(), tb.getBase(),
                    tb.getLeft(), tb.getRight(), tb.getStr());
            processedTextboxes.add(ptb);
        }

        processedTextboxes.sort(new SortByStartWord());
        // find character space estimate

        for (int i = 0; i < processedTextboxes.size(); i++) {
            TextBox textBox1 = processedTextboxes.get(i);

            if (textBox1.isProcessed()) continue;

            float top1   = textBox1.getTop();
            float base1  = textBox1.getBase();
            float left1  = textBox1.getLeft();
            float right1 = textBox1.getRight();

            List<BaseTextBox> tbList1 = new ArrayList<>();
            for (int j = 0; j < processedTextboxes.size(); j++) {
                if (i == j) continue;
                TextBox textBox2 = processedTextboxes.get(j);

                if (textBox2.isProcessed()) continue;

                float top2    = textBox2.getTop();
                float base2   = textBox2.getBase();
                float left2   = textBox2.getLeft();
                float right2  = textBox2.getRight();

                float vertical_overlap = getOverlap(top1, base1, top2, base2, .5f);
                if (vertical_overlap <= 0) continue;

                boolean isNeighbour = isNeighbour(textBox1, textBox2);
                if (!isNeighbour) continue;

                List<BaseTextBox> childs2 = textBox2.getChilds();

                if (childs2.isEmpty()){
                    tbList1.add(textBox2);
                } else {
                    tbList1.addAll(childs2);
                }

                textBox2.setProcessed(true);
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

        Iterator<TextBox> it = processedTextboxes.listIterator();
        while (it.hasNext()) {
            TextBox tb = it.next();
            List<BaseTextBox> childTextBoxes = tb.getChilds();
            if (tb.isProcessed()) {
                it.remove();
            } else if (childTextBoxes.isEmpty()){
                it.remove();
            }
        }

        return processedTextboxes;
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

    private static float getOverlap(float top1, float base1,
                             float top2, float base2,
                             float vertical_overlap_ratio)
    {
        // case 1:
        //     ---     |     ---
        //         --- | ---
        //         --- | ---
        //     ---     |     ---


        //                    2
        //     ---     |     ---
        //         --- |  1  ---
        //         --- | ---
        //     ---     | ---

        float h2 = base2 - top2;
        float h1 = base1 - top1;
        if (h2 == 0 || h1 == 0) return 0;

        // h1 100% covers h2
        if (top2 >= top1 && base2 <= base1) {
            return 1;
        }

        // h2 100% covers h1
        if (top2 <= top1 && base2 >= base1) {
            return 1;
        }


        // case 2:

        if (top2 <= top1 && base2 <= base1) {
            if (base2 >= top1){
                float overlap = base2 - top1;
                if (overlap < 0) {
                    return 0;
                }
                float overlap_ratio_1 = overlap / h1;
                float overlap_ratio_2 = overlap / h2;
                if (overlap_ratio_1 > vertical_overlap_ratio && overlap_ratio_2 > vertical_overlap_ratio) return overlap;
            }
            return 0;
        }
        if (top2 >= top1 && base2 >= base1) {
            if (base1 >= top2){
                float overlap = base1 - top2 ;
                if (overlap < 0) {
                    return 0;
                }
                float overlap_ratio_1 = overlap / (base1 - top1);
                float overlap_ratio_2 = overlap / (base2 - top2);
                if (overlap_ratio_1 > vertical_overlap_ratio && overlap_ratio_2 > vertical_overlap_ratio) return overlap;
            }
            return 0;
        }

        //       ---  |   ---
        //   ---      |       ---
        //       ---  |   ---
        //   ---      |       ---

        return 0;
    }

    private static float getSingleSpaceEstimate(BaseTextBox tb){
        String str = tb.getStr();
        if (str.length() == 0) return 0;
        float length = 0;
        for (int i=0; i< str.length(); i++){
            Character c = str.charAt(i);
            Float f = ratios.get(c);
            float s = f == null ? 50 : f;
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
        float avg;
        float std;
        List<BaseTextBox> textBoxes;

        public MeanVar(List<BaseTextBox> textBoxes){
            this.textBoxes = textBoxes;
        }

        public void calc(boolean removeEmptyStr){
            double powerSum1 = 0;
            double powerSum2 = 0;

            Iterator<BaseTextBox> it = textBoxes.listIterator();
            while (it.hasNext()){
                BaseTextBox textBox = it.next();
                if (removeEmptyStr) {
                    String str = textBox.getStr();
                    if (str == null || str.trim().isEmpty()) {
                        it.remove();
                        continue;
                    }
                }

                double w = (textBox.getRight() - textBox.getLeft() ) / textBox.getStr().length();
                powerSum1 += w;
                powerSum2 += Math.pow(w, 2);
            }

            float n = textBoxes.size();
            avg = (float) powerSum1 / n;
            std = (float) Math.sqrt(n * powerSum2 - Math.pow(powerSum1, 2)) / n;
        }

        public float sigma(){
            float ww = avg - std;
            if (ww < 1f) ww = 1f;
            return ww;
        }
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
