package com.quaxt.mcc.parser;


import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.semantic.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.CompoundAssignmentOperator.*;
import static com.quaxt.mcc.CompoundAssignmentOperator.SAR_EQ;
import static com.quaxt.mcc.Mcc.*;
import static com.quaxt.mcc.TokenType.*;
import static com.quaxt.mcc.TokenType.VOID;
import static com.quaxt.mcc.UnaryOperator.POST_DECREMENT;
import static com.quaxt.mcc.UnaryOperator.POST_INCREMENT;
import static com.quaxt.mcc.parser.NullStatement.NULL_STATEMENT;

public class Parser {
    record AbstractArrayDeclarator(AbstractDeclarator abstractDeclarator,
                                   Constant arraySize) implements AbstractDeclarator {}

    record NameDeclTypeParams(String name, Type type,
                              ArrayList<String> paramNames) {}
    sealed interface AbstractDeclarator extends DeclaratorOrAbstractDeclarator{};

    record AbstractBase() implements AbstractDeclarator {}

    record AbstractPointer(
            AbstractDeclarator declarator) implements AbstractDeclarator {}
    record FunctionAbstractDeclarator(AbstractDeclarator d,
                                      ParameterTypeList args) implements AbstractDeclarator{}
    record PointerDeclarator(Declarator d) implements Declarator {}
    sealed interface DirectDeclarator extends Declarator permits ArrayDeclarator, Ident, FunctionDeclarator {};
    record Ident(String identifier) implements DirectDeclarator {}
    sealed interface Declarator extends DeclaratorOrAbstractDeclarator permits  DirectDeclarator, PointerDeclarator {}
    record ArrayDeclarator(Declarator d, Exp arraySize) implements DirectDeclarator{};
    record FunctionDeclarator(DeclaratorOrAbstractDeclarator d, ParameterTypeList parameterTypeList) implements DirectDeclarator{};
    record ParameterTypeList(
            ArrayList<ParameterDeclaration> parameterDeclarations, boolean varArgs){}
    sealed interface DeclaratorOrAbstractDeclarator {}

    record ParameterDeclaration(
            List<DeclarationSpecifier> declarationSpecifiers,
            DeclaratorOrAbstractDeclarator declarator) {}
    public static List<DeclarationSpecifier> parseDeclarationSpecifiers(
            TokenList tokens, ArrayList<Map<String, Type>> typeAliases) {
        // declaration-specifiers:
        //        storage-class-specifier declaration-specifiers(opt)
        //        type-specifier declaration-specifiersopt
        //        type-qualifier declaration-specifiers(opt) (none of these
        //        are supported yet)
        //        function-specifier declaration-specifiers(opt) (not supported)
        StorageClass storageClass;
        TypeSpecifier typeSpecifier;
        TypeQualifier typeQualifier;
        var l = new ArrayList<DeclarationSpecifier>();
        loop:
        while (true) {
            if (tokens.isEmpty() || tokens.getFirst().equals(SEMICOLON)) break;
            if ((storageClass = parseStorageClassSpecifier(tokens)) != null) {
                l.add(storageClass);
            } else if ((typeQualifier = parseTypeQualifier(tokens)) != null) {
                l.add(typeQualifier);
            } else if ((typeSpecifier = parseTypeSpecifier(tokens,
                    typeAliases)) != null) {
                typeAliases = null; // we only want to recognize a typedef
                // name as a typedef name if it is the first typeSpecifier
                l.add(typeSpecifier);
            } else if (tokens.getFirst() instanceof
                    TokenWithValue(Token type, String value) && type==IDENTIFIER &&
                    (value.equals("inline") || value.equals("__inline") ||
                        value.equals("_NoReturn"))) {
                    tokens.removeFirst();
            } else if (tokens.getFirst() == GCC_ATTRIBUTE) {
                stripGccAttribute(tokens);
            }
            else break;
        }
        return l;
    }

    public static void skipRestrictAndConst(TokenList tokens) {
        while (true) {
            if (tokens.isEmpty()) return;
            if (tokens.getFirst() == RESTRICT) tokens.removeFirst();
            else if (tokens.getFirst() == CONST) {
                tokens.removeFirst();
            }
            else break;
        }
    }

    private static TypeQualifier parseTypeQualifier(TokenList tokens) {
        skipRestrictAndConst(tokens);
        TypeQualifier tq;
        switch (tokens.getFirst()) {
            case CONST -> tq = TypeQualifier.CONST;
            case VOLATILE -> tq = TypeQualifier.VOLATILE;
            case RESTRICT -> tq = TypeQualifier.RESTRICT;
            case ATOMIC -> tq = TypeQualifier.ATOMIC;
            default -> {
                return null;
            }
        }
        tokens.removeFirst();
        return tq;
    }


    private static TypeSpecifier parseTypeSpecifier(TokenList tokens,
                                                    ArrayList<Map<String,
                                                            Type>> typeAliases) {
        skipRestrictAndConst(tokens);
        TypeSpecifier ts;
        switch (tokens.getFirst()) {
            case VOID -> ts = PrimitiveTypeSpecifier.VOID;
            case FLOAT -> ts =PrimitiveTypeSpecifier.FLOAT;
            case BOOL -> ts = PrimitiveTypeSpecifier.BOOL;
            case CHAR -> ts = PrimitiveTypeSpecifier.CHAR;
            case SHORT -> ts = PrimitiveTypeSpecifier.SHORT;
            case INT -> ts = PrimitiveTypeSpecifier.INT;
            case LONG -> ts = PrimitiveTypeSpecifier.LONG;
            case DOUBLE -> ts = PrimitiveTypeSpecifier.DOUBLE;
            case SIGNED -> ts = PrimitiveTypeSpecifier.SIGNED;
            case UNSIGNED -> ts = PrimitiveTypeSpecifier.UNSIGNED;
            case ENUM -> {
                return parseEnumSpecifier(tokens, typeAliases);
            }
            case TYPEOF -> {
                return parseTypeofSpecifier(tokens, typeAliases);
            }
            default -> ts = null;
        }
        if (ts != null) tokens.removeFirst();
        else {
            ts = parseStructOrUnionSpecifier(tokens, typeAliases);
            if (ts == null && typeAliases != null)
                ts = parseTypedefName(tokens, typeAliases);
        }
        return ts;
    }

    private static TypeSpecifier parseTypeofSpecifier(TokenList tokens,
                                                      ArrayList<Map<String, Type>> typeAliases) {
        expect(TYPEOF, tokens);
        expect(OPEN_PAREN, tokens);
        if (isTypeSpecifier(tokens, 0, typeAliases)) {
            TypeName typeName = parseTypeName(tokens, typeAliases);
            expect(CLOSE_PAREN, tokens);
            return new TypeofT(typeNameToType(typeName, tokens, typeAliases));
        } else {
            Exp exp = parseExp(tokens, 0, true, typeAliases);
            expect(CLOSE_PAREN, tokens);
            return new Typeof(exp);
        }
    }

