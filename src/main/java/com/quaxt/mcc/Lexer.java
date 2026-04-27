package com.quaxt.mcc;

import com.quaxt.mcc.parser.TokenList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.CompoundAssignmentOperator.*;
import static com.quaxt.mcc.TokenType.*;

public class Lexer {
    static Pattern WHITESPACE = Pattern.compile("\\s+");
    static final Token[] TOKEN_TYPES_TO_MATCH = new Token[]{CHAR_LITERAL,IDENTIFIER,
            SUB_EQ, ADD_EQ, IMUL_EQ, DIVIDE_EQ, REMAINDER_EQ, AND_EQ,
            BITWISE_AND_EQ, OR_EQ, BITWISE_OR_EQ, BITWISE_XOR_EQ, SHL_EQ,
            SAR_EQ, OPEN_PAREN, CLOSE_PAREN, OPEN_BRACE,
            STRING_LITERAL, HEX_FLOAT_LITERAL,HEX_DOUBLE_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL, UNSIGNED_LONG_LITERAL, UNSIGNED_HEX_LONG_LITERAL,
            HEX_LONG_LITERAL, UNSIGNED_HEX_INT_LITERAL, HEX_INT_LITERAL, UNSIGNED_INT_LITERAL, CLOSE_BRACE, LONG_LITERAL,
            INT_LITERAL, SEMICOLON, SINGLE_LINE_COMMENT, MULTILINE_COMMENT,
            DECREMENT, INCREMENT, BITWISE_NOT, ARROW, SUB, ADD, IMUL, DIVIDE,
            BITWISE_XOR, REMAINDER, AND, OR, EQUALS, NOT_EQUALS, SHL,
            LESS_THAN_OR_EQUAL, SAR, GREATER_THAN_OR_EQUAL, LESS_THAN,
            GREATER_THAN, NOT, BECOMES, QUESTION_MARK, COLON, COMMA,
            BITWISE_AND, BITWISE_OR, OPEN_BRACKET, CLOSE_BRACKET, ELLIPSIS,
            DOT};

