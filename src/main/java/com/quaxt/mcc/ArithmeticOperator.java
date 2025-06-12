package com.quaxt.mcc;

import java.util.regex.Pattern;

/*sometimes not binary (e.g. minus)*/
public enum ArithmeticOperator implements BinaryOperator {
    SUB("-"), ADD("\\+"), IMUL("\\*"), DIVIDE("/"), REMAINDER("%"), AND("&&"),
    BITWISE_AND("&"), OR("\\|\\|"), BITWISE_OR("\\|"), BITWISE_XOR("\\^"), BECOMES("="),
    SHL("<<"), SAR(">>"),
    COMMA(","),
    // These DOUBLE_FOO operators are used in asm but not before, so in parser and tacky SUB is used
    // for both integer and double subtraction.
    DOUBLE_SUB(), DOUBLE_ADD(), DOUBLE_MUL(), DOUBLE_DIVIDE(),

    // just used in asm
    UNSIGNED_RIGHT_SHIFT(">>>");

    final Pattern regex;

    ArithmeticOperator(String pattern) {
        regex = Pattern.compile(pattern);
    }

    ArithmeticOperator() {
        regex = null;
    }

    public Pattern regex() {
        return regex;
    }

    public String toString() {
        return switch (this) {
            case ADD, DOUBLE_ADD -> "+";
            case IMUL, DOUBLE_MUL -> "*";
            case DOUBLE_SUB -> "-";
            case OR -> "|";
            case SHL -> "<<";
            case SAR -> ">>";
            case DOUBLE_DIVIDE -> "/";
            default -> regex.pattern();
        };
    }
}
