package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.UINT;

public record ConstUInt(int i) implements Constant {
    @Override
    public Type type() {
        return UINT;
    }

    public static void main(String[] args) {
        var r = new ConstUInt(-1);
        var s = r.toString();
        Record x;
        System.out.println(s);
    }

    @Override
    public String toString() {
        return "ConstUInt[i=" + Integer.toUnsignedString(i) + "]";
    }
}
