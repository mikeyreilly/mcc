package com.quaxt.mcc.parser;


import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.semantic.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.CompoundAssignmentOperator.*;
import static com.quaxt.mcc.CompoundAssignmentOperator.SAR_EQ;
import static com.quaxt.mcc.TokenType.*;
import static com.quaxt.mcc.UnaryOperator.POST_DECREMENT;
import static com.quaxt.mcc.UnaryOperator.POST_INCREMENT;
import static com.quaxt.mcc.parser.NullStatement.NULL_STATEMENT;

public class Parser {
    public static Token expect(Token expected, List<Token> tokens) {
        Token token = tokens.removeFirst();
        if (expected != token.type()) {
            throw new IllegalArgumentException("Expected " + expected + ", " +
                    "got " + token);
        }
        return token;
    }

    static Statement parseStatement(ArrayList<Token> tokens, List<String> labels, Switch enclosingSwitch) {
        Token token = tokens.getFirst();
        Token tokenType = token.type();
        if (RETURN == token.type()) {
            tokens.removeFirst();
            if (tokens.getFirst() == SEMICOLON) return new Return(null);
            Exp exp = parseExp(tokens, 0, true);
            expect(SEMICOLON, tokens);
            return new Return(exp);
        } else if (token == SEMICOLON) {
            tokens.removeFirst();
            return NULL_STATEMENT;
        } else if (token == IF) {
            tokens.removeFirst();
            expect(OPEN_PAREN, tokens);
            Exp condition = parseExp(tokens, 0, true);
            expect(CLOSE_PAREN, tokens);
            Statement ifTrue = parseStatement(tokens, labels, enclosingSwitch);
            Statement ifFalse = switch (tokens.getFirst()) {
                case ELSE -> {
                    tokens.removeFirst();
                    yield parseStatement(tokens, labels, enclosingSwitch);
                }
                default -> null;
            };
            return new If(condition, ifTrue, ifFalse);

        } else if (token == OPEN_BRACE) {
            return parseBlock(tokens, labels, enclosingSwitch);
        } else if (token == WHILE) {
            return parseWhile(tokens, labels, enclosingSwitch);
        } else if (token == DO) {
            return parseDoWhile(tokens, labels, enclosingSwitch);
        } else if (token == FOR) {
            tokens.removeFirst();
            return parseFor(tokens, labels, enclosingSwitch);
        } else if (token == SWITCH) {
            tokens.removeFirst();
            return parseSwitch(tokens, labels);
        } else if (token == BREAK) {
            tokens.removeFirst();
            expect(SEMICOLON, tokens);
            return new Break();
        } else if (token == CONTINUE) {
            tokens.removeFirst();
            expect(SEMICOLON, tokens);
            return new Continue();
        } else if (token == GOTO) {
            tokens.removeFirst();
            var label = expectIdentifier(tokens);
            expect(SEMICOLON, tokens);
            return new Goto(label);
        } else if (tokenType == LABEL) {
            tokens.removeFirst(); // LABEL
            tokens.removeFirst(); // has to be COLON because of how LABEL regex is defined
            TokenWithValue twv = (TokenWithValue) token;

            String label = twv.value();
            if (labels.contains(label)) {
                throw new Err("duplicate label: "+label);
            }
            labels.add(label);
            return new LabelledStatement(".L"+label, parseStatement(tokens, labels, enclosingSwitch));
        }  else if (tokenType == CASE) {
            tokens.removeFirst(); // CASE
            Constant<?> c = parseConst(tokens, true);
            expect(COLON, tokens);
            return new CaseStatement(enclosingSwitch, c, parseStatement(tokens, labels, enclosingSwitch));
        }   else if (tokenType == DEFAULT) {
            tokens.removeFirst(); // CASE
            expect(COLON, tokens);
            return new CaseStatement(enclosingSwitch, null, parseStatement(tokens, labels, enclosingSwitch));
        }
        Exp exp = parseExp(tokens, 0, true);
        expect(SEMICOLON, tokens);
        return exp;
    }

    private static DoWhile parseDoWhile(ArrayList<Token> tokens,
                                        List<String> labels,
                                        Switch enclosingSwitch) {
        expect(DO, tokens);
        Statement body = parseStatement(tokens, labels, enclosingSwitch);
        expect(WHILE, tokens);
        expect(OPEN_PAREN, tokens);
        Exp condition = parseExp(tokens, 0, true);
        expect(CLOSE_PAREN, tokens);
        expect(SEMICOLON, tokens);
        return new DoWhile(body, condition, null);
    }

