package com.quaxt.mcc;

import java.util.regex.Pattern;

public enum CmpOperator implements BinaryOperator {
    EQUALS("==", "e", "e"),
    NOT_EQUALS("!=", "ne", "ne"),
    LESS_THAN_OR_EQUAL("<=", "le", "be"),
    GREATER_THAN_OR_EQUAL(">=", "ge", "ae"),
    LESS_THAN("<", "l", "b"),
    GREATER_THAN(">", "g", "a");

    final Pattern regex;
    public final String code;
    public final String unsignedCode;

    CmpOperator(String pattern, String code, String unsignedCode) {
        regex = Pattern.compile(pattern);
        this.code = code;
        this.unsignedCode = unsignedCode;
    }

    public Pattern regex() {
        return regex;
    }

    public String toString() {
        return regex.pattern();
    }
}
