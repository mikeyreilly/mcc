package com.quaxt.mcc.semantic;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.parser.Initializer;

import static com.quaxt.mcc.semantic.Primitive.ULONG;

public enum NullptrT implements Type {
    NULLPTR_T,

    ;
    final Initializer zeroInitializer;

    NullptrT() {
        zeroInitializer = ULONG.zeroInitializer;
    }

    @Override
    public boolean looseEquals(Type other) {
        return NULLPTR_T == other;
    }

}