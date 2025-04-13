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
                    CHAR_LITERAL, STRING_LITERAL, DOUBLE_LITERAL,
                    UNSIGNED_LONG_LITERAL, UNSIGNED_INT_LITERAL,
                    CLOSE_BRACE, LONG_LITERAL, INT_LITERAL, SEMICOLON,
                    SINGLE_LINE_COMMENT, MULTILINE_COMMENT, DECREMENT,
                    INCREMENT, BITWISE_NOT, ARROW, SUB, ADD, IMUL, DIVIDE, BITWISE_XOR, REMAINDER,
                    AND, OR, EQUALS, NOT_EQUALS, LESS_THAN_OR_EQUAL,
                    GREATER_THAN_OR_EQUAL, LESS_THAN, GREATER_THAN, NOT,
                    BECOMES, QUESTION_MARK, COLON, COMMA, AMPERSAND, OPEN_BRACKET,
                    CLOSE_BRACKET, DOT};

    public static ArrayList<Token> lex(String src) {
        Matcher matcher = IDENTIFIER.regex.matcher(src);
        ArrayList<Token> tokens = new ArrayList<>();
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
                    if (tokenType != SINGLE_LINE_COMMENT && tokenType != MULTILINE_COMMENT) {

                        if (tokenType == CHAR_LITERAL    || tokenType == STRING_LITERAL
                               ){
                            int start = matcher.start()+1;
                            String value = src.substring(start, end-1);
                            Token token = new TokenWithValue(tokenType, value);
                            tokens.add(token);
                        }
                        else if (tokenType == IDENTIFIER
                                || tokenType == DOUBLE_LITERAL
                                || tokenType == INT_LITERAL
                                || tokenType == LONG_LITERAL
                                || tokenType == UNSIGNED_INT_LITERAL
                                || tokenType == UNSIGNED_LONG_LITERAL) {

                            int start = matcher.start();
                            String value = src.substring(start, end);
                            Token token = switch (value) {
                                case "break" -> BREAK;
                                case "char" -> CHAR;
                                case "continue" -> CONTINUE;
                                case "do" -> DO;
                                case "double" -> DOUBLE;
                                case "else" -> ELSE;
                                case "extern" -> EXTERN;
                                case "for" -> FOR;
                                case "if" -> IF;
                                case "int" -> INT;
                                case "long" -> LONG;
                                case "return" -> RETURN;
                                case "signed" -> SIGNED;
                                case "sizeof" -> SIZEOF;
                                case "static" -> STATIC;
                                case "struct" -> STRUCT;
                                case "unsigned" -> UNSIGNED;
                                case "void" -> VOID;
                                case "while" -> WHILE;
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