    sealed interface Declarator permits ArrayDeclarator, FunDeclarator, Ident
            , PointerDeclarator {}

    record Ident(String identifier) implements Declarator {}

    record PointerDeclarator(Declarator declarator) implements Declarator {}

    record ArrayDeclarator(Declarator declarator,
                           Constant size) implements Declarator {}

    record FunDeclarator(List<ParamInfo> params,
                         Declarator declarator) implements Declarator {}

    record ParamInfo(Type type, Declarator declarator) {}


    record NameDeclTypeParams(String name, Type type,
                              ArrayList<String> paramNames) {}

    sealed interface AbstractDeclarator permits AbstractArrayDeclarator,
            AbstractBase, AbstractPointer, DirectAbstractDeclarator {}

    record AbstractBase() implements AbstractDeclarator {}

    record DirectAbstractDeclarator(
            AbstractDeclarator declarator) implements AbstractDeclarator {}

    record AbstractPointer(
            AbstractDeclarator declarator) implements AbstractDeclarator {}

    private static AbstractDeclarator parseAbstractDeclarator(
            List<Token> tokens) {
        // <abstract-declarator> ::= "*" [ <abstract-declarator> ]
        //                         | <direct-abstract-declarator>
        if (tokens.getFirst() == OPEN_PAREN || tokens.getFirst() == OPEN_BRACKET)
            return parseDirectAbstractDeclarator(tokens);
        if (tokens.getFirst() == IMUL) {
            tokens.removeFirst();
            return new AbstractPointer(parseAbstractDeclarator(tokens));
        }
        return new AbstractBase();
    }

    private static AbstractDeclarator parseDirectAbstractDeclarator(
            List<Token> tokens) {
        // <direct-abstract-declarator> ::= "(" <abstract-declarator> ")" {
        // "[" <const> "]" }
        //                                | { "[" <const> "]" }+
        AbstractDeclarator d = null;
        if (tokens.getFirst() == OPEN_PAREN) {
            tokens.removeFirst();
            d = parseAbstractDeclarator(tokens);
            expect(CLOSE_PAREN, tokens);
        }
        if (tokens.getFirst() == OPEN_BRACKET) {
            if (d == null) d = new AbstractBase();
            while (tokens.getFirst() == OPEN_BRACKET) {
                tokens.removeFirst();
                Constant c = parseConst(tokens, true);
                d = new AbstractArrayDeclarator(d, c);
                expect(CLOSE_BRACKET, tokens);
            }
            return d;
        }
        return d;

    }

    record AbstractArrayDeclarator(AbstractDeclarator abstractDeclarator,
                                   Constant arraySize) implements AbstractDeclarator {}

