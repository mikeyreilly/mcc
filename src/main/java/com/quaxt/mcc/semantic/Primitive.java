package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;

public enum Primitive implements Type {
    INT(new IntInit(0)), UINT(new UIntInit(0)), LONG(new LongInit(0)), ULONG(new ULongInit(0));

    private final StaticInit zero;

    Primitive(StaticInit zero) {
        this.zero = zero;
    }

     @Override
     public StaticInit zero() {
        return zero;
    }

}
