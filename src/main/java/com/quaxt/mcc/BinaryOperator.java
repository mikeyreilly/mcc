package com.quaxt.mcc;

import java.util.Locale;

public enum BinaryOperator {
    ADD, SUB, IMUL, DIVIDE, REMAINDER;

    final String code;

    BinaryOperator(){
        this.code = name().toLowerCase(Locale.ROOT);
    }
}
