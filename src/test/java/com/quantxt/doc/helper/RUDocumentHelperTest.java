package com.quantxt.doc.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Created by dejani on 2/1/18.
 */
public class RUDocumentHelperTest {

    private static RUDocumentHelper helper = new RUDocumentHelper();

    @Test
    public void testIsTag() {
        // GIVEN
        String tag1 = "C";
        String tag3 = "I";
        String tag2 = "S";

        String tag4 = "D";
        String tag5 = "CC";
        String tag6 = "CS";

        // WHEN
        boolean isTag1 = helper.isTagDC(tag1);
        boolean isTag2 = helper.isTagDC(tag2);
        boolean isTag3 = helper.isTagDC(tag3);

        boolean isTag4 = helper.isTagDC(tag4);
        boolean isTag5 = helper.isTagDC(tag5);
        boolean isTag6 = helper.isTagDC(tag6);

        // THEN
        assertTrue(isTag1);
        assertTrue(isTag2);
        assertTrue(isTag3);

        assertFalse(isTag4);
        assertFalse(isTag5);
        assertFalse(isTag6);
    }

}
