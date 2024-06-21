package com.dat3m.dartagnan.spirv.header;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class FormatTest extends AbstractTest {

    private final String input;
    private final boolean passed;
    private final String msg;

    public FormatTest(String input, String msg, boolean passed) {
        this.input = input;
        this.msg = msg;
        this.passed = passed;
    }

    @Parameterized.Parameters()
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"""
                ; @Output: forall (0)
                ; @Config: 1, 1, 1
                ; @Config: 1, 1, 1
                """,
                        "Multiple configs should not have thrown any exception", true},
                {"""
                ; @Input: %v1=7, %v2=123, %v3=0
                ; @Input: %v1=7
                ; @Output: forall (%v1==7 and %v2==123 and %v3==0)
                ; @Config: 1, 1, 1
                """,
                        "Duplicated definition '%v1'", false},
                {"""
                ; @Input: %v1=7, %v2=123, %v3=0
                ; @Output: exists (%undefined!=456)
                ; @Config: 1, 1, 1
                """,
                        "Undefined memory object '%undefined'", false},
                {"""
                ; @Input: %v1=7, %v2=123, %v3=0
                ; @Output: exists (%v1!=7)
                ; @Output: not exists (%v2==123 and %v3==0)
                ; @Config: 1, 1, 1
                """,
                        "Existential assertions can not be used in conjunction with other assertions", false},
                {"""
                ; @Input: %v1=7, %v2=123, %v3=0
                ; @Output: exists (%v1!=7)
                ; @Output: forall (%v2==123 and %v3==0)
                ; @Config: 1, 1, 1
                """,
                        "Existential assertions can not be used in conjunction with other assertions", false},
                {"""
                ; @Input: %v1=7, %v2=123, %v3=0
                ; @Output: exists (%v1!=7)
                ; @Output: exists (%v2==123 and %v3==0)
                ; @Config: 1, 1, 1
                """,
                        "Existential assertions can not be used in conjunction with other assertions", false},
        });
    }

    @Test
    public void testValidFormatHeader() {
        if (passed) {
            parse(input);
        } else {
            try {
                parse(input);
                fail("Should throw exception");
            } catch (Exception e) {
                assertEquals(msg, e.getMessage());
            }
        }
    }
}