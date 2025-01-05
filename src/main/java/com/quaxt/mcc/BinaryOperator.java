package com.quaxt.mcc;

import java.util.regex.Pattern;
/*sometimes not binary (e.g. minus)*/
public enum BinaryOperator implements Token {
    SUB("-"), ADD("\\+"), IMUL("\\*"), DIVIDE("/"), REMAINDER("%");

    final Pattern regex;

    BinaryOperator(String pattern) {
       this(Pattern.compile(pattern));
    }

    BinaryOperator(Pattern pattern) {
        regex = pattern;
    }

    BinaryOperator() {
        regex = null;
    }

    public Pattern regex(){
        return regex;
    }
}
