package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.expression.type.IntegerType;

// TODO why is NonDetInt not a IntConst?
public class NonDetInt extends IntExpr {

    private final int id;
    private String sourceName;

    // Should only be accessed from Program
    public NonDetInt(int id, IntegerType type) {
        super(type);
        this.id = id;
    }

    public String getName() {
        return "nondet_int#" + id;
    }

    public void setSourceName(String name) {
        sourceName = name;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        if (sourceName != null) {
            return sourceName;
        }
        IntegerType type = getType();
        if (type.isMathematical()) {
            return String.format("nondet_int(%d)", id);
        }
        return String.format("nondet_i%d(%d)", type.getBitWidth(), id);
    }
}