    private static EnumSpecifier parseEnumSpecifier(TokenList tokens, ArrayList<Map<String, Type>> typeAliases) {
        expect(ENUM, tokens);
// enum-specifier:
//    enum  identifier opt  enum-type-specifier opt  { enumerator-list }
//    enum  identifier opt  enum-type-specifier opt  { enumerator-list , }
//   enum identifier enum-type-specifier opt

// enumerator-list:
//   enumerator
//   enumerator-list , enumerator

// enumerator:
//   enumeration-constant
//   enumeration-constant  = constant-expression

// enum-type-specifier:
//    : specifier-qualifier-list
        String enumName=null;
        Token token = tokens.getFirst();
        Type type = null;
        if (token instanceof TokenWithValue(Token tokentype,
                                            String value) ) {
           tokens.removeFirst(); // enumName
           if (tokentype == IDENTIFIER) enumName = value;
           if (tokens.getFirst() == COLON) {
               tokens.removeFirst();
               TypeName typeName = parseTypeName(tokens, typeAliases);
               type = typeNameToType(typeName, tokens, typeAliases);
           }
        }
        if (enumName == null) {
            enumName = generatePseudoIdentifier();
        }
        if (tokens.getFirst() != OPEN_BRACE)
            return new EnumSpecifier(type, enumName, null);
        tokens.removeFirst();
        Constant current = IntInit.ZERO;
        ArrayList<Enumerator> enumerators = new ArrayList<>();
        Type effectiveType = Primitive.INT;
        var ret= new EnumSpecifier(type, enumName, enumerators);
        SemanticAnalysis.resolveEnumSpecifier(ret, null);
        while(true) {
            Token t = tokens.getFirst();
            if (t instanceof TokenWithValue(Token tokenType,
                                            String enumeratorName) && tokenType == IDENTIFIER) {
                tokens.removeFirst();
                Token next = tokens.getFirst();
                if (next == BECOMES) {
                    tokens.removeFirst();
                    current = parseConstExp(tokens, typeAliases);
//                    if (!current.type().isInteger()) {
//                        throw makeErr("Only integer types are allowed for enum constants", tokens);
//                    }
                }
                if (!(current instanceof ConstantExp) && current.type() != effectiveType){
                    effectiveType = SemanticAnalysis.getCommonType(current.type(), effectiveType);
                }
                enumerators.add(new Enumerator(enumeratorName, current));
                current = current.apply(POST_INCREMENT);
                t = tokens.getFirst();
                if (t == CLOSE_BRACE) {
                    tokens.removeFirst();
                    break;
                }
                if (t == COMMA) {
                    tokens.removeFirst();
                } else {
                    throw makeErr("Expected ',' or '}' but found "+t, tokens);
                }

            } else if (t == CLOSE_BRACE) {
                tokens.removeFirst();
                break;
            }
            else {
                throw makeErr("Expected IDENTIFER but found " + t, tokens);
            }

        }
        //MR-TODO move this to type checker
//        if (type != null) effectiveType = type;
//        for (int i = 0; i < enumerators.size(); i++){
//            var e = enumerators.get(i);
//            enumerators.set(i,new Enumerator(e.name(), convertConst(e.value(), effectiveType)));
//        }
        return ret;
    }

    private static TypeSpecifier parseTypedefName(TokenList tokens,
                                                  ArrayList<Map<String, Type>> typeAliases) {
        var first = tokens.getFirst();
        if (first instanceof TokenWithValue(Token type,
                                            String value) && type == IDENTIFIER) {

            if (Parser.findTypeByName(typeAliases, value) != null) {
                tokens.removeFirst();
                return new TypedefName(value);
            }
        }
        return null;

    }

    private static StructOrUnionSpecifier parseStructOrUnionSpecifier(
            TokenList tokens, ArrayList<Map<String, Type>> typeAliases) {
        boolean isUnion = false;
        switch (tokens.getFirst()) {
            case STRUCT -> {
                isUnion = false;
            }
            case UNION -> {
                isUnion = true;
            }
            default -> {
                return null;
            }
        }

        String tag = null;
        ArrayList<MemberDeclaration> members = null;

        tokens.removeFirst(); //struct-or-union
        Token first = tokens.getFirst();

        if (first instanceof TokenWithValue(Token type,
                                            String value) && type == IDENTIFIER) {
            tokens.removeFirst();
            first = tokens.getFirst();
            tag = value;
        }
        if (first.equals(OPEN_BRACE)) {
            tokens.removeFirst();
            members = new ArrayList<>();
                List<DeclarationList> dl = new ArrayList<>();
                while(tokens.getFirst()!=CLOSE_BRACE) {
                    DeclarationList x = parseDeclarationList(tokens, false, typeAliases, true);
                    for(Declaration y:x.list()){
                        switch(y){
                            case VarDecl(Var name,
                                         Initializer init, Type t,
                                         StorageClass storageClass,
                                         StructOrUnionSpecifier structOrUnionSpecifier,
                                         Constant bitFieldWidth) -> {
                                                        if (t instanceof FunType)
                            fail("error: member declaration can't be function");
                                members.add(new MemberDeclaration(t, name.name(), structOrUnionSpecifier, bitFieldWidth));
                            }
                            case StructOrUnionSpecifier sous -> {
                                // the only way we can get one of these is if
                                // we have an anonymous inner struct or union
                                members.add(new MemberDeclaration(new Structure(sous.isUnion(), sous.tag(), TYPE_TABLE.get(sous.tag())), null, sous, null));
                            }
                            default->{
                                throw makeErr("Todo", tokens);
                            }
                        }


                    }
                }
                tokens.removeFirst(); // CLOSE_BRACE
//                List<DeclarationSpecifier> specifiers =
//                        parseDeclarationSpecifiers(tokens, typeAliases);
//                TypeAndStorageClass typeAndStorageClass =
//                        parseTypeAndStorageClass(specifiers, typeAliases, tokens);
//                if (typeAndStorageClass.storageClass() != null)
//                    fail("error: storage class specified for struct member");
//                Declarator paramDeclarator =
//                        parseDeclarator(tokens, typeAliases);
//                NameDeclTypeParams nameDeclTypeParams =
//                        processDeclarator(paramDeclarator, typeAndStorageClass.type(), typeAliases, tokens);


        } else if (tag == null) {
            throw new Err("Expected either union identifer or '{', found: " + tokens.removeFirst());
        }
        //MR-TODO something better
        boolean anonymous = false;
        if (tag == null) {
            anonymous = true;
            tag = generatePseudoIdentifier();
        }
        return new StructOrUnionSpecifier(isUnion, tag, members, anonymous);
    }

    /* So that things (e.g. structs, unions, enums) can have a name even when in the code they are not given one*/
    private static String generatePseudoIdentifier() {
        return makeTemporary("tag.");
    }

    private static StorageClass parseStorageClassSpecifier(TokenList tokens) {
        skipRestrictAndConst(tokens);
        StorageClass sc;
        switch (tokens.getFirst()) {
            case TYPEDEF -> sc = StorageClass.TYPEDEF;
            case EXTERN -> sc = StorageClass.EXTERN;
            case STATIC -> sc = StorageClass.STATIC;
            case REGISTER -> sc = StorageClass.REGISTER;
            default -> {
                return null;
            }
        }
        tokens.removeFirst();
        return sc;

    }

    private static void addTypedefToCurrentScope(
            ArrayList<Map<String, Type>> stack, String name, Type type) {
        stack.getLast().put(name, type);
    }

