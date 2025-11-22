package com.quaxt.mcc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum TokenType implements Token {
    IDENTIFIER("[a-zA-Z_]\\w*\\b"), OPEN_PAREN("\\("), CLOSE_PAREN("\\)"),
    OPEN_BRACE("\\{"), CLOSE_BRACE("\\}"), CHAR_LITERAL("'([^'\\\\\n" +
            "]|\\\\['\"\\\\?abfnrtv0])'"),
    STRING_LITERAL("\"([^\"\\\\\n]|\\\\['\"\\\\?abfnrtvox]|\\\\[0-7]{1,3})*\""),
    DOUBLE_LITERAL("(([0-9]*\\" +
            ".[0-9]+|[0-9]+\\.?)[Ee][+-]?[0-9]+|[0-9]*\\.[0-9]+|[0-9]+\\.)" +
            "[^\\w.]", 1),
    FLOAT_LITERAL(
            "((([0-9]*\\.[0-9]+|[0-9]+\\.?)[Ee][+-]?[0-9]+|[0-9]*\\.[0-9]+|[0-9]+\\.)[fF])[^\\w.]", 1),
    UNSIGNED_LONG_LITERAL("([0-9]+([lL][uU]|[uU][lL]|[lL][lL][uU]|[uU][lL][lL]))" +
            "[^\\w.]", 1), UNSIGNED_INT_LITERAL("([0-9]+[uU])[^\\w.]", 1),
    UNSIGNED_HEX_INT_LITERAL("0x([a-fA-F0-9]+[uU])"),
    LONG_LITERAL("([0-9]+([lL]|ll|LL))[^\\w.]", 1),
    UNSIGNED_HEX_LONG_LITERAL("0x([0-9a-fA-F]+([lL][uU]|[uU][lL]|[lL][lL][uU]|[uU][lL][lL]))" +
            "[^\\w.]", 1),
    HEX_LONG_LITERAL("0x([0-9a-fA-F]+([lL]|ll|LL))" +
            "[^\\w.]", 1),
    HEX_INT_LITERAL("0x([a-fA-F0-9]+)[^\\w.]", 1),INT_LITERAL("([0-9]+)[^\\w.]", 1)
    , SEMICOLON(";"), SINGLE_LINE_COMMENT("//.*|#\\s*pragma\\b.*"),
    MULTILINE_COMMENT(Pattern.compile("/\\*.*\\*/", Pattern.DOTALL), 0),
    ASM(),
    GCC_ATTRIBUTE(),
    BUILTIN_C23_VA_START(), BUILTIN_VA_ARG(), BUILTIN_VA_END(),
    BUILTIN_OFFSETOF,
    UNSIGNED(), SIGNED(), ENUM(), GOTO(), BOOL(), TRUE(), FALSE(), CHAR(), SHORT(), FLOAT(), DOUBLE(), LONG(),
    NULLPTR(), INT(), RETURN(), SWITCH(),
    TYPEDEF(), TYPEOF(), CASE(),DEFAULT(),
    CONST(), VOLATILE(), RESTRICT(), ATOMIC(),
    VOID(), ELSE(), IF(), BREAK(), CONTINUE(), WHILE(), DO(), FOR(), EXTERN()
    , SIZEOF(), STATIC(), REGISTER(), STRUCT(), UNION(), DECREMENT("--"), INCREMENT("\\+\\+"),
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
            case ASM -> "__asm__";
            case BITWISE_NOT -> "bitwise_not";
            case BREAK -> "break";
            case BOOL -> "bool";
            case CASE -> "case";
            case TRUE -> "true";
            case FALSE -> "false";
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
            case FLOAT -> "float";
            case DOUBLE_LITERAL -> "double_literal";
            case ELLIPSIS -> "ellipsis";
            case ELSE -> "else";
            case ENUM-> "enum";
            case EXTERN -> "extern";
            case FOR -> "for";
            case GOTO -> "goto";
            case IDENTIFIER -> "identifier";
            case IF -> "if";
            case INCREMENT -> "increment";
            case INT -> "int";
            case INT_LITERAL -> "int_literal";
            case NULLPTR -> "nullptr";
            case HEX_INT_LITERAL -> "hex_int_literal";
            case UNSIGNED_HEX_INT_LITERAL -> "unsigned_hex_int_literal";
            case UNSIGNED_HEX_LONG_LITERAL -> "unsigned_hex_long_literal";
            case HEX_LONG_LITERAL -> "hex_long_literal";
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
            case REGISTER -> "register";
            case STATIC -> "static";
            case STRING_LITERAL -> "string_literal";
            case STRUCT -> "struct";
            case SWITCH -> "switch";
            case BUILTIN_C23_VA_START -> "__builtin_c23_va_start";
            case BUILTIN_VA_ARG -> "__builtin_va_arg";
            case BUILTIN_VA_END -> "__builtin_va_end";
            case BUILTIN_OFFSETOF -> "__builtin_offsetof";
            case TYPEDEF -> "typedef";
            case TYPEOF -> "typeof";
            case UNION -> "union";
            case UNSIGNED -> "unsigned";
            case UNSIGNED_INT_LITERAL -> "unsigned_int_literal";
            case FLOAT_LITERAL -> "float_literal";
            case UNSIGNED_LONG_LITERAL -> "unsigned_long_literal";
            case CONST -> "const";
            case VOLATILE -> "volatile";
            case RESTRICT -> "restrict";
            case ATOMIC -> "_Atomic";
            case VOID -> "void";
            case WHILE -> "while";
            case GCC_ATTRIBUTE -> "__attribute__";
        };
    }

    public static void main(String[] args) {
        String src = "0x1ULL, 0x0000000000000002ULL};";
        var tokenType=UNSIGNED_HEX_LONG_LITERAL;
        Matcher matcher = tokenType.regex.matcher(src);
        boolean b = matcher.lookingAt();
        System.out.println(b);

        int end = matcher.end(tokenType.group());
        int start = matcher.start();
        System.out.println(src.substring(start, end));

        System.out.println("DONE");
    }
}
