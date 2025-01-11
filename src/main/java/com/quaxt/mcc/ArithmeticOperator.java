package com.quaxt.mcc;

import java.util.regex.Pattern;

/*sometimes not binary (e.g. minus)*/
public enum ArithmeticOperator implements BinaryOperator {
    SUB("-"), ADD("\\+"), IMUL("\\*"), DIVIDE("/"), REMAINDER("%"),
    AND("&&"), OR("\\|\\|"), BECOMES("=");

    final Pattern regex;

    ArithmeticOperator(String pattern) {
        this(Pattern.compile(pattern));
    }

    ArithmeticOperator(Pattern pattern) {
        regex = pattern;
    }

    public Pattern regex() {
        return regex;
    }
}
