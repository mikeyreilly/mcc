package com.quaxt.mcc;

import java.util.regex.Pattern;

/*sometimes not binary (e.g. minus)*/
public enum BinaryOperator implements Token {
    SUB("-"), ADD("\\+"), IMUL("\\*"), DIVIDE("/"), REMAINDER("%"),
    AND("&&"), OR("\\|\\|"), EQUALS("=="), NOT_EQUALS("!="), LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN_OR_EQUAL(">="), LESS_THAN("<"), GREATER_THAN(">");

    final Pattern regex;

    BinaryOperator(String pattern) {
        this(Pattern.compile(pattern));
    }

    BinaryOperator(Pattern pattern) {
        regex = pattern;
    }

    public Pattern regex() {
        return regex;
    }
}