    public static String debugTokens(List<Token> tokens) {
        return tokens.stream().map(Object::toString).collect(Collectors.joining(" "));
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
                    throw new Err("while parsing declarator found unexpected " +
                            "token :" + t);
        };
        if (tokens.getFirst() == OPEN_PAREN) {
            tokens.removeFirst();
            Token firstParam = tokens.getFirst();
            List<ParamInfo> params;
            if (VOID == firstParam.type() && tokens.get(1) == CLOSE_PAREN) {
                tokens.removeFirst();
                expect(CLOSE_PAREN, tokens);
                params = Collections.emptyList();
            } else {
                params = new ArrayList<>();

                while (true) {
                    TypeAndStorageClass typeAndStorageClass =
                            parseTypeAndStorageClass(tokens, true);
                    if (typeAndStorageClass.storageClass() != null)
                        fail("error: storage class specified for parameter");
                    Declarator paramDeclarator = parseDeclarator(tokens);
                    NameDeclTypeParams nameDeclTypeParams =
                            processDeclarator(paramDeclarator,
                                    typeAndStorageClass.type());
                    params.add(new ParamInfo(nameDeclTypeParams.type(),
                            paramDeclarator));

                    Token token = tokens.removeFirst();
                    if (token == CLOSE_PAREN) break;
                    else if (token != COMMA)
                        throw new IllegalArgumentException("Expected COMMA, " +
                                "got " + token);
                }

            }
            return new FunDeclarator(params, d);
        } else {
            while (tokens.getFirst() == OPEN_BRACKET) {
                tokens.removeFirst();
                var c = parseConst(tokens, false);
                if (c instanceof DoubleInit) {
                    throw new Err("illegal non-integer array size");
                }
                d = new ArrayDeclarator(d, c);
                expect(CLOSE_BRACKET, tokens);
            }
        }
        return d;
    }

    private static NameDeclTypeParams processDeclarator(Declarator declarator,
                                                        Type baseType) {
        return switch (declarator) {
            case Ident(String name) ->
                    new NameDeclTypeParams(name, baseType, new ArrayList<>());
            case PointerDeclarator(Declarator d) ->
                    processDeclarator(d, new Pointer(baseType));
            case FunDeclarator(List<ParamInfo> params, Declarator d) -> {

                ArrayList<String> paramNames = new ArrayList<>();
                List<Type> paramTypes = new ArrayList<>();

                for (ParamInfo pi : params) {
                    NameDeclTypeParams decl =
                            processDeclarator(pi.declarator(), pi.type());
                    String name = decl.name();
                    Type type = pi.type();
                    if (type instanceof FunType)
                        throw new Err("function pointers are not supported");
                    paramNames.add(name);
                    paramTypes.add(type);
                }
                FunType derivedType = new FunType(paramTypes, baseType);
                yield new NameDeclTypeParams(switch (d) {
                    case Ident(String name) -> name;
                    default ->
                            throw new Err("Can't apply additional derivations" +
                                    " to a function type");
                }, derivedType, paramNames);
            }
            case ArrayDeclarator(Declarator inner, Constant size) -> {
                Array derivedType = new Array(baseType, size);
                yield processDeclarator(inner, derivedType);
            }
        };
    }

    private static Declaration parseDeclaration(ArrayList<Token> tokens,
                                                boolean throwExceptionIfNoType) {
        // parse int i; or int i=5; or int foo(void) or struct...;
        TypeAndStorageClass typeAndStorageClass =
                parseTypeAndStorageClass(tokens, throwExceptionIfNoType);
        if (typeAndStorageClass == null) return null;
        if (typeAndStorageClass.type() instanceof Structure(String tag)) {
            var t = tokens.getFirst();
            switch (t) {
                case SEMICOLON:
                    tokens.removeFirst();
                    return new StructDecl(tag, null);
                case OPEN_BRACE: {
                    tokens.removeFirst();
                    ArrayList<MemberDeclaration> members = new ArrayList<>();
                    while (tokens.getFirst() != CLOSE_BRACE) {
                        members.add(parseMemberDeclaration(tokens));
                        expect(SEMICOLON, tokens);
                    }
                    tokens.removeFirst(); // closing brace
                    expect(SEMICOLON, tokens);
                    if (members.isEmpty()) {
                        throw new Err("A struct must have one or more member " +
                                "declarators");
                    }
                    return new StructDecl(tag, members);
                }

                default:
                    break;
            }
        }
        Declarator declarator = parseDeclarator(tokens);
        NameDeclTypeParams nameDeclTypeParams = processDeclarator(declarator,
                typeAndStorageClass.type());
        String name = nameDeclTypeParams.name();
        Type type = nameDeclTypeParams.type();
        ArrayList<String> paramNames = nameDeclTypeParams.paramNames();
        if (type instanceof FunType(List<Type> paramTypes1, Type ret)) {
            return parseRestOfFunction(paramNames, paramTypes1, tokens, name,
                    ret, typeAndStorageClass.storageClass());
        }
        Token token = tokens.removeFirst();
        Initializer init;
        switch (token.type()) {
            case BECOMES:
                init = parseInitializer(tokens);
                expect(SEMICOLON, tokens);
                break;
            case SEMICOLON:
                init = null;
                break;
            default:
                throw new IllegalArgumentException("Expected ; or =, got " + token);
        }

        return new VarDecl(new Var(name, type), init, type,
                typeAndStorageClass.storageClass());
    }

    private static MemberDeclaration parseMemberDeclaration(
            ArrayList<Token> tokens) {
        TypeAndStorageClass typeAndStorageClass =
                parseTypeAndStorageClass(tokens, true);
        if (typeAndStorageClass.storageClass() != null)
            fail("error: storage class specified for struct member");
        Declarator paramDeclarator = parseDeclarator(tokens);
        NameDeclTypeParams nameDeclTypeParams =
                processDeclarator(paramDeclarator, typeAndStorageClass.type());
        var t = nameDeclTypeParams.type();
        if (t instanceof FunType)
            fail("error: member declaration can't be function");
        return new MemberDeclaration(t, nameDeclTypeParams.name());

    }

    private static Initializer parseInitializer(ArrayList<Token> tokens) {
        Token token = tokens.getFirst();
        if (token == OPEN_BRACE) {
            tokens.removeFirst();
            boolean done = false;
            ArrayList<Initializer> inits = new ArrayList<>();

            while (!done) {
                Initializer init = parseInitializer(tokens);
                inits.add(init);
                Token t = tokens.removeFirst();
                done = switch (t) {
                    case COMMA -> {
                        boolean trailingComma =
                                !tokens.isEmpty() && tokens.getFirst() == CLOSE_BRACE;
                        if (trailingComma) {
                            tokens.removeFirst();//remove close brace
                        }
                        yield trailingComma;
                    }
                    case CLOSE_BRACE -> true;
                    default ->
                            throw new IllegalStateException("Unexpected " +
                                    "value: " + tokens.removeFirst());
                };
            }
            return new CompoundInit(inits, null);

        }
        return new SingleInit(parseExp(tokens, 0, false), null);

    }


    public static TypeAndStorageClass parseTypeAndStorageClass(
            List<Token> tokens, boolean throwExceptionIfNoType) {
        if (tokens.isEmpty()) return null;
        List<Token> types = new ArrayList<>();
        List<StorageClass> storageClasses = new ArrayList<>();
        Token t;

        while (true) {
            t = tokens.getFirst();
            if (t == STRUCT) {
                tokens.removeFirst();
                types.add(STRUCT);
                if (tokens.getFirst().type() == IDENTIFIER) {
                    types.add(tokens.removeFirst());
                }
            } else if (isTypeSpecifier(tokens, 0)) {
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
        StorageClass storageClass = storageClasses.isEmpty() ? null :
                storageClasses.getFirst();
        return type == null ? null : new TypeAndStorageClass(type,
                storageClass);
    }

    static int parseTypeCount = 0;

    private static Type parseType(List<Token> types,
                                  boolean throwExceptionIfNoType) {
        parseTypeCount++;
        boolean foundInt = false;
        boolean foundLong = false;
        boolean foundSigned = false;
        boolean foundUnsigned = false;
        boolean foundChar = false;
        for (Token t : types) {
            switch (t) {
                case DOUBLE -> {
                    if (types.size() != 1) {
                        fail("can't combine double with other type specifiers");
                    }
                    return Primitive.DOUBLE;
                }
                case INT -> {
                    if (foundInt || foundChar) fail("invalid type specifier");
                    else foundInt = true;
                }
                case CHAR -> {
                    if (foundChar || foundInt || foundLong)
                        fail("invalid type specifier");
                    else foundChar = true;
                }
                case LONG -> {
                    if (foundLong || foundChar) fail("invalid type specifier");
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
                case VOID -> {
                    if (types.size() > 1)
                        fail("can't combine void with other type specifiers");
                    return Primitive.VOID;
                }
                case STRUCT -> {
                    if (types.size() > 2)
                        fail("can't combine void with other type specifiers");
                    if (types.get(1) instanceof TokenWithValue(Token type,
                                                               String tag) && type == IDENTIFIER)
                        return new Structure(tag);
                    else fail("identifier expected following struct");
                }
                default -> fail("invalid type specifier");
            }
        }

        if (foundChar)
            return foundSigned ? Primitive.SCHAR : foundUnsigned ?
                    Primitive.UCHAR : Primitive.CHAR;
        else if (foundLong)
            return foundUnsigned ? Primitive.ULONG : Primitive.LONG;
        else if (foundInt)
            return foundUnsigned ? Primitive.UINT : Primitive.INT;
        else if (foundSigned) return Primitive.INT;
        else if (foundUnsigned) return Primitive.UINT;
        if (throwExceptionIfNoType)
            throw new Err("invalid type specifier: " + types);
        return null;
    }


    public static Program parseProgram(ArrayList<Token> tokens) {
        Declaration declaration;
        ArrayList<Declaration> declarations = new ArrayList<>();
        while ((declaration = parseDeclaration(tokens, true)) != null) {
            declarations.add(declaration);
        }
        return new Program(declarations);
    }

    private static boolean isTypeSpecifier(List<Token> tokens, int start) {
        Token first = tokens.get(start);
        return CHAR == first || INT == first || LONG == first || UNSIGNED == first || SIGNED == first || DOUBLE == first || VOID == first || STRUCT == first;
    }

    private static Function parseRestOfFunction(ArrayList<String> paramNames,
                                                List<Type> paramTypes,
                                                ArrayList<Token> tokens,
                                                String functionName,
                                                Type returnType,
                                                StorageClass storageClass) {

        List<Var> params = new ArrayList<>();
        for (int i = 0; i < paramNames.size(); i++) {
            params.add(new Var(paramNames.get(i), paramTypes.get(i)));
        }

        Block block;
        if (tokens.getFirst() == OPEN_BRACE) {
            block = parseBlock(tokens, new ArrayList<>(), null);
        } else {
            expect(SEMICOLON, tokens);
            block = null;
        }
        return new Function(functionName, params, block,
                new FunType(paramTypes, returnType), storageClass);
    }

    private static String expectIdentifier(List<Token> tokens) {
        Token token = tokens.removeFirst();

        if (token instanceof TokenWithValue(Token type,
                                            String value) && type == IDENTIFIER) {
            return value;
        }
        throw new IllegalArgumentException("Expected IDENTIFIER got " + token);
    }

    private static Block parseBlock(ArrayList<Token> tokens, List<String> labels,
                                    Switch enclosingSwitch) {
        expect(OPEN_BRACE, tokens);

        ArrayList<BlockItem> blockItems = new ArrayList<>();
        while (tokens.getFirst() != CLOSE_BRACE) {
            blockItems.add(parseBlockItem(tokens, labels, enclosingSwitch));
        }
        tokens.removeFirst();
        return new Block(blockItems);
    }

    private static While parseWhile(ArrayList<Token> tokens, List<String> labels,
                                    Switch enclosingSwitch) {
        expect(WHILE, tokens);
        expect(OPEN_PAREN, tokens);
        Exp condition = parseExp(tokens, 0, true);
        expect(CLOSE_PAREN, tokens);
        Statement body = parseStatement(tokens, labels, enclosingSwitch);
        return new While(condition, body, null);
    }


    private static BlockItem parseBlockItem(ArrayList<Token> tokens, List<String> labels,
                                            Switch enclosingSwitch) {
        Token t = tokens.getFirst();
        return t == EXTERN || t == STATIC || isTypeSpecifier(tokens, 0) ?
                parseDeclaration(tokens, false) : parseStatement(tokens, labels, enclosingSwitch);
    }

    public static Constant parseConst(String value, Type type) {
        if (type == Primitive.DOUBLE)
            return new DoubleInit(Double.parseDouble(value));
        if (type.isSigned()) {
            long v = Long.parseLong(value);
            if (v < 1L << 31 && type == Primitive.INT)
                return new IntInit((int) v);
            else return new LongInit(v);
        }
        long v = Long.parseUnsignedLong(value);
        if (Long.compareUnsigned(v, 0xffff_ffffL) <= 0 && (type == Primitive.INT || type == Primitive.UINT))
            return new UIntInit((int) v);
        else return new ULongInit(v);
    }

    private static Type processAbstractDeclarator(
            AbstractDeclarator abstractDeclarator, Type type) {
        return switch (abstractDeclarator) {
            case AbstractBase _ -> type;
            case AbstractPointer(AbstractDeclarator declarator) ->
                    processAbstractDeclarator(declarator, new Pointer(type));
            case DirectAbstractDeclarator(AbstractDeclarator declarator) ->
                    processAbstractDeclarator(declarator, type);
            case AbstractArrayDeclarator(AbstractDeclarator declarator,
                                         Constant arraySize) ->
                    processAbstractDeclarator(declarator, new Array(type,
                            arraySize));
        };
    }


    private static Exp parseUnaryExp(ArrayList<Token> tokens) {
        // <unary-exp> ::= <unop> <unary-exp>
        //               | "sizeof" <unary-exp>
        //               | "sizeof" "(" <type-name> ")"
        //               | <postfix-exp>

        return switch (tokens.getFirst()) {
            case INCREMENT -> {
                tokens.removeFirst();
                var exp = parseCastExp(tokens);
                yield new CompoundAssignment(ADD_EQ, exp, IntInit.ONE, null, null);
            }
            case DECREMENT -> {
                tokens.removeFirst();
                var exp = parseCastExp(tokens);
                yield new CompoundAssignment(SUB_EQ, exp, IntInit.ONE,null, null);
            }
            case SUB -> {
                tokens.removeFirst();
                yield new UnaryOp(UnaryOperator.UNARY_MINUS,
                        parseCastExp(tokens), null);
            }
            case BITWISE_NOT -> {
                tokens.removeFirst();
                yield new UnaryOp(UnaryOperator.BITWISE_NOT,
                        parseCastExp(tokens), null);
            }
            case BITWISE_AND -> {
                tokens.removeFirst();
                yield new AddrOf(parseCastExp(tokens), null);
            }
            case IMUL -> {
                tokens.removeFirst();
                yield new Dereference(parseCastExp(tokens), null);
            }
            case NOT -> {
                tokens.removeFirst();
                yield new UnaryOp(UnaryOperator.NOT, parseCastExp(tokens),
                        null);
            }
            case SIZEOF -> {
                tokens.removeFirst();
                if (tokens.getFirst() == OPEN_PAREN && isTypeSpecifier(tokens
                        , 1)) {
                    tokens.removeFirst();
                    TypeName typeName = parseTypeName(tokens);
                    expect(CLOSE_PAREN, tokens);
                    yield new SizeOfT(typeNameToType(typeName));
                } else {
                    yield new SizeOf(parseUnaryExp(tokens));
                }
            }
            default -> parsePostfixExp(tokens);
        };
    }

    private static Exp parsePostfixExp(ArrayList<Token> tokens) {
        // <postfix-exp> ::= <primary-exp> { "[" <exp> "]" }
        //                 | <primary-exp> { "." <identifier>  }
        //                 | <primary-exp>  { "->" <identifier>  }
        Exp exp = parsePrimaryExp(tokens);
        outer:
        while (true) {
            switch (tokens.getFirst()) {
                case OPEN_BRACKET:
                    tokens.removeFirst();
                    Exp subscript = parseExp(tokens, 0, true);
                    expect(CLOSE_BRACKET, tokens);
                    exp = new Subscript(exp, subscript, null);
                    break;
                case DOT:
                    tokens.removeFirst();
                    exp = new Dot(exp, expectIdentifier(tokens), null);
                    break;
                case ARROW:
                    tokens.removeFirst();
                    exp = new Arrow(exp, expectIdentifier(tokens), null);
                    break;
                case INCREMENT:
                    // for post increment, we rewrite with exp++ as exp = exp+1, exp-1
                    tokens.removeFirst();
                    exp = new UnaryOp(POST_INCREMENT, exp, null);
                    break;
                case DECREMENT:
                    // for post increment, we rewrite with exp-- as exp = exp-1, exp+1
                    tokens.removeFirst();
                    exp = new UnaryOp(POST_DECREMENT, exp, null);
                    break;
                default:
                    break outer;
            }
        }
        return exp;
    }

    private static Constant parseConst(List<Token> tokens,
                                       boolean throwIfNotFound) {
        Token token = tokens.getFirst();
        if (tokens.getFirst() instanceof TokenWithValue(Token tokenType,
                                                        String value)) {
            tokens.removeFirst();
            switch (token.type()) {
                case INT_LITERAL:
                case DOUBLE_LITERAL:
                case LONG_LITERAL:
                case UNSIGNED_LONG_LITERAL:
                case UNSIGNED_INT_LITERAL: {
                    Type t = Primitive.fromTokenType((TokenType) tokenType);
                    int len = value.length() - (t == null ? 0 : switch (t) {
                        case Primitive.LONG, Primitive.UINT -> 1;
                        case Primitive.ULONG -> 2;
                        default -> 0;
                    });

                    return parseConst(value.substring(0, len), t);
                }
                case CHAR_LITERAL: {
                    return new IntInit(parseChar(value));
                }
                default:
                    break;
            }
        }
        if (throwIfNotFound)
            throw new IllegalStateException("expected const, found: " + token);
        return null;
    }

    private static int parseChar(String s) {
        int len = s.length();
        switch (len) {
            case 2 -> {
                assert (s.charAt(0) == '\\');
                char c = s.charAt(1);
                return switch (c) {
                    case '\'' -> '\'';
                    case '\"' -> '\"';
                    case '?' -> '?';
                    case '\\' -> '\\';
                    case 'a' -> 7;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'v' -> 11;
                    default -> throw new AssertionError(c);
                };
            }
            case 1 -> {
                return s.charAt(0);
            }
            default -> throw new AssertionError(len);
        }
    }


    private static Exp parsePrimaryExp(ArrayList<Token> tokens) {
        // <primary-exp> ::= <const> | <identifier> | "(" <exp> ")"
        //                 | <identifier> "(" [ <argument-list> ] ")"
        return switch (tokens.getFirst()) {
//            case OPEN_BRACKET -> {
//                Exp exp = parseExp(tokens, 0);
//                expect(CLOSE_BRACKET, tokens);
//                yield exp;
//            }
            case TokenWithValue(Token tokenType, String value) -> {

                yield switch (tokenType) {
                    case STRING_LITERAL -> new Str(parseStr(tokens), null);
                    case IDENTIFIER,
                         // if we're in the middle of a ?: expression we
                         // might have token type label (because of the colon), but it's really an identifier
                         LABEL -> {
                        tokens.removeFirst();

                        Var id = new Var(value, null);
                        if (!tokens.isEmpty() && tokens.getFirst() == OPEN_PAREN) {
                            tokens.removeFirst();
                            Token current = tokens.getFirst();
                            if (current == CLOSE_PAREN) {
                                tokens.removeFirst();
                                yield new FunctionCall(id,
                                        Collections.emptyList(), null);
                            }
                            List<Exp> args = new ArrayList<>();

                            while (true) {
                                Exp e = parseExp(tokens, 0,
                                        false); // false because we want comma as argument separator, not operator
                                args.add(e);
                                current = tokens.removeFirst();
                                if (current == COMMA) {
                                    continue;
                                }
                                if (current == CLOSE_PAREN) {
                                    break;
                                } else
                                    throw new IllegalArgumentException(
                                            "unexpected token while parsing " +
                                                    "function call: " + current);

                            }
                            yield new FunctionCall(id, args, null);

                        }
                        yield id;

                    }
                    default -> parseConst(tokens, true);
                };
            }
            case OPEN_PAREN -> {
                tokens.removeFirst();
                Exp exp = parseExp(tokens, 0,true);
                expect(CLOSE_PAREN, tokens);
                yield exp;
            }
            default -> throw new Todo("can't handle:" + tokens.getFirst());
        };
    }

    private static String parseStr(ArrayList<Token> tokens) {
        int cslen = 0;
        int consecutiveStringCount = 0;
        for (; ; consecutiveStringCount++) {
            if (tokens.get(consecutiveStringCount) instanceof TokenWithValue(
                    Token type, String value) && type == STRING_LITERAL) {
                cslen += value.length();
            } else break;
        }


        char[] cs = new char[cslen];
        int toIndex = 0;
        for (int current = 0; current < consecutiveStringCount; current++) {
            TokenWithValue twv = (TokenWithValue) tokens.get(current);
            String value = twv.value();
            int slen = value.length();
            for (int i = 0; i < slen; i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\' -> {
                        char next = value.charAt(++i);
                        cs[toIndex++] = switch (next) {
                            case '\'' -> '\'';
                            case '\"' -> '\"';
                            case '?' -> '?';
                            case '\\' -> '\\';
                            case 'a' -> 7;
                            case 'b' -> '\b';
                            case 'f' -> '\f';
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            case 'v' -> 11;
                            default -> throw new AssertionError(next);
                        };
                    }
                    default -> cs[toIndex++] = value.charAt(i);

                }
            }
        }
        tokens.subList(0, consecutiveStringCount).clear();
        return new String(cs, 0, toIndex);
    }

    private static Exp parseCastExp(ArrayList<Token> tokens) {
        // <cast-exp> ::= "(" <type-name> ")" <cast-exp>
        //              | <unary-exp>

        if (tokens.getFirst() == OPEN_PAREN && isTypeSpecifier(tokens, 1)) {
            tokens.removeFirst();
            TypeName typeName = parseTypeName(tokens);
            expect(CLOSE_PAREN, tokens);
            Type type = typeNameToType(typeName);
            Exp inner = parseCastExp(tokens);
            return new Cast(type, inner);
        }
        return parseUnaryExp(tokens);
    }

    private static Type typeNameToType(TypeName typeName) {
        List<Token> typeSpecifiers = typeName.typeSpecifiers();
        Type t = parseType(typeSpecifiers, true);
        return processAbstractDeclarator(typeName.abstractDeclarator(), t);
    }

    record TypeName(List<Token> typeSpecifiers,
                    AbstractDeclarator abstractDeclarator) {}

    private static TypeName parseTypeName(ArrayList<Token> tokens) {
        List<Token> typeSpecifiers = new ArrayList<Token>();
        while (isTypeSpecifier(tokens, 0)) {
            var t = tokens.removeFirst();
            typeSpecifiers.add(t);
            if (t == STRUCT) typeSpecifiers.add(tokens.removeFirst());
        }
        AbstractDeclarator abstractDeclarator = parseAbstractDeclarator(tokens);
        return new TypeName(typeSpecifiers, abstractDeclarator);
    }

    private static Exp parseExp(ArrayList<Token> tokens, int minPrecedence, boolean allowComma) {

        //to this
        // <exp> ::= <cast-exp> | <exp> <binop> <exp> | <exp> "?" <exp> ":"
        // <exp>

        Exp left = parseCastExp(tokens);
        // now peek to see if there is "binop <exp>" or "? <exp> : <exp>

        while (!tokens.isEmpty()) {
            Token token = tokens.getFirst();
            if (token instanceof  BinaryOperator binop && (allowComma || binop != COMMA) || token == QUESTION_MARK) {
                int precedence = getPrecedence(token);
                if (precedence < minPrecedence) break;
                tokens.removeFirst();
                if (token == BECOMES ) { // right associative
                    Exp right = parseExp(tokens, precedence, true);
                    left = new Assignment(left, right, null);
                } else   if (token instanceof CompoundAssignmentOperator compOp) { // right associative
                    Exp right = parseExp(tokens, precedence, true);
                    left = new CompoundAssignment(compOp, left, right, null, null);
                } else if (token instanceof BinaryOperator binop) {
                    Exp right = parseExp(tokens, precedence + 1, true);
                    left = new BinaryOp(binop, left, right, null);
                } else { // QUESTION_MARK
                    Exp middle = parseExp(tokens, 0, true);
                    expect(COLON, tokens);
                    Exp right = parseExp(tokens, precedence, true);
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
            // case CAST -> 60; just reminding myself it's higher than these
            // others
            case IMUL, DIVIDE, REMAINDER -> 50;
            case SUB, ADD -> 45;
            case SHL, SAR, UNSIGNED_RIGHT_SHIFT -> 40;
            case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN,
                 GREATER_THAN_OR_EQUAL -> 35;
            case EQUALS, NOT_EQUALS -> 30;
            case BITWISE_AND -> 18;
            case BITWISE_XOR -> 17;
            case BITWISE_OR -> 16;
            case AND -> 10;
            case OR -> 5;
            case QUESTION_MARK -> 3;
            case BECOMES, SUB_EQ, ADD_EQ, IMUL_EQ, DIVIDE_EQ, REMAINDER_EQ, AND_EQ, BITWISE_AND_EQ, OR_EQ, BITWISE_OR_EQ, BITWISE_XOR_EQ, SHL_EQ, SAR_EQ -> 1;
            case COMMA -> 0;
            default ->
                    throw new IllegalStateException("No precedence for: " + t);
        };
    }

    private static ForInit parseForInit(ArrayList<Token> tokens) {

        if (isTypeSpecifier(tokens, 0))
            return (ForInit) parseDeclaration(tokens, true);
        Exp r = tokens.getFirst() == SEMICOLON ? null : parseExp(tokens, 0, true);
        expect(SEMICOLON, tokens);
        return r;
    }

    private static For parseFor(ArrayList<Token> tokens, List<String> labels,
                                Switch enclosingSwitch) {
        expect(OPEN_PAREN, tokens);
        ForInit init = parseForInit(tokens);
        Token t = tokens.getFirst();
        Exp condition = t == SEMICOLON ? null : parseExp(tokens, 0, true);
        expect(SEMICOLON, tokens);
        t = tokens.getFirst();
        Exp post = t == CLOSE_PAREN ? null : parseExp(tokens, 0, true);
        expect(CLOSE_PAREN, tokens);
        Statement body = parseStatement(tokens, labels, enclosingSwitch);
        return new For(init, condition, post, body, null);
    }
    private static Switch parseSwitch(ArrayList<Token> tokens, List<String> labels) {
        expect(OPEN_PAREN, tokens);
        Exp switchExpression = parseExp(tokens, 0, true);
        expect(CLOSE_PAREN, tokens);
        Switch s= new Switch();
        s.label = Mcc.makeTemporary(".Lswitch.");
        Statement body = parseStatement(tokens, labels, s);

        s.exp=switchExpression;

        s.body=body;
        return s;
    }

    private static Exp fail(String s) {
        throw new Err(s);
    }

    public record TypeAndStorageClass(Type type, StorageClass storageClass) {}

}
