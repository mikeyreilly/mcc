package com.quaxt.mcc;

import java.util.regex.Pattern;

public enum TokenType implements Token {
    IDENTIFIER("[a-zA-Z_]\\w*\\b"), OPEN_PAREN("\\("), CLOSE_PAREN("\\)"),
    OPEN_BRACE("\\{"), CLOSE_BRACE("\\}"), LONG_LITERAL("[0-9]+[lL]\\b"), NUMERIC("[0-9]+\\b"), SEMICOLON(";"),
    SINGLE_LINE_COMMENT("//.*"), MULTILINE_COMMENT(Pattern.compile("/\\*.*\\*/",
            Pattern.DOTALL)), LONG(), INT(), RETURN(), VOID(), ELSE(), IF(),
            BREAK(), CONTINUE(), WHILE(), DO(), FOR(), EXTERN(), STATIC(),
    DECREMENT("--"), INCREMENT("\\+\\+"), COMPLIMENT("~"),
    NOT("!"),QUESTION_MARK("\\?"),COLON(":"),
    COMMA(",");
    final Pattern regex;

    TokenType(String pattern) {
       this(Pattern.compile(pattern));
    }

    TokenType(Pattern pattern) {
        regex = pattern;
    }

    TokenType() {
        regex = null;
    }

    public Pattern regex(){
        return regex;
    }
}
