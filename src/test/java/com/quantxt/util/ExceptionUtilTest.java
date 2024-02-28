package com.quantxt.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExceptionUtilTest {

    @Test
    public void testGetRootCause() {
        //given:
        Exception rootException = new Exception("rootException");
        Exception wrapperException1 = new Exception("wrapperException1", rootException);
        Exception wrapperException2 = new RuntimeException("wrapperException2", wrapperException1);

        //when:
        Throwable rootCause = ExceptionUtil.getRootCause(wrapperException2);

        //then:
        assertEquals(rootException, rootCause);
    }
}