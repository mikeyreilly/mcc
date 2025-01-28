package com.quaxt.mcc.parser;


import com.quaxt.mcc.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.TokenType.*;
import static com.quaxt.mcc.parser.NullStatement.NULL_STATEMENT;

public class Parser {
    private static Token expect(Token expected, List<Token> tokens) {
        Token token = tokens.removeFirst();
        if (expected != token.type()) {
            throw new IllegalArgumentException("Expected " + expected + ", got " + token);
        }
        return token;
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
            Statement ifFalse = switch (tokens.getFirst()) {
                case ELSE -> {
                    tokens.removeFirst();
                    yield parseStatement(tokens);
                }
                default -> null;
            };
            return new If(condition, ifTrue, ifFalse);

        } else if (token == OPEN_BRACE) {
            return parseBlock(tokens);
        } else if (token == WHILE) {
            return parseWhile(tokens);
        } else if (token == DO) {
            return parseDoWhile(tokens);
        } else if (token == FOR) {
            return parseFor(tokens);
        } else if (token == BREAK) {
            tokens.removeFirst();
            expect(SEMICOLON, tokens);
            return new Break();
        } else if (token == CONTINUE) {
            tokens.removeFirst();
            expect(SEMICOLON, tokens);
            return new Continue();
        }
        Exp exp = parseExp(tokens, 0);
        expect(SEMICOLON, tokens);
        return exp;
    }

    private static DoWhile parseDoWhile(List<Token> tokens) {
        expect(DO, tokens);
        Statement body = parseStatement(tokens);
        expect(WHILE, tokens);
        expect(OPEN_PAREN, tokens);
        Exp condition = parseExp(tokens, 0);
        expect(CLOSE_PAREN, tokens);
        expect(SEMICOLON, tokens);
        return new DoWhile(body, condition, null);
    }

    private static Declaration parseDeclaration(List<Token> tokens) {
        // parse int i; or int i=5; or int foo(void);
        if (tokens.isEmpty()) return null;
        List<Token> types = new ArrayList<>();
        List<StorageClass> storageClasses = new ArrayList<>();
        Token t;

        while (true) {
            t = tokens.removeFirst();
            if (isType(t)) {
                types.add(t);
            } else if (STATIC == t) {
                storageClasses.add(StorageClass.STATIC);
            } else if (EXTERN == t) {
                storageClasses.add(StorageClass.EXTERN);
            } else {
                break;
            }
        }
        if (types.size() != 1) {
            fail("invalid type specifier");
        }
        if (storageClasses.size() > 1) {
            fail("invalid storage class");
        }
        StorageClass storageClass = storageClasses.isEmpty() ? null : storageClasses.getFirst();
        String name = parseIdentifier(t);
        Token token = tokens.removeFirst();
        Exp exp;
        switch (token.type()) {
            case BECOMES:
                Exp init = parseExp(tokens, 0);
                expect(SEMICOLON, tokens);
                exp = init;
                break;
            case SEMICOLON:
                exp = null;
                break;
            case OPEN_PAREN:
                return parseRestOfFunction(tokens, name, storageClass);
            default:
                throw new IllegalArgumentException("Expected ; or =, got " + token);
        }

        return new VarDecl(name, exp, storageClass);
    }


    public static Program parseProgram(List<Token> tokens) {
        Declaration declaration;
        ArrayList<Declaration> declarations = new ArrayList<>();
        while ((declaration = parseDeclaration(tokens)) != null) {
            declarations.add(declaration);
        }
        return new Program(declarations);
    }

    private static String parseIdentifier(List<Token> tokens) {
        Token identifier = tokens.removeFirst();
        return parseIdentifier(identifier);
    }

    private static String parseIdentifier(Token identifier) {
        if (identifier instanceof TokenWithValue(
                TokenType type, String value
        ) && type == IDENTIFIER) {
            return value;
        }
        throw new IllegalArgumentException("Expected identifier, got " + identifier);
    }

    private static boolean isType(Token type) {
        return INT == type;
    }

    private static Function parseRestOfFunction(List<Token> tokens, String functionName, StorageClass storageClass) {
        Token firstParam = tokens.getFirst();
        List<Identifier> params;
        if (VOID == firstParam.type()) {
            tokens.removeFirst();
            expect(CLOSE_PAREN, tokens);
            params = Collections.emptyList();
        } else {
            params = new ArrayList<>();
            while (true) {
                expect(INT, tokens);
                params.add(new Identifier(expectIdentifier(tokens)));
                Token token = tokens.removeFirst();
                if (token == CLOSE_PAREN) break;
                else if (token != COMMA)
                    throw new IllegalArgumentException("Expected COMMA, got " + token);
            }

        }

        Block block;
        if (tokens.getFirst() == OPEN_BRACE) {
            block = parseBlock(tokens);
        } else {
            expect(SEMICOLON, tokens);
            block = null;
        }
        return new Function(functionName, params, block, storageClass);
    }

    private static String expectIdentifier(List<Token> tokens) {
        Token token = tokens.removeFirst();

        if (token instanceof TokenWithValue(
                Token type, String value
        ) && type == IDENTIFIER) {
            return value;
        }
        throw new IllegalArgumentException("Expected IDENTIFIER got " + token);
    }

    private static Block parseBlock(List<Token> tokens) {
        expect(OPEN_BRACE, tokens);

        ArrayList<BlockItem> blockItems = new ArrayList<>();
        while (tokens.getFirst() != CLOSE_BRACE) {
            blockItems.add(parseBlockItem(tokens));
        }
        tokens.removeFirst();
        return new Block(blockItems);
    }

    private static While parseWhile(List<Token> tokens) {
        expect(WHILE, tokens);
        expect(OPEN_PAREN, tokens);
        Exp condition = parseExp(tokens, 0);
        expect(CLOSE_PAREN, tokens);
        Statement body = parseStatement(tokens);
        return new While(condition, body, null);
    }


    private static BlockItem parseBlockItem(List<Token> tokens) {
        return switch (tokens.getFirst()) {
            case INT, STATIC, EXTERN -> parseDeclaration(tokens);
            default -> parseStatement(tokens);
        };
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
            ) -> {
                if (type == NUMERIC)
                    yield new Int(Integer.parseInt(value));
                Identifier id = new Identifier(value);
                if (!tokens.isEmpty() && tokens.getFirst() == OPEN_PAREN) {
                    tokens.removeFirst();
                    Token current = tokens.getFirst();
                    if (current == CLOSE_PAREN) {
                        tokens.removeFirst();
                        yield new FunctionCall(id, Collections.emptyList());
                    }
                    List<Exp> args = new ArrayList<>();

                    while (true) {
                        Exp e = parseExp(tokens, 0);
                        args.add(e);
                        current = tokens.removeFirst();
                        if (current == COMMA) {
                            continue;
                        }
                        if (current == CLOSE_PAREN) {
                            break;
                        } else
                            throw new IllegalArgumentException("unexpected token while parsing function call: " + current);

                    }
                    yield new FunctionCall(id, args);

                }
                yield id;
            }

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

    private static ForInit parseForInit(List<Token> tokens) {
        Token t = tokens.getFirst();
        if (t == INT) return (ForInit) parseDeclaration(tokens);
        Exp r = t == SEMICOLON ? null : parseExp(tokens, 0);
        expect(SEMICOLON, tokens);
        return r;
    }

    private static For parseFor(List<Token> tokens) {
        expect(FOR, tokens);
        expect(OPEN_PAREN, tokens);
        ForInit init = parseForInit(tokens);
        Token t = tokens.getFirst();
        Exp condition = t == SEMICOLON ? null : parseExp(tokens, 0);
        expect(SEMICOLON, tokens);
        t = tokens.getFirst();
        Exp post = t == CLOSE_PAREN ? null : parseExp(tokens, 0);
        expect(CLOSE_PAREN, tokens);
        Statement body = parseStatement(tokens);
        return new For(init, condition, post, body, null);
    }

    private static Exp fail(String s) {
        throw new RuntimeException(s);
    }
}
