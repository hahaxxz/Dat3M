package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.expression.type.Type;

public class NonDetExpr implements Expression {

    private final int id;
    private final Type type;
    private String sourceName;

    // Should only be accessed from Program
    public NonDetExpr(int id, Type type) {
        this.id = id;
        this.type = type;
    }

    public String getName() {
        return "nondet_#" + id;
    }

    public void setSourceName(String name) {
        sourceName = name;
    }

    @Override
    public Type getType() {
        return type;
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
        return String.format("nondet_%s(%d)", type, id);
    }
}
