package com.quaxt.mcc.parser;


import com.quaxt.mcc.*;

import java.util.List;

public class Parser {

    private static void expect(Token expected, List<Token> tokens) {
        Token token = tokens.removeFirst();
        if (expected != token.type()) {
            throw new IllegalArgumentException("Expected " + expected + ", got " + token);
        }
    }

    static Statement parseStatement(List<Token> tokens) {
        expect(TokenType.RETURN, tokens);
        Exp exp = parseExp(tokens, 0);
        expect(TokenType.SEMICOLON, tokens);
        return new Return(exp);
    }

    public static Program parseProgram(List<Token> tokens) {
        Function function = parseFunction(tokens);
        return new Program(function);
    }

    private static Token parseType(List<Token> tokens) {
        Token type = tokens.removeFirst();
        if (TokenType.IDENTIFIER == type.type() || TokenType.INT == type || TokenType.VOID == type) {
            return type;
        }
        throw new IllegalArgumentException("Expected type, got " + type);
    }

    private static String parseIdentifier(List<Token> tokens) {
        Token identifier = tokens.removeFirst();
        if (identifier instanceof TokenWithValue(
                TokenType type,
                String value
        ) && type == TokenType.IDENTIFIER) {
            return value;
        }
        throw new IllegalArgumentException("Expected identifier, got " + identifier);
    }

    private static Function parseFunction(List<Token> tokens) {
        Token returnType = parseType(tokens);
        String name = parseIdentifier(tokens);
        expect(TokenType.OPEN_PAREN, tokens);
        expect(TokenType.VOID, tokens);
        expect(TokenType.CLOSE_PAREN, tokens);
        expect(TokenType.OPEN_BRACE, tokens);
        Statement statement = parseStatement(tokens);
        expect(TokenType.CLOSE_BRACE, tokens);

        return new Function(name, returnType, statement);
    }


    private static Exp parseFactor(List<Token> tokens) {
        Token token = tokens.removeFirst();
        return switch (token) {
            case TokenType.MINUS ->
                    new UnaryOp(UnaryOperator.NEG, parseFactor(tokens));
            case TokenType.COMPLIMENT ->
                    new UnaryOp(UnaryOperator.NOT, parseFactor(tokens));
            case TokenType.OPEN_PAREN -> {
                Exp r = parseExp(tokens, 0);
                expect(TokenType.CLOSE_PAREN, tokens);
                yield r;
            }
            case TokenWithValue(
                    TokenType type, String value
            ) when (type == TokenType.NUMERIC) ->
                    new Int(Integer.parseInt(value));
            default -> throw new IllegalArgumentException("Expected exp, got "
                    + token);
        };
    }


    private static Exp parseExp(List<Token> tokens, int minPrec) {
        Exp left = parseFactor(tokens);
        loop:
        while (!tokens.isEmpty()) {
            Token token = tokens.getFirst();
            BinaryOperator operator;
            switch (token) {
                case TokenType.MINUS:
                    operator = BinaryOperator.SUB;
                    break;
                case TokenType.PLUS:
                    operator = BinaryOperator.ADD;
                    break;
                case TokenType.MULTIPLY:
                    operator = BinaryOperator.IMUL;
                    break;
                case TokenType.DIVIDE:
                    operator = BinaryOperator.DIVIDE;
                    break;
                case TokenType.REMAINDER:
                    operator = BinaryOperator.REMAINDER;
                    break;
                default:
                    break loop;
            }
            int prec = switch (operator) {
                case SUB, ADD -> 45;
                case IMUL, DIVIDE, REMAINDER -> 50;
            };
            if (prec < minPrec)
                break;
            tokens.removeFirst();
            Exp right = parseExp(tokens, prec + 1);
            left = new BinaryOp(operator, left, right);
        }
        return left;
    }
}
