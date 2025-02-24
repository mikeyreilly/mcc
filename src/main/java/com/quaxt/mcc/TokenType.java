package com.quaxt.mcc;

import java.util.regex.Pattern;

public enum TokenType implements Token {
    IDENTIFIER("[a-zA-Z_]\\w*\\b"), OPEN_PAREN("\\("), CLOSE_PAREN("\\)"),
    OPEN_BRACE("\\{"), CLOSE_BRACE("\\}"),
    DOUBLE_LITERAL(
            "(([0-9]*\\.[0-9]+|[0-9]+\\.?)[Ee][+-]?[0-9]+|[0-9]*\\.[0-9]+|[0-9]+\\.)[^\\w.]", 1),
    UNSIGNED_LONG_LITERAL("([0-9]+([lL][uU]|[uU][lL]))[^\\w.]", 1),
    UNSIGNED_INT_LITERAL("([0-9]+[uU])[^\\w.]", 1),
    LONG_LITERAL("([0-9]+[lL])[^\\w.]", 1), INT_LITERAL("([0-9]+)[^\\w.]", 1), SEMICOLON(";"),
    SINGLE_LINE_COMMENT("//.*"), MULTILINE_COMMENT(Pattern.compile("/\\*.*\\*/",
            Pattern.DOTALL), 0), UNSIGNED(), SIGNED(),
    DOUBLE(), LONG(), INT(), RETURN(), VOID(), ELSE(), IF(),
    BREAK(), CONTINUE(), WHILE(), DO(), FOR(), EXTERN(), STATIC(),
    DECREMENT("--"), INCREMENT("\\+\\+"), BITWISE_NOT("~"),
    NOT("!"), QUESTION_MARK("\\?"), COLON(":"),
    COMMA(","), AMPERSAND("&");
    final Pattern regex;
    private final int group;

    TokenType(String pattern, int group) {
        this(Pattern.compile(pattern), group);
    }

    TokenType(Pattern pattern, int group) {
        regex = pattern;
        this.group = group;
    }

    TokenType() {
        regex = null;
        group = 0;
    }

    TokenType(String pattern) {
        this(Pattern.compile(pattern), 0);
    }

    public Pattern regex() {
        return regex;
    }

    public int group() {
        return group;
    }
}
