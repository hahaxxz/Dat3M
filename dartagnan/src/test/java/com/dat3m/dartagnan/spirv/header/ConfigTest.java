package com.dat3m.dartagnan.spirv.header;

import com.dat3m.dartagnan.program.Program;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConfigTest extends AbstractTest {

    @Test
    public void testDefaultConfig() {
        // when
        Program program = parse("");

        // then
        assertEquals(1, program.getThreads().size());
    }

    @Test
    public void testExplicitConfig() {
        // when
        Program program = parse("; @Config: 2, 3, 4");

        // then
        assertEquals(24, program.getThreads().size());
    }
}
