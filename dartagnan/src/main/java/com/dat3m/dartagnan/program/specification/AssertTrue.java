package com.dat3m.dartagnan.program.specification;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AssertTrue extends AbstractAssert {

    @Override
    public BooleanFormula encode(EncodingContext ctx) {
        return ctx.getBooleanFormulaManager().makeTrue();
    }

    @Override
    public String toString() {
        return "true";
    }

    @Override
    public List<Register> getRegisters() {
        return Collections.emptyList();
    }

    @Override
    public Set<MemoryObject> getMemoryObjects() {
        return Set.of();
    }
}
