package com.quaxt.mcc.tacky;

import com.quaxt.mcc.StaticInit;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.CHAR;

public record CharInit(int i) implements StaticInit, Constant {
    final static CharInit ZERO = new CharInit((byte) 0);

    public static ValIr zero() {
        return ZERO;
    }

    @Override
    public Type type() {
        return CHAR;
    }

    @Override
    public String toString() {
        return String.valueOf((char) i);
    }


    public long toLong() {
        return i;
    }
}