    private static Type findTypeByName(ArrayList<Map<String, Type>> stack,
                                       String name) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Type t = stack.get(i).get(name);
            if (t != null) return t;
        }
        return null;
    }

    public static Token expect(Token expected, TokenList tokens) {
        Token token = null;
        while(!tokens.isEmpty()) {
            token = tokens.getFirst();
            if (token == GCC_ATTRIBUTE) {
                stripGccAttribute(tokens);
            } else if (token == ASM) {
                stripAsm(tokens);
            } else if (token == CONST) {
                tokens.removeFirst();
            } else {
                if (expected == token.type()) {
                    tokens.removeFirst();
                    return token;
                }
                break;
            }
        }
        throw makeErr("Expected " + expected + ", " + "got " + token,
                tokens);

    }

    public static void stripGccAttribute(TokenList tokens) {
        int cursorAtStart = tokens.cursor;
        tokens.removeFirst();

        if (tokens.removeFirst() == OPEN_PAREN && tokens.removeFirst() == OPEN_PAREN) {
            int openCount = 2;
            while (!tokens.isEmpty()) {
                Token t = tokens.removeFirst();
                if (t == OPEN_PAREN) openCount++;
                if (t == CLOSE_PAREN) {
                    openCount--;
                    if (openCount == 0) return;
                }
            }
            tokens.cursor = cursorAtStart;
            throw makeErr("Error parsing __attribute__. I never found closing \"))\""
                    , tokens);
        } else {
            tokens.cursor = cursorAtStart;
            throw makeErr("Error: __attribute__ should be followed by \"((\""
                    , tokens);
        }
    }

    private static void stripAsm(TokenList tokens) {
        int cursorAtStart = tokens.cursor;
        tokens.removeFirst();

        if (tokens.removeFirst() == OPEN_PAREN ) {
            int openCount = 1;
            while (!tokens.isEmpty()) {
                Token t = tokens.removeFirst();
                if (t == OPEN_PAREN) openCount++;
                if (t == CLOSE_PAREN) {
                    openCount--;
                    if (openCount == 0) return;
                }
            }
            tokens.cursor = cursorAtStart;
            throw makeErr("Error parsing __asm__. I never found closing \")\""
                    , tokens);
        } else {
            tokens.cursor = cursorAtStart;
            throw makeErr("Error: __attribute__ should be followed by \"(\""
                    , tokens);
        }
    }

    static Statement parseStatement(TokenList tokens, List<String> labels,
                                    Switch enclosingSwitch,
                                    ArrayList<Map<String, Type>> typeAliases) {
        while (tokens.getFirst() == GCC_ATTRIBUTE) {
            stripGccAttribute(tokens);
        }

        Token token = tokens.getFirst();
        Token tokenType = token.type();
        switch (token) {
            case TokenType.RETURN -> {
                tokens.removeFirst();
                if (tokens.getFirst() == SEMICOLON) return new Return(null);
                Exp exp = parseExp(tokens, 0, true, typeAliases);
                expect(SEMICOLON, tokens);
                return new Return(exp);
            }
            case TokenType.SEMICOLON -> {
                tokens.removeFirst();
                return NULL_STATEMENT;
            }
            case TokenType.IF -> {
                tokens.removeFirst();
                expect(OPEN_PAREN, tokens);
                Exp condition = parseExp(tokens, 0, true, typeAliases);
                expect(CLOSE_PAREN, tokens);
                Statement ifTrue =
                        parseStatement(tokens, labels, enclosingSwitch, typeAliases);
                Statement ifFalse = switch (tokens.getFirst()) {
                    case ELSE -> {
                        tokens.removeFirst();
                        yield parseStatement(tokens, labels, enclosingSwitch, typeAliases);
                    }
                    default -> null;
                };
                return new If(condition, ifTrue, ifFalse);
            }
            case TokenType.OPEN_BRACE -> {
                return parseBlock(tokens, labels, enclosingSwitch, typeAliases);
            }
            case TokenType.WHILE -> {
                return parseWhile(tokens, labels, enclosingSwitch, typeAliases);
            }
            case TokenType.DO -> {
                return parseDoWhile(tokens, labels, enclosingSwitch, typeAliases);
            }
            case TokenType.FOR -> {
                tokens.removeFirst();
                return parseFor(tokens, labels, enclosingSwitch, typeAliases);
            }
            case TokenType.SWITCH -> {
                tokens.removeFirst();
                return parseSwitch(tokens, labels, typeAliases);
            }
            case TokenType.BUILTIN_C23_VA_START -> {
                tokens.removeFirst();
                return parseBuiltinC23VaStart(tokens, labels, typeAliases);
            }
            case TokenType.BUILTIN_VA_END -> {
                tokens.removeFirst();
                return parseBuiltinVaEnd(tokens, labels, typeAliases);
            }
            case TokenType.BREAK -> {
                tokens.removeFirst();
                expect(SEMICOLON, tokens);
                return new Break();
            }
            case TokenType.CONTINUE -> {
                tokens.removeFirst();
                expect(SEMICOLON, tokens);
                return new Continue();
            }
            case TokenType.GOTO -> {
                tokens.removeFirst();
                var label = expectIdentifier(tokens);
                expect(SEMICOLON, tokens);
                return new Goto(label);
            }
             case TokenType.CASE -> {
                tokens.removeFirst(); // CASE
                Constant<?> c = parseConstExp(tokens, typeAliases);
                expect(COLON, tokens);
                return new CaseStatement(enclosingSwitch, c,
                        parseStatement(tokens, labels, enclosingSwitch,
                                typeAliases));
            }
            case TokenType.DEFAULT -> {
                tokens.removeFirst(); // CASE
                expect(COLON, tokens);
                return new CaseStatement(enclosingSwitch, null,
                        parseStatement(tokens, labels, enclosingSwitch,
                                typeAliases));
            }
            default -> {}
        }

        if (tokenType == IDENTIFIER && tokens.get(1) == COLON) {
            tokens.removeFirst(); // LABEL
            tokens.removeFirst(); // has to be COLON because of how LABEL
            // regex is defined
            TokenWithValue twv = (TokenWithValue) token;

            String label = twv.value();
            if (labels.contains(label)) {
                throw new Err("duplicate label: " + label);
            }
            labels.add(label);
            return new LabelledStatement(".L" + label, parseStatement(tokens,
                    labels, enclosingSwitch, typeAliases));
        }
        Exp exp = parseExp(tokens, 0, true, typeAliases);
        if (tokens.getFirst() == OPEN_PAREN) {
            throw makeErr("function application is not supported in this kind of situation yet", tokens);
        }
        expect(SEMICOLON, tokens);
        return exp;
    }


    private static BuiltinC23VaStart parseBuiltinC23VaStart(TokenList tokens,
                                                            List<String> labels,
                                                            ArrayList<Map<String, Type>> typeAliases) {
        // we can either have (args, paramN)
        // or (ap) - ap will be the name of the arg in the c code
        // processing the va list. paramN is the name of the last parameter
        // before the varags
        // This compiler doesn't do anything with paramN.
        expect(OPEN_PAREN, tokens);
        String ap = expectIdentifier(tokens);
        if (tokens.getFirst() == COMMA) {
            tokens.removeFirst();
            expectIdentifier(tokens); // paramN - ignore
        }
        expect(CLOSE_PAREN, tokens);
        expect(SEMICOLON, tokens);
        return new BuiltinC23VaStart(new Var(ap, null));
    }

    private static BuiltinVaEnd parseBuiltinVaEnd(TokenList tokens,
                                                  List<String> labels,
                                                  ArrayList<Map<String, Type>> typeAliases) {
        expect(OPEN_PAREN, tokens);
        String ap = expectIdentifier(tokens);
        expect(CLOSE_PAREN, tokens);
        return new BuiltinVaEnd(new Var(ap, null));
    }

    private static DoWhile parseDoWhile(TokenList tokens, List<String> labels,
                                        Switch enclosingSwitch,
                                        ArrayList<Map<String, Type>> typeAliases) {
        expect(DO, tokens);
        Statement body = parseStatement(tokens, labels, enclosingSwitch,
                typeAliases);
        expect(WHILE, tokens);
        expect(OPEN_PAREN, tokens);
        Exp condition = parseExp(tokens, 0, true, typeAliases);
        expect(CLOSE_PAREN, tokens);
        expect(SEMICOLON, tokens);
        return new DoWhile(body, condition, null);
    }



    private static AbstractDeclarator parseAbstractDeclarator(
            TokenList tokens, ArrayList<Map<String, Type>> typeAliases) {
        // <abstract-declarator> ::= "*" [ <abstract-declarator> ]
        //                         | <direct-abstract-declarator>
        if (tokens.getFirst() == CONST) {
            tokens.removeFirst();
        }
        if (tokens.getFirst() == OPEN_PAREN || tokens.getFirst() == OPEN_BRACKET){
            var d= parseDirectAbstractDeclarator(tokens, typeAliases);
            if (tokens.getFirst() == OPEN_PAREN) {
                tokens.removeFirst();
                var args=parseParameterTypeList(tokens, typeAliases);
                expect(CLOSE_PAREN, tokens);
                return new FunctionAbstractDeclarator(d, args);
            }
            return d;
        }

        if (tokens.getFirst() == IMUL) {
            tokens.removeFirst();
            return new AbstractPointer(parseAbstractDeclarator(tokens, typeAliases));
        }

        return new AbstractBase();
    }

    private static AbstractDeclarator parseDirectAbstractDeclarator(
            TokenList tokens, ArrayList<Map<String, Type>> typeAliases) {
        // <direct-abstract-declarator> ::= "(" <abstract-declarator> ")" {
        // "[" <const> "]" }
        //                                | { "[" <const> "]" }+
        AbstractDeclarator d = null;
        if (tokens.getFirst() == OPEN_PAREN) {
            tokens.removeFirst();
            d = parseAbstractDeclarator(tokens, typeAliases);
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


    private static String debugTokens(TokenList tokens) {
        return tokens.stream().map(Object::toString).collect(Collectors.joining(" "));
    }

    public static Constant parseConstExp(TokenList tokens,
                                          ArrayList<Map<String, Type>> typeAliases) {
        Exp e = parseExp(tokens, 0, false, typeAliases);
        //e = typeCheckAndConvert(e);
        if (e==null) return null;
        if (e instanceof Constant c) return c;
        return new ConstantExp(e);
    }


    private static NameDeclTypeParams processDeclarator(DeclaratorOrAbstractDeclarator declarator,
                                                        Type baseType,
                                                        ArrayList<Map<String, Type>> typeAliases,
                                                        TokenList tokens) {
        switch (declarator) {
            case null: {
                return new NameDeclTypeParams(null, baseType, new ArrayList<>());
            }
            case Ident(String name):
            {
                return new NameDeclTypeParams(name, baseType, new ArrayList<>());
            }
            case PointerDeclarator(Declarator d):
                   return  processDeclarator(d, new Pointer(baseType), typeAliases, tokens);

            case ArrayDeclarator(Declarator inner, Exp size): {
                Constant sizeC = size==null ? null:size instanceof Constant c ? c : new ConstantExp(size);
                Array derivedType = new Array(baseType, sizeC);
                return processDeclarator(inner, derivedType, typeAliases, tokens);
            }

            case FunctionDeclarator(DeclaratorOrAbstractDeclarator d, ParameterTypeList parameterTypeList) :
            {
                ArrayList<ParameterDeclaration> parameterDeclarations = parameterTypeList.parameterDeclarations();
                boolean varargs = parameterTypeList.varArgs();
                ArrayList<String> paramNames = new ArrayList<>();
                List<Type> paramTypes = new ArrayList<>();
                for(ParameterDeclaration pi:parameterDeclarations){
                    TypeAndStorageClass typeAndStorageClass =
                            parseTypeAndStorageClass(pi.declarationSpecifiers(), typeAliases, tokens);
                    NameDeclTypeParams decl =
                            processDeclarator((Declarator) pi.declarator(), typeAndStorageClass.type(), typeAliases, tokens);

                    String name = decl.name();
                    Type type = decl.type();
                    if (type == Primitive.VOID) continue;
                    if (type instanceof FunType)
                        throw new Err("function pointers are not supported");
                    paramNames.add(name);
                    paramTypes.add(type);
                }
                FunType derivedType =
                        new FunType(paramTypes, baseType, varargs);
                if (d instanceof Ident(String name)) {
                    return new NameDeclTypeParams(name, derivedType, paramNames);
                } else if (d instanceof PointerDeclarator(Declarator decl)) {
                    return processDeclarator(decl, new Pointer(derivedType), typeAliases, tokens);
                }  else if (d instanceof AbstractPointer(AbstractDeclarator inner)) {
                    return processDeclarator(inner, new Pointer(derivedType), typeAliases, tokens);
                }else {
                    throw new Todo();
                }
            }
            case AbstractDeclarator abstractDeclarator: {
                Type derivedType = processAbstractDeclarator(abstractDeclarator, baseType,
                        typeAliases,
                        tokens);
                return new NameDeclTypeParams(null, derivedType, new ArrayList<>());
            }
        }

    }


    public static Declarator parseDeclarator(TokenList tokens,
                                             ArrayList<Map<String, Type>> typeAliases){
        Parser.skipRestrictAndConst(tokens);
        Token t = tokens.getFirst();
        if (t == IMUL){
            tokens.removeFirst();
            return new PointerDeclarator(parseDeclarator(tokens, typeAliases));
        }

        return parseDirectDeclarator(tokens, typeAliases);

    }

    /** it returns declarator because of the "(" declarator ")" rule*/
    private static Declarator parseDirectDeclarator(TokenList tokens,
                                                    ArrayList<Map<String, Type>> typeAliases) {
//        direct-declarator:
//            identifier attribute-specifier-sequenceopt
//            "(" declarator ")"
//            array-declarator attribute-specifier-sequenceopt
//            function-declarator attribute-specifier-sequenceopt

        Token t  = tokens.getFirst();
        Declarator d = switch (t) {
            case TokenWithValue(Token type,
                                String identifier) -> {
                if (type == IDENTIFIER) {
                    tokens.removeFirst();
                    yield new Ident(identifier);
                }
                yield null;

            }
            case OPEN_PAREN -> {
                tokens.removeFirst();
                var r = parseDeclarator(tokens, typeAliases);
                Parser.expect(CLOSE_PAREN, tokens);
                yield r;
            }
            default -> {
                yield null;
            }
        };
        while(true) {
            t = tokens.getFirst();
            if (t == GCC_ATTRIBUTE) {
                Parser.stripGccAttribute(tokens);
            } else if (t == OPEN_BRACKET) {
                tokens.removeFirst();
                Constant arraySize=parseArraySize(tokens, typeAliases);
                Parser.expect(CLOSE_BRACKET, tokens);
                d = new ArrayDeclarator(d, arraySize);
            } else if (t == OPEN_PAREN) {
                tokens.removeFirst();
                ParameterTypeList parameterTypeList = parseParameterTypeList(tokens, typeAliases);
                Parser.expect(CLOSE_PAREN, tokens);
                d = new FunctionDeclarator(d, parameterTypeList);
            } else break;
        }
        return d;
    }

    private static ParameterTypeList parseParameterTypeList(TokenList tokens, ArrayList<Map<String, Type>> typeAliases) {

        ArrayList<ParameterDeclaration> parameterDeclarations = new ArrayList<>();
        boolean allowComma=false;
        while(true){
            Token t = tokens.getFirst();

            switch(t){
                case CLOSE_PAREN -> {
                    return new ParameterTypeList(parameterDeclarations, false);
                }
                case ELLIPSIS -> {
                    tokens.removeFirst();
                    return new ParameterTypeList(parameterDeclarations, true);
                } case COMMA -> {
                    if (allowComma) {
                        tokens.removeFirst();
                        allowComma = false;
                    } else throw makeErr("Unexpected comma while parsing parameter list", tokens);
                }
                default -> {
                    var pd = parseParameterDeclaration(tokens,typeAliases);
                    allowComma = true;
                    parameterDeclarations.add(pd);
                }
            }
        }


    }

    private static ParameterDeclaration parseParameterDeclaration(TokenList tokens, ArrayList<Map<String, Type>> typeAliases) {
//        parameter-declaration:
//        attribute-specifier-sequenceopt declaration-specifiers declarator
//        attribute-specifier-sequenceopt declaration-specifiers abstract-declaratoropt
        int cursorAtStart = tokens.cursor;
        List<DeclarationSpecifier> s = Parser.parseDeclarationSpecifiers(tokens, typeAliases);
        if (s.isEmpty()) {
            tokens.cursor = cursorAtStart;
            throw makeErr("Could not find declaraton specifier while parsing parameter declaration", tokens);
        }
        DeclaratorOrAbstractDeclarator d= parseDeclarator(tokens, typeAliases);
        return new ParameterDeclaration(s, d);
    }

    private static Constant parseArraySize(TokenList tokens,
                                           ArrayList<Map<String, Type>> typeAliases) {
        Constant c;
        if (tokens.getFirst() == CLOSE_BRACKET) {
            c = null;
        }
        else {
            c = parseConstExp(tokens, typeAliases);
//            if (c.isFloatingPointType()) {
//                throw new Err("illegal non-integer array size");
//            }
//            if (c.toLong() < 0L)
//                throw new Err("illegal negative array size");
        }
        return c;
    }

    private static Initializer parseInitializer(TokenList tokens,
                                                ArrayList<Map<String, Type>> typeAliases) {
        Token token = tokens.getFirst();
        if (token == OPEN_BRACE) {
            tokens.removeFirst();
            boolean done = false;
            ArrayList<Initializer> inits = new ArrayList<>();

            while (!done) {
                Initializer init = parseInitializer(tokens, typeAliases);
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
                            throw makeErr("Unexpected " +
                                    "value: " + tokens.removeFirst(), tokens);
                };
            }
            return new CompoundInit(inits, null);

        }
        return new SingleInit(parseExp(tokens, 0, false, typeAliases), null);

    }

    public static Program parseProgram(TokenList tokens,
                                       ArrayList<Declaration> declarations) {
        ArrayList<Map<String, Type>> typeAliases = new ArrayList<>();
        // built initial typeAliases. Will contain __builtin_va_list
        typeAliases.add(new HashMap<>());
        SymbolTableEntry e = SYMBOL_TABLE.get("__builtin_va_list");
        if (e != null) { // before Mcc.mcc loading the users c file, it loads
            // some built in source which is parsed and validated to
            // create __builtin_va_list. So during that parsing e will be null.
            addTypedefToCurrentScope(typeAliases, "__builtin_va_list",
                    e.type());

            addTypedefToCurrentScope(typeAliases, "_Float128", Primitive.DOUBLE);
        }
        DeclarationList declarationList;
        while (true) {
            while ((declarationList =
                    parseDeclarationList(tokens, true, typeAliases, false)) != null) {
                declarations.addAll(declarationList.list());
            }
            if (!tokens.isEmpty() && tokens.getFirst() == SEMICOLON) {
                tokens.removeFirst();
            } else {
                break;
            }
        }
        return new Program(declarations);
    }

    private static DeclarationList parseDeclarationList(TokenList tokens,
                                                        boolean throwExceptionIfNoType,
                                                        ArrayList<Map<String,
                                                                Type>> typeAliases,
    boolean isMemberDeclarationList) {
        // declaration:
        //        declaration-specifiers init-declarator-list(opt) ";"

        // init-declarator-list:
        //         init-declarator
        //         init-declarator-list "," init-declarator
        // init-declarator:
        //         declarator
        //         declarator "=" initializer
        Constant bitFieldWidth=null;
        List<DeclarationSpecifier> specifiers =
                parseDeclarationSpecifiers(tokens, typeAliases);
        TypeAndStorageClass typeAndStorageClass =
                parseTypeAndStorageClass(specifiers, typeAliases, tokens);
        if (typeAndStorageClass == null) return null;
        if (tokens.getFirst() == SEMICOLON) {
            tokens.removeFirst();

            for (var x : specifiers) {
                if (x instanceof StructOrUnionSpecifier su)
                    return new DeclarationList(Collections.singletonList(su));
                if (x instanceof EnumSpecifier es) {
                    return new DeclarationList(Collections.singletonList(es));
                }
            }

            throw new Todo();
        }
        List<Declaration> l = new ArrayList<>();

        boolean first = false;
        out:
        while (!tokens.isEmpty()) {

            Token token = tokens.getFirst();
            if (token == GCC_ATTRIBUTE){
                stripGccAttribute(tokens);
            } else if (token.equals(SEMICOLON)) {
                tokens.removeFirst();
                break out;
            } else if (token.equals(COMMA)) {
                if (first) throw new Err("unexpected comma");
                tokens.removeFirst();
            } else if (token.equals(OPEN_PAREN) || token.equals(IMUL) ||
                    token instanceof TokenWithValue(Token type, String _) &&
                            type == IDENTIFIER) {
                Declaration decl;
                Declarator declarator =
                        parseDeclarator(tokens, typeAliases);
                NameDeclTypeParams nameDeclTypeParams = processDeclarator(declarator,
                        typeAndStorageClass.type(), typeAliases, tokens);
                String name = nameDeclTypeParams.name();
                Type type = nameDeclTypeParams.type();
                ArrayList<String> paramNames = nameDeclTypeParams.paramNames();
                if (type instanceof FunType funType) {
                    decl =
                            parseRestOfFunction(paramNames, tokens, name, typeAndStorageClass.storageClass(), typeAliases, funType);
                } else {
                    Token token1 = tokens.getFirst();
                    Initializer init=null;
                    switch (token1.type()) {
                        case BECOMES:
                            tokens.removeFirst();
                            init = parseInitializer(tokens, typeAliases);
                            break;
                        case SEMICOLON:
                            init = null;
                            break;
                        case COMMA:
                            tokens.removeFirst();
                            break;
                            //throw new Todo();
                        case COLON:
                            if (isMemberDeclarationList) {
                                tokens.removeFirst();
                                bitFieldWidth =
                                        parseConst(tokens, true);
                                break;
                            }
                        default:
                            throw makeErr(
                                    "Expected ; or =, got " + token1, tokens);
                    }

                    decl =
                            new VarDecl(new Var(name, type), init, type, typeAndStorageClass.storageClass(), typeAndStorageClass.structOrUnionSpecifier(),bitFieldWidth);
                }
                if (typeAndStorageClass.storageClass() ==
                        StorageClass.TYPEDEF) {
                    addTypedefToCurrentScope(typeAliases, name, type);
                }

                l.add(decl);
                if (decl instanceof Function) break out;
            } else if (token == COLON && isMemberDeclarationList) {
                    tokens.removeFirst();
                    bitFieldWidth =
                            parseConst(tokens, true);

                VarDecl decl =
                        new VarDecl(new Var(null, null), null, typeAndStorageClass.type(), typeAndStorageClass.storageClass(), typeAndStorageClass.structOrUnionSpecifier(), bitFieldWidth);
                l.add(decl);
            }
            else {
                break out;
            }
        }
        if (l.isEmpty()) {
            throw makeErr("Expected identifier or (", tokens);
        }
        return new DeclarationList(l);
    }


    private static TypeAndStorageClass parseTypeAndStorageClass(
            List<DeclarationSpecifier> specifiers,
            ArrayList<Map<String, Type>> typeAliases,
            TokenList tokens) {
        if (specifiers.isEmpty()) return null;
        StorageClass storageClass = null;
        int signedness = 0;
        boolean foundSolo = false;
        int intCount=0;
        StructOrUnionSpecifier structOrUnionSpecifier = null;
        Type type = null;
        EnumSet<TypeQualifier> typeQualifiers =
                EnumSet.noneOf(TypeQualifier.class);

        for (DeclarationSpecifier x : specifiers) {
            switch (x) {
                case StorageClass s -> {
                    if (storageClass != null)
                        throw new Err("Found second class " + s + ". Already "
                                + "saw " + storageClass);
                    storageClass = s;
                }
                case PrimitiveTypeSpecifier s -> {
//                    if (type != null) {
//                        fail("can't combine " + type + " with other " +
//                                "specifiers");
//                    }
                    switch (s) {
                        case DOUBLE -> {
                            if ((type != null && type != Primitive.LONG) | signedness != 0) {
                                fail("can't combine double with other type " +
                                        "specifiers");
                            }
                            type = Primitive.DOUBLE;
                        }
                        case FLOAT -> {
                            if (type != null | signedness!=0) {
                                fail("can't combine float with other type specifiers");
                            }
                            type=Primitive.DOUBLE;
                            foundSolo=true;
                        }
                        case BOOL -> {
                            if (type != null | signedness!=0) {
                                fail("can't combine float with other type specifiers");
                            }
                            type = Primitive.BOOL;
                            foundSolo = true;
                        }
                        case INT -> {
                            if (intCount != 0 || type == Primitive.CHAR ||
                                    foundSolo) fail("invalid type specifier");
                            else if (type == null) {
                                type = Primitive.INT;
                                intCount++;
                            }
                        }
                        case CHAR -> {
                            if (type!=null || foundSolo)
                                fail("invalid type specifier");
                            else  {
                                type=Primitive.CHAR;
                            }
                        }
                        case SHORT -> {
                            if (foundSolo || (type!=null && type!=Primitive.INT))
                                fail("invalid type specifier");
                            else type=Primitive.SHORT;
                        }
                        case LONG -> {
                            // Note this compiler treats long long the same as long
                            if (foundSolo || (type!=null && type!=Primitive.INT && type!=Primitive.LONG))
                                fail("invalid type specifier");
                            else type=Primitive.LONG;
                        }
                        case SIGNED -> {
                            if (foundSolo || signedness!=0)
                                fail("invalid type specifier");
                            else signedness=1;
                        }
                        case UNSIGNED -> {
                            if (foundSolo || signedness!=0)
                                fail("invalid type specifier");
                            else signedness=-1;
                        }
                        case VOID -> {
                            if (foundSolo || signedness!=0)
                                fail("Can't combine void with other type specifiers");
                            else type=Primitive.VOID;
                        }
                        default ->
                                throw new Todo("This compiler doesn't yet " + "support the type: " + s);
                    }

                }

                case StructOrUnionSpecifier sous -> {
                    if (foundSolo || signedness!=0)
                        fail("can't combine struct or union with other type " + "specifiers");
                    type = new Structure(sous.isUnion(),
                            sous.tag(), null);
                    structOrUnionSpecifier = sous;
                }
                case TypedefName(String name) -> {
                    if (foundSolo || signedness!=0)
                        fail("can't combine typedef name with other type " + "specifiers");
                    type = findTypeByName(typeAliases, name);
                }
                case TypeQualifier typeQualifier -> {
                    typeQualifiers.add(typeQualifier);
                }
                case EnumSpecifier es -> {
                    type=es.type();
                    if (type == null) type=Primitive.INT;
                }
                //default -> throw new Todo();
                case Typeof typeof -> {type=typeof;}
                case TypeofT typeofT -> {type=typeofT;}
            }
        }

        if (type==null && signedness!=0){
            type=Primitive.INT;
        }

        if (signedness == -1) {
            if (type == Primitive.LONG) type = Primitive.ULONG;
            else if (type == Primitive.SHORT) type = Primitive.USHORT;
            else if (type == Primitive.CHAR) type = Primitive.UCHAR;
            else if (type == Primitive.INT) type = Primitive.UINT;
        } else if (signedness==1) {
           if (type == Primitive.CHAR) type = Primitive.SCHAR;
        }
        if (type == null) {
            throw makeErr("TODO", tokens);
        }


        return new TypeAndStorageClass(type, storageClass, null,
                typeQualifiers, structOrUnionSpecifier);

    }

    private static boolean isTypeSpecifier(TokenList tokens, int start,
                                           ArrayList<Map<String, Type>> typeAliases) {
        while (CONST == tokens.get(start) || VOLATILE == tokens.get(start)) {
            start++;
        }
        Token first = tokens.get(start);

        if (BOOL == first || CHAR == first || INT == first || SHORT == first || LONG == first ||
                UNSIGNED == first || SIGNED == first || DOUBLE == first ||
                FLOAT == first || VOID == first || STRUCT == first ||
                UNION == first || ENUM == first || TYPEOF == first) return true;
        return typeAliases != null &&
                first instanceof TokenWithValue(Token type, String name) &&
                type == IDENTIFIER && findTypeByName(typeAliases, name) != null;
    }

    private static Function parseRestOfFunction(ArrayList<String> paramNames,
                                                TokenList tokens,
                                                String functionName,
                                                StorageClass storageClass,
                                                ArrayList<Map<String, Type>> typeAliases,
                                                FunType funType) {

        List<Type> paramTypes = funType.params();

        List<Var> params = new ArrayList<>();
        for (int i = 0; i < paramNames.size(); i++) {
            params.add(new Var(paramNames.get(i), paramTypes.get(i)));
        }

        Block block;
        if (tokens.getFirst() == OPEN_BRACE) {
            block = parseBlock(tokens, new ArrayList<>(), null, typeAliases);
        } else {
            expect(SEMICOLON, tokens);
            block = null;
        }
        return new Function(functionName, params, block, funType,
                storageClass, false, false);
    }

    private static String expectIdentifier(TokenList tokens) {
        Token token = tokens.removeFirst();

        if (token instanceof TokenWithValue(Token type,
                                            String value) && type == IDENTIFIER) {
            return value;
        }
        throw makeErr("Expected IDENTIFIER got " + token, tokens);
    }

    private static Block parseBlock(TokenList tokens, List<String> labels,
                                    Switch enclosingSwitch,
                                    ArrayList<Map<String, Type>> typeAliases) {
        expect(OPEN_BRACE, tokens);

        ArrayList<BlockItem> blockItems = new ArrayList<>();

        // new scope for typedefs
        typeAliases.add(new HashMap<>());

        while (tokens.getFirst() != CLOSE_BRACE) {
            // parse block-item
            while(tokens.getFirst()==GCC_ATTRIBUTE){
                stripGccAttribute(tokens);
            }
            Token t = tokens.getFirst();
            if (t == EXTERN || t == STATIC || t == TYPEDEF || t == REGISTER
                    || t==VOLATILE || isTypeSpecifier(tokens, 0, typeAliases)) {
                blockItems.addAll(parseDeclarationList(tokens, false,
                        typeAliases, false).list());
            } else {
                blockItems.add(parseStatement(tokens, labels, enclosingSwitch
                        , typeAliases));
            }


        }

        // end of scope
        typeAliases.removeLast();
        tokens.removeFirst();
        return new Block(blockItems);
    }

    private static While parseWhile(TokenList tokens, List<String> labels,
                                    Switch enclosingSwitch,
                                    ArrayList<Map<String, Type>> typeAliases) {
        expect(WHILE, tokens);
        expect(OPEN_PAREN, tokens);
        Exp condition = parseExp(tokens, 0, true, typeAliases);
        expect(CLOSE_PAREN, tokens);
        Statement body = parseStatement(tokens, labels, enclosingSwitch,
                typeAliases);
        return new While(condition, body, null);
    }

    private static Constant parseConst(String value, Type type, boolean hex) {
        int base = hex ? 16 : 10;
        if (type == Primitive.DOUBLE)
            return new DoubleInit(Double.parseDouble(value));
        if (type == Primitive.FLOAT)
            return new FloatInit(Float.parseFloat(value));
        if (type.isSigned() && base == 10) {
            long v = Long.parseLong(value, base);
            if (v < 1L << 31 && type == Primitive.INT)
                return new IntInit((int) v);
            else return new LongInit(v);
        }
        long v = Long.parseUnsignedLong(value, base);
        if (Long.compareUnsigned(v, 0xffff_ffffL) <= 0 && (type == Primitive.INT || type == Primitive.UINT))
            return new UIntInit((int) v);
        else return new ULongInit(v);
    }

    private static Type processAbstractDeclarator(
            AbstractDeclarator abstractDeclarator, Type type,
            ArrayList<Map<String, Type>> typeAliases,
            TokenList tokens) {
        return switch (abstractDeclarator) {
            case AbstractBase _ -> type;
            case AbstractPointer(AbstractDeclarator declarator) ->
                    processAbstractDeclarator(declarator, new Pointer(type) ,typeAliases ,tokens);
//            case DirectAbstractDeclarator(AbstractDeclarator declarator) ->
//                    processAbstractDeclarator(declarator, type);
            case AbstractArrayDeclarator(AbstractDeclarator declarator,
                                         Constant arraySize) ->
                    processAbstractDeclarator(declarator, new Array(type,
                            arraySize) ,typeAliases ,tokens);
            case FunctionAbstractDeclarator(AbstractDeclarator d,
                                            ParameterTypeList args) -> {
               var xxx= processDeclarator(new FunctionDeclarator(d, args), type ,typeAliases ,tokens);
               yield xxx.type();
            }
        };
    }


    private static Exp parseUnaryExp(TokenList tokens,
                                     ArrayList<Map<String, Type>> typeAliases) {
        // <unary-exp> ::= <unop> <unary-exp>
        //               | "sizeof" <unary-exp>
        //               | "sizeof" "(" <type-name> ")"
        //               | <postfix-exp>

        return switch (tokens.getFirst()) {
            case INCREMENT -> {
                tokens.removeFirst();
                var exp = parseCastExp(tokens, typeAliases);
                yield new CompoundAssignment(ADD_EQ, exp, IntInit.ONE, null,
                        null);
            }
            case DECREMENT -> {
                tokens.removeFirst();
                var exp = parseCastExp(tokens, typeAliases);
                yield new CompoundAssignment(SUB_EQ, exp, IntInit.ONE, null,
                        null);
            }
            case SUB -> {
                tokens.removeFirst();
                yield new UnaryOp(UnaryOperator.UNARY_MINUS,
                        parseCastExp(tokens, typeAliases), null);
            }
            case BITWISE_NOT -> {
                tokens.removeFirst();
                yield new UnaryOp(UnaryOperator.BITWISE_NOT,
                        parseCastExp(tokens, typeAliases), null);
            }
            case BITWISE_AND -> {
                tokens.removeFirst();
                yield new AddrOf(parseCastExp(tokens, typeAliases), null);
            }
            case IMUL -> {
                tokens.removeFirst();
                yield new Dereference(parseCastExp(tokens, typeAliases), null);
            }
            case NOT -> {
                tokens.removeFirst();
                yield new UnaryOp(UnaryOperator.NOT, parseCastExp(tokens,
                        typeAliases), null);
            }
            case SIZEOF -> {
                tokens.removeFirst();
                if (tokens.getFirst() == OPEN_PAREN && isTypeSpecifier(tokens
                        , 1, typeAliases)) {
                    tokens.removeFirst();
                    TypeName typeName = parseTypeName(tokens, typeAliases);
                    expect(CLOSE_PAREN, tokens);
                    yield new SizeOfT(typeNameToType(typeName, tokens, typeAliases));
                } else {
                    yield new SizeOf(parseUnaryExp(tokens, typeAliases));
                }
            }
            default -> parsePostfixExp(tokens, typeAliases);
        };
    }

    private static Exp parsePostfixExp(TokenList tokens,
                                       ArrayList<Map<String, Type>> typeAliases) {
        // postfix-expression:
        //                    primary-expression
        //                    postfix-expression [ expression ]
        //                    postfix-expression ( argument-expression-listopt )
        //                    postfix-expression . identifier
        //                    postfix-expression -> identifier
        //                    postfix-expression ++
        //                    postfix-expression --
        //                    compound-literal

        Exp exp = parsePrimaryExp(tokens, typeAliases);
        outer:
        while (true) {
            switch (tokens.getFirst()) {
                case OPEN_BRACKET:
                    tokens.removeFirst();
                    Exp subscript = parseExp(tokens, 0, true, typeAliases);
                    expect(CLOSE_BRACKET, tokens);
                    exp = new Subscript(exp, subscript, null);
                    break;
                case OPEN_PAREN:
                    exp = parseFunctionCallArgs(exp, tokens, typeAliases);
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
                    // for post increment, we rewrite with exp++ as exp =
                    // exp+1, exp-1
                    tokens.removeFirst();
                    exp = new UnaryOp(POST_INCREMENT, exp, null);
                    break;
                case DECREMENT:
                    // for post increment, we rewrite with exp-- as exp =
                    // exp-1, exp+1
                    tokens.removeFirst();
                    exp = new UnaryOp(POST_DECREMENT, exp, null);
                    break;
                default:
                    break outer;
            }
        }
        return exp;
    }

    private static Constant parseConst(TokenList tokens,
                                       boolean throwIfNotFound) {
        Token token = tokens.getFirst();
        if (token == TRUE) {
            tokens.removeFirst();
            return BoolInit.TRUE;
        } else if (token == FALSE) {
            tokens.removeFirst();
            return BoolInit.FALSE;
        }
        if (tokens.getFirst() instanceof TokenWithValue(Token tokenType,
                                                        String value)) {
            tokens.removeFirst();
            boolean isHex = UNSIGNED_HEX_INT_LITERAL == token.type() ||
                    UNSIGNED_HEX_LONG_LITERAL == token.type() ||
                    HEX_LONG_LITERAL == token.type() ||
                    HEX_INT_LITERAL == token.type();

            switch (token.type()) {
                case FLOAT_LITERAL:
                case DOUBLE_LITERAL:
                case UNSIGNED_LONG_LITERAL:
                case UNSIGNED_INT_LITERAL:
                case UNSIGNED_HEX_INT_LITERAL:
                case LONG_LITERAL:
                case UNSIGNED_HEX_LONG_LITERAL:
                case HEX_LONG_LITERAL:
                case HEX_INT_LITERAL:
                case INT_LITERAL: {
                    Type t = Primitive.fromTokenType((TokenType) tokenType);
                    int end = value.length();
                    int start=isHex ? 2 : 0;
                   while(end>start+1){
                       int c=value.charAt(end-1);
                       if (c == 'u' || c == 'U' || c == 'l' || c == 'L' ||
                               ((c == 'f' || c == 'F') &&
                                       t == Primitive.FLOAT)) {
                           end--;
                       } else break;
                   }

                    return parseConst(value.substring(start, end), t,
                            isHex);
                }
                case CHAR_LITERAL: {
                    return new IntInit(parseChar(value));
                }
                default:
                    break;
            }
        }
        if (throwIfNotFound)
            throw makeErr("expected const, found: " + token, tokens);
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
                    default -> {
                        if (c>='0' && c<='7'){
                            yield (char) Integer.parseInt(s.substring(1),8);
                        } else throw new AssertionError(c);
                    }
                };
            }
            case 1 -> {
                return s.charAt(0);
            }
            default -> throw new AssertionError(len);
        }
    }


    private static Exp parsePrimaryExp(TokenList tokens,
                                       ArrayList<Map<String, Type>> typeAliases) {
        // primary-expression:
        //                   identifier
        //                           constant
        //                   string-literal
        //                           ( expression )
        //                   generic-selection

        while (tokens.getFirst() == GCC_ATTRIBUTE) {
            stripGccAttribute(tokens);
        }
        return switch (tokens.getFirst()) {
            case BUILTIN_VA_ARG -> {
                tokens.removeFirst();
                expect(OPEN_PAREN, tokens);
                String identifier = expectIdentifier(tokens);
                expect(COMMA, tokens);
                TypeName typeName = parseTypeName(tokens, typeAliases);
                Type type = typeNameToType(typeName, tokens, typeAliases);
                expect(CLOSE_PAREN, tokens);
                yield new BuiltinVaArg(new Var(identifier, null), type);
            }
            case BUILTIN_OFFSETOF -> {
                tokens.removeFirst();
                expect(OPEN_PAREN, tokens);


                TypeName typeName = parseTypeName(tokens, typeAliases);
                Type t = typeNameToType(typeName, tokens, typeAliases);

                expect(COMMA, tokens);
                String member = expectIdentifier(tokens);

                if (t instanceof Structure type) {
                    expect(CLOSE_PAREN, tokens);
                    yield new Offsetof(type, member);
                } else {
                    throw makeErr("request for member ‘" + member +
                            "’ in something not a structure or union", tokens);
                }
            }
            case TRUE, FALSE -> parseConst(tokens, true);
            case TokenWithValue(Token tokenType, String value) -> {

                yield switch (tokenType) {
                    case STRING_LITERAL -> new Str(parseStr(tokens), null);
                    case IDENTIFIER
                         // if we're in the middle of a ?: expression we
                         // might have token type label (because of the
                         // colon), but it's really an identifier
                        -> {
                        tokens.removeFirst();

                        Var id = new Var(value, null);
                        Exp id1 =
                                parseFunctionCallArgs(id, tokens, typeAliases);
                        if (id1 != null) yield id1;
                        yield id;

                    }
                    default -> parseConst(tokens, true);
                };
            }
            case NULLPTR -> {
                tokens.removeFirst();
                yield Nullptr.NULLPTR;
            }
            case OPEN_PAREN -> {
                tokens.removeFirst();
                Exp exp = parseExp(tokens, 0, true, typeAliases);
                expect(CLOSE_PAREN, tokens);
                yield exp;
            }
            case RESTRICT, VOLATILE -> {
                tokens.removeFirst();
                yield parsePrimaryExp(tokens, typeAliases);
            }
            case ADD -> {
                // MR-TODO: c has unary plus - going to add it later for now just negate twice
                tokens.removeFirst();
                yield new UnaryOp(UnaryOperator.UNARY_MINUS, new UnaryOp(UnaryOperator.UNARY_MINUS, parsePrimaryExp(tokens, typeAliases), null), null);
            }
            case GENERIC -> {
                tokens.removeFirst();
                expect(OPEN_PAREN, tokens);
                Exp controllingExp  = parseExp(tokens, 0, false, typeAliases);
                Exp defaultExp = null;
                expect(COMMA, tokens);
                ArrayList<Cast> genericAssocList = new ArrayList<>();
                while (true) {
                    if (defaultExp == null && tokens.getFirst() == DEFAULT) {
                        tokens.removeFirst();
                        expect(COLON, tokens);
                        defaultExp = parseExp(tokens, 0, false, typeAliases);
                    } else {
                        TypeName typeName = parseTypeName(tokens, typeAliases);
                        Type type =
                                typeNameToType(typeName, tokens, typeAliases);
                        expect(COLON, tokens);
                        Exp assignmentExp =
                                parseExp(tokens, 0, false, typeAliases);
                        genericAssocList.add(new Cast(type, assignmentExp));
                    }
                    Token t = tokens.removeFirst();
                    if (t == CLOSE_PAREN) break;
                    else if (t != COMMA) {
                        tokens.back();
                        expect(COMMA, tokens);
                    }
                }
                yield new Generic(controllingExp,genericAssocList,defaultExp);

            }
            default ->
                    throw makeErr("Expected either identifier, constant or (,"
                            + " found:" + tokens.getFirst(), tokens);
        };
    }

    private static Exp parseFunctionCallArgs(Exp id, TokenList tokens,
                                                ArrayList<Map<String, Type>> typeAliases) {
        if (!tokens.isEmpty() && tokens.getFirst() == OPEN_PAREN) {
            tokens.removeFirst();
            Token current = tokens.getFirst();
            if (current == CLOSE_PAREN) {
                tokens.removeFirst();
                return newFunctionCall(id, Collections.emptyList(), false, null);
            }
            List<Exp> args = new ArrayList<>();

            while (true) {
                Exp e = parseExp(tokens, 0, false, typeAliases); // false because we
                // want comma as argument separator, not
                // operator
                args.add(e);
                current = tokens.removeFirst();
                if (current == COMMA) {
                    continue;
                }
                if (current == CLOSE_PAREN) {
                    break;
                } else
                    throw new IllegalArgumentException(
                            "unexpected token while parsing " + "function call: " + current);

            }
            return newFunctionCall(id, args, false, null);

        }
        return null;
    }

    private static Exp newFunctionCall(Exp name,
                                    List<Exp> args,
                                    boolean varargs,
                                    Type type) {
        if (name instanceof Var v) {
            BuiltInFunction b = BuiltInFunction.fromIdentifier(v.name());
            if (b!=null){
                return new BuiltInFunctionCall(b, args, type);
            }
        }
        return new FunctionCall(name, args, varargs, type);
    }

    private static String parseStr(TokenList tokens) {
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
                            default -> {
                                int len = 0;
                                int base = 8;
                                if (next == 'x') {
                                    next = value.charAt(++i);
                                    base = 16;
                                    while (i < slen && len < 2 &&
                                            ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f')
                                                    || (next >= 'A' && next <= 'F'))) {
                                        next = value.charAt(i);
                                        len++;
                                        i++;
                                    }

                                } else {
                                    while (i < slen && len < 3 && next >= '0' && next <= '7') {
                                        next = value.charAt(i);
                                        len++;
                                        i++;
                                    }
                                }
                                if (len == 0) throw new AssertionError(next);
                                yield (char) Integer.parseInt(value.substring(i - len, i), base);
                            }

                        };
                    }
                    default -> cs[toIndex++] = value.charAt(i);

                }
            }
        }
        for (int i = 0; i < consecutiveStringCount; i++) {
            tokens.removeFirst();
        }
        return new String(cs, 0, toIndex);
    }

    private static Exp parseCastExp(TokenList tokens,
                                    ArrayList<Map<String, Type>> typeAliases) {
        // <cast-exp> ::= "(" <type-name> ")" <cast-exp>
        //              | <unary-exp>

        if (tokens.getFirst() == OPEN_PAREN && isTypeSpecifier(tokens, 1,
                typeAliases)) {
            tokens.removeFirst();
            TypeName typeName = parseTypeName(tokens, typeAliases);
            expect(CLOSE_PAREN, tokens);
            Type type = typeNameToType(typeName, tokens, typeAliases);
            Exp inner = parseCastExp(tokens, typeAliases);
            return new Cast(type, inner);
        }
        return parseUnaryExp(tokens, typeAliases);
    }

    private static Type typeNameToType(TypeName typeName,
                                       TokenList tokens,
                                       ArrayList<Map<String, Type>> typeAliases) {
        Type t = parseTypeAndStorageClass(typeName.typeSpecifierQualifiers(), typeAliases, tokens).type();
        return processAbstractDeclarator(typeName.abstractDeclarator(), t, typeAliases, tokens);
    }

    record TypeName(List<DeclarationSpecifier> typeSpecifierQualifiers,
                    AbstractDeclarator abstractDeclarator) {}

    private static TypeName parseTypeName(TokenList tokens,
                                          ArrayList<Map<String, Type>> typeAliases) {
        List<DeclarationSpecifier> typeSpecifiers= parseDeclarationSpecifiers(tokens, typeAliases);
        AbstractDeclarator abstractDeclarator = parseAbstractDeclarator(tokens, typeAliases);
        return new TypeName(typeSpecifiers, abstractDeclarator);
    }

    private static Exp parseExp(TokenList tokens, int minPrecedence,
                                boolean allowComma,
                                ArrayList<Map<String, Type>> typeAliases) {

        //to this
        // <exp> ::= <cast-exp> | <exp> <binop> <exp> | <exp> "?" <exp> ":"
        // <exp>

        Exp left = parseCastExp(tokens, typeAliases);
        // now peek to see if there is "binop <exp>" or "? <exp> : <exp>

        while (!tokens.isEmpty()) {
            Token token = tokens.getFirst();
            if (token instanceof BinaryOperator binop && (allowComma || binop != COMMA) || token == QUESTION_MARK || token == OPEN_PAREN) {
                int precedence = getPrecedence(token);
                if (precedence < minPrecedence) break;
                tokens.removeFirst();
                if (token == BECOMES) { // right associative
                    Exp right = parseExp(tokens, precedence, true, typeAliases);
                    left = new Assignment(left, right, null);
                } else if (token instanceof CompoundAssignmentOperator compOp) { // right associative
                    Exp right = parseExp(tokens, precedence, true, typeAliases);
                    left = new CompoundAssignment(compOp, left, right, null,
                            null);
                } else if (token instanceof BinaryOperator binop) {
                    Exp right = parseExp(tokens, precedence + 1, true,
                            typeAliases);
                    left = new BinaryOp(binop, left, right, null);
                } else if (token == OPEN_PAREN) {
                    tokens.back();
                    left = parseFunctionCallArgs(left, tokens, typeAliases);
                }else { // QUESTION_MARK
                    Exp middle = parseExp(tokens, 0, true, typeAliases);
                    expect(COLON, tokens);
                    Exp right = parseExp(tokens, precedence, true, typeAliases);
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
            case OPEN_PAREN -> 70;
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
            case BECOMES, SUB_EQ, ADD_EQ, IMUL_EQ, DIVIDE_EQ, REMAINDER_EQ,
                 AND_EQ, BITWISE_AND_EQ, OR_EQ, BITWISE_OR_EQ, BITWISE_XOR_EQ,
                 SHL_EQ, SAR_EQ -> 1;
            case COMMA -> 0;
            default ->
                    throw new IllegalStateException("No precedence for: " + t);
        };
    }

    private static ForInit parseForInit(TokenList tokens,
                                        ArrayList<Map<String, Type>> typeAliases) {

        if (isTypeSpecifier(tokens, 0, typeAliases))
            return parseDeclarationList(tokens, true, typeAliases, false);
        Exp r = tokens.getFirst() == SEMICOLON ? null : parseExp(tokens, 0,
                true, typeAliases);
        expect(SEMICOLON, tokens);
        return r;

    }

    private static For parseFor(TokenList tokens, List<String> labels,
                                Switch enclosingSwitch,
                                ArrayList<Map<String, Type>> typeAliases) {
        expect(OPEN_PAREN, tokens);
        ForInit init = parseForInit(tokens, typeAliases);
        Token t = tokens.getFirst();
        Exp condition = t == SEMICOLON ? null : parseExp(tokens, 0, true,
                typeAliases);
        expect(SEMICOLON, tokens);
        t = tokens.getFirst();
        Exp post = t == CLOSE_PAREN ? null : parseExp(tokens, 0, true,
                typeAliases);
        expect(CLOSE_PAREN, tokens);
        Statement body = parseStatement(tokens, labels, enclosingSwitch,
                typeAliases);
        return new For(init, condition, post, body, null);
    }

    private static Switch parseSwitch(TokenList tokens, List<String> labels,
                                      ArrayList<Map<String, Type>> typeAliases) {
        expect(OPEN_PAREN, tokens);
        Exp switchExpression = parseExp(tokens, 0, true, typeAliases);
        expect(CLOSE_PAREN, tokens);
        Switch s = new Switch();
        s.label = makeTemporary(".Lswitch.");
        Statement body = parseStatement(tokens, labels, s, typeAliases);

        s.exp = switchExpression;

        s.body = body;
        return s;
    }

    private static Exp fail(String s) {
        throw makeErr(s, null);
    }

    private record TypeAndStorageClass(Type type, StorageClass storageClass,
                                       String typeDefName,
                                       EnumSet<TypeQualifier> typeQualifiers,
                                       StructOrUnionSpecifier structOrUnionSpecifier) {}

    public sealed static interface DeclarationSpecifier permits StorageClass, TypeSpecifier, TypeQualifier {}
}
