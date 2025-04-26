package com.quaxt.mcc.tacky;

import com.quaxt.mcc.StaticInit;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.UINT;

public record UCharInit(int i) implements StaticInit, Constant {
    @Override
    public Type type() {
        return UINT;
    }

    @Override
    public String toString() {
        return "(uchar)" + String.valueOf((char) i);
    }


    public long toLong() {
        return i;
    }
}
