package com.quaxt.mcc.parser;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.semantic.NullptrT;
import com.quaxt.mcc.semantic.Type;

public enum Nullptr implements Constant {
    NULLPTR;

    @Override
    public long toLong() {
        return 0;
    }

    @Override
    public Constant<?> apply(BinaryOperator op, Constant v2) {
        return null;
    }

    @Override
    public boolean isZero() {
        return false;
    }

    @Override
    public Constant apply(UnaryOperator op) {
        return null;
    }

    @Override
    public Constant<?> apply1(BinaryOperator op, Constant c2) {
        return null;
    }

    @Override
    public Type type() {
        return NullptrT.NULLPTR_T;
    }
}
