package com.quaxt.mcc.parser;


import com.quaxt.mcc.*;
import com.quaxt.mcc.semantic.FunType;
import com.quaxt.mcc.semantic.Pointer;
import com.quaxt.mcc.semantic.Primitive;
import com.quaxt.mcc.semantic.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.TokenType.*;
import static com.quaxt.mcc.parser.NullStatement.NULL_STATEMENT;
import static com.quaxt.mcc.semantic.Primitive.UINT;
import static com.quaxt.mcc.semantic.Primitive.ULONG;

public class Parser {
    public static Token expect(Token expected, List<Token> tokens) {
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

    sealed interface Declarator permits Ident, PointerDeclarator, FunDeclarator {}

    record Ident(String identifier) implements Declarator {}

    record PointerDeclarator(Declarator declarator) implements Declarator {}

    record FunDeclarator(List<ParamInfo> params,
                         Declarator declarator) implements Declarator {}

    record ParamInfo(Type type, Declarator declarator) {}


    record NameDeclTypeParams(String name, Type type,
                              ArrayList<String> paramNames) {}

    sealed interface AbstractDeclarator permits AbstractBase, AbstractPointer, DirectAbstractDeclarator {}

    record AbstractBase() implements AbstractDeclarator {}

    record DirectAbstractDeclarator(
            AbstractDeclarator declarator) implements AbstractDeclarator {}

    record AbstractPointer(
            AbstractDeclarator declarator) implements AbstractDeclarator {}

    private static AbstractDeclarator parseAbstractDeclarator(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return new AbstractBase();
        }
        return switch (tokens.getFirst()) {
            case IMUL -> {
                tokens.removeFirst();
                yield new AbstractPointer(parseAbstractDeclarator(tokens));
            }
            case OPEN_PAREN -> {
                tokens.removeFirst();
                AbstractDeclarator d = new DirectAbstractDeclarator(parseAbstractDeclarator(tokens));
                expect(CLOSE_PAREN, tokens);
                yield d;
            }
            default -> new AbstractBase();
        };
    }

    private static Declarator parseDeclarator(List<Token> tokens) {
        Token t = tokens.removeFirst();
        Declarator d = switch (t) {
            case OPEN_PAREN -> {
                Declarator inner = parseDeclarator(tokens);
                expect(CLOSE_PAREN, tokens);
                yield inner;
            }
            case IMUL -> new PointerDeclarator(parseDeclarator(tokens));
            case TokenWithValue(Token type,
                                String name) when type == IDENTIFIER ->
                    new Ident(name);
            default ->
                    throw new RuntimeException("while parsing declarator found unexpected token :" + t);
        };
        if (tokens.getFirst() == OPEN_PAREN) {
            tokens.removeFirst();
            Token firstParam = tokens.getFirst();
            List<ParamInfo> params;
            if (VOID == firstParam.type()) {
                tokens.removeFirst();
                expect(CLOSE_PAREN, tokens);
                params = Collections.emptyList();
            } else {
                params = new ArrayList<>();

                while (true) {
                    TypeAndStorageClass typeAndStorageClass = parseTypeAndStorageClass(tokens, true);
                    if (typeAndStorageClass.storageClass() != null)
                        fail("error: storage class specified for parameter");
                    Declarator paramDeclarator = parseDeclarator(tokens);
                    NameDeclTypeParams ppp = processDeclarator(paramDeclarator, typeAndStorageClass.type());
                    params.add(new ParamInfo(ppp.type(), paramDeclarator));

                    Token token = tokens.removeFirst();
                    if (token == CLOSE_PAREN) break;
                    else if (token != COMMA)
                        throw new IllegalArgumentException("Expected COMMA, got " + token);
                }

            }
            return new FunDeclarator(params, d);
        }
        return d;
    }

    private static NameDeclTypeParams processDeclarator(Declarator declarator, Type baseType) {
        return switch (declarator) {
            case Ident(String name) ->
                    new NameDeclTypeParams(name, baseType, new ArrayList<>());
            case PointerDeclarator(Declarator d) ->
                    processDeclarator(d, new Pointer(baseType));
            case FunDeclarator(List<ParamInfo> params, Declarator d) -> {

                ArrayList<String> paramNames = new ArrayList<>();
                List<Type> paramTypes = new ArrayList<>();

                for (ParamInfo pi : params) {
                    NameDeclTypeParams decl = processDeclarator(pi.declarator(), pi.type());
                    String name = decl.name();
                    Type type = decl.type();
                    if (type instanceof FunType)
                        throw new RuntimeException("function pointers are not supported");
                    paramNames.add(name);
                    paramTypes.add(type);
                }
                FunType derivedType = new FunType(paramTypes, baseType);
                yield new NameDeclTypeParams(switch (d) {
                    case Ident(String name) -> name;
                    default ->
                            throw new RuntimeException("Can't apply additional derivations to a function type");
                }, derivedType, paramNames);
            }
        };
    }

    private static Declaration parseDeclaration(List<Token> tokens, boolean throwExceptionIfNoType) {
        // parse int i; or int i=5; or int foo(void);
        TypeAndStorageClass typeAndStorageClass = parseTypeAndStorageClass(tokens, throwExceptionIfNoType);
        if (typeAndStorageClass == null) return null;
        Declarator declarator = parseDeclarator(tokens);
        NameDeclTypeParams nameDeclTypeParams = processDeclarator(declarator, typeAndStorageClass.type());
        String name = nameDeclTypeParams.name();
        Type type = nameDeclTypeParams.type();
        ArrayList<String> paramNames = nameDeclTypeParams.paramNames();
        if (type instanceof FunType(List<Type> paramTypes1, Type ret)) {
            return parseRestOfFunction(paramNames, paramTypes1, tokens, name, typeAndStorageClass.type(), typeAndStorageClass.storageClass());
        }
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
            default:
                throw new IllegalArgumentException("Expected ; or =, got " + token);
        }

