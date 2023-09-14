package com.quantxt.doc.helper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.doc.helper.textbox.TextBox;
import com.quantxt.model.*;
import com.quantxt.model.Dictionary;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.QTDocumentHelper;

import javax.swing.text.TabExpander;

import static com.quantxt.doc.helper.textbox.TextBox.*;
import static com.quantxt.doc.helper.textbox.TextBox.getProjectedXLength;
import static com.quantxt.model.DictSearch.AnalyzType.SIMPLE;
import static com.quantxt.model.DictSearch.Mode.ORDERED_SPAN;
import static com.quantxt.types.QSpan.EXTBOXType.*;
import static com.quantxt.nlp.search.QTSearchable.*;
import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingInt;

/**
 * Created by dejani on 1/24/18.
 */

public class CommonQTDocumentHelper implements QTDocumentHelper {

    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelper.class);

    private static String AUTO = "__AUTO__";
    private static Character PAD_CH = ' ';

    private static int max_string_length_for_search = 30000;
    private static Pattern WORD_PTR = Pattern.compile("\\S+");
    private static Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static Pattern SPC_BET_WORDS_PTR = Pattern.compile("\\S(?= \\S)");
    private static Pattern LONG_SPACE = Pattern.compile(" {5,}");
    private static Pattern START_SPACE = Pattern.compile("^ *");
    // bullets
    protected static String UTF8_BULLETS = "\\u2022|\\u2023|\\u25E6|\\u2043|\\u2219";
    protected static Pattern UTF8_TOKEN = Pattern.compile("^(?:[a-zA-Z]\\.){2,}|([\\p{L}\\p{N}]+[\\.\\&]{0,1}[\\p{L}\\p{N}])");
    final private static Pattern simple_form_val   = Pattern.compile("^[^\\p{L}\\:]*: *((?:\\S([^\\:\\s]+ )*[^\\:\\s]+))(?=$|\\s{2,})");
    final private static String begin_pad = "(?<=^|[:\\s])";
    final private static String end_pad   = "(?=$|\\s)";
    final private static String genricPharse =  "([^:\\s]+)";
    final private static String SpcCharacter1 =  "[0-9_\\-\\.\\/\\)\\(\\*]+";
    final private static String numAlphabet =  "(\\p{N}[\\p{L}\\p{N}\\-\\/\\)\\(\\.]+|[\\p{L}]|[\\p{L}\\-]+\\p{N}[\\p{L}\\p{N}\\-\\/\\)\\(\\.]*)";
    final private static Pattern FormKey  = Pattern.compile("(?<=\\s{2})(\\p{L}(?:[^\\: \\n]+ )*[^\\:\\n ]+ {0,20}[\\:](?:(?: {0,35}(?:(?:[^ \\n]*[^\\:\\n]) ){0,4}[^ \\n]+[^\\:])))"+ end_pad);
    final private static Pattern GenericDate1  = Pattern.compile(begin_pad + "((?:[1-9]|[0123]\\d)[ -\\/\\.](?:[1-9]|[0123]\\d)[ -\\/\\.](?:19\\d{2}|20\\d{2}|\\d{2}))" + end_pad);  // mm dd yyyy
    final private static Pattern GenericDate2  = Pattern.compile(begin_pad + "([12]\\d{3}[ -\\/](?:0[1-9]|1[0-2])[ -\\/](?:0[1-9]|[12]\\d|3[01]))" + end_pad);  // YYYY-mm-dd
    // Dec 12, 2013
    final private static Pattern GenericDate3  = Pattern.compile(begin_pad + "[A-Za-z]{3,9}[ ,]{1,3}\\d{1,2}[ ,]{1,3}\\d{4}" + end_pad);  // YYYY-mm-dd
    // 12 Dec 2013
    private static Pattern GenericDate4  = Pattern.compile(begin_pad + "\\d{1,2}[ ,]{1,3}[A-Za-z]{3,9}[ ,]{1,3}\\d{4}" + end_pad);  // YYYY-mm-dd

 //   final private static Pattern Numbers       = Pattern.compile(begin_pad + "((?:\\-)?(?:\\p{Sc} {0,6})?[+\\-#]{0,1}[0-9]{1,3}(?:[\\.,]?[0-9]{3})*(?:(?:\\S+ ){0,4}\\S*))(?=  |\\n)");  // mm dd yyyy

    final private static Pattern Numbers       = Pattern.compile(begin_pad + "((?:\\-)?(?:\\p{Sc} {0,6})?[+\\-#]{0,1}[0-9]{1,3}(?:[\\.,]?[0-9]{3})*(?:[,\\.][0-9]{2})?%?)" + end_pad);  // mm dd yyyy
    final private static Pattern Id1           = Pattern.compile(begin_pad + "(" +numAlphabet + ")"+ end_pad);
    final private static Pattern ShortDesc1    = Pattern.compile(begin_pad + "((?:" +SpcCharacter1 + " )*" + SpcCharacter1 +")" + end_pad);
    final private static Pattern GenericToken = Pattern.compile("(\\S+)");
    final private static Pattern Generic = Pattern.compile("(?<= )" + "((?:"+ genricPharse+" ){0,15}" + genricPharse + ")"  + "(?=$|\\:?\\s)");
    final private static Pattern [] AUTO_Patterns = new Pattern[] {GenericDate1, GenericDate2, GenericDate3, GenericDate4, Numbers, ShortDesc1, Id1};

    protected Analyzer analyzer;

    public CommonQTDocumentHelper() {
        analyzer = new ClassicAnalyzer();
    }

    @Override
    public List<String> tokenize(String str) {
        List<String> tokens = new ArrayList<>();
        try {
            TokenStream result = analyzer.tokenStream(null, str);
            CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
            result.reset();

            while (result.incrementToken()) {
                tokens.add(resultAttr.toString());
            }
            result.close();
        } catch (Exception e) {
            logger.error("Analyzer: " + e.getMessage());
        }
        return tokens;
    }

    private String tokenize(Analyzer analyzer , String str){
        StringBuilder sb = new StringBuilder();
        try {
            TokenStream result = analyzer.tokenStream(null, str);
            CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
            result.reset();

            while (result.incrementToken()) {
                sb.append(resultAttr.toString()).append(" ");
            }
            result.close();
        } catch (Exception e) {
            logger.error("Analyzer: " + e.getMessage());
        }
        return sb.toString().trim();
    }

    private void findLabelsHelper(DictSearch dictSearch,
                                  Map<Integer, BaseTextBox> lineTextBoxMap,
                                  String content,
                                  int shift,
                                  int slop,
                                  List<QSpan> dicLabels,
                                  boolean isolatedLabelsOnly){
        if (content.isEmpty()) return;
        dictSearch.reset();
        Collection<QSpan> qspans = dictSearch.search(content, lineTextBoxMap, slop, isolatedLabelsOnly);

        for (QSpan qSpan : qspans) {
            ExtInterval ext = qSpan.getExtInterval(false);
            int start = ext.getStart() + shift;
            int end = ext.getEnd() + shift;
            ext.setStart(start);
            ext.setEnd(end);
            dicLabels.add(qSpan);
        }
    }

    private Map<String, Collection<QSpan>> findLabels(List<DictSearch> extractDictionaries,
                                                      Map<Integer, BaseTextBox> lineTextBoxMap,
                                                      String content,
                                                      int slop,
                                                      boolean isolatedLabelsOnly) {
        int content_length = content.length();
        Map<String, Collection<QSpan>> labels = new LinkedHashMap<>();
        boolean hasTextBoxes = lineTextBoxMap!=null;

        for (DictSearch dictSearch : extractDictionaries) {
            String dict_id = dictSearch.getDictionary().getId();
            List<QSpan> dicLabels = new ArrayList<>();

            if (content_length > max_string_length_for_search){
                int cnt_idx = 0;
                while (cnt_idx < content_length){
                    String cnt_chunk = content.substring(cnt_idx, Math.min(cnt_idx + max_string_length_for_search, content_length));
                    findLabelsHelper(dictSearch, lineTextBoxMap, cnt_chunk, cnt_idx, slop, dicLabels, isolatedLabelsOnly);
                    dicLabels.addAll(dictSearch.postSearch(hasTextBoxes));
                    cnt_idx += max_string_length_for_search - 1000;
                }
            } else {
    //            findLabelsHelper(dictSearch, lineTextBoxMap, content, 0, slop, dicLabels, isolatedLabelsOnly);
                dictSearch.reset();
                dictSearch.search(content, lineTextBoxMap, slop, isolatedLabelsOnly);
            }
            if (!dicLabels.isEmpty()) {
                labels.put(dict_id, dicLabels);
            }
        }
        if (content_length > max_string_length_for_search){
            //excel files, CSV, etc. stuff without textboxes
            return labels;
        } else {
            for (int i=0 ; i<extractDictionaries.size(); i++) {
                QTSearchable dictSearch1 = (QTSearchable) extractDictionaries.get(i);
                String dict_id = dictSearch1.getDictionary().getId();

                for (int j=0 ; j<extractDictionaries.size(); j++) {
                    if (i == j) continue;
                    QTSearchable dictSearch2 = (QTSearchable) extractDictionaries.get(j);
                    dictSearch1.filterOverlap(dictSearch2);
                }

                List<QSpan> dicLabels = dictSearch1.postSearch(hasTextBoxes);
                if (!dicLabels.isEmpty()) {
                    labels.put(dict_id, dicLabels);
                }
            }
            return labels;
        }
    }

    private Map<Integer, BaseTextBox> getLineTextBoxMap(List<BaseTextBox> rawTextBoxes){
        if (rawTextBoxes == null || rawTextBoxes.size() == 0) return null;

        rawTextBoxes.sort((p1, p2) -> Float.compare(p1.getBase(), p2.getBase()));
        List<BaseTextBox> textBoxes = TextBox.process(rawTextBoxes);
        Map<Integer, BaseTextBox> lineTextBoxes = new TreeMap<>();
        if (textBoxes == null) return lineTextBoxes;
        for (BaseTextBox tb : textBoxes){
            int l = tb.getLine();
            lineTextBoxes.put(l, tb);
        }
        return lineTextBoxes;
    }

    private Map<Integer, List<ExtIntervalTextBox>> getLocalLineAndTextBox3(Map<String, Collection<QSpan>> labels){
        Map<Integer, List<ExtIntervalTextBox>> lineTolabelsWithLineAndTextbox = new HashMap<>();
        for (Map.Entry<String, Collection<QSpan>> e : labels.entrySet()) {
            for (QSpan qSpan : e.getValue()) {
                ExtIntervalTextBox eitb = new ExtIntervalTextBox(qSpan.getExtInterval(true), qSpan.getTextBox());
                List<ExtIntervalTextBox> tbList = lineTolabelsWithLineAndTextbox.get(qSpan.getLine());
                if (tbList == null) {
                    tbList = new ArrayList<>();
                    lineTolabelsWithLineAndTextbox.put(qSpan.getLine(), tbList);
                }
                tbList.add(eitb);
            }
        }
        return lineTolabelsWithLineAndTextbox;
    }

    private boolean hasOvelapWLabels(AutoValue autoValue, List<QSpan> allLabels){
        if (autoValue.hValue == null && autoValue.vValue.size() == 0) return true;
        if (autoValue.hValue != null){
            for (QSpan qSpan : allLabels){
                BaseTextBox bt = qSpan.getTextBox();
                if (bt == null) continue;
                float ho = getHorizentalOverlap(autoValue.hValue.getTextBox(), bt);
                float vo = getVerticalOverlap(autoValue.hValue.getTextBox(), bt);
                if (ho >0 && vo >0){
                    autoValue.hValue = null;
                    return true;
                }
            }
        }

        if (autoValue.vValue.size() > 0){
            for (QSpan qSpan : allLabels){
                BaseTextBox bt = qSpan.getTextBox();
                if (bt == null) continue;
                float ho = getHorizentalOverlap(autoValue.vValue.get(0).getTextBox(), bt);
                float vo = getVerticalOverlap(autoValue.vValue.get(0).getTextBox(), bt);
                if (ho >0 && vo >0){
                    autoValue.vValue = null;
                    return true;
                }
            }
        }
        return false;
    }

    private Map<Integer, BaseTextBox> getFakeLineTextBoxMapFromContent(String content){

        String [] lines = content.split("\n");
        // we multiple lines and start positions by 10
        Map<Integer, BaseTextBox> lineTextBox = new HashMap<>();
        for (int line=0; line < lines.length; line++){
            String line_str = lines[line];
            Matcher m = GenericToken.matcher(line_str);
            BaseTextBox firstTextBox = null;
            while (m.find()){
                int start = m.start(1);
                int end = m.end(1);
                int top = 10 * line;
                int base = 10 * line + 9;
                int left = 10 * start;
                int right = 10 * end;

                BaseTextBox baseTextBox = new BaseTextBox(top, base, left, right, m.group(1));
                baseTextBox.setStart(start);
                baseTextBox.setEnd(end);
                baseTextBox.setLine(line);
                if (firstTextBox == null){
                    baseTextBox.setLine_str(line_str);
                    firstTextBox = baseTextBox;
                }
                firstTextBox.getChilds().add(baseTextBox);
            }
            lineTextBox.put(line, firstTextBox);
        }
        return lineTextBox;
    }

    private String removeTextHeavyParts(String content){
        Pattern p = Pattern.compile("^ *(([A-Za-z\\-,;\\'\\(\\)\\.\\/\\@\\$]+ {1,2}){5,}[;\\'\\/\\)\\(A-Za-z\\-,\\.\\@\\$]+) *$");
        String [] lines = content.split("\\n");
        List<String> newLines = new ArrayList<>();
        for (String line : lines) {
            Matcher m = p.matcher(line);
    //        if (line.length() == 0) continue;
     //       String lean_line = line.replaceAll("(([A-Za-z\\-,]+ {1,2}){3,}[A-Za-z\\-]+)", "");
     //       lean_line = lean_line.replaceAll(" ", "").trim();
     //       float r = (float) lean_line.length() / (float) line.length();
            if (m.find() ){
                int l = line.length();
                StringBuilder sb = new StringBuilder();
                for (int i=0; i< l; i++){
                    sb.append(" ");
                }
                newLines.add(sb.toString());
            } else {
                newLines.add(line);
            }
        }
        return String.join("\n", newLines);
    }
    private String removeLabels(String content,
                                Map<String, Collection<QSpan>> labels){

        char [] stripped = content.toCharArray();

        for (Map.Entry<String, Collection<QSpan>> entry : labels.entrySet()){
            for (QSpan l : entry.getValue()){
                if (l.getExtIntervalTextBoxes() == null) continue;
                for (ExtIntervalTextBox etb : l.getExtIntervalTextBoxes()){
                    int s = etb.getExtInterval().getStart();
                    int e = etb.getExtInterval().getEnd();
                    for (int c=s; c<e; c++) {
                        stripped[c] = PAD_CH;
                    }
                }
            }
        }

        return String.valueOf(stripped);
    }

    private List<DictSearch> getFormFields(final String content){
        Matcher m = FormKey.matcher(content);
        List<DictSearch> dictSearches = new ArrayList<>();
        while (m.find()){
            String f = m.group(1);
            List<DictItm> dictItmList = new ArrayList<>();
            dictItmList.add(new DictItm(f, f));
            dictItmList.add(new DictItm(f, f.replaceAll("\\s+", "")));
            com.quantxt.model.Dictionary dictionary = new com.quantxt.model.Dictionary(f, f, dictItmList);

            DictSearch dictSearch = new QTSearchable(dictionary,
                    null,
                    null,
                    null, ORDERED_SPAN,
                    SIMPLE);
            dictSearches.add(dictSearch);
        }

        return dictSearches;
    }

    private static class TableHeader {
        int firstRow;
        final QSpan header;
        final List<QSpan> headerList;
        public TableHeader(QSpan h){
            headerList = new ArrayList<>();
            header = h;
        }
    }

    private List<TableHeader> detectTableHeadersHelper(List<QSpan> allLabels,
                                                       QCollection vertical_matches,
                                                       float upper_limit_white_region){
        allLabels.sort(
                (QSpan qs2, QSpan qs1) -> Float.compare(qs1.getBase() - qs1.getTop(),
                        qs2.getBase() - qs2.getTop())
        );
        List<TableHeader> tableHeaders = new ArrayList<>();

        for (int i = 0; i< allLabels.size(); i++){
            QSpan qSpan1 = allLabels.get(i);
            BaseTextBox bt1 = qSpan1.getTextBox();
            if (bt1 == null) continue;
            if (qSpan1.getSpanType() != null) continue;
            // we look for labels that are not overlapping - spaced-out columns
            List<QSpan> hdrs = new ArrayList<>();
            hdrs.add(qSpan1);
            for (int j =i+1; j< allLabels.size(); j++){
                QSpan qSpan2 = allLabels.get(j);
                BaseTextBox bt2 = qSpan2.getTextBox();
                float ho = getHorizentalOverlap(bt1, bt2);
                float vo = getVerticalOverlap(bt1, bt2);
                if (ho < .1 && vo > .15f){
                    hdrs.add(qSpan2);
                }
            }
            if (hdrs.size() == 1) continue;
            QSpan tblHdr = new QSpan();
            TableHeader tableHeader = new TableHeader(tblHdr);
            tableHeader.headerList.addAll(hdrs);
            for (QSpan qs : hdrs){
                qs.setSpanType(VERTICAL_MANY);
                for (ExtIntervalTextBox ext : qs.getExtIntervalTextBoxes()){
                    tblHdr.add(ext);
                }
            }
            tblHdr.process(null);
            tableHeader.firstRow = qSpan1.getLine();
            tableHeaders.add(tableHeader);
        }

        // for each header find out how much gap we have that is not a value
        ListIterator<TableHeader> iter = tableHeaders.listIterator();
        while (iter.hasNext()){
            TableHeader tableHeader = iter.next();
            int first_line = tableHeader.firstRow;
            List<BaseTextBox> all_candide_values = new ArrayList<>();
            for (int lineNumber=first_line-1; lineNumber < first_line + 5; lineNumber++) {
                List<QSpan> vals = vertical_matches.get(lineNumber);
                if (vals == null) continue;
                for (QSpan qs : vals){
                    all_candide_values.add(qs.getTextBox());
                }
            }
            List<QSpan> table_hdrs = tableHeader.headerList;
            if (table_hdrs.size() == 1) continue;
            Collections.sort(table_hdrs, Comparator.comparingDouble(o -> o.getLeft()));
            BaseTextBox header_box = tableHeader.header.getTextBox();
            float top = header_box.getTop();
            float base = header_box.getBase();

            List<BaseTextBox> detected_areas_tbs = new ArrayList<>();
            List<BaseTextBox> overlaping_header_tbs = new ArrayList<>();

            for (ExtIntervalTextBox etb : table_hdrs.get(table_hdrs.size()-1).getExtIntervalTextBoxes()) {
                BaseTextBox btb = etb.getTextBox();
                detected_areas_tbs.add(etb.getTextBox());
                overlaping_header_tbs.add(btb);
            }

            for (int i=0; i<table_hdrs.size()-1; i++){
                QSpan cur = table_hdrs.get(i);
                QSpan next = table_hdrs.get(i+1);
                BaseTextBox gap = new BaseTextBox(top, base, cur.getRight(), next.getLeft(), "");

                for (ExtIntervalTextBox etb : cur.getExtIntervalTextBoxes()) {
                    BaseTextBox btb = etb.getTextBox();
                    overlaping_header_tbs.add(btb);
                }

                boolean valueDetected = false;
                for (BaseTextBox val_bt : all_candide_values) {
                    float ho = getHorizentalOverlap(gap, val_bt);
                    if (ho < .1) continue;
                    float vo = getVerticalOverlap(gap, val_bt);
                    if (ho > .5f && vo > .5f) {
                        valueDetected = true;
                        break;
                    }
                }
                if (!valueDetected){
                    for (ExtIntervalTextBox etb : cur.getExtIntervalTextBoxes()) {
                        BaseTextBox btb = etb.getTextBox();
                        detected_areas_tbs.add(etb.getTextBox());
                        overlaping_header_tbs.add(btb);
                    }
                    detected_areas_tbs.add(gap);
                }
            }

            float header_length = header_box.getRight() - header_box.getLeft();
            float fully_detected_region = getProjectedXLength(detected_areas_tbs);
            float not_detected_region = header_length - fully_detected_region;
            float r_not_detected_region = not_detected_region / header_length;

            if (r_not_detected_region < upper_limit_white_region) {
                for (QSpan l : allLabels){
                    BaseTextBox bl = l.getTextBox();
                    float ho = getHorizentalOverlap(bl, header_box);
                    float vo = getVerticalOverlap(bl, header_box);
                    if (ho > .97 & vo > .97){
                        l.setSpanType(VERTICAL_MANY);
                    }
                }

            } else {
                //    logger.info("Removed 1 Header -> {}", sb);
                iter.remove();
                for (QSpan q : tableHeader.headerList){
                    q.setSpanType(null);
                }
            }
        }

        List<TableHeader> refinedHeaders = new ArrayList<>();
        //combine table headers if they have overlap
        // this is needed for split row tables and cases where document is skewed
        for (int i = 0; i< tableHeaders.size(); i++){
            TableHeader tableHeader1 = tableHeaders.get(i);
            QSpan qSpan1 = tableHeader1.header;
            if (qSpan1.getSpanType() != null) continue;
            BaseTextBox bt1 = qSpan1.getTextBox();
            if (bt1 == null) continue;
            if (qSpan1.getSpanType() != null) continue;
            // we look for labels that are not overlapping - spaced-out columns
            List<TableHeader> combinedHeader = new ArrayList<>();
            for (int j =i+1; j< tableHeaders.size(); j++){
                TableHeader tableHeader2 = tableHeaders.get(j);
                QSpan qSpan2 = tableHeader2.header;
                BaseTextBox bt2 = qSpan2.getTextBox();
                float ho = getHorizentalOverlap(bt1, bt2);
                float vo = getVerticalOverlap(bt1, bt2);
                if (ho > .2 && vo > .2f){
                    combinedHeader.add(tableHeader2);
                }
            }
            refinedHeaders.add(tableHeader1);
            if (combinedHeader.size() == 0) continue;
            for (TableHeader tblh : combinedHeader){
                tblh.header.setSpanType(VERTICAL_MANY);
                tableHeader1.headerList.addAll(tblh.headerList);
            }
            tableHeader1.header.process(null);
            tableHeader1.header.setSpanType(VERTICAL_MANY);
        }

        return refinedHeaders;
    }
    private List<TableHeader> detectTableHeaders(List<QSpan> allLabels,
                                                 Map<Integer, BaseTextBox> lineTextBoxMap,
                                                 QCollection vertical_matches,
                                                 float upper_limit_white_region){

        // find table headers
        List<TableHeader> tableHeaders = detectTableHeadersHelper(allLabels, vertical_matches, upper_limit_white_region);

        int nextLineToFindTable = 0;
        for (int lineNumber=0; lineNumber <= vertical_matches.getMax_line(); lineNumber++){
            // table needs to have at least two columns and two rows
            // or at least 3 columns and 1 row
            // so minimum 2x2 or 1x3 tables
            if ( lineNumber < nextLineToFindTable) continue;

            List<QSpan> cols = vertical_matches.get(lineNumber);
            if (cols == null) continue;
            // check if the values can be table headers
            int num_good_headers = 0;
            for (QSpan c : cols){
                String str = c.getStr().trim();
                int t_lgh = str.length();
                if (t_lgh == 0) continue;
                float all_char_lgh = (float) str.replaceAll("[^\\p{L}]", "").length();
                if ((all_char_lgh/ (float) t_lgh) < .6){
                    logger.debug("Not a valid header {}", str);
                    continue;
                }
                num_good_headers++;
            }
            if (num_good_headers < 2) continue;
            Collections.sort(cols, Comparator.comparingInt(o -> o.getStart()));

            boolean isTable = true;
            if (cols.size() == 2){
                if (cols.get(0).getExtIntervalTextBoxes().size() < 2 ||
                        cols.get(1).getExtIntervalTextBoxes().size() < 2){
                    isTable = false;
                }
            }
            if (!isTable) continue;

            // so it is either a vertical header or horizontal column
            // we look for labels that are not overlapping - spaced-out columns
            for (QSpan col : cols) {
                List<ExtIntervalTextBox> eitbs = col.getExtIntervalTextBoxes();
                if (eitbs == null) continue;
                int rows = eitbs.size();
                if (rows < 2) continue;
                int numOverlaps = 0;
                boolean labelHasOverlap = false;
                List<QSpan> hdrs_cols = new ArrayList<>();
                for (ExtIntervalTextBox etb : eitbs) {
                    BaseTextBox bt2 = etb.getTextBox();
                    if (bt2 == null) continue;
                    ExtInterval ext = etb.getExtInterval();
                    for (QSpan qSpan : allLabels) {
                        if (Math.abs(ext.getLine() - qSpan.getLine()) > 5) continue;
                        BaseTextBox bt1 = qSpan.getTextBox();
                        if (bt1 == null) continue;
                        float ho = getHorizentalOverlap(bt1, bt2);
                        float vo = getVerticalOverlap(bt1, bt2);
                        if (ho > .1 && vo > .1f) {
                            hdrs_cols.add(qSpan);
                            labelHasOverlap = true;
                            numOverlaps++;
                            break;
                        }
                    }
                    if (labelHasOverlap) break;
                }

                if ((rows == 2 && numOverlaps == 2) || (rows == 3 && numOverlaps > 1) ||
                        rows == 4 && numOverlaps > 2 || (float)numOverlaps / (float) rows >= .6f){
                    for (QSpan qSpan : hdrs_cols){
                        qSpan.setSpanType(HORIZENTAL_ONE);
                    }
                }
            }

            // The header must have at least two labels
            List<QSpan> hdrs = new ArrayList<>();
            for (QSpan qSpan : allLabels){
                if (qSpan.getSpanType() != null) continue;
                BaseTextBox bt1 = qSpan.getTextBox();
                if (bt1 == null) continue;
                // we look for labels that are not overlapping - spaced-out columns
                boolean labelHasOverlap = false;
                for (QSpan qs : hdrs){
                    BaseTextBox btl = qs.getTextBox();
                    float ho = getHorizentalOverlap(bt1, btl);
                    float vo = getVerticalOverlap(bt1, btl);
                    if (ho > .1 && vo > .1f){
                        labelHasOverlap = true;
                        break;
                    }
                }
                if (labelHasOverlap) continue;
                for (QSpan col : cols){
                    BaseTextBox bt2 = col.getTextBox();
                    if (bt2 == null) continue;
                    float ho = getHorizentalOverlap(bt1, bt2);
                    float vo = getVerticalOverlap(bt1, bt2);
                    if (ho > .1 && vo > .1f){
                        hdrs.add(qSpan);
                    }
                }
            }

            if (hdrs.size() > 1){
                QSpan tblHdr = new QSpan();
                for (QSpan qs : hdrs){
                    qs.setSpanType(VERTICAL_MANY);
                    for (ExtIntervalTextBox ext : qs.getExtIntervalTextBoxes()){
                        tblHdr.add(ext);
                    }
                }

                for (QSpan col : cols){
                    col.setSpanType(VERTICAL_MANY);
                }
                tblHdr.process(null);
                TableHeader tableHeader = new TableHeader(tblHdr);
                tableHeader.firstRow = lineNumber;
                tableHeaders.add(tableHeader);
            }
        }

        tableHeaders.sort(comparingInt((TableHeader qs2) -> qs2.firstRow));

        // let's find stacked up labels as well
        // if we have headers stacked up, there are form values:
        // Key1     val1
        // Key2     val2
        // Key3     val3

        Collections.sort(allLabels, Comparator.comparingInt(o -> o.getLine()));
        for (int i=0; i<allLabels.size(); i++){
            QSpan qSpan1 = allLabels.get(i);
            BaseTextBox bt1 = qSpan1.getTextBox();
            if (bt1 == null) continue;
            Integer lastline = qSpan1.getLine();
            //line to QSPAN
            // stacking spans can NOT be on the sample line
            TreeMap<Integer, QSpan> h_candidates = new TreeMap<>();

            h_candidates.put(lastline , qSpan1);
            float h = bt1.getBase() - bt1.getTop();
            for (int j=i+1; j<allLabels.size(); j++) {
                QSpan qSpan2 = allLabels.get(j);
                BaseTextBox bt2 = qSpan2.getTextBox();
                if (bt2 == null) continue;
                Integer line2 = qSpan2.getLine();
                int lineDiff = line2 - lastline;
                if (lineDiff <= 0) continue;
                // we look for labels that are not overlapping - spaced-out columns
                if (lineDiff > 3 ) break;
                boolean isCandidate = true;
                for (int k=lastline+1; k < line2; k++){
                    BaseTextBox inbetween = lineTextBoxMap.get(k);
                    if (inbetween == null) continue;
                    for (BaseTextBox bt : inbetween.getChilds()) {
                        if (bt == null) continue;
                        float ho = getHorizentalOverlap(bt2, bt);
                        if (ho > 0) {
                            isCandidate = false;
                            break;
                        }
                    }
                    if (!isCandidate) break;
                }

                if (!isCandidate) break;
                if (Math.abs(bt2.getLeft() - bt1.getLeft()) < h) {
                    h_candidates.putIfAbsent(line2, qSpan2);
                }
                lastline = line2;
            }
            if (h_candidates.size() < 2) continue;
            int first_overlap = h_candidates.firstKey();
            int last_overlap = h_candidates.lastKey();

            int diff = last_overlap - first_overlap + 1;
            float ratio = (float) (h_candidates.size()) / (float) diff;

            List<QSpan> h_values = new ArrayList<>(h_candidates.values());
            // we don't mark the last row. we have cases such as
            //key1     val2
            //key2     val5
            //key3     key4     key5
            if (h_values.size() == 2){
                if (ratio == 1f){
                    h_values.get(0).setSpanType(HORIZENTAL_ONE);
        //            for (QSpan qs : h_candidates.values()) {
        //                qs.setSpanType(HORIZENTAL_ONE);
        //            }
                }
            } else if (h_values.size() == 3){
                if (ratio > .65f){
                    h_values.get(0).setSpanType(HORIZENTAL_ONE);
                    h_values.get(1).setSpanType(HORIZENTAL_ONE);
        //            for (QSpan qs : h_candidates.values()) {
        //                qs.setSpanType(HORIZENTAL_ONE);
        //            }
                }
            } else if (ratio >= .6f){
                for (int k=0; k<h_values.size()-1; k++ ) {
                    h_values.get(k).setSpanType(HORIZENTAL_ONE);
                }
        //        for (QSpan qs : h_candidates.values()) {
        //            qs.setSpanType(HORIZENTAL_ONE);
        //        }
            }
        }
        // let's add left over labels to table headers

        for (TableHeader th : tableHeaders) {
            BaseTextBox btl = th.header.getTextBox();
            List<QSpan> uniqs = new ArrayList<>();
            HashSet<String> keys = new HashSet<>();
            String headerKey = getKey(th.header);
            keys.add(headerKey);
            for (QSpan qSpan : allLabels) {
                if (qSpan.getSpanType() != null) continue;
                BaseTextBox bt1 = qSpan.getTextBox();
                if (bt1 == null) continue;
                String key = getKey(qSpan);
                if (keys.contains(key)) continue;
                keys.add(key);
                float ho = getHorizentalOverlap(bt1, btl);
                float vo = getVerticalOverlap(bt1, btl);
                if (ho > .1 && vo > .1f) {
                    qSpan.setSpanType(VERTICAL_MANY);
                    uniqs.add(qSpan);
                } else if (vo > .9f){
           //         float length = bt1.getRight() - bt1.getLeft();
           //         float dist1 = bt1.getLeft() >= btl.getRight() ?
           //                 bt1.getLeft() - btl.getRight() : btl.getLeft() - bt1.getRight();
            //        if (dist1 < 3 * length) {
                    qSpan.setSpanType(VERTICAL_MANY);
                    uniqs.add(qSpan);
            //        }
                }
            }
            if (uniqs.size() > 0) {
                List<ExtIntervalTextBox> eitbs = th.header.getExtIntervalTextBoxes();
                for (QSpan qs : uniqs) {
                    eitbs.addAll(qs.getExtIntervalTextBoxes());
                }
                th.header.process(null);
            }
        }


        //debug//

        for (TableHeader th : tableHeaders){
            StringBuilder sb = new StringBuilder();
            for (ExtIntervalTextBox ext : th.header.getExtIntervalTextBoxes()){
                ExtInterval e = ext.getExtInterval();
                sb.append(e.getStr()).append(" ");
            }
            sb.append("   ");
            logger.info("HEADER -> {}", sb);
        }

        return tableHeaders;
    }

    private void detectHorizentalLabels(List<QSpan> labels,
                                        Map<Integer, BaseTextBox> lineTextBoxMap){
        // first find labels that are aligned by left side
        // Label 1 hfhfd
        // Label 2 hfhfd

        labels.sort((o1 , o2) -> Float.compare(o1.getBase() , o2.getBase()));
        for (int i=0; i <labels.size(); i++){
            QSpan qSpan1 = labels.get(i);
            if (qSpan1.getSpanType() == VERTICAL_MANY) continue;
            BaseTextBox bt1 = qSpan1.getTextBox();
            if (bt1 == null) continue;
            int line1 = qSpan1.getLine();
            float h1 = qSpan1.getBase() - qSpan1.getTop();
            for (int j=i+1; j < labels.size(); j++){
                QSpan qSpan2 = labels.get(j);
                if (qSpan2.getSpanType() == VERTICAL_MANY) continue;
                if (qSpan2.getDict_id().equals(qSpan1.getDict_id())) continue;
                if (qSpan2.getTop() < qSpan1.getBase()) continue;
                int line2 = qSpan2.getLine();
                float h2 = qSpan2.getBase() - qSpan2.getTop();
                float dist = Math.abs(qSpan2.getTop() - qSpan1.getBase());
                if (dist > 2*h1) break;
                float diff_side = Math.abs(qSpan2.getLeft() - qSpan1.getLeft());
                if (diff_side < 2f){
                    boolean isHrzLabel = true;
                    for (int k=line1+1; k < line2; k++){
                        BaseTextBox bt = lineTextBoxMap.get(k);
                        if (bt == null) continue;
                        for (BaseTextBox bt2 : bt.getChilds()) {
                            if (bt2 == null) continue;
                            float vo = getHorizentalOverlap(bt1, bt2);
                            if (vo > 0) {
                                isHrzLabel = false;
                                break;
                            }
                        }
                        if (! isHrzLabel) break;
                    }
                    // check if there is any value
                    if (isHrzLabel) {
                        qSpan1.setSpanType(HORIZENTAL_ONE);
                        qSpan2.setSpanType(HORIZENTAL_ONE);
                        break;
                    }
                }
            }
        }
    }

    private String getKey(QSpan qSpan){
        StringBuilder sb = new StringBuilder();
        for (ExtIntervalTextBox eib : qSpan.getExtIntervalTextBoxes()){
            ExtInterval ext = eib.getExtInterval();
            sb.append(ext.getLine() + "_" + ext.getStart() + "_" + ext.getEnd()+"_");
        }

        return sb.toString();
    }

    private Map<String, Collection<QSpan>> removeOverlappingLabels2(Map<String, Collection<QSpan>> id2labels,
                                                                    List<DictSearch> extractDictionaries){

        Map<String, Collection<QSpan>> filtered = new HashMap<>();

        List<QSpan> labels = new ArrayList<>();
        for (Map.Entry<String, Collection<QSpan>> e1 : id2labels.entrySet()) {
            labels.addAll(e1.getValue());
        }

        for (QSpan qs : labels){
            float s = getArea(qs.getTextBox());
            qs.setArea(s);
        }

        boolean[] dups = new boolean[labels.size()];
        Arrays.fill(dups, false);

        labels.sort(comparingDouble((QSpan s) -> s.getArea()));

        for (int i=0; i < labels.size(); i++){
            QSpan qSpan1 = labels.get(i);
            String id1 = qSpan1.getDict_id();
            float area1 = qSpan1.getArea();
            if (area1 <= 0) continue;
            BaseTextBox bt1 = qSpan1.getTextBox();
            for (int j=i+1; j < labels.size(); j++){
                QSpan qSpan2 = labels.get(j);
                float area2 = qSpan2.getArea();
                if (area2 <= 0) continue;
                String id2 = qSpan2.getDict_id();
                BaseTextBox bt2 = qSpan2.getTextBox();
                float ho = getHorizentalOverlap(bt1, bt2);
                float vo = getVerticalOverlap(bt1, bt2);
                if (ho == 1f && vo == 1f){
                    if (area2 ==  area1) {
                        if (id1.equals(id2)){
                            dups[i] = true;  // span1 is smaller
                        }
                    } else {
                        if (!id1.equals(id2)){
                            dups[i] = true;  // span1 is smaller
                        }
                    }

                }
            }
        }
        for (int i=0; i<labels.size(); i++){
            if (dups[i]) continue;
            QSpan qSpan = labels.get(i);
            Collection<QSpan> list = filtered.get(qSpan.getDict_id());
            if (list == null){
                list = new ArrayList<>();
            }
            list.add(qSpan);
            filtered.put(qSpan.getDict_id(), list);
        }

        return filtered;
    }


    private Map<String, Collection<QSpan>> removeOverlappingLabels(Map<String, Collection<QSpan>> labels){

        Map<String, Collection<QSpan>> filtered = new HashMap<>();

        for (Map.Entry<String, Collection<QSpan>> e1 : labels.entrySet()){
            List<QSpan> newSpans = new ArrayList<>();
            for (QSpan qSpan1 : e1.getValue()) {
                BaseTextBox bt1 = qSpan1.getTextBox();
                if (bt1 == null) {
                    newSpans.add(qSpan1);
                    continue;
                }
                String key1 = getKey(qSpan1);
                boolean isUniq = true;
                for (Map.Entry<String, Collection<QSpan>> e2 : labels.entrySet()) {
                    for (QSpan qSpan2 : e2.getValue()) {
                        BaseTextBox bt2 = qSpan2.getTextBox();
                        if (bt2 == null) continue;
                        String key2 = getKey(qSpan2);
                        if (key1.equals(key2)) continue;
                        float ho = getHorizentalOverlap(bt1, bt2);
                        float vo = getVerticalOverlap(bt1, bt2);
                        if (ho == 1f && vo == 1f){
                            float s1 = getArea(bt1);
                            float s2 = getArea(bt2);
                    //        if (s2 >= s1) {
                            if (s2 > s1) {
   //                             logger.info("{} has overlap with {}", qSpan1.getStr(), qSpan2.getStr());
                                isUniq = false;
                                break;
                            }
                        }
                    }
                    if (!isUniq) break;
                }
                if (isUniq){
                    newSpans.add(qSpan1);
                }
            }
            if (newSpans.size() >0){
                filtered.put(e1.getKey(), newSpans);
            }
        }
        return filtered;
    }

    private void reviseVerticals(String content,
                                 QCollection all_auto_matches,
                                 QCollection all_auto_matches_generic,
                                 List<QSpan> qSpans){
        // let's find the boundaries:
        String [] lines = content.split("\\n");
        for (QSpan qSpan : qSpans) {
            float left = qSpan.getLeft();
            float right = qSpan.getRight();
            if (qSpan.getSpanType() != VERTICAL_MANY) continue;
            List<Interval> intervals = qSpan.getExtIntervalSimples();
            if (intervals == null) continue;

            for (Interval interval : intervals) {
                String str = interval.getStr();
                int start = interval.getStart();
                int end = interval.getEnd();
                int line = interval.getLine();
                String text_line = lines[line];
                if (text_line == null || text_line.length() < end+2) continue;
                // if next toekn is a value we skip
                List<QSpan> lineValues = all_auto_matches.get(line);
                if (lineValues != null){
                    boolean validValueFollows = false;
                    for (QSpan qs : lineValues){
                        ExtInterval ext = qs.getExtInterval();
                        int s = ext.getStart();
                        if (s == end+1) {
                            validValueFollows = true;
                            break;
                        }
                    }
                    if (validValueFollows) continue;
                }
                String text_after = text_line.substring(end, end+2);
                if (text_after.replaceAll("[ ]", "").isEmpty()) continue;
                List<QSpan> lineGenerics = all_auto_matches_generic.get(line);
                if (lineGenerics == null) continue;
                for (QSpan qs : lineGenerics){
                    ExtInterval ext = qs.getExtInterval();
                    int s = ext.getStart();
                    int e = ext.getStart();
                    if ((s >= start && s <= end) || (start >=s && start <=e) ){
                        logger.info(" {} --> {}", str , ext.getStr());
                    }
                }
            }

            // if a value is found as part of a phrase then we do this:
            // The phrase must have at least one more value associated with the table header
            // otherwise we drop the value
        }
    }
    @Override
    public List<ExtInterval> extract(final String content,
                                     List<DictSearch> extractDictionaries,
                                     List<BaseTextBox> textBoxes,
                                     boolean searchVertical) {

        Map<Integer, BaseTextBox> lineTextBoxMap = getLineTextBoxMap(textBoxes);
        int max_distance_bet_lines = 4;

        int heightMult1 = 15;
        int heightMult2 = 10;

        if (textBoxes == null) {
            // This is for processing nice formatted csv/tsv files and we don't cre about number of empty rows in between
            max_distance_bet_lines = 1000;
            heightMult1 = 1000;
            heightMult2 = 1000;
            lineTextBoxMap = getFakeLineTextBoxMapFromContent(content);
        }

        List<DictSearch> isolated = new ArrayList<>();
        List<DictSearch> nonIsolated = new ArrayList<>();

        for (DictSearch qtSearchable : extractDictionaries) {
            Pattern ptr = qtSearchable.getDictionary().getPattern();
            if (ptr != null && ptr.pattern().equals(AUTO)) {
                isolated.add(qtSearchable);
            } else {
                nonIsolated.add(qtSearchable);
            }
        }

        Map<Integer, BaseTextBox> lineTextBoxMap4NonIsolatedLabels = textBoxes == null ? null :
                lineTextBoxMap;

        Map<String, Collection<QSpan>> isolatedLabels = findLabels(isolated, lineTextBoxMap, content, 0, true);
        Map<String, Collection<QSpan>> nonIsolatedlabels = findLabels(nonIsolated, lineTextBoxMap4NonIsolatedLabels, content, 0, false);

        Map<String, Collection<QSpan>> labels = new LinkedHashMap<>();
        labels.putAll(isolatedLabels);

        for (Map.Entry<String, Collection<QSpan>> e : nonIsolatedlabels.entrySet()) {
            labels.putIfAbsent(e.getKey(), e.getValue());
        }

        // we dedup labels - If a label is fully (100%) overlapped by another label we remove it

        labels = removeOverlappingLabels2(labels, extractDictionaries);
    //    labels = removeOverlappingLabels(labels);

        String content_wt_form_vals = content;
        Map<Integer, List<ExtIntervalTextBox>> lineLabelMap = getLocalLineAndTextBox3(labels);
        Map<String, Collection<QSpan>> isolatedAugmentedlabels = new HashMap<>();
        boolean formValsRemoved = textBoxes != null && content.length() < 15000;
        String lean_content = content;
        if (formValsRemoved) {
            // rermove text-heavy sections for value matching
            lean_content = removeTextHeavyParts(content);

            List<DictSearch> allFormFields = getFormFields(content);
            isolatedAugmentedlabels = findLabels(allFormFields, null, lean_content, 0, true);

            content_wt_form_vals = removeLabels(lean_content, isolatedAugmentedlabels);

            for (Map.Entry<String, Collection<QSpan>> e : isolatedAugmentedlabels.entrySet()){
                for (QSpan qs : e.getValue()) {
                    LineInfo lineInfo = new LineInfo(content, qs.getExtInterval(false));
                    qs.setStart(lineInfo.getLocalStart());
                    qs.setEnd(lineInfo.getLocalEnd());
                }
            }
        }

        Map<String, QCollection> grouped_vertical_matches = new HashMap<>();
        QCollection grouped_vertical_auto_matches = new QCollection();
        QCollection grouped_vertical_auto_matches_generic = new QCollection();

        Map<String, QCollection> all_matches = new HashMap<>();
        QCollection all_auto_matches = new QCollection();
        QCollection all_auto_matches_generic = findPatterns(lean_content, Generic, lineTextBoxMap, false);

        for (Pattern aut_ptr : AUTO_Patterns) {
            QCollection matches = findPatterns(lean_content, aut_ptr, lineTextBoxMap, false);

            QCollection grouped_matches = all_auto_matches.combine(matches, false);

            if (formValsRemoved){
                grouped_matches = findPatterns(content_wt_form_vals,
                        aut_ptr, lineTextBoxMap, false);
            }

            grouped_matches.groupVertically(all_auto_matches_generic, lineLabelMap,
                    7, 3);

            grouped_matches.markVM();
            grouped_vertical_auto_matches.combine(grouped_matches, false);
        }

        QCollection groupedGenericMatches = formValsRemoved ? findPatterns(content_wt_form_vals,
                        Generic, lineTextBoxMap, false) : all_auto_matches_generic;

        groupedGenericMatches.groupVertically(all_auto_matches_generic, lineLabelMap,
                7, 3);
        groupedGenericMatches.markVM();

        grouped_vertical_auto_matches_generic.combine(groupedGenericMatches, true);

        // merge single matches
        for (int line = 0; line <= all_auto_matches.getMax_line(); line++){
            List<QSpan> values = all_auto_matches.get(line);
            if (values.size() == 0) continue;
            HashSet<Integer> overlapped = new HashSet<>();
            for (int i=0; i<values.size(); i++){
                if (overlapped.contains(i)) continue;
                QSpan value_1 = values.get(i);
                int s1 = value_1.getStart();
                int e1 = value_1.getEnd();
                for (int j=0; j<values.size(); j++){
                    if (i == j) continue;
                    if (overlapped.contains(j)) continue;
                    QSpan value_2 = values.get(j);
                    int s2 = value_2.getStart();
                    int e2 = value_2.getEnd();
                    if (s1 >= s2 && e1 <= e2){
                        overlapped.add(i);
                        break;
                    }
                }
            }

            if (overlapped.size() > 0) {
                for (int i = 0; i < values.size(); i++) {
                    if (overlapped.contains(i)) continue;
                    QSpan qs = values.get(i);
                    all_auto_matches.add(qs, true);
                }
            }
        }

        Map<String, DictSearch> valueNeededDictionaryMap = new HashMap<>();

        List<ExtInterval> found = new ArrayList<>();

        List<QSpan> candidateTblHeader = new ArrayList<>();
        for (DictSearch qtSearchable : extractDictionaries) {
            String dicId = qtSearchable.getDictionary().getId();
            Collection<QSpan> qSpans = labels.get(dicId);
            if (qSpans == null) continue;

            Pattern ptr = qtSearchable.getDictionary().getPattern();
            String ptr_str = ptr == null ? "" : ptr.pattern();

            if (ptr_str.isEmpty()) {
                candidateTblHeader.addAll(qSpans);
                for (QSpan q : qSpans) {
                    // Label only - fix the start and end to be local
                    ExtInterval exLabel = q.getExtInterval(false);
                    LineInfo lineInfo = new LineInfo(content, exLabel);
                    exLabel.setStart(lineInfo.getLocalStart());
                    exLabel.setEnd(lineInfo.getLocalEnd());
                    exLabel.setLine(lineInfo.getLineNumber());
                    found.add(exLabel);
                }
            } else {
                valueNeededDictionaryMap.put(dicId, qtSearchable);
                if (ptr_str.startsWith(AUTO) ) {
                    candidateTblHeader.addAll(qSpans);
                    if (!ptr_str.equals(AUTO)) {
                        Pattern pattern = Pattern.compile(ptr_str.replace(AUTO, ""));

                        QCollection matches = findPatterns(content,
                                pattern, lineTextBoxMap, false);
                        all_matches.put(pattern.pattern(), matches);
                        matches.groupVertically(all_auto_matches_generic, lineLabelMap,
                                heightMult1, heightMult2);
                        grouped_vertical_matches.put(pattern.pattern(), matches);
                    }
                }
            }
        }

        if (valueNeededDictionaryMap.isEmpty()) {
            return found;
        }

        Map<Integer, Integer> allValueLines = new HashMap<>();

        for (int line =0; line <= all_auto_matches.getMax_line(); line++){
            Integer num = allValueLines.get(line);
            if (num == null){
                num = 0;
            }
            List<QSpan> lineItems = all_auto_matches.get(line);
            if (lineItems.size() == 0) continue;
            allValueLines.put(line, num + lineItems.size());
        }

        List<QSpan> allLabels = new ArrayList<>();
        TreeMap<Integer, List<QSpan>> allLabelsTreeMap = new TreeMap<>();
        for (Map.Entry<String, Collection<QSpan>> e : labels.entrySet()) {
            allLabels.addAll(e.getValue());
            for (QSpan sp : e.getValue()){
                LineInfo lineInfo = new LineInfo(content, sp.getExtInterval(false));
                int line = lineInfo.getLineNumber();
                List<QSpan> list = allLabelsTreeMap.get(line);
                if (list == null){
                    list = new ArrayList<>();
                    allLabelsTreeMap.put(line, list);
                }
                list.add(sp);
            }
        }

        List<TableHeader> tableHeaders = detectTableHeaders(candidateTblHeader,
                lineTextBoxMap, all_auto_matches, .65f);

        // set HORIZENTAL labels -- TODO: remove this from tableHeaders
        if (lineLabelMap != null) {
            detectHorizentalLabels(allLabels, lineTextBoxMap);
        }

        ArrayList<QSpan> finalQSpans = new ArrayList<>();
        //dedup allLabels


        for (QSpan qSpan : allLabels) {
            String dictId = qSpan.getDict_id();
            DictSearch dictSearch = valueNeededDictionaryMap.get(dictId);
            if (dictSearch == null) continue;

            String raw_ptr = dictSearch.getDictionary().getPattern().pattern();
            boolean isAuto = raw_ptr.equals(AUTO);
            boolean isCustomAuto = raw_ptr.startsWith(AUTO) && raw_ptr.length() > AUTO.length();

    //        logger.info("{} {} {}", qSpan.getStr(), qSpan.getSpanType(), qSpan.getLine());

            if (isAuto || isCustomAuto) {
                if (qSpan.getTextBox() == null) continue;

                //TODO:improve this
                LineInfo lineInfo = new LineInfo(content, qSpan.getExtInterval(false));
                String line_str = lineTextBoxMap.get(lineInfo.getLineNumber()).getLine_str();
                int offset = lineInfo.getLocalEnd();
                qSpan.setStart(lineInfo.getLocalStart());
                qSpan.setEnd(lineInfo.getLocalEnd());
                qSpan.setLine(lineInfo.getLineNumber());
                qSpan.setLine_str(line_str);

                if (line_str.length() < offset){
                    logger.info("shorter ...");
                    continue;
                }
                String sub_str = line_str.substring(offset);
                Matcher m = simple_form_val.matcher(sub_str);
                ExtIntervalTextBox singleFormValueInterval = null;
                if (m.find()) {
                    ExtInterval interval = new ExtInterval();
                    String str = m.group(1);
                    interval.setStr(m.group(1));
                    interval.setLine(qSpan.getLine());
                    interval.setStart(offset + m.start(1));
                    interval.setEnd(offset + m.end(1));

                    LineInfo vLineInfo = new LineInfo(interval.getLine(), interval.getStart(), interval.getEnd());
                    BaseTextBox tb = findAssociatedTextBox(lineTextBoxMap, str, vLineInfo, false);
                    singleFormValueInterval = new ExtIntervalTextBox(new ExtIntervalLocal(interval), tb);
                    List<BaseTextBox> tbList = new ArrayList<>();
                    tbList.add(tb);
                    interval.setTextBoxes(tbList);
                }

                AutoValue bestAutoValue = new AutoValue();

                if (isAuto) {

                     AutoValue autoValue = findBestValue(content, lineTextBoxMap, lineLabelMap, labels, qSpan, all_auto_matches,
                             grouped_vertical_auto_matches, tableHeaders, max_distance_bet_lines, true);
                    boolean hasOverlapWithLabels  = hasOvelapWLabels(autoValue, allLabels);
                    if (!hasOverlapWithLabels) {
                        bestAutoValue.merge(autoValue);
                    }

                    if (bestAutoValue.hValue == null && bestAutoValue.vValue.size() == 0){
                        AutoValue genericMatches = findBestValue(content, lineTextBoxMap, lineLabelMap, labels, qSpan, all_auto_matches_generic, grouped_vertical_auto_matches_generic,
                                tableHeaders, max_distance_bet_lines, true);
                        if( bestAutoValue.vValue.size() != 0){
                            if (genericMatches.vValue.size() != 0){
                                ExtIntervalTextBox firstAutoValue = bestAutoValue.vValue.get(0);
                                ExtIntervalTextBox firstGenericMatch = genericMatches.vValue.get(0);
                                // generic match is higher, then bestAutoValue is not related to the key -- ignore it
                                if (firstGenericMatch.getTextBox().getTop() < firstAutoValue.getTextBox().getTop()){
                                    bestAutoValue.hValue = null;
                                    bestAutoValue.h_score = 10000f;
                                }
                            }
                        } else {
                            bestAutoValue = genericMatches;
                        }
                    } else if (bestAutoValue.hValue != null){

                        ExtInterval m1 = bestAutoValue.hValue.getExtInterval();
                        QSpan m2 = all_auto_matches_generic.lookup(m1.getLine(), m1.getStart());
                        if (m2 != null) {
                            bestAutoValue.hValue = m2.getExtIntervalTextBoxes().get(0);
                        }
                    }
                } else {
                    String ptr = raw_ptr.replace(AUTO, "");
                    if (singleFormValueInterval != null && Pattern.matches(ptr, singleFormValueInterval.getExtInterval().getStr())){
                        bestAutoValue = new AutoValue();
                        bestAutoValue.h_score = 0;
                        bestAutoValue.hValue = singleFormValueInterval;
                    } else {
                        QCollection h_matchs = all_matches.get(ptr);
                        QCollection v_matchs = grouped_vertical_matches.get(ptr);

                        bestAutoValue = findBestValue(content, lineTextBoxMap, lineLabelMap, labels,
                                qSpan, h_matchs, v_matchs, tableHeaders, max_distance_bet_lines, false);
                    }
                }

                List<Interval> best = bestAutoValue.getBest(allLabels, isAuto);
                if (best.size() > 0) {
                    qSpan.setExtIntervalSimples(best);
                    finalQSpans.add(qSpan);
                }

            } else {
                //TODO: extinterval has global positions for start and end of the line
                List<Interval> rowValues = findAllHorizentalMatches(content, dictSearch, qSpan.getExtInterval(false));
                if (searchVertical && rowValues.size() == 0) {
                    rowValues = findAllVerticalMatches(content, lineTextBoxMap, dictSearch, qSpan.getExtInterval(false));
                }

                if (rowValues.size() > 0) {
                    for (Interval eis : rowValues){
                        LineInfo extIntervalLineInfo = new LineInfo(content, eis);
                        eis.setEnd(extIntervalLineInfo.getLocalEnd());
                        eis.setStart(extIntervalLineInfo.getLocalStart());
                        eis.setLine(extIntervalLineInfo.getLineNumber());
                    }

                    qSpan.setExtIntervalSimples(rowValues);
                    LineInfo lineInfo = new LineInfo(content, qSpan.getExtInterval(false));
                    qSpan.setStart(lineInfo.getLocalStart());
                    qSpan.setEnd(lineInfo.getLocalEnd());
                    qSpan.setLine(lineInfo.getLineNumber());
                    finalQSpans.add(qSpan);
                }
            }
        }

        reviseVerticals(content, all_auto_matches, all_auto_matches_generic, finalQSpans);

        List<QSpan> new_final_qspan = new ArrayList<>();
        new_final_qspan.addAll(finalQSpans);
        for (ExtInterval ext : found){
            ExtIntervalTextBox eitb = new ExtIntervalTextBox(ext, null);
            new_final_qspan.add(new QSpan(eitb));
        }

        List<QSpan> filtered = cleanUp(content, new_final_qspan, valueNeededDictionaryMap,
                all_auto_matches, isolatedAugmentedlabels);

        Collections.sort(filtered, Comparator.comparingInt(QSpan::getStart));

        for (QSpan qs : filtered) {
            found.add(qs.getExtInterval());
        }

        return found;
    }

    /*

    private void trimTableData(List<TableHeader> tableHeaders,
                               List<QSpan> finalQSpans){
        for (TableHeader tblHdr : tableHeaders) {
            List<QSpan> headerList = tblHdr.headerList;
            for (QSpan qhd : headerList){
                String h1 = qhd.getDict_id();
            }
            if (extIntervals.size() == 0) break;
            Map<Integer, Item> lineItems = new TreeMap<>();
            findLineItemsLcl(extIntervals, lineItems, h1, true);
            for (String h2 : tableHdrs) {
                if (h1.equals(h2)) continue;
                findLineItemsLcl(extIntervals, lineItems, h2, false);
            }

            if (lineItems.size() == 0) continue;
            for (Iterator<Map.Entry<Integer, Item>> it = lineItems.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Item> ee = it.next();
                Map<String, ExtInterval> elements  = ee.getValue().getFields();
                if (elements.size() > 1) {
                    List<ExtInterval> list = newValues.get(ee.getKey());
                    if (list == null) list = new ArrayList<>();
                    list.addAll(elements.values());
                    newValues.put(ee.getKey(), list);
                }
            }
        }
    }

     */

    private static boolean findLineItemsLcl(List<ExtInterval> list,
                                            Map<Integer, Item> lineItems,
                                            String vocabName,
                                            boolean createNew){

        ListIterator<ExtInterval> iter = list.listIterator();
        boolean added = false;
        while (iter.hasNext()) {
            ExtInterval extInterval = iter.next();
            String vocab_name = extInterval.getDict_name();  // input field name
            if (!vocab_name.equals(vocabName)) continue;

            int line = extInterval.getLine();
            Item lineItem = lineItems.get(line);
            if (lineItem == null){
                if (!createNew) continue;
                lineItem = new Item();
                lineItems.put(line, lineItem);
                added = true;
            }
            if (lineItem.getFields().get(vocab_name) != null) continue;

            iter.remove();
            lineItem.getFields().put(vocab_name, extInterval);
        }

        return added;
    }

    private static class ValueDistance {
        QSpan v;
        int dist;
        public ValueDistance(QSpan qSpan, int d){
            v = qSpan;
            dist = d;
        }
    }

    private static class Item {
        public Map<String, ExtInterval> getFields() {
            return fields;
        }

        public void setFields(Map<String, ExtInterval> fields) {
            this.fields = fields;
        }

        private Map<String, ExtInterval> fields = new HashMap<>();

        public Item() {
        }
    }

    private boolean isStacked(QSpan qSpan){
        List<Interval> intervals = qSpan.getExtIntervalSimples();
        if (intervals == null || intervals.size() == 0) return false;
        int first_v_line = intervals.get(0).getLine();
        int last_label_line = qSpan.getExtIntervalTextBoxes().get(qSpan.getExtIntervalTextBoxes().size() -1).getExtInterval().getLine();
        if (first_v_line > last_label_line) return true;
        return false;
    }

    private boolean oneSpanOverlaps(Interval interval, QSpan qSpan){

        int value_line = interval.getLine();
        int value_s = interval.getStart();
        int value_e = interval.getEnd();
        if (qSpan.getLine() == value_line) {
            if ((qSpan.getStart() >= value_s && qSpan.getStart() <= value_e) ||
                    (value_s >= qSpan.getStart() && value_s <= qSpan.getEnd())) {
                return true;
            }
        }
        return false;
    }

    private boolean spanOverlaps(Map<String, Collection<QSpan>> formKeys,
                                 Interval interval){
        for (Collection<QSpan> fs : formKeys.values()){
            for (QSpan f : fs){
                boolean overlap = oneSpanOverlaps(interval, f);
                if (overlap) return true;
            }
        }
        return false;
    }
    private List<QSpan> cleanUp(String content,
                                List<QSpan> qSpans,
                                Map<String, DictSearch> vocabMap,
                                QCollection all_auto_matches,
                                Map<String, Collection<QSpan>> formKeys)
    {


        // we remove stacked values that belong to two headers
        // first find the real headers as the tableheader detection may have over-detected headers

        List<QSpan> headerLabels = new ArrayList<>();
        ListIterator<QSpan> spanIter = qSpans.listIterator();

        while (spanIter.hasNext()) {
            QSpan qSpan = spanIter.next();
            List<Interval> vals = qSpan.getExtIntervalSimples();
            if (vals == null || vals.size() == 0) continue;
            if ((vals.get(0).getLine() - qSpan.getLine()) == 0) continue;
            qSpan.setSpanType(null); // we do this so we can re-detect the headers
            headerLabels.add(qSpan);
        }

        List<TableHeader> verified_header = detectTableHeadersHelper(headerLabels, all_auto_matches, .65f);
        verified_header.sort(comparingInt(qs -> qs.firstRow));

        if (verified_header.size() > 1) {
            for (int i = 0; i < verified_header.size() - 1; i++) {
                TableHeader curr = verified_header.get(i);
                TableHeader next = verified_header.get(i+1);
                spanIter = qSpans.listIterator();
                while (spanIter.hasNext()) {
                    QSpan qSpan = spanIter.next();
                    List<Interval> vals = qSpan.getExtIntervalSimples();
                    if (vals == null || vals.size() < 2) continue;
                    String dictId = qSpan.getDict_id();
                    DictSearch dictSearch = vocabMap.get(dictId);
                    boolean isAuto = false;
                    if (dictSearch != null) {
                        String raw_ptr = dictSearch.getDictionary().getPattern().pattern();
                        isAuto = raw_ptr.startsWith(AUTO);
                    }
                    if (!isAuto) continue;
                    float ho = getHorizentalOverlap(curr.header.getTextBox(), qSpan.getTextBox());
                    float vo = getVerticalOverlap(curr.header.getTextBox(), qSpan.getTextBox());
                    if (ho <.99f || vo < .99f) continue;
                    ListIterator<Interval> iter = vals.listIterator();
                    while (iter.hasNext()) {
                        Interval interval = iter.next();
                        int value_line = interval.getLine();
                        if (value_line >= next.firstRow) {
                            iter.remove();
                        }
                    }
                    if (vals.size() == 0){
                        spanIter.remove();
                    }
                }
            }
        }

        // if a span is both key and value, we must drop the key
        ListIterator<QSpan> iter1 = qSpans.listIterator();
        while (iter1.hasNext()){
            QSpan label = iter1.next();
            boolean hasOverlap = false;
            for (ExtIntervalTextBox eitb : label.getExtIntervalTextBoxes()) {
                ExtInterval ext = eitb.getExtInterval();
                LineInfo lineInfo = new LineInfo(content, ext);
                int s = lineInfo.getLocalStart();
                int e = lineInfo.getLocalStart();
                for (QSpan v : qSpans) {
                    List<Interval> interval = v.getExtIntervalSimples();
                    if (interval == null) continue;
                    for (Interval inv : interval) {
                        if (inv.getLine() != ext.getLine()) continue;
                        if ((inv.getStart() >= s && inv.getStart() <= e) ||
                                (s >= inv.getStart() && s <= inv.getEnd())) {
                            hasOverlap = true;
                            break;
                        }
                    }
                    if (hasOverlap) break;
                }
                if (hasOverlap) break;
            }
            if (hasOverlap){
    //            logger.info("Removing {}", label.getStr());
                iter1.remove();
            }
        }

        // if vertical values overlap with another set of vertical values, then we break them
        // so if 1 2 3 4 5 go to Label1 and 3 happens to be Label2, where it has 1 or more vertical values
        // then we remove 3, 4 and 5 from Label1
        for (QSpan qSpan : qSpans) {
            List<Interval> vals = qSpan.getExtIntervalSimples();
            if (vals == null || vals.size() == 0) continue;

            boolean isStacked = isStacked(qSpan);
            if (!isStacked) continue;
            ListIterator<Interval> iter = vals.listIterator();

            List<Interval> newVals = new ArrayList<>();
            while (iter.hasNext()) {
                Interval interval = iter.next();
                boolean overlaps = false;
                for (QSpan label : qSpans){
                    List<Interval> vals2 = qSpan.getExtIntervalSimples();
                    if (vals2 == null || vals2.size() == 0) continue;
                    boolean isLStacked = isStacked(label);
                    if (!isLStacked) continue;
                    overlaps = oneSpanOverlaps(interval, label);
                    if (overlaps) break;
                }
                if (overlaps) break;
                newVals.add(interval);
            }
            qSpan.setExtIntervalSimples(newVals);
        }

        // if a value is assigned to more than one key, pick the closest key
        // filter out table values that overlap with form values
        // and values that overlap with labels

        spanIter = qSpans.listIterator();
        while (spanIter.hasNext()) {
            QSpan qSpan = spanIter.next();
            List<Interval> vals = qSpan.getExtIntervalSimples();
            if (vals == null || vals.size() == 0) continue;
            String dictId = qSpan.getDict_id();
            DictSearch dictSearch = vocabMap.get(dictId);
            boolean isAuto = false;
            if (dictSearch != null) {
                String raw_ptr = dictSearch.getDictionary().getPattern().pattern();
        //        isAuto = raw_ptr.equals(AUTO);
                isAuto = raw_ptr.startsWith(AUTO);
            }
            if (! isAuto) continue;
            int label_line = qSpan.getLine();
            ListIterator<Interval> iter  = vals.listIterator();
            HashSet<String> uniqueVals = new HashSet<>();
            while (iter.hasNext()) {
                Interval interval = iter.next();
                int value_line = interval.getLine();
                if (value_line <= label_line) continue;
                boolean hasOverlapWFormKey = spanOverlaps(formKeys, interval);
                if (hasOverlapWFormKey){
                    iter.remove();
                    continue;
                }
                String key = interval.getStart() + "-" + interval.getEnd() + "-" + interval.getLine();
                if (uniqueVals.contains(key)){
                    iter.remove();
                    continue;
                }
                uniqueVals.add(key);
            }
            if (vals.size() == 0) spanIter.remove();
        }

        Map<String, List<ValueDistance>> best_found_values = new HashMap<>();
        for (QSpan qSpan : qSpans){
            List<Interval> vals = qSpan.getExtIntervalSimples();
            if (vals == null || vals.size() == 0) continue;
            Interval interval = vals.get(0);
            String key = interval.getStart() + "-" + interval.getEnd() + "-" + interval.getLine();
            List<ValueDistance> list = best_found_values.get(key);
            if (list == null){
                list = new ArrayList<>();
                best_found_values.put(key, list);
            }
            int dist = interval.getLine() - qSpan.getLine();
            list.add(new ValueDistance(qSpan, dist));
        }

        List<QSpan> filtered = new ArrayList<>();
        for (Map.Entry<String, List<ValueDistance>> e : best_found_values.entrySet()){
            List<ValueDistance> valueDists = e.getValue();
            if (valueDists.size() == 1){
                QSpan qSpan = valueDists.get(0).v;
                filtered.add(qSpan);
            } else {
                // if we have values captured by labels that go across multiple cells,
                // we try to remove them here

                // a value can be captured by a vocab only once
                Map<String, Integer> valueDist4Vocab = new HashMap<>();
                for (ValueDistance vd : valueDists){
                    if (vd.v.getExtIntervalTextBoxes() == null || vd.v.getExtIntervalTextBoxes().size() == 0) continue;
                    String vocab = vd.v.getExtIntervalTextBoxes().get(0).getExtInterval().getDict_id();
                    String header = vocab;
                    Integer d = valueDist4Vocab.get(header);
                    if (d == null){
                        valueDist4Vocab.put(header, vd.dist);
                    } else {
                        if (vd.dist < d){
                            valueDist4Vocab.put(header, vd.dist);
                        }
                    }
                }
                ListIterator<ValueDistance> iter = valueDists.listIterator();
                while (iter.hasNext()){
                    ValueDistance vd = iter.next();
                    if (vd.v.getExtIntervalTextBoxes() == null || vd.v.getExtIntervalTextBoxes().size() == 0) continue;
                    String vocab = vd.v.getExtIntervalTextBoxes().get(0).getExtInterval().getDict_id();
                    String header = vocab;
                    Integer d = valueDist4Vocab.get(header);
                    if (d == null) continue;
                    if (d != vd.dist) iter.remove();
                }

                List<ValueDistance> spreadLabels = new ArrayList<>();
                List<ValueDistance> stackedLabels = new ArrayList<>();
                for (ValueDistance vd : valueDists){
                    QSpan qSpan = vd.v;
                    String str = qSpan.getStr();
                    if (str.split("  ").length > 1 && qSpan.getExtIntervalTextBoxes().size() == 1){
                        spreadLabels.add(vd);
                    } else {
                        stackedLabels.add(vd);
                        filtered.add(qSpan);
                    }
                }
                // we prefer stacked labels
                for (ValueDistance spread : spreadLabels){
                    BaseTextBox b1 = spread.v.getTextBox();
                    if (b1 == null) continue;
                    boolean hasOverlap = false;
                    for (ValueDistance stacked : stackedLabels){
                        BaseTextBox b2 = stacked.v.getTextBox();
                        if (b2 == null) continue;
                        float ho = getHorizentalOverlap(b1, b2);
                        float vo = getVerticalOverlap(b1, b2);
                        if (ho > .25 && vo > .25){
                            hasOverlap = true;
                            break;
                        }
                    }
                    if (!hasOverlap){
                        filtered.add(spread.v);
                    }
                }
            }
        }

        //TODO: refactor this piece- this might be repetetive
        Map<String, ValueDistance> verticalValue2dist = new HashMap<>();
        for (QSpan qSpan : filtered){
            List<Interval> intervals = qSpan.getExtIntervalSimples();
            if (intervals == null || intervals.size() == 0) continue;
            for (Interval interval : intervals) {
                String key = interval.getStart() + "-" + interval.getEnd() + "-" + interval.getLine();
                ValueDistance vd = verticalValue2dist.get(key);
                int dist = interval.getLine() - qSpan.getLine();
                if (vd == null || dist < vd.dist) {
                    verticalValue2dist.put(key, new ValueDistance(qSpan, dist));
                }
            }
        }

        ListIterator<QSpan> spanIter2 = filtered.listIterator();

        while (spanIter2.hasNext()){
            QSpan qSpan = spanIter2.next();
            List<Interval> intervals = qSpan.getExtIntervalSimples();
            if (intervals == null || intervals.size() == 0) continue;
            String qspanKey = qSpan.getStart() + "-" + qSpan.getEnd() + "-" + qSpan.getLine();
            ListIterator<Interval> iter = intervals.listIterator();
            while (iter.hasNext()) {
                Interval interval = iter.next();
                String key = interval.getStart() + "-" + interval.getEnd() + "-" + interval.getLine();
                ValueDistance vd = verticalValue2dist.get(key);
                if (vd == null) continue;
                //check if the lowest distance ValueDistance has the sams qspan
                String vdKey = vd.v.getStart() + "-" + vd.v.getEnd() + "-" + vd.v.getLine();
                if (!vdKey.equals(qspanKey)) {
                    iter.remove();
                }
            }
            if (intervals.size() == 0) spanIter2.remove();
        }

        //TODO: hack - we are filtering out duplicates stacked values but we should that before here
        HashSet<String> unqi_founds = new HashSet<>();
        ListIterator<QSpan> iter = filtered.listIterator();
        while (iter.hasNext()){
            QSpan qSpan = iter.next();
            List<Interval> list = qSpan.getExtIntervalSimples();
            if (list == null || list.size() == 0) continue;
            ListIterator<Interval> iter_int = list.listIterator();
            while (iter_int.hasNext()){
                Interval interval = iter_int.next();
                String key = interval.getLine() + "_" + interval.getStart() + "_" + interval.getEnd() + "_" + qSpan.getDict_name();
                if (unqi_founds.contains(key)){
                    iter_int.remove();
                } else {
                    unqi_founds.add(key);
                }
            }
            if (list == null || list.size() == 0) {
                iter.remove();
            }
        }
        return filtered;
    }
    private static class ExtIntervalLocal extends ExtInterval {
        private int local_start;
        private int local_end;

        public ExtIntervalLocal(ExtInterval e){
            this.setStart(e.getStart());
            this.setEnd(e.getEnd());
            this.setLine(e.getLine());
            this.setDict_name(e.getDict_name());
            this.setDict_id(e.getDict_id());
            this.setCategory(e.getCategory());
            this.setLine(e.getLine());
            this.setStr(e.getStr());
        }

        public int getLocal_start() {
            return local_start;
        }

        public void setLocal_start(int local_start) {
            this.local_start = local_start;
        }

        public int getLocal_end() {
            return local_end;
        }

        public void setLocal_end(int local_end) {
            this.local_end = local_end;
        }
    }

    private QCollection findPatterns(String content,
                                     Pattern regex,
                                     Map<Integer, BaseTextBox> lineTextBoxMap,
                                     boolean ignorePrefixBoxes)
    {

        QCollection values = new QCollection();
        if (lineTextBoxMap == null || lineTextBoxMap.size() == 0) return values;
        Matcher matcher = regex.matcher(content);
        int group = matcher.groupCount() >= 1 ? 1 : 0;
        while (matcher.find()){
            int s = matcher.start(group);
            int e = matcher.end(group);
            if (s <0 || e <0) continue;
            String str = matcher.group(group);

            ExtInterval extInterval = new ExtInterval();
            extInterval.setStr(str);
            extInterval.setStart(s);
            extInterval.setEnd(e);

            LineInfo lineInfo = new LineInfo(content, extInterval);
            BaseTextBox textBox = findAssociatedTextBox(lineTextBoxMap, str, lineInfo,  ignorePrefixBoxes);
            if (textBox == null) {
                logger.debug("{} wasn't matched to any textbox", str);
                continue;
            }
            textBox.setLine(lineInfo.getLineNumber());
            List<BaseTextBox> tbList = new ArrayList<>();
            tbList.add(textBox);
            extInterval.setTextBoxes(tbList);

            /*
            List<QSpan> list = values.get(lineInfo.getLineNumber());
            if (list == null){
                list = new ArrayList<>();
                values.put(lineInfo.getLineNumber(), list);
            }

             */

            ExtInterval ext = new ExtInterval();
            ext.setStart(lineInfo.getLocalStart());
            ext.setEnd(lineInfo.getLocalEnd());
            ext.setStr(str);
            ext.setLine(lineInfo.getLineNumber());
            QSpan qSpan = new QSpan(new ExtIntervalTextBox(ext, textBox));
            qSpan.setLine(lineInfo.getLineNumber());
            qSpan.setStart(lineInfo.getLocalStart());
            qSpan.setEnd(lineInfo.getLocalEnd());
        //    list.add(qSpan);
            values.add(qSpan, true);
        }

        return values;
    }

    private static class AutoValue{
        float h_score = 100000f;
        float v_score = 100000f;
        ExtIntervalTextBox hValue = null;
        List<ExtIntervalTextBox> vValue = new ArrayList<>();
        public AutoValue(){

        }

        private void checkValuesAgainestLabels(List<QSpan> labels){

            if (hValue != null) {
                QSpan hSpan = new QSpan(hValue);
                List<QSpan> testSpans = new ArrayList<>();
                testSpans.add(hSpan);
                if (hValue.getTextBox() != null){
                    filterNegativeWTextBox(testSpans, labels, .2f, false);
                } else {
                    filterNegativeWithoutTextBox(testSpans, labels);
                }
       //         List <QSpan> filtered = hValue.getTextBox() != null ? getFilteredSpansWithTextBox(testSpans, labels) :
       //                 getFilteredSpansWithoutTextBox(testSpans, labels);
                if (testSpans.size() == 0) {
                    hValue = null;
                    h_score = 100000f;
                }
            }

            if (vValue.size() > 0) {
                QSpan hSpan = new QSpan(vValue.get(0));
                List<QSpan> testSpans = new ArrayList<>();
                testSpans.add(hSpan);
                if (vValue.get(0).getTextBox() != null){
                    filterNegativeWTextBox(testSpans, labels, .2f, false);
                } else {
                    filterNegativeWithoutTextBox(testSpans, labels);
                }
            //    List <QSpan> filtered = vValue.get(0).getTextBox() != null  ? getFilteredSpansWithTextBox(testSpans, labels) :
            //            getFilteredSpansWithoutTextBox(testSpans, labels);
                if (testSpans.size() == 0) {
                    vValue = new ArrayList<>();
                    v_score = 100000f;
                }
            }
        }

        public void merge(AutoValue autoValue){
            if (autoValue.hValue != null) {
                if (autoValue.h_score < h_score) {
                    hValue = autoValue.hValue;
                    h_score = autoValue.h_score;
                }
            }
            if (autoValue.vValue.size() >0) {
                if (autoValue.v_score < v_score) {
                    vValue = autoValue.vValue;
                    v_score = autoValue.v_score;
                }
            }
        }

        public List<Interval> getBest(List<QSpan> labels, boolean isAuto){

            if (!isAuto){
                checkValuesAgainestLabels(labels);
            }

            List<Interval> list = new ArrayList<>();

    //        if (vValue.size() == 1 && v_score < h_score && v_score < 30){
            if (vValue.size() == 1 && hValue != null){
                list.add(vValue.get(0).getExtInterval());
                ExtInterval extInterval = hValue.getExtInterval();
                list.add(extInterval);
            } else if (h_score < 400 && hValue != null){
                ExtInterval extInterval = hValue.getExtInterval();
                list.add(extInterval);
            } else if (v_score < h_score) {
                for (ExtIntervalTextBox etb : vValue) {
                    list.add(etb.getExtInterval());
                }
            } else if (hValue != null){
                list.add(hValue.getExtInterval());
            }
            return list;
        }
    }

    private AutoValue findAssociatedVerticalValues(BaseTextBox label_span_tb,
                                                   List<QSpan> lineValues,
                                                   float min_overlap,
                                                   boolean onlyVerticallyVerified){

        List<ExtIntervalTextBox> line_items = new ArrayList<>();

        AutoValue verticalValues = new AutoValue();
        float height = label_span_tb.getBase() - label_span_tb.getTop();

        for (int i=0; i< lineValues.size(); i++) {
            QSpan qSpan = lineValues.get(i);
            if (qSpan.getSpanType() != null ) {
                if (onlyVerticallyVerified) { // only VERTICAL_MANY for now
                    if (qSpan.getSpanType() != VERTICAL_MANY) continue;
                    //             if (!(qSpan.getSpanType() == VERTICAL_MANY || qSpan.getSpanType() == VERTICAL_ONE_BELOW)) continue;
                }
            }
            BaseTextBox candidate_vtb = qSpan.getTextBox();
            if (candidate_vtb == null) {
                logger.warn("NO textbox {}", qSpan.getExtInterval(true).getStr());
                continue;
            }

            // for case of skewed documents - lets' check vertical stack up:
            if (candidate_vtb.getBase() < label_span_tb.getTop()) continue;
            List<BaseTextBox> vtbs = candidate_vtb.getChilds();
            BaseTextBox first = vtbs.get(0);
            BaseTextBox last = vtbs.get(vtbs.size()-1);

            float dvtc = vtbs.get(0).getTop() - label_span_tb.getBase();

            float d_left = Math.abs(first.getLeft() - label_span_tb.getLeft());
            float d_center = .5f * Math.abs(last.getRight() - label_span_tb.getRight() + first.getLeft() - label_span_tb.getLeft());
            float d_right = Math.abs(last.getRight() - label_span_tb.getRight());

            float min_horizental_d = Math.min(Math.min(d_left, d_center), d_right);

            float vOverlap = getHorizentalOverlap(label_span_tb, candidate_vtb, false);

            if (min_overlap > 0) {
                if (vOverlap < min_overlap) continue;
            } else {
                if (vOverlap < .5 && min_horizental_d > height) continue;
            }

            float s = (float) Math.sqrt(min_horizental_d * min_horizental_d + dvtc * dvtc);
            verticalValues.v_score = s;
            line_items.addAll(qSpan.getExtIntervalTextBoxes());
            verticalValues.vValue.addAll(line_items);
            return verticalValues;
        }

        return verticalValues;
    }

    private AutoValue findBestVerticalValues(Map<Integer, BaseTextBox> lineTextBoxMap,
                                             Map<Integer, List<ExtIntervalTextBox>> lineLabelMap,
                                             QSpan labelSpan,
                                             QCollection candidateValues,
                                             int max_distance_bet_lines,
                                             float min_overlap,
                                             boolean onlyVerticallyVerified,
                                             boolean isAuto){

        if (candidateValues == null || candidateValues.size() == 0) return new AutoValue();

        int lastLine = candidateValues.getMax_line();
        int labelLine = labelSpan.getExtIntervalTextBoxes().get(labelSpan.getExtIntervalTextBoxes().size()-1).getExtInterval().getLine();

        AutoValue autoValue = new AutoValue();
        List<BaseTextBox> lastMatchedTextBoxList = new ArrayList<>();
        int num_unaligned_lines = 0;
        for (int keyLine = labelLine+1 ; keyLine <= lastLine; keyLine++) {

            BaseTextBox lineTexboxes = lineTextBoxMap.get(keyLine);
            if (isAuto && lineTexboxes != null) {
                if (lastMatchedTextBoxList.size() != 0) {
                    float total_box_overlaps = 0;
                    HashSet<Float> uniqu_line_vals = new HashSet<>();

                    for (BaseTextBox bt1 : lineTexboxes.getChilds()) {
                        if (uniqu_line_vals.contains(bt1.getLeft())) continue;
                        uniqu_line_vals.add(bt1.getLeft());
                        for (BaseTextBox bt2 : lastMatchedTextBoxList) {
                            float ov = getHorizentalOverlap(bt1, bt2);
                            if (ov >= .8) {
                                total_box_overlaps += 1f;
                                break;
                            }
                        }
                    }
                    float total = lineTexboxes.getChilds().size();
                    if (total_box_overlaps < .5) continue;
                    if (total_box_overlaps == 0) return autoValue;
                    if (total > 4 && total_box_overlaps/total < .5) {
                        num_unaligned_lines++;
                        if (num_unaligned_lines > 3) {
                            return autoValue;
                        } else {
                            continue;
                        }
                    }
                }
            }
            List<QSpan> lineValues = candidateValues.get(keyLine);
            if (lineValues == null) {
                continue;
            }

            BaseTextBox lastValue = autoValue.vValue.size() == 0 ? labelSpan.getTextBox():
                            autoValue.vValue.get(autoValue.vValue.size()-1).getTextBox();

            float mn = autoValue.vValue.size() == 0 ? min_overlap : -1;
            AutoValue newAutoValue = findAssociatedVerticalValues(lastValue, lineValues, mn, onlyVerticallyVerified);
            if (newAutoValue.vValue.size() > 0) {
                if (isAuto){
                    if (newAutoValue.vValue.size() == 1){
                        BaseTextBox bt = autoValue.vValue.size() >0 ? autoValue.vValue.get(autoValue.vValue.size()-1).getTextBox()
                                : labelSpan.getTextBox();
                        BaseTextBox vbt = newAutoValue.vValue.get(0).getTextBox();
                        if (bt != null && vbt!= null) {
                            float h1 = bt.getBase() - bt.getTop();
                            float h2 = vbt.getBase() - vbt.getTop();
                            float h = Math.min(h1, h2);
                            if ((vbt.getTop() - bt.getBase()) > max_distance_bet_lines*h ) continue;
                        } else {
                            // don't return single far vertical elements
                            int dist = keyLine - labelSpan.getLine();
                            if (dist > max_distance_bet_lines) {
                                continue;
                            }
                        }
                    } else if (lineValues.size() == 1) {
                        continue;
                    }
                }

                if (lastMatchedTextBoxList.size() == 0){
                    HashSet<Float> uniqu_vals = new HashSet<>();
                    for (BaseTextBox bt : lineTexboxes.getChilds()) {
                        if (uniqu_vals.contains(bt.getLeft())) continue;
                        lastMatchedTextBoxList.add(bt);
                        uniqu_vals.add(bt.getLeft());
                    }
                }

                if (autoValue.vValue.size() == 0){
                    autoValue.v_score = newAutoValue.v_score;
                }

                for (ExtIntervalTextBox v : newAutoValue.vValue) {
                    /*
                    BaseTextBox tb2 = v.getTextBox();
                    if (nextBestTblHeader != null) {
                        float vo = getVerticalOverlap(nextBestTblHeader.header.getTextBox(), tb2);
                        float ho = getHorizentalOverlap(nextBestTblHeader.header.getTextBox(), tb2);
                        if (vo > .2f && ho > .2f) return autoValue;
                        if (tb2.getBase() >= nextBestTblHeader.header.getBase() ||
                                tb2.getTop() >= nextBestTblHeader.header.getTop()) return autoValue;
                    }

                     */

                    autoValue.vValue.add(v);
                }
            }
        }

        return autoValue;
    }
    private AutoValue findBestValue(String content,
                                    Map<Integer, BaseTextBox> lineTextBoxMap,
                                    Map<Integer, List<ExtIntervalTextBox>> lineLabelMap,
                                    Map<String, Collection<QSpan>> labels,
                                    QSpan labelSpan,
                                    QCollection candidateHorizentalValues,
                                    QCollection candidateVerticalValues,
                                    List<TableHeader> detectedHaders,
                                    int max_distance_bet_lines,
                                    boolean isAuto)
    {
        // check for simple form values
        AutoValue autoValue = new AutoValue();

        if (isAuto) {
            if (labelSpan.getSpanType() != null && labelSpan.getSpanType() == VERTICAL_MANY) {
                AutoValue vertical_1 = findBestVerticalValues(lineTextBoxMap, lineLabelMap,
                        labelSpan, candidateVerticalValues, max_distance_bet_lines, .1f, true, true);
                if (vertical_1.vValue.size() == 0) {
                    float oldRight = labelSpan.getRight();
                    float oldLeft = labelSpan.getLeft();
                    float newRight = TextBox.extendToNeighbours(lineTextBoxMap, labelSpan);
                    float newLeft = TextBox.extendToLeftNeighbours(lineTextBoxMap, labelSpan);
                    labelSpan.setRight(newRight);
                    labelSpan.setLeft(newLeft);
                    vertical_1 = findBestVerticalValues(lineTextBoxMap, lineLabelMap,
                            labelSpan, candidateVerticalValues, max_distance_bet_lines, .99f, true, true);
                    // make sure we change it back to original border
                    labelSpan.setRight(oldRight);
                    labelSpan.setLeft(oldLeft);
                }
                return vertical_1;
            }
        }

        HashMap<ExtIntervalTextBox, Double> nearSingleMatches = new HashMap<>();
        int r_end = -1;
        //TODO: this is not efficient. calculate this outside of this loop
        //eitb start and end are local, labelSpan start and end are global
        for (ExtIntervalTextBox rel_extbox : labelSpan.getExtIntervalTextBoxes()){
            ExtInterval rel_ext = rel_extbox.getExtInterval();
            LineInfo lineInfo = new LineInfo(content, rel_ext);
            if (r_end < lineInfo.getLocalEnd()) {
                r_end = lineInfo.getLocalEnd();
            }
        }

        if (r_end > -1) {
            for (int i=0; i<= candidateHorizentalValues.getMax_line(); i++) {
                List<QSpan> qSpans = candidateHorizentalValues.get(i);
                if (qSpans.size() == 0) continue;
                for (QSpan qSpan : qSpans) {
                    List<ExtIntervalTextBox> eitbs = qSpan.getExtIntervalTextBoxes();
                    for (ExtIntervalTextBox eitb : eitbs) {
                        BaseTextBox btb = eitb.getTextBox();
                        if (btb == null) continue;
                        float vOverlap = getVerticalOverlap(labelSpan.getTextBox(), btb);
                        if (vOverlap < .1) continue;
                        String cur_line_str = eitb.getTextBox().getLine_str();
                        if (cur_line_str.length() > eitb.getExtInterval().getStart() && eitb.getExtInterval().getStart() > r_end) {
                            String gap = cur_line_str.substring(r_end, eitb.getExtInterval().getStart());
                            //remove (some text here) pattern
                            gap = gap.replaceAll("\\([^\\)]+\\)", "").trim();
                            gap = gap.replaceAll("[^\\p{L}\\p{N}]", "");
                            if (gap.length() == 0) {
                                double d = btb.getLeft() - labelSpan.getTextBox().getRight();
                                if (d >= 0) {
                                    nearSingleMatches.put(eitb, d / vOverlap);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (nearSingleMatches.size() > 0) {
            Map<ExtIntervalTextBox, Double> sorted = MapSort.sortByValue(nearSingleMatches);
            autoValue.hValue = sorted.entrySet().iterator().next().getKey();
            double d = sorted.entrySet().iterator().next().getValue();
            autoValue.h_score = (float) d;
        }

        if (isAuto) {
            if (labelSpan.getSpanType() == HORIZENTAL_MANY || labelSpan.getSpanType() == HORIZENTAL_ONE) {
                // so we don't find vertical values if we have a valid value and label is Horizental
                return autoValue;
            }
        }


        AutoValue vertical_1 = findBestVerticalValues(lineTextBoxMap, lineLabelMap,
                labelSpan, candidateVerticalValues, 4, .1f, false, isAuto);
        if (vertical_1.vValue.size() == 0){


            float oldRight = labelSpan.getRight();
            float oldLeft = labelSpan.getLeft();
            float newRight = TextBox.extendToNeighbours(lineTextBoxMap, labelSpan);
            float newLeft = TextBox.extendToLeftNeighbours(lineTextBoxMap, labelSpan);
            labelSpan.setRight(newRight);
            labelSpan.setLeft(newLeft);
            vertical_1 = findBestVerticalValues(lineTextBoxMap, lineLabelMap,
                    labelSpan, candidateVerticalValues, 4, .99f, false, isAuto);
            // make sure we change it back to original border
            labelSpan.setRight(oldRight);
            labelSpan.setLeft(oldLeft);
        }

        autoValue.merge(vertical_1);
        return autoValue;
    }
    private float getArea(BaseTextBox textBox){
        if (textBox == null) return 0;
        return (textBox.getBase() - textBox.getTop()) * (textBox.getRight() - textBox.getLeft());
    }

    private boolean addShiftedValues(String content,
                                     int start_search_shift,
                                     Pattern match_pattern,
                                     int group,
                                     Analyzer analyzer,
                                     Pattern gap_pattern,
                                     List<Interval> matches)
    {

        String string_to_search = content.substring(start_search_shift);
        Matcher m = match_pattern.matcher(string_to_search);

        if (!m.find()) return false;

        int start_of_match = m.start();

        boolean match_is_valid = validateFoundValue(content, start_search_shift,
                start_of_match + start_search_shift, analyzer,
                gap_pattern);

        if (!match_is_valid) return false;

        // start and end should be shifted to match with the position of the match in
        // content
        int start_match = m.start(group);
        int end_match = m.end(group);
        Interval ext = new Interval(start_match + start_search_shift, end_match + start_search_shift);
        String extractionStr = string_to_search.substring(start_match, end_match);
        ext.setStr(extractionStr);

        matches.add(ext);
        return true;

    }
    private boolean validateFoundValue(String content,
                                       int start,
                                       int end,
                                       Analyzer analyzer,
                                       Pattern gapPattern){
        if (gapPattern == null) return true;

        String gap_between = content.substring(start, end);
        if (gap_between.length() == 0) return true;
        //first tokenize the gap
        if (analyzer != null) {
            gap_between = tokenize(analyzer, gap_between);
            if (gap_between.isEmpty()) return true;
        }

        Matcher gapmatcher = gapPattern.matcher(gap_between);
        if (!gapmatcher.find()) return false;
        return true;
    }

    private List<Interval> findAllHorizentalMatches(String content,
                                                    DictSearch dictSearch,
                                                    Interval labelInterval)
    {
        List<Interval> results = new ArrayList<>();
        Dictionary dictionary = dictSearch.getDictionary();
        Pattern padding_between_values = dictionary.getSkip_between_values();
        Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();
        // Find the lowest priority analyzer to tokenize the gaps
        // list of DctSearhFld is the same for all fields so we get the first one

        int start_search_shift = labelInterval.getEnd();
        int group = (dictionary.getGroups() == null || dictionary.getGroups().length == 0 )  ? 0 : dictionary.getGroups()[0];
        Pattern pattern = dictionary.getPattern();
        //field matching. One value for field
        boolean canContinueSearchForValue = addShiftedValues(content, start_search_shift,
                pattern, group, analyzer, padding_between_key_value, results);

        //Now find more than one value
        while (canContinueSearchForValue && padding_between_values != null) {
            start_search_shift = results.get(results.size() - 1).getEnd();
            canContinueSearchForValue = addShiftedValues(content, start_search_shift,
                    pattern, group, null, padding_between_values, results);
        }

        return results;
    }
    private List<Interval> findAllVerticalMatches(String content,
                                                  Map<Integer, BaseTextBox> lineTextBoxMap,
                                                  DictSearch dictSearch,
                                                  Interval labelInterval)
    {
        ArrayList<Interval> results = new ArrayList<>();
        Dictionary dictionary = dictSearch.getDictionary();
        Pattern padding_between_values = dictionary.getSkip_between_values();
        Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();

        //Best match has closest distance to center of label in X axis

        int startLineLabelInterval = getFirstNewLineBefore(content, labelInterval.getStart());

        int first_index_after_label = startNextToken(content, labelInterval.getEnd());
        int last_index_before_label = endPrevToken(content, labelInterval.getStart());

        // Start of header label
        int offsetStartLabel = last_index_before_label  - startLineLabelInterval;
        if (offsetStartLabel < 0) offsetStartLabel = 0;

        int offsetEndLabel   = first_index_after_label  - startLineLabelInterval;

        int end_interval = content.indexOf('\n', labelInterval.getEnd());

        if (end_interval < 0) return results; // no more lines to process

        int local_start_label = labelInterval.getStart() - startLineLabelInterval;
        int local_end_label = labelInterval.getEnd() - startLineLabelInterval;
        int header_length = local_end_label - local_start_label;
        if (header_length < 4) header_length = 4;  // minimum length for english word

        String [] lines = content.substring(end_interval).split("\n");
        int position_in_lines_array = end_interval;
        List<String> verticalGap = new ArrayList<>();

        for (int lc=0; lc < lines.length; lc++){
            String line = lines[lc];
            int line_length = line.length();
            if (line_length <= offsetStartLabel) {
                position_in_lines_array += line_length + 1;
                continue;
            }
            int start_local_interval = offsetStartLabel + position_in_lines_array;
            final int end_local_interval = Math.min(offsetEndLabel, line_length) + position_in_lines_array;

            // This is experimental
            // We look under a very wide range, the end of previous header all the way to the beginning of the next header
            // If table cells are longer than table headers this can cause problems.
            // let's move the start_local_interval forward if we find a wide white area. wide white area (aka spaces) means columns!
            Matcher white_space_matcher = LONG_SPACE.matcher(line);

            Map<Integer, Integer> index_2_space_width = new HashMap<>();
            while (white_space_matcher.find()) {
                if (white_space_matcher.start() > local_end_label) break;
                int distance_from_label_start = white_space_matcher.end() - local_start_label;

                index_2_space_width.put(white_space_matcher.end(), Math.abs(distance_from_label_start));
            }

            //account for the case when header starts at the beginning of the row
            if (local_start_label == 0) {
                Matcher beginning_white_space_matcher = START_SPACE.matcher(line);
                if (beginning_white_space_matcher.find()){
                    int d = beginning_white_space_matcher.end();
                    index_2_space_width.put(d, d);
                } else {
                    index_2_space_width.put(0, 0);
                }
            }

            if (index_2_space_width.size() > 0){
                // find the closest non-wide-space string as potential table value
                Map<Integer, Integer> index_2_space_width_sorted = MapSort.sortByValue(index_2_space_width);
                // end space gap is start of the cell value
                int end_space = index_2_space_width_sorted.entrySet().iterator().next().getKey();
                int cell_distance_from_header = Math.abs(local_start_label - end_space);
                if (cell_distance_from_header < 4 * header_length)
                {
                    start_local_interval = end_space + position_in_lines_array;
                } else {
                    position_in_lines_array += line_length + 1;
                    continue;
                }
            }

            position_in_lines_array += line_length + 1;

            if (start_local_interval >= end_local_interval) continue;
            String string2Search4Value = content.substring(start_local_interval, end_local_interval);
              // 1 is the length for \n

            Interval foundValue = null;

            int group = dictionary.getGroups() != null ? 1 : 0;
            Pattern pattern = dictionary.getPattern();
            boolean stopSearch = false;
            if (verticalGap.size() >0 && pattern.pattern().contains("\\n")){
                // if we allow multiline match
                String multiLineGap = String.join("\n", verticalGap);
                Matcher m = pattern.matcher(multiLineGap);
                if (m.find()) {
                    int start = m.start(group);
                    int end = m.end(group);
                    if (start >= 0 && end > start) {
                        // for multiline - we only capture the last line match
                    //    LineInfo lineInfo = getLineInfo(content, start + start_local_interval);
                        // we don't have a way of capturing bounding box of a multiline match!
                        foundValue = new Interval(start + start_local_interval,  end + end_local_interval);
                        foundValue.setStr(multiLineGap.substring(start, end));
                        stopSearch = true;
                    }
                    verticalGap.clear();
                }
            }
            if (!stopSearch) {
                Matcher m = pattern.matcher(string2Search4Value);
                if (m.find()) {
                    int start = m.start(group);
                    int end = m.end(group);
                    foundValue = new Interval(start + start_local_interval, end + start_local_interval);
                    String string_to_search = content.substring(start_local_interval);
                    String extractionStr = string_to_search.substring(start, end);
                    foundValue.setStr(extractionStr);
                }
            }

            if (foundValue == null) {
                verticalGap.add(string2Search4Value);
                continue;
            }

            if (results.size() == 0) {
                // this is field look up, one value for field
                if (padding_between_key_value == null){
                    results.add(foundValue);
                    if (padding_between_values == null) break;
                    verticalGap.clear();
                } else {
                    String vertical_gap = String.join("", verticalGap);
                    Matcher match_on_gap = padding_between_key_value.matcher(vertical_gap);
                    if (vertical_gap.isEmpty() || match_on_gap.find()) {
                        results.add(foundValue);
                        if (padding_between_values == null) break;
                        verticalGap.clear();
                    }
                }
            } else {
                String vertical_gap = String.join("", verticalGap);
                Matcher match_on_gap = padding_between_values.matcher(vertical_gap);
                if (vertical_gap.isEmpty() || match_on_gap.find()) {
                    results.add(foundValue);
                    verticalGap.clear();
                } else {
                    break;
                }
            }
        }

        return results;

    }
    protected int startNextToken(String str, int start){
        // chec if this is the last token in line
        int next_new_line = str.substring(start).indexOf("\n");
        if (next_new_line == -1){
            next_new_line = str.substring(start).length();
        }

        Matcher m = TOKEN.matcher(str.substring(start));
        if (m.find()){
            int start_next_token = m.start();
            if (start_next_token < next_new_line) {
                return start + start_next_token;
            }
            //else {
                // this is the last column/header in the line so the items below it can shift to the right as much as possible
          //      return start + 10000; // no line can be longer than 50000 ???
        //    }
        }
        return start;
    }
    protected int endPrevToken(String str, int start){
        // there is no way to search backward
        // so we create a substring just before the start and find the last match
        // 70 is long enough to look, back??
        int charBeforeStart = start;
        int beginning_of_line = getFirstNewLineBefore(str, start);
        if (beginning_of_line >= charBeforeStart) return charBeforeStart;

    //    int startString = Math.max(0, charBeforeStart - 70);
    //    if (charBeforeStart <= startString) return startString;
    //    String substringBeforeStart = str.substring(startString, charBeforeStart);
        String substringBeforeStart = str.substring(beginning_of_line, charBeforeStart);
        Matcher m = TOKEN.matcher(substringBeforeStart);
        int endPreviousToken = beginning_of_line;
        while (m.find()){
            endPreviousToken = m.end() + beginning_of_line;
        }
        return endPreviousToken;
    }
    // This is a very loose logic for assesing if the row is a header
    protected boolean lineIsTableRow(String lineStr){
        //logic to determine if a line of text is a table row
        //   xx xxxX[         ]yyyY[          ]     zzZ
        String trimmed_str = lineStr.trim();
        String[] number_large_gaps = trimmed_str.split("\\S {4,}");
        if (number_large_gaps.length - 1 > 0) return true;

        Matcher m1 = WORD_PTR.matcher(trimmed_str);
        int num_words = 0;
        while (m1.find()){
            num_words++;
        }
        if (num_words < 4) return true;
        Matcher m2 = SPC_BET_WORDS_PTR.matcher(trimmed_str);

        int num_space = 0;
        while (m2.find()){
            num_space++;
        }

        // dsds dsds dsds dsdsd dsdsd
        // aaa ffd a       hdj orii eof rere    --> not a tabke
        // fdfdfd       ffdfdfd fdfd fdfd      fdd ffd   --> could be a table
        if ( (num_words -1 ) ==  num_space ) return false;

        return true;
    }

    protected int getFirstNewLineBefore(String str, int index) {
        int index_start_line = str.substring(0, index).lastIndexOf('\n') + 1;
        if (index_start_line < 0) return 0;
        return index_start_line;
    }

    public String convertValues2titleTable(List<ExtInterval> values) {
        if (values == null) return null;
        Collections.sort(values, Comparator.comparingInt(ExtInterval::getStart));
        String result = "";
        LinkedHashSet<String> rows = new LinkedHashSet<>();
        for (ExtInterval ext : values) {

            StringBuilder sb = new StringBuilder();
            sb.append("<tr>");
            sb.append("<td>").append(ext.getCategory()).append("</td>");
            for (Interval extvStr : ext.getExtIntervalSimples()) {
                String customData = extvStr.getStr();
                if (customData == null) continue;
                sb.append("<td>").append(customData).append("</td>");
            }
            sb.append("</tr>");
            String row2add = sb.toString();
            rows.add(row2add);
        }

        if (!rows.isEmpty()) {
            if (!result.startsWith("<table ")) {
                result = "";
            }
            result += "<table width=\"100%\">" + String.join("", rows) + "</table>";
        } else {
            if (!result.startsWith("<table ")) {
                result = "";
            }
        }

        return result;
    }
}
