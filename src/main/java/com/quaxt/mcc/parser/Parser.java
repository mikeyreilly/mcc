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
        if (RETURN == token.type()) {
            tokens.removeFirst();
            Exp exp = parseExp(tokens, 0);
            expect(SEMICOLON, tokens);
            return new Return(exp);
        } else if (token == SEMICOLON) {
            tokens.removeFirst();
            return NULL_STATEMENT;
        } else if (token == IF) {
            tokens.removeFirst();
            expect(OPEN_PAREN, tokens);
            Exp condition = parseExp(tokens, 0);
            expect(CLOSE_PAREN, tokens);
            Statement ifTrue = parseStatement(tokens);
            Optional<Statement> ifFalse = switch (tokens.getFirst()) {
                case ELSE -> {
                    tokens.removeFirst();
                    yield Optional.of(parseStatement(tokens));
                }
                default -> Optional.empty();
            };
            return new If(condition, ifTrue, ifFalse);

        } else if (token == OPEN_BRACE){
            return parseBlock(tokens);
        }
        Exp exp = parseExp(tokens, 0);
        expect(SEMICOLON, tokens);
        return exp;
    }

    private static Declaration parseDeclaration(List<Token> tokens) {
        // parse int i; or int i=5;
        expect(INT, tokens);
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
        if (IDENTIFIER == type.type() || INT == type || VOID == type) {
            return type;
        }
        throw new IllegalArgumentException("Expected type, got " + type);
    }

    private static String parseIdentifier(List<Token> tokens) {
        Token identifier = tokens.removeFirst();
        if (identifier instanceof TokenWithValue(
                TokenType type, String value
        ) && type == IDENTIFIER) {
            return value;
        }
        throw new IllegalArgumentException("Expected identifier, got " + identifier);
    }

    private static Function parseFunction(List<Token> tokens) {
        Token returnType = parseType(tokens);
        String name = parseIdentifier(tokens);
        expect(OPEN_PAREN, tokens);
        expect(VOID, tokens);
        expect(CLOSE_PAREN, tokens);
        Block block = parseBlock(tokens);

        return new Function(name, returnType, block);
    }

    private static Block parseBlock(List<Token> tokens) {
        expect(OPEN_BRACE, tokens);

        List<BlockItem> blockItems = new ArrayList<>();
        while (tokens.getFirst() != CLOSE_BRACE) {
            blockItems.add(parseBlockItem(tokens));
        }
        tokens.removeFirst();
        Block block =new Block(blockItems);
        return block;
    }

    private static BlockItem parseBlockItem(List<Token> tokens) {
        if (tokens.getFirst() == INT) {
            return parseDeclaration(tokens);
        }
        return parseStatement(tokens);
    }


    private static Exp parseFactor(List<Token> tokens) {
        Token token = tokens.removeFirst();
        return switch (token) {
            case SUB ->
                    new UnaryOp(UnaryOperator.COMPLEMENT, parseFactor(tokens));
            case COMPLIMENT ->
                    new UnaryOp(UnaryOperator.NEGATE, parseFactor(tokens));
            case NOT -> new UnaryOp(UnaryOperator.NOT, parseFactor(tokens));
            case OPEN_PAREN -> {
                Exp r = parseExp(tokens, 0);
                expect(CLOSE_PAREN, tokens);
                yield r;
            }
            case TokenWithValue(
                    TokenType type, String value
            ) ->
                    type == NUMERIC ? new Int(Integer.parseInt(value)) : new Var(value);

            default ->
                    throw new IllegalArgumentException("Expected exp, got " + token);

        };
    }


    private static Exp parseExp(List<Token> tokens, int minPrecedence) {
        Exp left = parseFactor(tokens);

        while (!tokens.isEmpty()) {
            Token token = tokens.getFirst();
            if (tokens.getFirst() instanceof BinaryOperator || token == QUESTION_MARK) {
                int precedence = getPrecedence(token);
                if (precedence < minPrecedence) break;
                tokens.removeFirst();
                if (token == BECOMES) {
                    Exp right = parseExp(tokens, precedence);
                    left = new Assignment(left, right);
                } else if (token instanceof BinaryOperator binop) {
                    Exp right = parseExp(tokens, precedence + 1);
                    left = new BinaryOp(binop, left, right);
                } else { // QUESTION_MARK
                    Exp middle = parseExp(tokens, 0);
                    expect(COLON, tokens);
                    Exp right = parseExp(tokens, precedence);
                    left = new Conditional(left, middle, right);
                }
            } else {
                break;
            }
        }
        return left;
    }

    private static int getPrecedence(Token t) {
        return switch (t) {
            case IMUL, DIVIDE, REMAINDER -> 50;
            case SUB, ADD -> 45;
            case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN,
                 GREATER_THAN_OR_EQUAL -> 35;
            case EQUALS, NOT_EQUALS -> 30;
            case AND -> 10;
            case OR -> 5;
            case QUESTION_MARK -> 3;
            case BECOMES -> 1;
            default ->
                    throw new IllegalStateException("No precedence for: " + t);
        };
    }
}
