package com.dat3m.dartagnan.program.specification;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.integers.IntCmpOp;
import com.dat3m.dartagnan.expression.processing.ExpressionInspector;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssertBasic extends AbstractAssert {

    private final Expression e1;
    private final Expression e2;
    private final IntCmpOp op;

    public AssertBasic(Expression e1, IntCmpOp op, Expression e2) {
        this.e1 = e1;
        this.e2 = e2;
        this.op = op;
    }

    public Expression getLeft() {
        return e1;
    }

    public Expression getRight() {
        return e2;
    }

    @Override
    public BooleanFormula encode(EncodingContext context) {
        return context.encodeComparison(op,
                context.encodeFinalExpression(e1),
                context.encodeFinalExpression(e2));
    }

    @Override
    public String toString() {
        return valueToString(e1) + op + valueToString(e2);
    }

    private String valueToString(Expression value) {
        if (value instanceof Register register) {
            return register.getFunction().getId() + ":" + value;
        }
        return value.toString();
    }

    @Override
    public List<Register> getRegisters() {
        List<Register> regs = new ArrayList<>();
        if (e1 instanceof Register r1) {
            regs.add(r1);
        }
        if (e2 instanceof Register r2) {
            regs.add(r2);
        }
        return regs;
    }

    @Override
    public Set<MemoryObject> getMemoryObjects() {
        MemoryObjectCollector collector = new MemoryObjectCollector();
        e1.accept(collector);
        e2.accept(collector);
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
