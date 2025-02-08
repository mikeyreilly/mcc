package com.quaxt.mcc.semantic;

import com.quaxt.mcc.IntInit;
import com.quaxt.mcc.LongInit;
import com.quaxt.mcc.StaticInit;

public enum Primitive implements Type {
    INT(new IntInit(0)), LONG(new LongInit(0));

    private final StaticInit zero;

    Primitive(StaticInit zero) {
        this.zero = zero;
    }

     @Override
     public StaticInit zero() {
        return zero;
    }

}
