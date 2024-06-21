package com.dat3m.dartagnan.program.specification;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.processing.ExpressionInspector;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.core.Assert;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.program.processing.RemoveUnusedMemory;
import com.google.common.collect.Sets;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssertInline extends AbstractAssert {

    private final Assert assertion;

    public AssertInline(Assert assertion) {
        this.assertion = assertion;
    }

    public Assert getAssertion() {
        return assertion;
    }

    @Override
    public BooleanFormula encode(EncodingContext ctx) {
        final BooleanFormulaManager bmgr = ctx.getBooleanFormulaManager();
        return bmgr.implication(ctx.execution(assertion),
                ctx.encodeExpressionAsBooleanAt(assertion.getExpression(), assertion));
    }

    @Override
    public String toString() {
        return String.format("%s@%d", assertion.getExpression(), assertion.getGlobalId());
    }

    @Override
    public List<Register> getRegisters() {
        return new ArrayList<>(assertion.getExpression().getRegs());
    }

    @Override
    public Set<MemoryObject> getMemoryObjects() {
        MemoryObjectCollector collector = new MemoryObjectCollector();
        assertion.getExpression().accept(collector);
        return collector.memoryObjects;
    }

    private static class MemoryObjectCollector implements ExpressionInspector {

        private final HashSet<MemoryObject> memoryObjects = new HashSet<>();

        @Override
        public Expression visitMemoryObject(MemoryObject address) {
            memoryObjects.add(address);
            return address;
        }

        @Override
        public Expression visitLocation(Location location) {
            memoryObjects.add(location.getMemoryObject());
            return location;
        }
    }
}
