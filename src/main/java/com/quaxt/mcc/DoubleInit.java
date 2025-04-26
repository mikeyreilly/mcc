package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;
import static com.quaxt.mcc.semantic.Primitive.DOUBLE;

public record DoubleInit(double d) implements StaticInit, Constant {
    @Override
    public Type type() {
        return DOUBLE;
    }

    public long toLong() {
        return (long)d;
    }
}
