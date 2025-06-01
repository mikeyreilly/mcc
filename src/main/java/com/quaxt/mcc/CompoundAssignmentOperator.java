package com.quaxt.mcc;

import java.util.regex.Pattern;

public enum CompoundAssignmentOperator implements BinaryOperator {
    SUB_EQ("-="),
    ADD_EQ("\\+="),
    IMUL_EQ("\\*="),
    DIVIDE_EQ("/="),
    REMAINDER_EQ("%="),
    AND_EQ("&&="),
    BITWISE_AND_EQ("&="),
    OR_EQ("\\|\\|="),
    BITWISE_OR_EQ("\\|="),
    BITWISE_XOR_EQ("\\^="),
    SHL_EQ("<<="),
    SAR_EQ(">>=");

    final Pattern regex;

    CompoundAssignmentOperator(String pattern) {
        regex = Pattern.compile(pattern);
    }

    public Pattern regex() {
        return regex;
    }
}
