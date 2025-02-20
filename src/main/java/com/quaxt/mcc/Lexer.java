package com.quaxt.mcc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.TokenType.*;

public class Lexer {
    static Pattern WHITESPACE = Pattern.compile("\\s+");
    static final Token[] TOKEN_TYPES_TO_MATCH =
            new Token[]{IDENTIFIER, OPEN_PAREN, CLOSE_PAREN, OPEN_BRACE,
                    DOUBLE_LITERAL,
                    UNSIGNED_LONG_LITERAL, UNSIGNED_INT_LITERAL,
                    CLOSE_BRACE, LONG_LITERAL, INT_LITERAL, SEMICOLON,
                    SINGLE_LINE_COMMENT, MULTILINE_COMMENT, DECREMENT,
                    INCREMENT, BITWISE_NOT, SUB, ADD, IMUL, DIVIDE, BITWISE_XOR, REMAINDER,
                    AND, OR, EQUALS, NOT_EQUALS, LESS_THAN_OR_EQUAL,
                    GREATER_THAN_OR_EQUAL, LESS_THAN, GREATER_THAN, NOT,
                    BECOMES, QUESTION_MARK, COLON, COMMA};

    public static List<Token> lex(String src) {
        Matcher matcher = IDENTIFIER.regex.matcher(src);
        List<Token> tokens = new ArrayList<>();
        outer:
        for (int i = 0; i < src.length(); ) {
            matcher.usePattern(WHITESPACE);
            matcher.region(i, src.length());
            if (matcher.lookingAt()) {
                int end = matcher.end();
                matcher.region(end, src.length());
                i = end;
                if (i == src.length()) {
                    break;
                }
            }
            for (Token tokenType : TOKEN_TYPES_TO_MATCH) {
                matcher.usePattern(tokenType.regex());
                if (matcher.lookingAt()) {
                    int end = matcher.end(tokenType.group());
                    if (tokenType != TokenType.SINGLE_LINE_COMMENT && tokenType != TokenType.MULTILINE_COMMENT) {
                        if (tokenType == IDENTIFIER || tokenType == TokenType.DOUBLE_LITERAL ||
                                tokenType == TokenType.INT_LITERAL || tokenType == LONG_LITERAL
                                || tokenType == UNSIGNED_INT_LITERAL || tokenType == UNSIGNED_LONG_LITERAL) {
                            int start = matcher.start();
                            String value = src.substring(start, end);
                            Token token = switch (value) {
                                case "break" -> TokenType.BREAK;
                                case "continue" -> TokenType.CONTINUE;
                                case "do" -> TokenType.DO;
                                case "double" -> TokenType.DOUBLE;
                                case "else" -> TokenType.ELSE;
                                case "extern" -> TokenType.EXTERN;
                                case "for" -> TokenType.FOR;
                                case "if" -> TokenType.IF;
                                case "int" -> TokenType.INT;
                                case "long" -> TokenType.LONG;
                                case "return" -> TokenType.RETURN;
                                case "signed" -> TokenType.SIGNED;
                                case "static" -> TokenType.STATIC;
                                case "unsigned" -> TokenType.UNSIGNED;
                                case "void" -> TokenType.VOID;
                                case "while" -> TokenType.WHILE;
                                default -> new TokenWithValue(tokenType, value);
                            };
                            tokens.add(token);
                        } else {
                            tokens.add(tokenType);
                        }
                    }

                    i = end;
                    continue outer;
                }
            }
            throw new IllegalArgumentException("can't handle token at " + src.substring(i));
        }
        return tokens;
    }
}
