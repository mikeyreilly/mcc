package com.quaxt.mcc;

import java.util.regex.Pattern;

/*sometimes not binary (e.g. minus)*/
public enum ArithmeticOperator implements BinaryOperator {
    SUB("-"), ADD("\\+"), IMUL("\\*"), DIVIDE("/"), REMAINDER("%"), AND("&&")
    , OR("\\|\\|"), BITWISE_XOR("\\^"), BECOMES("="), // These are used in
    // asm but not before, so in parser and tacky SUB is used
    // for both integer and double subtraction.
    DOUBLE_SUB(), DOUBLE_ADD(), DOUBLE_MUL(), DOUBLE_DIVIDE(), SHL, SHR_TWO_OP;

    final Pattern regex;

    ArithmeticOperator(String pattern) {
        this(Pattern.compile(pattern));
    }

    ArithmeticOperator(Pattern pattern) {
        regex = pattern;
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
            case SHL -> "SHL";
            case SHR_TWO_OP -> "SHR_TWO_OP";
            case DOUBLE_DIVIDE -> "/";
            default -> regex.pattern();
        };
    }
}
