package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.ULONG;

public record ConstULong(long l) implements Constant {
    public ConstULong(long l){
        this.l=l;
    }
    @Override
    public Type type() {
        return ULONG;
    }

    @Override
    public String toString() {
        return "ConstULong[l=" + Long.toUnsignedString(l) + "]";
    }


    public long toLong() {
        return l;
    }
}
