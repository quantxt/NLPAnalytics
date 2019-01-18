package com.quantxt.nlp.types;

import com.quantxt.helper.types.ExtInterval;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Created by matin on 11/24/18.
 */
public class QTValueNumberTest {
    private static Logger logger = LoggerFactory.getLogger(QTValueNumberTest.class);

    @Test
    public void testNumber1() {
        String str = "The rate are -5.13% and 4.32% with -4.39 4.35% are the rates";
        List<ExtInterval> list = new ArrayList<>();
        QTValueNumber.detect(str, str, list);
        assertTrue(list.size() == 4);
        assertTrue(list.get(0).getNumbervalue() == -5.13);
        assertTrue(list.get(3).getNumbervalue() == 4.35);
        assertTrue(list.get(3).getType() == ExtInterval.ExtType.PERCENT);
    }

    @Test
    public void testNumber2() {
        String str = "Standardized Total Returns (I-shares) for Yield to Worst (%) 7.84 17.84 (6.09) 1.764";
        List<ExtInterval> list = new ArrayList<>();
        QTValueNumber.detect(str, str, list);
        assertTrue(list.size() == 4);
        assertTrue(list.get(2).getNumbervalue() == -6.09);
        assertTrue(list.get(3).getNumbervalue() == 1.764);
    }

    @Test
    public void multiplepointTest1() {
        String str = "Standardized Total Returns (I-shares) for Yield to Worst (%) 7.84 17.84 (6.09) 1.764.";
        List<ExtInterval> list = new ArrayList<>();
        QTValueNumber.detect(str, str, list);
        assertTrue(list.size() == 4);
        assertTrue(list.get(2).getNumbervalue() == -6.09);
        assertTrue(list.get(3).getNumbervalue() == 1.764);
    }

    @Test
    public void commaNumberTest1() {
        String str = "Standardized Total Returns (I-shares) for Yield to Worst (%) 70,000,840 17.84 (6.09) 1.764.";
        List<ExtInterval> list = new ArrayList<>();
        QTValueNumber.detect(str, str, list);
        assertTrue(list.size() == 4);
        assertTrue(list.get(0).getNumbervalue() == 70000840);
        assertTrue(list.get(2).getNumbervalue() == -6.09);
    }

    @Test
    public void negativeNumberCommaTest1() {
        String str = "Standardized Total Returns (I-shares) for Yield to Worst (%) (70,000,840) 17.84 (6.09) 1.764.";
        List<ExtInterval> list = new ArrayList<>();
        QTValueNumber.detect(str, str, list);
        assertTrue(list.size() == 4);
        assertTrue(list.get(0).getNumbervalue() == -70000840);
    }

    @Test
    public void moneyTest1() {
        String str = "Standardized Total Returns (I-shares) for Yield to Worst (%) $70,000,840 17.84 (6.09) 1.764.";
        List<ExtInterval> list = new ArrayList<>();
        QTValueNumber.detect(str, str, list);
        assertTrue(list.size() == 4);
        assertTrue(list.get(0).getNumbervalue() == 70000840);
        assertTrue(list.get(0).getType() == ExtInterval.ExtType.MONEY);
    }

    @Test
    public void moneyTest2() {
        String str = "Standardized Total Returns (I-shares) for Yield to Worst (%) $" + "\n\n\n" +
                "          70,000,840 17.84 (6.09) 1.764.";
        List<ExtInterval> list = new ArrayList<>();
        QTValueNumber.detect(str, str, list);
        assertTrue(list.size() == 4);
        assertTrue(list.get(0).getNumbervalue() == 70000840);
        assertTrue(list.get(0).getType() == ExtInterval.ExtType.MONEY);
    }

    @Test
    public void moneyCommaAndDot() {
        String str = "Standardized Total Returns (I-shares) for Yield to Worst (%) $" + "\n\n\n" +
                "          70,000,840.3 17.84 (6.09) 1.764.";
        List<ExtInterval> list = new ArrayList<>();
        QTValueNumber.detect(str, str, list);
        assertTrue(list.size() == 4);
        assertTrue(list.get(0).getNumbervalue() == 70000840.3);
        assertTrue(list.get(0).getType() == ExtInterval.ExtType.MONEY);
    }
}
