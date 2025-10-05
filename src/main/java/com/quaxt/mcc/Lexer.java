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
    static final Token[] TOKEN_TYPES_TO_MATCH = new Token[]{IDENTIFIER,
            SUB_EQ, ADD_EQ, IMUL_EQ, DIVIDE_EQ, REMAINDER_EQ, AND_EQ,
            BITWISE_AND_EQ, OR_EQ, BITWISE_OR_EQ, BITWISE_XOR_EQ, SHL_EQ,
            SAR_EQ, OPEN_PAREN, CLOSE_PAREN, OPEN_BRACE, CHAR_LITERAL,
            STRING_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL, UNSIGNED_LONG_LITERAL, UNSIGNED_HEX_LONG_LITERAL,
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
        int startOfLine = 0;
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
            boolean atStartOfLine = i - startOfLine <= 1;
            while (atStartOfLine && src.charAt(i) == '#' && i < len - 1 && src.charAt(i + 1) == ' ') {
                int startOfLineNumber = i + 2;
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
            matcher.region(i, src.length());
            for (Token tokenType : TOKEN_TYPES_TO_MATCH) {
                matcher.usePattern(tokenType.regex());
                if (matcher.lookingAt()) {
                    int end = matcher.end(tokenType.group());
                    if (tokenType != SINGLE_LINE_COMMENT && tokenType != MULTILINE_COMMENT) {

                        if (tokenType == CHAR_LITERAL || tokenType == STRING_LITERAL) {
                            int start = matcher.start() + 1;
                            String value = src.substring(start, end - 1);
                            Token token = new TokenWithValue(tokenType, value);
                            tokens.add(token, filename,lineNumber);
                        } else if (tokenType == IDENTIFIER || tokenType == FLOAT_LITERAL
                                || tokenType == DOUBLE_LITERAL
                                || tokenType == UNSIGNED_HEX_LONG_LITERAL
                                || tokenType == HEX_LONG_LITERAL
                                || tokenType == UNSIGNED_HEX_INT_LITERAL
                                || tokenType == HEX_INT_LITERAL || tokenType == INT_LITERAL || tokenType == LONG_LITERAL || tokenType == UNSIGNED_INT_LITERAL || tokenType == UNSIGNED_LONG_LITERAL) {

                            int start = matcher.start();
                            String value = src.substring(start, end);
                            Token token = switch (value) {
                                case "__builtin_c23_va_start" ->
                                        BUILTIN_C23_VA_START;
                                case "__builtin_va_arg" -> BUILTIN_VA_ARG;
                                case "__builtin_va_end" -> BUILTIN_VA_END;
                                case "__asm__" -> ASM;
                                case "__attribute__" -> GCC_ATTRIBUTE;
                                case "break" -> BREAK;
                                case "char" -> CHAR;
                                case "continue" -> CONTINUE;
                                case "do" -> DO;
                                case "double" -> DOUBLE;
                                case "_Float64" -> DOUBLE;
                                case "_Float64x" -> DOUBLE;
                                case "else" -> ELSE;
                                case "extern" -> EXTERN;
                                case "for" -> FOR;
                                case "float" -> FLOAT;
                                case "_Float32" -> FLOAT;
                                case "_Float32x" -> FLOAT;
                                case "enum" -> ENUM;
                                case "goto" -> GOTO;
                                case "if" -> IF;
                                case "int" -> INT;
                                case "long" -> LONG;
                                case "return" -> RETURN;
                                case "short" -> SHORT;
                                case "switch" -> SWITCH;
                                case "typedef" -> TYPEDEF;
                                case "typeof", "__typeof__" -> TYPEOF;
                                case "case" -> CASE;
                                case "default" -> DEFAULT;
                                case "signed" -> SIGNED;
                                case "sizeof" -> SIZEOF;
                                case "static" -> STATIC;
                                case "struct" -> STRUCT;
                                case "union" -> UNION;
                                case "unsigned" -> UNSIGNED;
                                case "void" -> VOID;
                                case "while" -> WHILE;
                                case "const" -> CONST;
                                case "volatile" -> VOLATILE;
                                case "restrict" -> RESTRICT;
                                case "__restrict__" -> RESTRICT;
                                case "__restrict" -> RESTRICT;
                                case "__extension__" -> RESTRICT;
                                case "_Atomic" -> ATOMIC;
                                default -> new TokenWithValue(tokenType, value);
                            };
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

}
