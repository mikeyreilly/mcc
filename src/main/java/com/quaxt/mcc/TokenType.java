package com.quaxt.mcc;

import java.util.regex.Pattern;

public enum TokenType implements Token {
    LABEL("([a-zA-Z_]\\w*)\\s*:", 1),
    IDENTIFIER("[a-zA-Z_]\\w*\\b"), OPEN_PAREN("\\("), CLOSE_PAREN("\\)"),
    OPEN_BRACE("\\{"), CLOSE_BRACE("\\}"), CHAR_LITERAL("'([^'\\\\\n" +
            "]|\\\\['\"\\\\?abfnrtv])'"), STRING_LITERAL("\"([^\"\\\\\n" +
            "]|\\\\['\"\\\\?abfnrtv])*\""), DOUBLE_LITERAL("(([0-9]*\\" +
            ".[0-9]+|[0-9]+\\.?)[Ee][+-]?[0-9]+|[0-9]*\\.[0-9]+|[0-9]+\\.)" +
            "[^\\w.]", 1), UNSIGNED_LONG_LITERAL("([0-9]+([lL][uU]|[uU][lL]))" +
            "[^\\w.]", 1), UNSIGNED_INT_LITERAL("([0-9]+[uU])[^\\w.]", 1),
    LONG_LITERAL("([0-9]+[lL])[^\\w.]", 1), HEX_INT_LITERAL("0x([a-fA-F0-9]+)[^\\w.]", 1),INT_LITERAL("([0-9]+)[^\\w.]", 1)
    , SEMICOLON(";"), SINGLE_LINE_COMMENT("//.*"),
    MULTILINE_COMMENT(Pattern.compile("/\\*.*\\*/", Pattern.DOTALL), 0),
    BUILTIN_C23_VA_START(), BUILTIN_VA_ARG(), BUILTIN_VA_END(),
    UNSIGNED(), SIGNED(), GOTO(), CHAR(), SHORT(), DOUBLE(), LONG(), INT(), RETURN(), SWITCH(),
    TYPEDEF(), CASE(),DEFAULT(),
    VOID(), ELSE(), IF(), BREAK(), CONTINUE(), WHILE(), DO(), FOR(), EXTERN()
    , SIZEOF(), STATIC(), STRUCT(), UNION(), DECREMENT("--"), INCREMENT("\\+\\+"),
    BITWISE_NOT("~"), NOT("!"), QUESTION_MARK("\\?"), COLON(":"),
    OPEN_BRACKET("\\["), CLOSE_BRACKET("\\]"), ARROW("->"),
    ELLIPSIS("\\.\\.\\."), DOT("([.])[^0-9]", 1);
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
        return switch (this) {
            case ARROW -> "->";
            case BITWISE_NOT -> "bitwise_not";
            case BREAK -> "break";
            case CASE -> "case";
            case CHAR -> "char";
            case CHAR_LITERAL -> "char_literal";
            case CLOSE_BRACE -> "}";
            case CLOSE_BRACKET -> "]";
            case CLOSE_PAREN -> ")";
            case COLON -> ":";
            case CONTINUE -> "continue";
            case DECREMENT -> "decrement";
            case DEFAULT -> "default";
            case DO -> "do";
            case DOT -> ".";
            case DOUBLE -> "double";
            case DOUBLE_LITERAL -> "double_literal";
            case ELLIPSIS -> "ellipsis";
            case ELSE -> "else";
            case EXTERN -> "extern";
            case FOR -> "for";
            case GOTO -> "goto";
            case IDENTIFIER -> "identifier";
            case IF -> "if";
            case INCREMENT -> "increment";
            case INT -> "int";
            case INT_LITERAL -> "int_literal";
            case HEX_INT_LITERAL -> "hex_int_literal";
            case LABEL -> "label";
            case LONG -> "long";
            case LONG_LITERAL -> "long_literal";
            case MULTILINE_COMMENT -> "multiline_comment";
            case NOT -> "not";
            case OPEN_BRACE -> "{";
            case OPEN_BRACKET -> "[";
            case OPEN_PAREN -> "(";
            case QUESTION_MARK -> "?";
            case RETURN -> "return";
            case SEMICOLON -> ";";
            case SIGNED -> "signed";
            case SINGLE_LINE_COMMENT -> "single_line_comment";
            case SHORT -> "short";
            case SIZEOF -> "sizeof";
            case STATIC -> "static";
            case STRING_LITERAL -> "string_literal";
            case STRUCT -> "struct";
            case SWITCH -> "switch";
            case BUILTIN_C23_VA_START -> "__builtin_c23_va_start";
            case BUILTIN_VA_ARG -> "__builtin_va_arg";
            case BUILTIN_VA_END -> "__builtin_va_end";
            case TYPEDEF -> "typedef";
            case UNION -> "union";
            case UNSIGNED -> "unsigned";
            case UNSIGNED_INT_LITERAL -> "unsigned_int_literal";
            case UNSIGNED_LONG_LITERAL -> "unsigned_long_literal";
            case VOID -> "void";
            case WHILE -> "while";
        };
    }
}
