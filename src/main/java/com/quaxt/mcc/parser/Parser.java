package com.quaxt.mcc.parser;


import com.quaxt.mcc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.TokenType.*;
import static com.quaxt.mcc.parser.NullStatement.NULL_STATEMENT;

public class Parser {
    private static void expect(Token expected, List<Token> tokens) {
        Token token = tokens.removeFirst();
        if (expected != token.type()) {
            throw new IllegalArgumentException("Expected " + expected + ", got " + token);
        }
    }

    static Statement parseStatement(List<Token> tokens) {
        Token token = tokens.getFirst();
        if (TokenType.RETURN == token.type()) {
            tokens.removeFirst();
            Exp exp = parseExp(tokens, 0);
            expect(SEMICOLON, tokens);
            return new Return(exp);
        } else if (token == SEMICOLON) {
            tokens.removeFirst();
            return NULL_STATEMENT;
        }
        Exp exp = parseExp(tokens, 0);
        expect(SEMICOLON, tokens);
        return exp;
    }

    private static Declaration parseDeclaration(List<Token> tokens) {
        // parse int i; or int i=5;
        expect(TokenType.INT, tokens);
        String name = parseIdentifier(tokens);
        Token token = tokens.removeFirst();
        return new Declaration(name, switch (token.type()) {
            case BECOMES -> {
                Optional<Exp> init = Optional.of(parseExp(tokens, 0));
                expect(SEMICOLON, tokens);
                yield init;
            }
            case SEMICOLON -> Optional.empty();
            default ->
                    throw new IllegalArgumentException("Expected ; or =, got " + token);
        });
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
                TokenType type, String value
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
        List<BlockItem> blockItems = new ArrayList<>();
        while (tokens.getFirst() != CLOSE_BRACE) {
            blockItems.add(parseBlockItem(tokens));
        }
        tokens.removeFirst();
        //    Statement blockItems = parseStatement(tokens);
        // expect(CLOSE_BRACE, tokens);

        return new Function(name, returnType, blockItems);
    }

    private static BlockItem parseBlockItem(List<Token> tokens) {
        if (tokens.getFirst() == TokenType.INT) {
            return parseDeclaration(tokens);
        }
        return parseStatement(tokens);
    }


    private static Exp parseFactor(List<Token> tokens) {
        Token token = tokens.removeFirst();
        return switch (token) {
            case SUB ->
                    new UnaryOp(UnaryOperator.COMPLEMENT, parseFactor(tokens));
            case TokenType.COMPLIMENT ->
                    new UnaryOp(UnaryOperator.NEGATE, parseFactor(tokens));
            case TokenType.NOT ->
                    new UnaryOp(UnaryOperator.NOT, parseFactor(tokens));
            case TokenType.OPEN_PAREN -> {
                Exp r = parseExp(tokens, 0);
                expect(TokenType.CLOSE_PAREN, tokens);
                yield r;
            }
            case TokenWithValue(
                    TokenType type, String value
            ) ->
                    type == NUMERIC ? new Int(Integer.parseInt(value)) : new Identifier(value);

            default ->
                    throw new IllegalArgumentException("Expected exp, got " + token);

        };
    }


    private static Exp parseExp(List<Token> tokens, int minPrecedence) {
        Exp left = parseFactor(tokens);

        while (!tokens.isEmpty()) {
            if (tokens.getFirst() instanceof BinaryOperator binop) {
                int precedence = switch (binop) {
                    case IMUL, DIVIDE, REMAINDER -> 50;
                    case SUB, ADD -> 45;
                    case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN,
                         GREATER_THAN_OR_EQUAL -> 35;
                    case EQUALS, NOT_EQUALS -> 30;
                    case AND -> 10;
                    case OR -> 5;
                    case BECOMES -> 1;
                };
                if (precedence < minPrecedence) break;
                tokens.removeFirst();
                if (binop == BECOMES) {
                    Exp right = parseExp(tokens, precedence);
                    left = new Assignment(left, right);

                } else {
                    Exp right = parseExp(tokens, precedence + 1);
                    left = new BinaryOp(binop, left, right);
                }
            } else {
                break;
            }
        }
        return left;
    }
}
