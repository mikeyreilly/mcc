package com.quaxt.mcc;

import java.util.regex.Pattern;

public enum TokenType implements Token {
    IDENTIFIER("[a-zA-Z_]\\w*\\b"), OPEN_PAREN("\\("), CLOSE_PAREN("\\)"),
    OPEN_BRACE("\\{"), CLOSE_BRACE("\\}"),
    CHAR_LITERAL("'([^'\\\\\n]|\\\\['\"\\\\?abfnrtv])'"),
    STRING_LITERAL("\"([^\"\\\\\n]|\\\\['\"\\\\?abfnrtv])*\""),
    DOUBLE_LITERAL(
            "(([0-9]*\\.[0-9]+|[0-9]+\\.?)[Ee][+-]?[0-9]+|[0-9]*\\.[0-9]+|[0-9]+\\.)[^\\w.]", 1),
    UNSIGNED_LONG_LITERAL("([0-9]+([lL][uU]|[uU][lL]))[^\\w.]", 1),
    UNSIGNED_INT_LITERAL("([0-9]+[uU])[^\\w.]", 1),
    LONG_LITERAL("([0-9]+[lL])[^\\w.]", 1), INT_LITERAL("([0-9]+)[^\\w.]", 1), SEMICOLON(";"),
    SINGLE_LINE_COMMENT("//.*"), MULTILINE_COMMENT(Pattern.compile("/\\*.*\\*/",
            Pattern.DOTALL), 0), UNSIGNED(), SIGNED(),
    CHAR(),
    DOUBLE(), LONG(), INT(), RETURN(), VOID(), ELSE(), IF(),
    BREAK(), CONTINUE(), WHILE(), DO(), FOR(), EXTERN(), SIZEOF(), STATIC(),
    DECREMENT("--"), INCREMENT("\\+\\+"), BITWISE_NOT("~"),
    NOT("!"), QUESTION_MARK("\\?"), COLON(":"),
    COMMA(","), AMPERSAND("&"),OPEN_BRACKET("\\["),CLOSE_BRACKET("\\]");
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
    public String toString() {
        return switch(this) {
        case IDENTIFIER -> "identifier";
        case OPEN_PAREN -> "(";
        case CLOSE_PAREN -> ")";
        case OPEN_BRACE -> "{";
        case CLOSE_BRACE -> "}";
        case CHAR_LITERAL -> "char_literal";
        case STRING_LITERAL -> "string_literal";
        case DOUBLE_LITERAL -> "double_literal";
        case UNSIGNED_LONG_LITERAL -> "unsigned_long_literal";
        case UNSIGNED_INT_LITERAL -> "unsigned_int_literal";
        case LONG_LITERAL -> "long_literal";
        case INT_LITERAL -> "int_literal";
        case SEMICOLON -> ";";
        case SINGLE_LINE_COMMENT -> "single_line_comment";
        case MULTILINE_COMMENT -> "multiline_comment";
        case UNSIGNED -> "unsigned";
        case SIGNED -> "signed";
        case SIZEOF -> "sizeof";
        case CHAR -> "char";
        case DOUBLE -> "double";
        case LONG -> "long";
        case INT -> "int";
        case RETURN -> "return";
        case VOID -> "void";
        case ELSE -> "else";
        case IF -> "if";
        case BREAK -> "break";
        case CONTINUE -> "continue";
        case WHILE -> "while";
        case DO -> "do";
        case FOR -> "for";
        case EXTERN -> "extern";
        case STATIC -> "static";
        case DECREMENT -> "decrement";
        case INCREMENT -> "increment";
        case BITWISE_NOT -> "bitwise_not";
        case NOT -> "not";
        case QUESTION_MARK -> "?";
        case COLON -> ":";
        case COMMA -> ",";
        case AMPERSAND -> "&";
        case OPEN_BRACKET -> "[";
        case CLOSE_BRACKET -> "]";
        };
    }
}