    public static TokenList lex(String src) {
        Matcher matcher = IDENTIFIER.regex.matcher(src);
        TokenList tokens = new TokenList();
        int lineNumber = 1;
        String filename="<builtin>";
        int startOfLine = -1;
        int len = src.length();
        outer:
        for (int i = 0; i < len; ) {

            // skip whitespace, count newlines, test if we are at start of line
            {


                for (; ; i++) {
                    if (i >= len) break outer;
                    char c = src.charAt(i);

                    if (Character.isWhitespace(c)) {
                        if (c == '\n') {
                            lineNumber++;
                            startOfLine = i;
                        }
                    } else break;
                }

            }
            boolean atStartOfLine =
                    containsOnlyHorizontalWhitespace(src, startOfLine + 1, i);
            while (atStartOfLine && src.charAt(i) == '#' && i < len - 1 &&
                    (src.charAt(i + 1) == ' ' || src.startsWith("#line ", i))) {
                int startOfLineNumber = src.startsWith("#line ", i) ? i + 6 : i + 2;
                int endOfLineNumber = startOfLineNumber;
                char c;
                while (Character.isDigit(c = src.charAt(endOfLineNumber))) {
                    endOfLineNumber++;
                }
                if (c == ' ' && endOfLineNumber > startOfLineNumber) {
                    lineNumber =
                            Integer.parseInt(src.substring(startOfLineNumber,
                                    endOfLineNumber));
                    if (src.charAt(endOfLineNumber + 1) == '"') {
                        int startOfFileName = endOfLineNumber + 2;
                        int endOfFileName = startOfFileName;
                        while (src.charAt(endOfFileName) != '"') {
                            endOfFileName++;
                        }
                        filename = src.substring(startOfFileName,
                                endOfFileName);
                        // now skip to end of line;
                        for (i = endOfFileName + 1; i < len; i++) {
                            if (src.charAt(i) == '\n') {
                                startOfLine = i;
                                i++;
                                continue outer;
                            }
                        }
                    }

                }
            }
            int skippedMsvcKeyword = skipMsvcKeywordCall(src, i);
            if (skippedMsvcKeyword != -1) {
                for (int j = i; j < skippedMsvcKeyword; j++) {
                    if (src.charAt(j) == '\n') {
                        lineNumber++;
                        startOfLine = j;
                    }
                }
                i = skippedMsvcKeyword;
                continue;
            }
            matcher.region(i, src.length());
            for (Token tokenType : TOKEN_TYPES_TO_MATCH) {
                matcher.usePattern(tokenType.regex());
                if (matcher.lookingAt()) {
                    int end = matcher.end(tokenType.group());
                    if (tokenType != SINGLE_LINE_COMMENT && tokenType != MULTILINE_COMMENT) {
                        if (tokenType == STRING_LITERAL) {
                            int start = matcher.start() + 1;
                            String value = src.substring(start, end - 1);
                            Token token = new TokenWithValue(tokenType, value);
                            tokens.add(token, filename,lineNumber);
                        } else if (tokenType == IDENTIFIER ||
                                tokenType == CHAR_LITERAL ||
                                tokenType == HEX_FLOAT_LITERAL ||
                                tokenType == HEX_DOUBLE_LITERAL ||
                                tokenType == FLOAT_LITERAL ||
                                tokenType == DOUBLE_LITERAL ||
                                tokenType == UNSIGNED_HEX_LONG_LITERAL ||
                                tokenType == HEX_LONG_LITERAL ||
                                tokenType == UNSIGNED_HEX_INT_LITERAL ||
                                tokenType == HEX_INT_LITERAL ||
                                        tokenType == INT_LITERAL ||
                                        tokenType == LONG_LITERAL ||
                                        tokenType == UNSIGNED_INT_LITERAL ||
                                        tokenType == UNSIGNED_LONG_LITERAL) {
                            int start = matcher.start();
                            String value = src.substring(start, end);
                            if (value.equals("__int64")) {
                                tokens.add(LONG, filename, lineNumber);
                                tokens.add(LONG, filename, lineNumber);
                                i = end;
                                continue outer;
                            }
                            Token token = switch (value) {
                                case "_Atomic" -> ATOMIC;
                                case "alignof", "_Alignof", "__alignof__" ->
                                        ALIGNOF;
                                case "alignas", "_Alignas" -> ALIGNAS;
                                case "_Bool" -> BOOL;
                                case "_Float32" -> FLOAT;
                                case "_Float32x" -> FLOAT;
                                case "_Float64" -> DOUBLE;
                                case "_Float64x" -> DOUBLE;
                                case "_Generic" -> GENERIC;
                                case "__asm__" -> ASM;
                                case "__attribute__" -> GCC_ATTRIBUTE;
                                case "__builtin_c23_va_start" -> BUILTIN_C23_VA_START;
                                case "__builtin_offsetof" -> BUILTIN_OFFSETOF;
                                case "__builtin_va_arg" -> BUILTIN_VA_ARG;
                                case "__builtin_va_end" -> BUILTIN_VA_END;
                                case "__extension__" -> null;
                                case "__cdecl", "_cdecl", "__stdcall", "_stdcall",
                                     "__fastcall", "_fastcall", "__thiscall",
                                     "__vectorcall", "__clrcall", "__ptr32",
                                     "__ptr64", "__sptr", "__uptr",
                                     "__unaligned" -> null;
                                case "__restrict" -> RESTRICT;
                                case "__restrict__" -> RESTRICT;
                                case "__signed__" -> SIGNED;
                                case "bool" -> BOOL;
                                case "break" -> BREAK;
                                case "case" -> CASE;
                                case "char" -> CHAR;
                                case "const" -> CONST;
                                case "continue" -> CONTINUE;
                                case "default" -> DEFAULT;
                                case "do" -> DO;
                                case "double" -> DOUBLE;
                                case "else" -> ELSE;
                                case "enum" -> ENUM;
                                case "extern" -> EXTERN;
                                case "false" -> FALSE;
                                case "float" -> FLOAT;
                                case "for" -> FOR;
                                case "goto" -> GOTO;
                                case "if" -> IF;
                                case "int" -> INT;
                                case "inline" -> INLINE;
                                case "__inline" -> INLINE;
                                case "__inline__", "__forceinline" -> INLINE;
                                case "long" -> LONG;
                                case "nullptr" -> NULLPTR;
                                case "register" -> REGISTER;
                                case "restrict" -> RESTRICT;
                                case "return" -> RETURN;
                                case "short" -> SHORT;
                                case "signed" -> SIGNED;
                                case "sizeof" -> SIZEOF;
                                case "static" -> STATIC;
                                case "struct" -> STRUCT;
                                case "switch" -> SWITCH;
                                case "true" -> TRUE;
                                case "typedef" -> TYPEDEF;
                                case "typeof", "__typeof__" -> TYPEOF;
                                case "union" -> UNION;
                                case "unsigned" -> UNSIGNED;
                                case "void" -> VOID;
                                case "volatile" -> VOLATILE;
                                case "while" -> WHILE;
                                default -> new TokenWithValue(tokenType, value.intern());
                            };
                            if (token != null)
                                tokens.add(token, filename, lineNumber);
                        } else {
                            tokens.add(tokenType, filename, lineNumber);
                        }
                    }

                    i = end;
                    continue outer;
                }
            }
            throw new IllegalArgumentException("can't handle token in " + filename+":"+lineNumber +
                    " at " + src.substring(i));
        }
        return tokens;
    }

    private static int skipMsvcKeywordCall(String src, int start) {
        if (startsWithWord(src, start, "__declspec")) {
            return skipParenthesizedCall(src, start + "__declspec".length());
        }
        if (startsWithWord(src, start, "__pragma")) {
            return skipParenthesizedCall(src, start + "__pragma".length());
        }
        return -1;
    }

    private static boolean containsOnlyHorizontalWhitespace(String src,
                                                           int start,
                                                           int end) {
        for (int i = start; i < end; i++) {
            char c = src.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\f') {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithWord(String src, int start, String word) {
        if (!src.startsWith(word, start)) return false;
        int end = start + word.length();
        return end >= src.length() || !isIdentifierPart(src.charAt(end));
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int skipParenthesizedCall(String src, int start) {
        int i = start;
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
            i++;
        }
        if (i >= src.length() || src.charAt(i) != '(') return -1;

        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (; i < src.length(); i++) {
            char c = src.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (inString) {
                if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else if (inChar) {
                if (c == '\\') escaped = true;
                else if (c == '\'') inChar = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return -1;
    }

}