        return new VarDecl(name, exp, typeAndStorageClass.type(), typeAndStorageClass.storageClass());
    }


    public static TypeAndStorageClass parseTypeAndStorageClass(List<Token> tokens, boolean throwExceptionIfNoType) {
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
        return type == null ? null : new TypeAndStorageClass(type, storageClass);
    }

    private static Type parseType(List<Token> types, boolean throwExceptionIfNoType) {
        boolean foundDouble = false;
        boolean foundInt = false;
        boolean foundLong = false;
        boolean foundSigned = false;
        boolean foundUnsigned = false;
        for (Token t : types) {
            switch (t) {
                case DOUBLE -> {
                    if (foundDouble) fail("invalid type specifier");
                    else foundDouble = true;
                }
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
        if (foundDouble) {
            if (types.size() != 1) {
                fail("can't combine double with other type specifiers");
            }
            return Primitive.DOUBLE;
        }
        if (foundLong) return foundUnsigned ? ULONG : Primitive.LONG;
        else if (foundInt) return foundUnsigned ? UINT : Primitive.INT;
        else if (foundSigned) return Primitive.INT;
        else if (foundUnsigned) return UINT;
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
        if (identifier instanceof TokenWithValue(TokenType type,
                                                 String value) && type == IDENTIFIER) {
            return value;
        }
        throw new IllegalArgumentException("Expected identifier, got " + identifier);
    }

    private static boolean isTypeSpecifier(Token type) {
        return INT == type || LONG == type || UNSIGNED == type || SIGNED == type || DOUBLE == type;
    }

    private static Function parseRestOfFunction(ArrayList<String> paramNames, List<Type> paramTypes, List<Token> tokens, String functionName, Type returnType, StorageClass storageClass) {

        List<Identifier> params = new ArrayList<>();
        for (int i = 0; i < paramNames.size(); i++) {
            params.add(new Identifier(paramNames.get(i), paramTypes.get(i)));
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

        if (token instanceof TokenWithValue(Token type,
                                            String value) && type == IDENTIFIER) {
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
        return t == EXTERN || t == STATIC || isTypeSpecifier(t) ? parseDeclaration(tokens, false) : parseStatement(tokens);
    }

    public static Constant parseConst(String value, Type type) {
        if (type == Primitive.DOUBLE)
            return new ConstDouble(Double.parseDouble(value));
        if (type.isSigned()) {
            long v = Long.parseLong(value);
            if (v < 1L << 31 && type == Primitive.INT)
                return new ConstInt((int) v);
            else return new ConstLong(v);
        }
        long v = Long.parseUnsignedLong(value);
        if (Long.compareUnsigned(v, 0xffff_ffffL) <= 0 && type == Primitive.INT)
            return new ConstUInt((int) v);
        else return new ConstULong(v);
    }

    private static Exp parseFactor(List<Token> tokens) {
        Token token = tokens.removeFirst();
        return switch (token) {
            case SUB ->
                    new UnaryOp(UnaryOperator.UNARY_MINUS, parseFactor(tokens), null);
            case BITWISE_NOT ->
                    new UnaryOp(UnaryOperator.BITWISE_NOT, parseFactor(tokens), null);
            case AMPERSAND ->
                    new UnaryOp(UnaryOperator.ADDRESS, parseFactor(tokens), null);
            case IMUL ->
                    new UnaryOp(UnaryOperator.DEREFERENCE, parseFactor(tokens), null);
            case NOT ->
                    new UnaryOp(UnaryOperator.NOT, parseFactor(tokens), null);
            case OPEN_PAREN -> {
                TypeAndStorageClass typeSpecifierAndStorageClass = parseTypeAndStorageClass(tokens, false);
                if (typeSpecifierAndStorageClass != null) {
                    if (typeSpecifierAndStorageClass.storageClass() != null) {
                        fail("storage class not allowed in cast");
                    }
                    Type type = typeSpecifierAndStorageClass.type();
                    if (tokens.getFirst() != CLOSE_PAREN) {
                        type = processAbstractDeclarator(parseAbstractDeclarator(tokens), type);
                    }
                    expect(CLOSE_PAREN, tokens);

                    // We use parseFactor so (int)x=y is not parsed as (int)(x=y)
                    // Could use parseExp(tokens, 60) which would do the same
                    // thing but more slowly
                    Exp inner = parseFactor(tokens);
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
            case TokenWithValue(TokenType tokenType, String value) -> {
                Type t = Primitive.fromTokenType(tokenType);
                int len = value.length() - (t == null ? 0 : switch (t) {
                    case Primitive.LONG, UINT -> 1;
                    case ULONG -> 2;
                    default -> 0;
                });
                if (t != null) {
                    yield parseConst(value.substring(0, len), t);
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

    private static Type processAbstractDeclarator(AbstractDeclarator abstractDeclarator, Type type) {
        return switch (abstractDeclarator) {
            case AbstractBase _ -> type;
            case AbstractPointer _ -> new Pointer(type);
            case DirectAbstractDeclarator(AbstractDeclarator declarator) ->
                    processAbstractDeclarator(declarator, type);
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
            // case CAST -> 60; just reminding myself it's higher than these others
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

    public record TypeAndStorageClass(Type type, StorageClass storageClass) {}

}
