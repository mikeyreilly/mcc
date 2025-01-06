package com.quaxt.mcc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quaxt.mcc.BinaryOperator.*;
import static com.quaxt.mcc.TokenType.*;

public class Lexer {
    static Pattern WHITESPACE = Pattern.compile("\\s+");
    static final Token[] TOKEN_TYPES_TO_MATCH =
            new Token[]{IDENTIFIER, OPEN_PAREN, CLOSE_PAREN, OPEN_BRACE,
                    CLOSE_BRACE, NUMERIC, SEMICOLON, SINGLE_LINE_COMMENT,
                    MULTILINE_COMMENT, DECREMENT, INCREMENT, COMPLIMENT, SUB,
                    ADD, IMUL, DIVIDE, REMAINDER,
                    AND, OR, EQUALS, NOT_EQUALS, LESS_THAN_OR_EQUAL,
                    GREATER_THAN_OR_EQUAL, LESS_THAN, GREATER_THAN, NOT};


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
                    int end = matcher.end();
                    if (tokenType != TokenType.SINGLE_LINE_COMMENT && tokenType != TokenType.MULTILINE_COMMENT) {
                        if (tokenType == IDENTIFIER || tokenType == TokenType.NUMERIC) {
                            int start = matcher.start();
                            String value = src.substring(start, end);
                            Token token = switch (value) {
                                case "int" -> TokenType.INT;
                                case "void" -> TokenType.VOID;
                                case "return" -> TokenType.RETURN;
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
