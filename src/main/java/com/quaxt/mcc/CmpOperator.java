package com.quaxt.mcc;

import java.util.regex.Pattern;

/*sometimes not binary (e.g. minus)*/
public enum CmpOperator implements BinaryOperator {
    EQUALS("==", "e"),
    NOT_EQUALS("!=", "ne"),
    LESS_THAN_OR_EQUAL("<=", "le"),
    GREATER_THAN_OR_EQUAL(">=", "ge"),
    LESS_THAN("<", "l"),
    GREATER_THAN(">", "g");

    final Pattern regex;
    public final String code;
    
    CmpOperator(String pattern, String code) {
        regex=Pattern.compile(pattern);
        this.code = code;
    }

    public Pattern regex() {
        return regex;
    }
}
