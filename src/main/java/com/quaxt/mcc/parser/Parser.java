package com.quaxt.mcc.parser;


import com.quaxt.mcc.*;
import com.quaxt.mcc.semantic.FunType;
import com.quaxt.mcc.semantic.Type;

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

    private static Declaration parseDeclaration(List<Token> tokens, boolean throwExceptionIfNoType) {
        // parse int i; or int i=5; or int foo(void);
        TypeAndStorageClass typeAndStorageClass = parseTypeAndStorageClass(tokens, throwExceptionIfNoType);
        if (typeAndStorageClass == null) return null;
        Token t = tokens.removeFirst();
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
                return parseRestOfFunction(tokens, name, typeAndStorageClass.type(), typeAndStorageClass.storageClass());
            default:
                throw new IllegalArgumentException("Expected ; or =, got " + token);
        }

        return new VarDecl(name, exp, typeAndStorageClass.type(), typeAndStorageClass.storageClass());
    }

    private static TypeAndStorageClass parseTypeAndStorageClass(List<Token> tokens, boolean throwExceptionIfNoType) {
        if (tokens.isEmpty()) return null;
        List<Token> types = new ArrayList<>();
        List<StorageClass> storageClasses = new ArrayList<>();
        Token t;

        while (true) {
            t = tokens.getFirst();
            if (isTypeSpecifier(t)) {
                tokens.removeFirst();
                types.add(t);
            } else if (STATIC == t) {
                tokens.removeFirst();
                storageClasses.add(StorageClass.STATIC);
            } else if (EXTERN == t) {
                tokens.removeFirst();
                storageClasses.add(StorageClass.EXTERN);
            } else {
                break;
            }
        }
        Type type = parseType(types, throwExceptionIfNoType);

        if (storageClasses.size() > 1) {
            fail("invalid storage class");
        }
        StorageClass storageClass = storageClasses.isEmpty() ? null : storageClasses.getFirst();
        return new TypeAndStorageClass(type, storageClass);
    }

    private static Type parseType(List<Token> types, boolean throwExceptionIfNoType) {
        boolean foundInt = false;
        boolean foundLong = false;
        boolean foundSigned = false;
        boolean foundUnsigned = false;
        for (Token t : types) {
            switch (t) {
                case INT -> {
                    if (foundInt) fail("invalid type specifier");
                    else foundInt = true;
                }
                case LONG -> {
                    if (foundLong) fail("invalid type specifier");
                    else foundLong = true;
                }
                case SIGNED -> {
                    if (foundSigned || foundUnsigned)
                        fail("invalid type specifier");
                    else foundSigned = true;
                }
                case UNSIGNED -> {
                    if (foundSigned || foundUnsigned)
                        fail("invalid type specifier");
                    else foundUnsigned = true;
                }
                default -> fail("invalid type specifier");
            }
        }

        if (foundLong)
            return foundUnsigned ? com.quaxt.mcc.semantic.Primitive.ULONG : com.quaxt.mcc.semantic.Primitive.LONG;
        else if (foundInt)
            return foundUnsigned ? com.quaxt.mcc.semantic.Primitive.UINT : com.quaxt.mcc.semantic.Primitive.INT;
        else if (foundSigned) return com.quaxt.mcc.semantic.Primitive.INT;
        else if (foundUnsigned) return com.quaxt.mcc.semantic.Primitive.UINT;
        if (throwExceptionIfNoType)
            throw new RuntimeException("invalid type specifier");
        return null;
    }


    public static Program parseProgram(List<Token> tokens) {
        Declaration declaration;
        ArrayList<Declaration> declarations = new ArrayList<>();
        while ((declaration = parseDeclaration(tokens, true)) != null) {
            declarations.add(declaration);
        }
        return new Program(declarations);
    }

    private static String parseIdentifier(Token identifier) {
        if (identifier instanceof TokenWithValue(
                TokenType type, String value
        ) && type == IDENTIFIER) {
            return value;
        }
        throw new IllegalArgumentException("Expected identifier, got " + identifier);
    }

    private static boolean isTypeSpecifier(Token type) {
        return INT == type || LONG == type || UNSIGNED == type || SIGNED == type;
    }

    private static Function parseRestOfFunction(List<Token> tokens, String functionName, Type returnType, StorageClass storageClass) {
        Token firstParam = tokens.getFirst();
        List<Identifier> params;
        List<Type> paramTypes;
        if (VOID == firstParam.type()) {
            tokens.removeFirst();
            expect(CLOSE_PAREN, tokens);
            params = Collections.emptyList();
            paramTypes = Collections.emptyList();
        } else {
            params = new ArrayList<>();
            paramTypes = new ArrayList<>();

            while (true) {
                TypeAndStorageClass typeAndStorageClass = parseTypeAndStorageClass(tokens, true);
                paramTypes.add(typeAndStorageClass.type());
                String identifierName = expectIdentifier(tokens);
                if (typeAndStorageClass.storageClass() != null)
                    fail("error: storage class specified for parameter " + identifierName);
                params.add(new Identifier(identifierName, null));

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
        return new Function(functionName, params, block, new FunType(paramTypes, returnType), storageClass);
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
        Token t = tokens.getFirst();
        return t == EXTERN || t == STATIC || isTypeSpecifier(t) ?
                parseDeclaration(tokens, false)
                : parseStatement(tokens);
    }

    public static Constant parseConst(String value, boolean isIntToken, boolean signed) {
        if (signed) {
            long v = Long.parseLong(value);
            if (v < 1L << 31 && isIntToken)
                return new ConstInt((int) v);
            else return new ConstLong(v);
        }
        long v = Long.parseUnsignedLong(value);
        if (v <= 0xffff_ffffL && isIntToken)
            return new ConstUInt((int) v);
        else return new ConstLong(v);
    }

    private static Exp parseFactor(List<Token> tokens) {
        Token token = tokens.removeFirst();
        return switch (token) {
            case SUB ->
                    new UnaryOp(UnaryOperator.COMPLEMENT, parseFactor(tokens), null);
            case COMPLIMENT ->
                    new UnaryOp(UnaryOperator.NEGATE, parseFactor(tokens), null);
            case NOT ->
                    new UnaryOp(UnaryOperator.NOT, parseFactor(tokens), null);
            case OPEN_PAREN -> {
                TypeAndStorageClass typeSpecifierAndStorageClass = parseTypeAndStorageClass(tokens, false);
                if (typeSpecifierAndStorageClass != null && CLOSE_PAREN == tokens.getFirst()) {
                    if (typeSpecifierAndStorageClass.storageClass() != null) {
                        fail("storage class not allowed in cast");
                    }
                    Type type = typeSpecifierAndStorageClass.type();
                    tokens.removeFirst();//close_paren
                    Exp inner = parseExp(tokens, 0);
                    if (inner instanceof Assignment) {
                        fail("lvalue required as left operand of assignment");
                    }
                    yield new Cast(type, inner);

                } else {
                    Exp r = parseExp(tokens, 0);
                    expect(CLOSE_PAREN, tokens);
                    yield r;
                }
            }
            case TokenWithValue(
                    TokenType type, String value
            ) -> {
                if (type == INT_LITERAL)
                    yield parseConst(value, true, true);
                if (type == LONG_LITERAL)
                    yield parseConst(value.substring(0, value.length() - 1), false, true);
                if (type == UNSIGNED_INT_LITERAL)
                    yield parseConst(value.substring(0, value.length() - 1), true, false);
                if (type == UNSIGNED_LONG_LITERAL) {
                    yield parseConst(value.substring(0, value.length() - 2), false, false);
                }
                Identifier id = new Identifier(value, null);
                if (!tokens.isEmpty() && tokens.getFirst() == OPEN_PAREN) {
                    tokens.removeFirst();
                    Token current = tokens.getFirst();
                    if (current == CLOSE_PAREN) {
                        tokens.removeFirst();
                        yield new FunctionCall(id, Collections.emptyList(), null);
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
                    yield new FunctionCall(id, args, null);

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
                    left = new Assignment(left, right, null);
                } else if (token instanceof BinaryOperator binop) {
                    Exp right = parseExp(tokens, precedence + 1);
                    left = new BinaryOp(binop, left, right, null);
                } else { // QUESTION_MARK
                    Exp middle = parseExp(tokens, 0);
                    expect(COLON, tokens);
                    Exp right = parseExp(tokens, precedence);
                    left = new Conditional(left, middle, right, null);
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
        if (isTypeSpecifier(t)) return (ForInit) parseDeclaration(tokens, true);
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

    private record TypeAndStorageClass(Type type, StorageClass storageClass) {
    }
}
