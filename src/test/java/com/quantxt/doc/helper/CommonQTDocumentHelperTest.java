package com.quantxt.doc.helper;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.DictItm;
import com.quantxt.types.Dictionary;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.QTField.QTFieldType.DOUBLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CommonQTDocumentHelperTest {
    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelperTest.class);


    @Test
    public void simpleExtraction() {
        String str = "Share-based compensation (benefit) expense (in thousands)\n" +
                "(54.6 \n" +
                ") \n" +
                "102.7 \n" +
                "(153.2 \n" +
                ")% \n" +
                "Total selling, general and administrative expense(1) \n" +
                "$ \n" +
                "177.3 \n" +
                "$ \n" +
                "304.0 \n" +
                "(41.7 \n" +
                ")% ";

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));
        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Expense" , dictItms);

        Dictionary dictionary = new Dictionary(entMap, "test", DOUBLE, 5,
                Pattern.compile("^\\s*(\\([^\\)]+\\))?[\\s,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]+$"), null, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<QTSearchable> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177300.0</td><td>304000.0</td><td>41.7</td></tr></table>")
        ;
    }
}
