package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.CharInit;
import com.quaxt.mcc.tacky.PointerInit;
import com.quaxt.mcc.tacky.StringInit;
import com.quaxt.mcc.UCharInit;

import java.util.*;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.InitialValue.NoInitializer.NO_INITIALIZER;
import static com.quaxt.mcc.InitialValue.Tentative.TENTATIVE;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.Mcc.makeTemporary;
import static com.quaxt.mcc.UnaryOperator.*;
import static com.quaxt.mcc.parser.StorageClass.EXTERN;
import static com.quaxt.mcc.parser.StorageClass.STATIC;
import static com.quaxt.mcc.semantic.Primitive.*;

public class SemanticAnalysis {

    record Entry(String name, boolean fromCurrentScope, boolean hasLinkage) {}

    record StructureEntry(String name, boolean fromCurrentScope) {}

    public static Program loopLabelProgram(Program program) {
        ArrayList<Declaration> decls = program.declarations();
        for (int i = 0; i < decls.size(); i++) {
            switch (decls.get(i)) {
                case Function f -> decls.set(i, loopLabelFunction(f));
                default -> {
                }
            }
        }
        return program;
    }

    private static Function loopLabelFunction(Function function) {
        return new Function(function.name(), function.parameters(),
                loopLabelStatement(function.body(), null, null),
                function.funType(), function.storageClass());

    }

    @SuppressWarnings("unchecked")
    private static <T extends Statement> T loopLabelStatement(T statement,
                                                              String currentLabel,
                                                              String currentNonSwitchLabel) {
        return switch (statement) {
            case null -> null;
            case Block block -> {//update the blockItems in-place
                ArrayList<BlockItem> blockItems = block.blockItems();
                blockItems.replaceAll(blockItem -> switch (blockItem) {
                    case VarDecl declaration ->
                            loopLabelVarDecl(declaration, currentLabel,
                                    currentNonSwitchLabel);
                    case Statement innerStatement ->
                            loopLabelStatement(innerStatement, currentLabel,
                                    currentNonSwitchLabel);
                    case Function function -> loopLabelFunction(function);
                    case StructDecl structDecl -> structDecl;
                });
                yield (T) block;
            }
            case Break aBreak -> {
                if (currentLabel == null) {
                    fail("break statement outside of loop");
                }
                aBreak.label = currentLabel;
                yield statement;
            }
            case Continue aContinue -> {
                if (currentLabel == null) {
                    fail("continue statement outside of loop");
                }
                aContinue.label = currentNonSwitchLabel;
                yield statement;
            }
            case DoWhile(Statement body, Exp condition, String _) -> {
                String newLabel = makeTemporary(".Ldo.");
                yield (T) new DoWhile(loopLabelStatement(body, newLabel,
                        newLabel), condition, newLabel);

            }
            case LabelledStatement(String label, Statement stmt) ->
                    (T) new LabelledStatement(label, loopLabelStatement(stmt,
                            currentLabel, currentNonSwitchLabel));
            case CaseStatement(Switch enclosingSwitch, Constant<?> label,
                               Statement stmt) ->
                    (T) new CaseStatement(enclosingSwitch, label,
                            loopLabelStatement(stmt, currentLabel,
                                    currentNonSwitchLabel));
            case Exp _, Goto _ -> statement;
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String _) -> {
                String newLabel = makeTemporary(".Lfor.");
                ForInit labeledForInit = switch (init) {
                    case null -> null;
                    case VarDecl declaration ->
                            loopLabelVarDecl(declaration, newLabel, newLabel);
                    case Exp exp -> loopLabelStatement(exp, newLabel, newLabel);
                };
                Exp labeledCondition = loopLabelStatement(condition, newLabel
                        , newLabel);
                Exp labelledPost = loopLabelStatement(post, newLabel, newLabel);
                Statement labelledBody = loopLabelStatement(body, newLabel,
                        newLabel);
                yield (T) new For(labeledForInit, labeledCondition,
                        labelledPost, labelledBody, newLabel);


            }
            case If(Exp condition, Statement ifTrue, Statement ifFalse) ->
                    (T) new If(condition, loopLabelStatement(ifTrue,
                            currentLabel, currentNonSwitchLabel),
                            loopLabelStatement(ifFalse, currentLabel,
                                    currentNonSwitchLabel));
            case NullStatement _ -> statement;
            case Return(Exp exp) ->
                    (T) new Return(loopLabelStatement(exp, currentLabel,
                            currentNonSwitchLabel));
            case While(Exp condition, Statement body, String _) -> {
                String newLabel = makeTemporary(".Lwhile.");
                yield (T) new While(condition, loopLabelStatement(body,
                        newLabel, newLabel), newLabel);

            }
            case Switch switchStatement -> {
                loopLabelStatement(switchStatement.body,
                        switchStatement.label, currentLabel);
                yield (T) switchStatement;
            }
        };
    }

    private static VarDecl loopLabelVarDecl(VarDecl declaration,
                                            String currentLabel,
                                            String currentNonSwitchLabel) {
        Initializer init = declaration.init();
        return new VarDecl(declaration.name(), loopLabelInitializer(init,
                currentLabel, currentNonSwitchLabel), declaration.varType(),
                declaration.storageClass());
    }

    private static Initializer loopLabelInitializer(Initializer init,
                                                    String currentLabel,
                                                    String currentNonSwitchLabel) {
        return switch (init) {
            case CompoundInit(ArrayList<Initializer> inits, Type t) -> {
                inits.replaceAll(i -> loopLabelInitializer(i, currentLabel,
                        currentNonSwitchLabel));
                yield new CompoundInit(inits, t);
            }
            case SingleInit(Exp exp, Type targetType) ->
                    new SingleInit(loopLabelStatement(exp, currentLabel,
                            currentNonSwitchLabel), targetType);
            case null -> null;
        };
    }

    public static void typeCheckProgram(Program program) {
        for (int i = 0; i < program.declarations().size(); i++) {
            Declaration d = program.declarations().get(i);

            program.declarations().set(i, switch (d) {
                case Function function ->
                        typeCheckFunctionDeclaration(function, false);
                case VarDecl varDecl ->
                        typeCheckFileScopeVariableDeclaration(varDecl);

                case StructDecl structDecl -> {
                    typeCheckStructureDeclaration(structDecl);
                    yield structDecl;
                }
            });
        }
    }

    private static void typeCheckStructureDeclaration(StructDecl structDecl) {
        if (structDecl.members() == null) return;
        if (Mcc.TYPE_TABLE.containsKey(structDecl.tag())) {
            throw new Err("redefinition of struct");
        }
        validateStructDefinition(structDecl);
        ArrayList<MemberEntry> memberEntries = new ArrayList<>();
        int structSize = 0;
        int structAlignment = 0;
        for (MemberDeclaration member : structDecl.members()) {
            int memberAlignment = Mcc.typeAlignment(member.type());
            int memberOffset = roundUp(structSize, memberAlignment);
            MemberEntry m = new MemberEntry(member.name(), member.type(),
                    memberOffset);
            memberEntries.add(m);
            structAlignment = Math.max(structAlignment, memberAlignment);
            structSize = (int) (memberOffset + Mcc.size(member.type()));
        }
        structSize = roundUp(structSize, structAlignment);
        Mcc.TYPE_TABLE.put(structDecl.tag(), new StructDef(structAlignment,
                structSize, memberEntries));
    }

    private static int roundUp(int x, int n) {
        int rem = x % n;
        if (rem == 0) return x;
        return x - rem + n;
    }

    private static void validateStructDefinition(StructDecl structDecl) {
        for (var m : structDecl.members()) {
            if (m.type() == VOID) fail("Can't declare void field");
            validateTypeSpecifier(m.type());
        }
    }

    private static ZeroInit createZeroInit(Type targetType) {
        long size = 1;
        out:
        while (true) switch (targetType) {
            case Array(Type element, Constant<?> arraySize) -> {
                size *= arraySize.toLong();
                targetType = element;
            }
            case FunType funType ->
                    throw new Err("Can't zero initialize function");
            case Pointer pointer -> {
                size *= 8;
                break out;
            }
            case Primitive primitive -> {
                size *= Mcc.size(primitive);
                break out;
            }

            case Structure(String tag) -> {
                size *= Mcc.TYPE_TABLE.get(tag).size();
                break out;
            }
        }
        return new ZeroInit(size);
    }

    private static void convertCompoundInitializerToStaticInitList(
            Initializer init, Type targetType, List<StaticInit> acc) {
        switch (init) {
            case null -> acc.add(createZeroInit(targetType));
            case CompoundInit(ArrayList<Initializer> inits, Type type) -> {
                switch (targetType) {
                    case Array(Type inner, Constant arraySize) -> {
                        long declaredLength = arraySize.toLong();
                        if (declaredLength < inits.size()) {
                            throw new Err("Length of initializer (" + inits.size() + ") exceeds declared length of array (" + arraySize + ")");
                        }
                        inits.forEach(i -> convertCompoundInitializerToStaticInitList(i, inner, acc));
                        if (declaredLength < inits.size()) {
                            throw new Err("Length of initializer (" + inits.size() + ") exceeds declared length of array (" + arraySize + ")");
                        }
                        if (declaredLength > inits.size()) {
                            acc.add(createZeroInit(new Array(inner,
                                    new ULongInit(declaredLength - inits.size()))));
                        }
                    }

                    case Pointer _, FunType _, Primitive _ ->
                            throw new Err("illegal compound initializer for " + "scalar type:" + targetType);

                    case Structure(String tag) -> {
                        StructDef structDef = Mcc.TYPE_TABLE.get(tag);
                        if (inits.size() > structDef.members().size()) {
                            throw new Err("Too many elements in structure " + "initializer");
                        }
                        int currentOffset = 0;
                        int i = 0;

                        for (var initElement : inits) {
                            var member = structDef.members().get(i);
                            if (member.offset() != currentOffset) {
                                acc.add(new ZeroInit(member.offset() - currentOffset));
                            }
                            convertCompoundInitializerToStaticInitList(initElement, member.type(), acc);
                            currentOffset =
                                    (int) (member.offset() + Mcc.size(member.type()));
                            i++;
                        }
                        if (structDef.size() != currentOffset) {
                            acc.add(new ZeroInit(structDef.size() - currentOffset));
                        }
                    }
                }
            }
            case SingleInit(Exp exp, Type _) -> {
                if (exp instanceof Str(String s,
                                       Type _) && targetType instanceof Array(
                        Type element, Constant arraySize)) {
                    handleStringLiteral(acc, s, element, arraySize.toLong());
                } else if (exp instanceof Str(String s,
                                              Type _) && targetType instanceof Pointer(
                        Type element)) {

                    String pointerName = handleStringPointer(s);
                    acc.add(new PointerInit(pointerName));
                } else {
                    if (targetType instanceof Array) {
                        throw new Err("Can't initialize static array with a " + "scalar");
                    }
                    if (targetType instanceof Structure) {
                        throw new Err("Can't initialize structure with a " +
                                "scalar");
                    }
                    acc.add(switch (exp) {
                        case IntInit(int i) ->
                                convertConst(new IntInit(i), targetType);
                        case LongInit(long i) ->
                                convertConst(new LongInit(i), targetType);
                        case UIntInit(int i) ->
                                convertConst(new UIntInit(i), targetType);
                        case ULongInit(long i) ->
                                convertConst(new ULongInit(i), targetType);
                        case DoubleInit(double d) ->
                                convertConst(new DoubleInit(d), targetType);
                        default -> throw new Err("Non constant initializer");
                    });
                }
            }
        }
    }

    static Map<String, String> STRING_TABLE = new HashMap<>();

    private static String handleStringPointer(String s) {
        String k = STRING_TABLE.get(s);
        if (k != null) {
            return k;
        }
        String stringId = makeTemporary("string.");
        STRING_TABLE.put(s, stringId);
        StringInit stringInit = new StringInit(s, true);
        StaticAttributes attrs =
                new StaticAttributes(new Initial(Collections.singletonList(stringInit)), false);
        SYMBOL_TABLE.put(stringId, new SymbolTableEntry(new Pointer(CHAR),
                attrs));
        return stringId;
    }

    private static void handleStringLiteral(List<StaticInit> acc, String s,
                                            Type element, long arrayLen) {
        if (!element.isCharacter())
            throw new Err("Can't initialize non-character type with a string "
                    + "literal");
        if (s.length() > arrayLen)
            throw new Err("Too many chars in string literal");
        // how many zeros past the first one that comes with asciz
        long zeroCount = arrayLen - s.length() - 1;
        acc.add(new StringInit(s, zeroCount >= 0));
        if (zeroCount > 0) acc.add(new ZeroInit(zeroCount));
    }

    private static VarDecl typeCheckFileScopeVariableDeclaration(VarDecl decl) {
        InitialValue initialValue;
        var varType = decl.varType();
        if (varType == VOID) {
            fail("Can't declare void variable");
        }
        if (!isComplete(varType) && (decl.storageClass() != EXTERN || decl.init() != null)) {
            fail("Can't declare incomplete variable");
        }
        if (decl.init() == null)
            initialValue = decl.storageClass() == EXTERN ? NO_INITIALIZER :
                    TENTATIVE;
        else {
            ArrayList<StaticInit> staticInits = new ArrayList<>();
            convertCompoundInitializerToStaticInitList(decl.init(), varType,
                    staticInits);
            initialValue = new Initial(staticInits);
        }
        validateTypeSpecifier(varType);
        if (varType instanceof Pointer && decl.init() instanceof SingleInit(
                Exp exp,
                Type targetType) && exp instanceof Constant c && !isNullPointerConstant(c)) {
            throw new Err("Cannot convert type for assignment");
        }
        boolean global = decl.storageClass() != STATIC;
        if (SYMBOL_TABLE.get(decl.name().name()) instanceof SymbolTableEntry ste) {
            var type = ste.type();
            var attrs = ste.attrs();
            if (!type.looseEquals(varType))
                fail("variable declared with inconsistent type");
            if (decl.storageClass() == EXTERN) global = attrs.global();
            else if (attrs.global() != global)
                fail("conflicting variable linkage");

            if (attrs instanceof StaticAttributes(InitialValue oldInit,
                                                  boolean _)) {

                if (oldInit instanceof Initial oldInitialConstant) {
                    if (initialValue instanceof Initial)
                        fail("Conflicting file scope variable definitions");
                    else initialValue = oldInitialConstant;
                } else if (!(initialValue instanceof Initial) && oldInit == TENTATIVE)
                    initialValue = TENTATIVE;
            }
        }
        // when initializing a static pointer with a string
        if (varType instanceof Pointer(Type referenced) && referenced == CHAR) {
            String uniqueName = makeTemporary(decl.name() + ".string.");
            StringInit stringInit =
                    (StringInit) ((Initial) initialValue).initList().getFirst();
            SYMBOL_TABLE.put(uniqueName, new SymbolTableEntry(new Array(CHAR,
                    new IntInit(stringInit.str().length() + 1)),
                    new ConstantAttr(stringInit)));
            StaticAttributes attrs = new StaticAttributes(initialValue, global);
            SYMBOL_TABLE.put(decl.name().name(), new SymbolTableEntry(varType
                    , attrs));
        } else {
            StaticAttributes attrs = new StaticAttributes(initialValue, global);
            SYMBOL_TABLE.put(decl.name().name(), new SymbolTableEntry(varType
                    , attrs));
        }
        return decl;
    }


    private static double unsignedLongToDouble(long ul) {
        if (ul>0) return (double) ul;
        long half = ul >>> 1;
        long parity = ul & 1;
        long roundedToOdd = half | parity;
        double d = (double) roundedToOdd;
        return d+d;
    }

    private static long doubleToUnsignedLong(double d) {
        if (d > 0x1.0p63) {
            return (long) (d - 0x1.0p63) + (1L << 63);
        }
        return (long) d;
    }

    public static StaticInit convertConst(StaticInit init, Type type) {
        if (init instanceof DoubleInit(double d)) {
            return switch (type) {
                case DOUBLE -> init;
                case LONG -> new LongInit((long) d);
                case INT -> new IntInit((int) d);
                case ULONG -> new ULongInit(doubleToUnsignedLong(d));
                // casting directly to int would be wrong result for doubles
                // > 2^31
                case UINT -> new UIntInit((int) (long) d);
                case CHAR, SCHAR -> new CharInit((byte) (long) d);
                case UCHAR -> new UCharInit((byte) doubleToUnsignedLong(d));
                default ->
                        throw new IllegalArgumentException("not a const:" + init);
            };
        }
        long initL = switch (init) {
            case IntInit(int i) -> i;
            case CharInit(byte i) -> i;
            case UCharInit(byte i) -> i & 0xff;
            case LongInit(long l) -> l;
            case UIntInit(int i) -> Integer.toUnsignedLong(i);
            case ULongInit(long l) -> l;
            default ->
                    throw new IllegalArgumentException("not a const:" + init);
        };
        return switch (type) {
            case DOUBLE -> {
                double d = init instanceof ULongInit ?
                        unsignedLongToDouble(initL) : (double) initL;
                yield new DoubleInit(d);
            }
            case LONG -> new LongInit(initL);
            case INT -> new IntInit((int) initL);
            case ULONG -> new ULongInit(initL);
            case UINT -> new UIntInit((int) initL);
            case CHAR, SCHAR -> new CharInit((byte) initL);
            case UCHAR -> new UCharInit((byte) initL);
            case Pointer _ -> new ULongInit((int) initL);
            default -> null;
        };
    }


    private static Function typeCheckFunctionDeclaration(Function decl,
                                                         boolean blockScope) {
        Type ret = decl.funType().ret();
        if (ret instanceof Array) {
            fail("A function cannot return an array");
        }
        validateTypeSpecifier(ret);
        if (ret != VOID && !isComplete(ret) && decl.body() != null) {
            fail("function return type is incomplete");
        }
        List<Type> oldParams = decl.funType().params();
        ArrayList<Type> adjustedParams = new ArrayList<>(oldParams.size());
        for (int i = 0; i < oldParams.size(); i++) {
            Type p = oldParams.get(i);
            validateTypeSpecifier(p);
            if (p == VOID) {
                fail("named parameter " + (i + 1) + " is void");
            }
            if (!isComplete(p) && decl.body() != null) {
                fail("function with incomplete type parameter");
            }
            adjustedParams.add(arrayToPointer(p));
        }
        if (blockScope && decl.storageClass() == STATIC) {
            fail("invalid storage class for block scope function declaration "
                    + "‘" + decl.name() + "’");
        }
        boolean defined = decl.body() != null;
        boolean global = decl.storageClass() != STATIC;
        SymbolTableEntry oldEntry = SYMBOL_TABLE.get(decl.name());
        boolean alreadyDefined = false;
        FunType funType = new FunType(adjustedParams, ret);
        if (oldEntry instanceof SymbolTableEntry ste) {
            var oldType = ste.type();
            if (oldType instanceof FunType) {
                alreadyDefined = oldEntry.attrs().defined();
                if (alreadyDefined && defined)
                    fail("already defined: " + decl.name());

                if (oldEntry.attrs().global() && decl.storageClass() == STATIC)
                    fail("Static function declaration follows non-static");
                global = oldEntry.attrs().global();
                if (!funType.equals(oldType))
                    fail("Incompatible function declarations for " + decl.name());
            } else {
                fail("Incompatible function declarations for " + decl.name());
            }
        }
        FunAttributes attrs =
                new FunAttributes(alreadyDefined || decl.body() != null,
                        global);

        SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(funType, attrs));

        Block typeCheckedBody;
        if (decl.body() != null) {
            for (int i = 0; i < decl.parameters().size(); i++) {
                Var param = decl.parameters().get(i);
                SYMBOL_TABLE.put(param.name(),
                        new SymbolTableEntry(adjustedParams.get(i),
                                LOCAL_ATTR));
            }
            typeCheckedBody = typeCheckBlock(decl.body(), decl);
        } else typeCheckedBody = null;
        List<Var> declParams = decl.parameters();
        for (int i = 0; i < declParams.size(); i++) {
            Var oldParam = declParams.get(i);
            declParams.set(i, new Var(oldParam.name(), adjustedParams.get(i)));
        }
        return new Function(decl.name(), decl.parameters(), typeCheckedBody,
                funType, decl.storageClass());
    }

    private static Type arrayToPointer(Type p) {
        return switch (p) {
            case Array(Type t, Constant _) -> new Pointer(t);
            default -> p;
        };
    }

    private static Block typeCheckBlock(Block body,
                                        Function enclosingFunction) {
        for (int i = 0; i < body.blockItems().size(); i++) {
            BlockItem blockItem = body.blockItems().get(i);
            body.blockItems().set(i, typeCheckBlockItem(blockItem,
                    enclosingFunction));
        }
        return body;
    }

    private static BlockItem typeCheckBlockItem(BlockItem blockItem,
                                                Function enclosingFunction) {
        return switch (blockItem) {
            case LabelledStatement(String label, Statement statement) ->
                    new LabelledStatement(label,
                            (Statement) typeCheckBlockItem(statement,
                                    enclosingFunction));
            case CaseStatement(Switch enclosingSwitch, Constant<?> label,
                               Statement statement) -> {
                Type switchExpType = enclosingSwitch.exp.type();
                var entries = enclosingSwitch.entries;
                Constant<? extends Constant<?>> convertedConst;
                if (label != null) {
                    var t = label.type();
                    if (!t.isInteger()) {
                        throw new Err("case label is not an integer");
                    }
                    convertedConst =
                            (Constant<?>) convertConst((StaticInit) label,
                                    switchExpType);
                    if (entries.contains(convertedConst)) {
                        String err =
                                "duplicate case: " + convertedConst.toLong();
                        if (convertedConst.toLong() != label.toLong())
                            err += " (converted from " + label + ")";
                        throw new Err(err);
                    }
                } else {
                    convertedConst = null;
                    if (entries.contains(null)) {
                        throw new Err("duplicate default");
                    }
                }
                enclosingSwitch.addEntry(convertedConst);
                yield new CaseStatement(enclosingSwitch, convertedConst,
                        (Statement) typeCheckBlockItem(statement,
                                enclosingFunction));
            }
            case VarDecl declaration ->
                    typeCheckLocalVariableDeclaration(declaration);
            case Exp exp -> typeCheckAndConvert(exp);
            case Function function -> {
                if (function.body() != null)
                    throw new Err("nested function definition not allowed");
                else yield typeCheckFunctionDeclaration(function, true);
            }
            case Block block -> typeCheckBlock(block, enclosingFunction);
            case DoWhile(Statement whileBody, Exp condition, String label) ->
                    new DoWhile((Statement) typeCheckBlockItem(whileBody,
                            enclosingFunction),
                            requireScalar(typeCheckAndConvert(condition)),
                            label);
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> new For(switch (init) {
                case null -> null;
                case Exp exp -> typeCheckAndConvert(exp);
                case VarDecl varDecl ->
                        typeCheckLocalVariableDeclaration(varDecl);
            }, condition == null ? null :
                    requireScalar(typeCheckAndConvert(condition)),
                    typeCheckAndConvert(post),
                    (Statement) typeCheckBlockItem(body, enclosingFunction),
                    label);
            case If(Exp condition, Statement ifTrue, Statement ifFalse) ->
                    new If(requireScalar(typeCheckAndConvert(condition)),
                            (Statement) typeCheckBlockItem(ifTrue,
                                    enclosingFunction),
                            (Statement) typeCheckBlockItem(ifFalse,
                                    enclosingFunction));

            case Return(Exp exp) -> {
                Type returnType = enclosingFunction.funType().ret();
                if (returnType == VOID) {
                    if (exp != null) {
                        fail("Can't return value from void function");
                    }
                    yield blockItem;
                }
                if (exp == null) {
                    fail("non-void function must return a value");
                }
                yield new Return(convertByAssignment(typeCheckAndConvert(exp)
                        , returnType));

            }
            case While(Exp condition, Statement whileBody, String label) ->
                    new While(requireScalar(typeCheckAndConvert(condition)),
                            (Statement) typeCheckBlockItem(whileBody,
                                    enclosingFunction), label);
            case StructDecl structDecl -> {
                typeCheckStructureDeclaration(structDecl);
                yield structDecl;
            }
            case NullStatement _, Continue _, Break _, Goto _ -> blockItem;
            case Switch switchStatement -> {
                var typedExp = (Exp) typeCheckBlockItem(switchStatement.exp,
                        enclosingFunction);
                Type t = typedExp.type();
                if (!t.isInteger()) {
                    throw new Err("switch quantity not an integer");
                }
                switchStatement.exp = promoteIfNecessary(typedExp);
                switchStatement.body =
                        (Statement) typeCheckBlockItem(switchStatement.body,
                                enclosingFunction);
                yield switchStatement;
            }
            case null -> null;
        };
    }

    private static Exp requireScalar(Exp exp) {
        if (!exp.type().isScalar()) {
            fail("Scalar required here");
        }
        return exp;
    }


    private static void validateTypeSpecifier(Type type) {
        switch (type) {
            case Array(Type element, Constant arraySize) -> {
                if (!isComplete(element))
                    throw new Err("Illegal array of incomplete type");
                validateTypeSpecifier(element);
            }
            case FunType(List<Type> params, Type ret) -> {
                validateTypeSpecifier(ret);
                for (Type p : params) {
                    validateTypeSpecifier(p);
                }
            }
            case Pointer(Type element) -> validateTypeSpecifier(element);
            default -> {}
        }
    }


    private static VarDecl typeCheckLocalVariableDeclaration(VarDecl decl) {
        validateTypeSpecifier(decl.varType());
        if (decl.varType() == VOID) fail("Can't declare void variable");
        if (!isComplete(decl.varType())) {
            if (decl.storageClass() != EXTERN)
                fail("Attempt to declare variable of incomplete type with " + "non-external storage class");
            if (decl.init() != null)
                fail("Attempt to define variable of incomplete type");
        }
        if (decl.storageClass() == EXTERN) {
            if (decl.init() != null)
                fail("Initializer on local extern variable declaration");
            if (SYMBOL_TABLE.get(decl.name().name()) instanceof SymbolTableEntry ste) {
                Type oldType = ste.type();
                if (!oldType.looseEquals(decl.varType()))
                    fail("inconsistent variable redefinition");

            } else {
                SYMBOL_TABLE.put(decl.name().name(),
                        new SymbolTableEntry(decl.varType(),
                                new StaticAttributes(NO_INITIALIZER, true)));
            }
            return decl;
        } else if (decl.storageClass() == STATIC) {
            InitialValue initialValue;
            ArrayList<StaticInit> staticInits = new ArrayList<>();

            convertCompoundInitializerToStaticInitList(decl.init(),
                    decl.varType(), staticInits);
            initialValue = new Initial(staticInits);

            if (isStringPointerInit(staticInits) && decl.varType() instanceof Pointer(
                    Type referenced)) {
                if (referenced == CHAR) {
                    String uniqueName = makeTemporary(decl.name().name() +
                            ".string.");
                    /* TODO: this logic is probably not going to handle
                        arrays of char* well*/
                    SYMBOL_TABLE.put(uniqueName,
                            new SymbolTableEntry(new Array(referenced,
                                    new IntInit((int) strlen(staticInits))),
                                    new StaticAttributes(initialValue, false)));
                    SYMBOL_TABLE.put(decl.name().name(),
                            new SymbolTableEntry(decl.varType(),
                                    new StaticAttributes(new Initial(Collections.singletonList(new PointerInit(uniqueName))), false)));
                } else
                    throw new Err("Can't initialize pointer to " + referenced + " with string literal");
            } else

                SYMBOL_TABLE.put(decl.name().name(),
                        new SymbolTableEntry(decl.varType(),
                                new StaticAttributes(initialValue, false)));
            return new VarDecl(new Var(decl.name().name(), decl.varType()),
                    decl.init(), decl.varType(), decl.storageClass());
        } else {
            Initializer init;
            Type type = decl.varType();
            SYMBOL_TABLE.put(decl.name().name(),
                    new SymbolTableEntry(decl.varType(), LOCAL_ATTR));
            if (decl.init() != null) {
                init = typeCheckInit(decl.init(), decl.varType());
                type = init.type();
                // the init type might have an array length when decl.varType
                // () doesn't
                SYMBOL_TABLE.put(decl.name().name(),
                        new SymbolTableEntry(type, LOCAL_ATTR));
            } else init = null;


            return new VarDecl(new Var(decl.name().name(), type), init, type,
                    decl.storageClass());
        }
    }

    private static boolean isStringPointerInit(ArrayList<StaticInit> l) {
        if (l.isEmpty()) {
            return false;
        }
        return l.getFirst() instanceof PointerInit;
    }

    private static long strlen(ArrayList<StaticInit> l) {
        /* this logic is probably not going to handle arrays of char* well*/
        long len = 0;
        for (StaticInit s : l) {
            len += switch (s) {
                case ZeroInit(long bytes) -> bytes;
                case StringInit(String str, boolean nullTerminated) ->
                        str.length();

                default -> 0;
            };
        }
        return len;
    }

    private static Initializer typeCheckInit(Initializer init,
                                             Type targetType) {
        return switch (init) {
            case SingleInit(Exp exp, Type _) -> {
                if (exp instanceof Str(String s,
                                       Type _) && targetType instanceof Array(
                        Type element, Constant arraySize)) {
                    if (!element.isCharacter())
                        throw new Err("Can't initialize non-character type " + "with a string literal");
                    if (s.length() > arraySize.toLong())
                        throw new Err("Too many chars in string literal");
                    yield new SingleInit(new Str(s, targetType), targetType);
                }
                var typeCheckedExp = typeCheckAndConvert(exp);
                yield new SingleInit(convertByAssignment(typeCheckedExp,
                        targetType), targetType);
            }
            case CompoundInit(ArrayList<Initializer> inits, Type type) -> {
                if (targetType instanceof Array(Type elementType,
                                                Constant arraySize)) {
                    long zerosToAdd = 0;
                    if (arraySize != null) {
                        long l = arraySize.toLong();
                        if (inits.size() > l) {
                            throw new Err("wrong number of values in " +
                                    "initializer");
                        }
                        zerosToAdd = l - inits.size();
                    }
                    inits.replaceAll(i -> typeCheckInit(i, elementType));

                    for (long i = 0; i < zerosToAdd; i++) {
                        inits.add(zeroInitializer(elementType));
                    }
                    yield new CompoundInit(inits, arraySize == null ?
                            new Array(elementType,
                                    new ULongInit(inits.size())) : targetType);
                } else if (targetType instanceof Structure(String tag)) {
                    StructDef structDef = Mcc.TYPE_TABLE.get(tag);
                    ArrayList<MemberEntry> members = structDef.members();
                    if (inits.size() > members.size()) {
                        throw new Err("Too many elements in structure " +
                                "initializer");
                    }
                    ArrayList<Initializer> typeCheckedList = new ArrayList<>();
                    int i;
                    for (i = 0; i < inits.size(); i++) {
                        typeCheckedList.add(typeCheckInit(inits.get(i),
                                members.get(i).type()));
                    }
                    for (; i < members.size(); i++) {
                        typeCheckedList.add(zeroInitializer(members.get(i).type()));
                    }
                    yield new CompoundInit(typeCheckedList, targetType);
                } else {
                    throw new Err("Can't use compound initializer to " +
                            "initialize scalar type: " + targetType);
                }
            }

        };
    }

    private static Initializer zeroInitializer(Type elementType) {
        return switch (elementType) {
            case Array(Type element, Constant arraySize) -> {
                long len = arraySize.toLong();
                ArrayList<Initializer> zeros = new ArrayList<>((int) len);
                var zero = zeroInitializer(element);
                for (int i = 0; i < len; i++) {
                    zeros.add(zero);
                }
                yield new CompoundInit(zeros, elementType);
            }
            case Primitive primitive -> primitive.zeroInitializer;
            case Pointer _ -> ULONG.zeroInitializer;
            case FunType funType -> throw new AssertionError(funType);

            case Structure(String tag) -> {
                StructDef structDef = Mcc.TYPE_TABLE.get(tag);
                ArrayList<MemberEntry> members = structDef.members();
                ArrayList<Initializer> typeCheckedList = new ArrayList<>();
                for (MemberEntry member : members) {
                    typeCheckedList.add(zeroInitializer(member.type()));
                }
                yield new CompoundInit(typeCheckedList, elementType);
            }
        };

    }

    private static Exp convertTo(Exp e, Type t) {
        if (e == null || e.type() == t) return e;
        if (e instanceof Constant && e instanceof StaticInit s) {
            return (Exp) convertConst(s, t);
        }
        return new Cast(t, e);
    }

    private static Exp convertByAssignment(Exp e, Type targetType) {
        Type t = e.type();
        if (t.looseEquals(targetType)) return e;
        if ((isArithmeticType(t) && isArithmeticType(targetType)) || (isNullPointerConstant(e) && targetType instanceof Pointer))
            return convertTo(e, targetType);
        if (targetType instanceof Pointer(
                Type referenced) && referenced == VOID && e.type() instanceof Pointer) {
            return convertTo(e, targetType);
        }
        if (e.type() instanceof Pointer(
                Type referenced) && referenced == VOID && targetType instanceof Pointer) {
            return convertTo(e, targetType);
        }
        throw new Err("Cannot convert type for assignment");
    }

    private static boolean isArithmeticType(Type t) {
        return t instanceof Primitive && t != VOID;
    }

    private static Exp typeCheckAndConvert(Exp exp) {
        if (exp == null) return null;
        var typedE = typeCheckExpression(exp);
        return switch (typedE.type()) {
            case Array(Type element, _) ->
                    new AddrOf(typedE, new Pointer(element));
            case Structure(String tag) -> {
                if (Mcc.TYPE_TABLE.containsKey(tag)) yield typedE;
                else throw new Err("Invalid use of incomplete structure type");
            }
            default -> typedE;

        };
    }

    public static Exp typeCheckExpression(Exp exp) {
        return switch (exp) {
            case null -> null;
            case Assignment(Exp left, Exp right, Type _) -> {
                Exp typedLeft = typeCheckAndConvert(left);
                if (!isLvalue(typedLeft))
                    throw new Err("cannot assign to non-lvalue");
                Exp typedRight = typeCheckAndConvert(right);
                Type leftType = typedLeft.type();
                Exp convertedRight = convertByAssignment(typedRight, leftType);
                if (typedRight.type() == VOID) {
                    fail("can't assign void");
                }
                yield new Assignment(typedLeft, convertedRight, leftType);
            }
            case CompoundAssignment(CompoundAssignmentOperator compoundOp,
                                    Exp left, Exp right, Type tempType,
                                    Type t) -> {
                ArithmeticOperator newOp = switch (compoundOp) {
                    case SUB_EQ -> SUB;
                    case ADD_EQ -> ADD;
                    case IMUL_EQ -> IMUL;
                    case DIVIDE_EQ -> DIVIDE;
                    case REMAINDER_EQ -> REMAINDER;
                    case AND_EQ -> AND;
                    case BITWISE_AND_EQ -> BITWISE_AND;
                    case OR_EQ -> OR;
                    case BITWISE_OR_EQ -> BITWISE_OR;
                    case BITWISE_XOR_EQ -> BITWISE_XOR;
                    case SHL_EQ -> SHL;
                    case SAR_EQ -> SAR;
                };

//                Exp typedLeft = typeCheckAndConvert(left);
//                t = typedLeft.type();
//                SYMBOL_TABLE.put(temp.name(),
//                        new SymbolTableEntry(t, LOCAL_ATTR));
//
//                //ugh I thought I could use a temp var to get around
//                evaluating left twice, but it's not that easy
//                // need a way to separate the assignment part from the
//                evaluate part
//                yield typeCheckExpression(new Assignment(new Var(temp.name
//                (),t),
//                        new BinaryOp(newOp, left, right, t), t));
                BinaryOp foo =
                        (BinaryOp) typeCheckExpression(new BinaryOp(newOp,
                                left, right, t));

                Exp typedLeft = typeCheckAndConvert(left);
                if (!isLvalue(typedLeft))
                    throw new Err("cannot assign to non-lvalue");
                Exp typedRight = typeCheckAndConvert(right);
                Type leftType = typedLeft.type();
                if (typedRight.type() == VOID) {
                    fail("can't assign void");
                }


                yield new CompoundAssignment(compoundOp, typedLeft,
                        foo.right(), foo.type(), leftType);
            }
            case BinaryOp(BinaryOperator op, Exp e1, Exp e2,
                          Type _) when op == EQUALS || op == NOT_EQUALS -> {
                Exp typedE1 = typeCheckAndConvert(e1);
                Exp typedE2 = typeCheckAndConvert(e2);
                Type t1 = typedE1.type();
                Type t2 = typedE2.type();
                Type commonType;
                if (t1 instanceof Pointer || t2 instanceof Pointer)
                    commonType = getCommonPointerType(typedE1, typedE2);
                else if (isArithmeticType(t1) && isArithmeticType(t2)) {
                    commonType = getCommonType(t1, t2);
                } else throw new Err("Invalid operands to equality expression");
                Exp convertedE1 = convertTo(typedE1, commonType);
                Exp convertedE2 = convertTo(typedE2, commonType);
                yield new BinaryOp(op, convertedE1, convertedE2, INT);
            }
            case BinaryOp(BinaryOperator op, Exp e1, Exp e2,
                          Type _) when op == ADD -> {
                Exp typedE1 = typeCheckAndConvert(e1);
                Exp typedE2 = typeCheckAndConvert(e2);

                Type t1 = typedE1.type();
                Type t2 = typedE2.type();
                if (isArithmeticType(t1) && isArithmeticType(t2)) {
                    Type commonType = getCommonType(t1, t2);
                    Exp convertedE1 = convertTo(typedE1, commonType);
                    Exp convertedE2 = convertTo(typedE2, commonType);

                    yield new BinaryOp(op, convertedE1, convertedE2,
                            commonType);
                } else if (isPointerToComplete(t1) && t2.isInteger()) {
                    var convertedE2 = convertTo(typedE2, LONG);
                    yield new BinaryOp(ADD, typedE1, convertedE2, t1);
                } else if (t1.isInteger() && isPointerToComplete(t2)) {
                    var convertedE1 = convertTo(typedE1, LONG);
                    yield new BinaryOp(ADD, convertedE1, typedE2, t2);
                } else throw new Err("Invalid operands for addition");
            }
            case BinaryOp(BinaryOperator op, Exp e1, Exp e2,
                          Type _) when op == SUB -> {
                Exp typedE1 = typeCheckAndConvert(e1);
                Exp typedE2 = typeCheckAndConvert(e2);

                Type t1 = typedE1.type();
                Type t2 = typedE2.type();
                if (isArithmeticType(t1) && isArithmeticType(t2)) {
                    Type commonType = getCommonType(t1, t2);
                    Exp convertedE1 = convertTo(typedE1, commonType);
                    Exp convertedE2 = convertTo(typedE2, commonType);
                    yield new BinaryOp(op, convertedE1, convertedE2,
                            commonType);
                } else if (isPointerToComplete(t1) && t2.isInteger()) {
                    var convertedE2 = convertTo(typedE2, LONG);
                    yield new BinaryOp(SUB, typedE1, convertedE2, t1);
                } else if (isPointerToComplete(t1) && t1.equals(t2)) {
                    yield new BinaryOp(SUB, typedE1, typedE2, LONG);
                } else throw new Err("Invalid operands for subtraction");
            }
            case BinaryOp(BinaryOperator op, Exp e1, Exp e2, Type _) -> {
                Exp typedE1 = typeCheckAndConvert(e1);
                Exp typedE2 = typeCheckAndConvert(e2);
                Type t1 = typedE1.type();
                Type t2 = typedE2.type();
                if (!t1.isScalar() || !t2.isScalar()) {
                    fail("Non-scalar operand illegal here");
                }

                if (op == AND || op == OR) {
                    yield new BinaryOp(op, typedE1, typedE2, INT);
                }
                if (op == SAR || op == SHL || op == UNSIGNED_RIGHT_SHIFT) {
                    if (t1 == DOUBLE || t2 == DOUBLE) {
                        fail("invalid operands to binary " + op + " (have ‘" + t1 + "’ " + "and" + " ‘" + t2 + "’");
                    }
                    typedE1 = promoteIfNecessary(typedE1);
                    yield new BinaryOp(op, typedE1,
                            promoteIfNecessary(typedE2), typedE1.type());
                }
                if ((op == REMAINDER && (t1 == DOUBLE || t2 == DOUBLE))
                        || ((op == REMAINDER || op == DIVIDE || op == IMUL) && (t1 instanceof Pointer || t2 instanceof Pointer)))
                    fail("invalid operands to binary " + op + " (have ‘" + t1 + "’ " + "and" + " ‘" + t2 + "’");
                if ((op == BITWISE_AND || op == BITWISE_XOR || op == BITWISE_OR) && (t1 == DOUBLE || t2 == DOUBLE || t1 instanceof Pointer || t2 instanceof Pointer)) {
                    fail("invalid operands to binary " + op + " (have ‘" + t1 + "’ " + "and" + " ‘" + t2 + "’");
                }
                if ((t1 instanceof Pointer || t2 instanceof Pointer) && switch (op) {
                    case LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL, LESS_THAN,
                         GREATER_THAN -> true;
                    default -> false;
                }) {
                    if (!t1.equals(t2))
                        throw new Err("can't apply " + op + " to " + t1 + " " + "and " + t2);
                    yield new BinaryOp(op, typedE1, typedE2, INT);
                }

                Type commonType = getCommonType(t1, t2);
                Exp convertedE1 = convertTo(typedE1, commonType);
                Exp convertedE2 = convertTo(typedE2, commonType);


                yield new BinaryOp(op, convertedE1, convertedE2, switch (op) {
                    case CompoundAssignmentOperator _ -> t1;
                    case SUB, ADD, IMUL, DIVIDE, REMAINDER, BITWISE_AND,
                         BITWISE_OR, BITWISE_XOR, SHL, SAR, COMMA -> commonType;
                    case EQUALS, NOT_EQUALS, LESS_THAN_OR_EQUAL,
                         GREATER_THAN_OR_EQUAL, LESS_THAN, GREATER_THAN -> INT;
                    default -> {
                        throw new Todo();
                    }
                });

            }
            case Cast(Type type, Exp inner) -> {
                validateTypeSpecifier(type);
                Exp typedInner = typeCheckAndConvert(inner);
                if (type == VOID) yield new Cast(type, typedInner);
                if (!type.isScalar()) {
                    fail("Can only cast to scalar type or void");
                }
                Type innerType = typedInner.type();
                if (!innerType.isScalar()) {
                    fail("Cannot cast non-scalar expression to scalar type");
                }

                if ((type instanceof Array) || (type instanceof Pointer && innerType == DOUBLE || innerType instanceof Pointer && type == DOUBLE))
                    throw new Err("illegal cast:" + innerType + "->" + type);
                yield new Cast(type, typedInner);
            }
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type _) -> {
                Exp typedCondition =
                        requireScalar(typeCheckAndConvert(condition));
                Exp typedIfTrue = typeCheckAndConvert(ifTrue);
                Exp typedIfFalse = typeCheckAndConvert(ifFalse);
                Type t1 = typedIfTrue.type();
                Type t2 = typedIfFalse.type();
                Type commonType;
                if (t1 == VOID && t2 == VOID) commonType = VOID;
                else if (t1 instanceof Pointer || t2 instanceof Pointer)
                    commonType = getCommonPointerType(typedIfTrue,
                            typedIfFalse);
                else if (isArithmeticType(t1) && isArithmeticType(t1))
                    commonType = getCommonType(t1, t2);
                else if (t1 instanceof Structure(
                        String tag1) && t2 instanceof Structure(
                        String tag2) && tag1.equals(tag2)) {
                    commonType = t1;
                } else
                    throw new Err("Can't convert branches of conditional to " + "a" + " common type");
                yield new Conditional(typedCondition, convertTo(typedIfTrue,
                        commonType), convertTo(typedIfFalse, commonType),
                        commonType);
            }
            case Constant constant -> constant;
            case Str(String s, Type type) ->
                    new Str(s, new Array(CHAR, new IntInit(s.length() + 1)));
            case FunctionCall(Var name, List<Exp> args, Type _) -> {
                Type fType = SYMBOL_TABLE.get(name.name()).type();
                yield switch (fType) {
                    case FunType(List<Type> params, Type ret) -> {
                        if (params.size() != args.size())
                            fail("Function called with wrong number of " +
                                    "arguments");
                        ArrayList<Exp> convertedArgs = new ArrayList<>();
                        for (int i = 0; i < params.size(); i++) {
                            Exp arg = args.get(i);
                            Type paramType = params.get(i);
                            Exp typedArg = typeCheckAndConvert(arg);
                            convertedArgs.add(convertByAssignment(typedArg,
                                    paramType));
                        }
                        yield new FunctionCall(name, convertedArgs, ret);
                    }
                    default ->
                            fail("variable " + name.name() + " used as " +
                                    "function");
                };
            }
            case Var(String name, Type _) -> {
                Type t = SYMBOL_TABLE.get(name).type();
                if (t instanceof FunType)
                    fail("Function " + name + " used as a variable");
                yield new Var(name, t);
            }
            case UnaryOp(UnaryOperator op, Exp e1,
                         Type _) when op == POST_INCREMENT || op == POST_DECREMENT -> {
                Exp typedE1 = typeCheckAndConvert(e1);
                Type t1 = typedE1.type();
                if (isLvalue(typedE1) && (isArithmeticType(t1) || isPointerToComplete(t1))) {
                    yield new UnaryOp(op, typedE1, t1);
                } else throw new Err("Invalid operand for " + op);
            }
            case UnaryOp(UnaryOperator op, Exp inner, Type _) -> {
                Exp typedInner = typeCheckAndConvert(inner);
                if (!typedInner.type().isScalar())
                    fail("can't apply " + op + " to non scalar type");
                if (op == BITWISE_NOT && typedInner.type() == DOUBLE) {
                    fail("can't apply ~ to double");
                }
                if (typedInner.type() instanceof Pointer && op != NOT && op != POST_INCREMENT && op != POST_DECREMENT) {
                    fail("Can't apply " + op + " to pointer");
                }
                if ((op == UNARY_MINUS || op == BITWISE_NOT) && typedInner.type().isCharacter()) {
                    typedInner = convertTo(typedInner, INT);
                }
                yield switch (op) {
                    case NOT -> new UnaryOp(op, typedInner, INT);
                    default -> new UnaryOp(op, typedInner, typedInner.type());
                };

            }
            case Dereference(Exp inner, Type _) -> {
                Exp typedInner = typeCheckAndConvert(inner);
                yield switch (typedInner.type()) {
                    case Pointer(Type referenced) ->
                            new Dereference(typedInner, referenced);
                    default -> fail("Cannot dereference non-pointer");
                };

            }
            case AddrOf(Exp inner, Type _) -> {
                if (isLvalue(inner)) {
                    Exp typedInner = typeCheckExpression(inner);
                    Type referencedType = typedInner.type();
                    yield new AddrOf(typedInner, new Pointer(referencedType));
                } else {
                    yield fail("Cannot take address of non-lvalue");
                }

            }
            case Subscript(Exp e1, Exp e2, Type _) -> {
                var typedE1 = typeCheckAndConvert(e1);
                var typedE2 = typeCheckAndConvert(e2);
                var t1 = typedE1.type();
                var t2 = typedE2.type();
                Pointer ptrType;
                if (t1 instanceof Pointer p && t2.isInteger() && isPointerToComplete(t1)) {
                    ptrType = p;
                    typedE2 = convertTo(typedE2, LONG);
                } else if (t1.isInteger() && t2 instanceof Pointer p && isPointerToComplete(t2)) {
                    ptrType = p;
                    typedE1 = convertTo(typedE1, LONG);
                } else
                    throw new Err("Subscript must have integer and pointer " + "(to complete type) operands");
                yield new Subscript(typedE1, typedE2, ptrType.referenced());
            }
            //    default -> throw new Todo("Unexpected value: " + exp);
            case SizeOf(Exp e) -> {
                Exp typedE = typeCheckExpression(e);
                if (!isComplete(typedE.type())) {
                    fail("Complete type required here");
                }
                yield new SizeOf(typedE);
            }
            case SizeOfT(Type typeToSize) -> {
                validateTypeSpecifier(typeToSize);
                if (!isComplete(typeToSize)) {
                    fail("Complete type required here");
                }
                yield exp;
            }

            case Arrow(Exp pointer, String member, Type _) -> {
                Exp typedPointer = typeCheckAndConvert(pointer);
                if (typedPointer.type() instanceof Pointer(
                        Type structure) && structure instanceof Structure(
                        String tag)) {
                    StructDef structDef = Mcc.TYPE_TABLE.get(tag);
                    MemberEntry me = structDef.findMember(member);
                    if (me == null) {
                        throw new Err("Structure has no member with this name");
                    }
                    yield new Arrow(typedPointer, member, me.type());
                }
                throw new Err("Tried to get member of non-structure");
            }
            case Dot(Exp structure, String member, Type _) -> {
                Exp typedStructure = typeCheckAndConvert(structure);
                if (typedStructure.type() instanceof Structure(String tag)) {
                    StructDef structDef = Mcc.TYPE_TABLE.get(tag);
                    MemberEntry me = structDef.findMember(member);
                    if (me == null) {
                        throw new Err("Structure has no member with this name");
                    }
                    yield new Dot(typedStructure, member, me.type());
                }
                throw new Err("Tried to get member of non-structure");
            }
        };
    }

    /**
     * When a character value is used in some operators, it needs to be
     * promoted to int
     */
    private static Exp promoteIfNecessary(Exp e) {
        return e.type().isCharacter() ? convertTo(e, INT) : e;
    }


    private static boolean isComplete(Type t) {
        return switch (t) {
            case VOID -> false;
            case Structure(String tag) -> Mcc.TYPE_TABLE.containsKey(tag);
            default -> true;
        };
    }

    private static boolean isPointerToComplete(Type t1) {
        return t1 instanceof Pointer(Type referenced) && isComplete(referenced);
    }

    private static Type getCommonPointerType(Exp e1, Exp e2) {
        Type t1 = e1.type();
        Type t2 = e2.type();
        if (t1.equals(t2)) return t1;
        if (isNullPointerConstant(e1)) return t2;
        if (isNullPointerConstant(e2)) return t1;
        if (t1 instanceof Pointer(Type r1) && t2 instanceof Pointer(Type r2)) {
            if (r1 == VOID) return t1;
            if (r2 == VOID) return t2;
        }
        throw new Err("Expressions have incompatible types");
    }

    private static boolean isNullPointerConstant(Exp e) {
        return switch (e) {
            case IntInit(int i) -> i == 0;
            case LongInit(long l) -> l == 0L;
            case UIntInit(int i) -> i == 0;
            case ULongInit(long l) -> l == 0;
            default -> false;
        };
    }

    private static boolean isLvalue(Exp exp) {
        if (exp instanceof BinaryOp binop && binop.op() instanceof CompoundAssignmentOperator)
            return true;
        if (exp instanceof Dot(Exp structure, String member, Type type))
            return isLvalue(structure);
        return exp instanceof Dereference || exp instanceof Var || exp instanceof Subscript || exp instanceof Str || exp instanceof Arrow;
    }

    private static Type getCommonType(Type t1, Type t2) {
        t1 = t1.isCharacter() ? INT : t1;
        t2 = t2.isCharacter() ? INT : t2;
        if (t1 == t2) return t1;
        if (t1 == DOUBLE || t2 == DOUBLE) return DOUBLE;
        if ((long) Mcc.size(t1) == (long) Mcc.size(t2))
            return t1.isSigned() ? t2 : t1;
        if ((long) Mcc.size(t1) > (long) Mcc.size(t2)) return t1;
        return t2;
    }

    public static Type resolveType(Type typeSpecifier,
                                   Map<String, StructureEntry> structureMap) {
        return switch (typeSpecifier) {
            case Structure(String tag) -> {
                var e = structureMap.get(tag);
                if (e != null) yield new Structure(e.name());
                else
                    throw new Err("Specified an undeclared structure: tag=" + tag);
            }
            case Pointer(Type referenced) ->
                    new Pointer(resolveType(referenced, structureMap));
            case Array(Type element, Constant size) ->
                    new Array(resolveType(element, structureMap), size);
            case FunType(List<Type> params, Type ret) ->
                    new FunType(params.stream().map(p -> resolveType(p,
                            structureMap)).toList(), resolveType(ret,
                            structureMap));
            case Primitive primitive -> primitive;
        };
    }

    public static Program resolveProgram(Program program) {
        Map<String, Entry> identifierMap = new HashMap<>();
        Map<String, StructureEntry> structureMap = new HashMap<>();
        ArrayList<Declaration> decls = program.declarations();
        for (int i = 0; i < decls.size(); i++) {
            switch (decls.get(i)) {
                case Function f ->
                        decls.set(i, resolveFunctionDeclaration(f,
                                identifierMap, structureMap));
                case VarDecl varDecl ->
                        decls.set(i,
                                resolveFileScopeVariableDeclaration(varDecl,
                                        identifierMap, structureMap));
                case StructDecl decl ->
                        decls.set(i, resolveStructureDeclaration(decl,
                                structureMap));
            }

        }
        return program;
    }

    private static StructDecl resolveStructureDeclaration(StructDecl decl,
                                                          Map<String,
                                                                  StructureEntry> structureMap) {
        StructureEntry prevEntry = structureMap.get(decl.tag());
        String uniqueTag;
        if (prevEntry == null || !prevEntry.fromCurrentScope()) {
            uniqueTag = makeTemporary(decl.tag() + ".");
            structureMap.put(decl.tag(), new StructureEntry(uniqueTag, true));
        } else {
            uniqueTag = prevEntry.name();

        }
        ArrayList<MemberDeclaration> processedMembers;
        if (decl.members() == null) processedMembers = null;
        else {
            processedMembers = new ArrayList<>();
            for (MemberDeclaration member : decl.members()) {
                for (var p : processedMembers) {
                    if (p.name().equals(member.name())) {
                        throw new Err("Duplicate structure member name");
                    }
                }
                processedMembers.add(new MemberDeclaration(resolveType(member.type(), structureMap), member.name()));
            }
        }
        return new StructDecl(uniqueTag, processedMembers);
    }

    private static Declaration resolveFileScopeVariableDeclaration(
            VarDecl varDecl, Map<String, Entry> identifierMap,
            Map<String, StructureEntry> structureMap) {
        return switch (varDecl) {
            case VarDecl(Var name, Initializer init, Type type,
                         StorageClass storageClass) -> {
                identifierMap.put(name.name(), new Entry(name.name(), true,
                        true));
                Type t = resolveType(type, structureMap);
                yield new VarDecl(new Var(name.name(), t), init, t,
                        storageClass);
            }
        };
    }

    private static Block resolveBlock(Block block,
                                      Map<String, Entry> identifierMap,
                                      Map<String, StructureEntry> structureMap) {
        ArrayList<BlockItem> blockItems = new ArrayList<>();
        for (BlockItem i : block.blockItems()) {
            blockItems.add(resolveIdentifiersBlockItem(i, identifierMap,
                    structureMap));
        }
        return new Block(blockItems);
    }

    private static Function resolveFunctionDeclaration(Function function,
                                                       Map<String, Entry> identifierMap,
                                                       Map<String,
                                                               StructureEntry> structureMap) {
        String name = function.name();
        if (identifierMap.get(name) instanceof Entry previousEntry) {
            if (previousEntry.fromCurrentScope() && !previousEntry.hasLinkage()) {
                throw new Err("Duplicate declaration: " + name);
            }
        }
        identifierMap.put(name, new Entry(name, true, true));
        Map<String, Entry> innerMap = copyIdentifierMap(identifierMap);
        Map<String, StructureEntry> innerStructureMap =
                copyStructureMap(structureMap);
        List<Var> newArgs = resolveParams(function.parameters(), innerMap,
                innerStructureMap);

        Block newBody = function.body() instanceof Block block ?
                resolveBlock(block, innerMap, innerStructureMap) : null;
        return new Function(function.name(), newArgs, newBody,
                resolveFunType(function.funType(), innerStructureMap),
                function.storageClass());
    }

    private static FunType resolveFunType(FunType funType,
                                          Map<String, StructureEntry> structureMap) {
        return new FunType(funType.params().stream().map(p -> resolveType(p,
                structureMap)).toList(), resolveType(funType.ret(),
                structureMap));
    }

    private static List<Var> resolveParams(List<Var> parameters,
                                           Map<String, Entry> identifierMap,
                                           Map<String, StructureEntry> innerStructureMap) {
        List<Var> newParams = new ArrayList<>();
        for (Var d : parameters) {
            if (identifierMap.get(d.name()) instanceof Entry e && e.fromCurrentScope()) {
                fail("Duplicate variable declaration");
            }
            String uniqueName = makeTemporary(d.name() + ".");
            identifierMap.put(d.name(), new Entry(uniqueName, true, false));
            newParams.add(new Var(uniqueName, resolveType(d.type(),
                    innerStructureMap)));
        }
        return newParams;
    }

    private static BlockItem resolveIdentifiersBlockItem(BlockItem blockItem,
                                                         Map<String, Entry> identifierMap,
                                                         Map<String,
                                                                 StructureEntry> structureMap) {
        return switch (blockItem) {
            case VarDecl declaration ->
                    resolveLocalIdentifierDeclaration(declaration,
                            identifierMap, structureMap);
            case Statement statement ->
                    resolveStatement(statement, identifierMap, structureMap);
            case Function function ->
                    resolveFunctionDeclaration(function, identifierMap,
                            structureMap);
            case StructDecl structDecl ->
                    resolveStructureDeclaration(structDecl, structureMap);
        };
    }

    private static Statement resolveStatement(Statement blockItem,
                                              Map<String, Entry> identifierMap,
                                              Map<String, StructureEntry> structureMap) {
        return switch (blockItem) {
            case null -> null;
            case Exp exp -> resolveExp(exp, identifierMap, structureMap);
            case LabelledStatement(String label, Statement statement) ->
                    new LabelledStatement(label, resolveStatement(statement,
                            identifierMap, structureMap));
            case CaseStatement(Switch enclosingSwitch, Constant<?> label,
                               Statement statement) ->
                    new CaseStatement(enclosingSwitch, label,
                            resolveStatement(statement, identifierMap,
                                    structureMap));
            case Return(Exp exp) ->
                    new Return(resolveExp(exp, identifierMap, structureMap));
            case If(Exp condition, Statement ifTrue, Statement ifFalse) ->
                    new If(resolveExp(condition, identifierMap, structureMap)
                            , resolveStatement(ifTrue, identifierMap,
                            structureMap), resolveStatement(ifFalse,
                            identifierMap, structureMap));
            case Block block ->
                    resolveBlock(block, copyIdentifierMap(identifierMap),
                            copyStructureMap(structureMap));
            case NullStatement nullStatement -> nullStatement;
            case Break _, Continue _, Goto _ -> blockItem;
            case DoWhile(Statement body, Exp condition, String label) ->
                    new DoWhile(resolveStatement(body, identifierMap,
                            structureMap), resolveExp(condition,
                            identifierMap, structureMap), label);
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> {
                Map<String, Entry> newVariableMap =
                        copyIdentifierMap(identifierMap);
                Map<String, StructureEntry> newStructureMap =
                        copyStructureMap(structureMap);
                yield new For(resolveForInit(init, newVariableMap,
                        newStructureMap), resolveExp(condition,
                        newVariableMap, newStructureMap), resolveExp(post,
                        newVariableMap, newStructureMap),
                        resolveStatement(body, newVariableMap,
                                newStructureMap), label);
            }
            case While(Exp condition, Statement body, String label) ->
                    new While(resolveExp(condition, identifierMap,
                            structureMap), resolveStatement(body,
                            identifierMap, structureMap), label);
            case Switch switchStatement -> {
                switchStatement.exp = resolveExp(switchStatement.exp,
                        identifierMap, structureMap);
                switchStatement.body = resolveStatement(switchStatement.body,
                        identifierMap, structureMap);
                yield switchStatement;
            }
        };

    }

    private static ForInit resolveForInit(ForInit init,
                                          Map<String, Entry> identifierMap,
                                          Map<String, StructureEntry> structureMap) {
        return switch (init) {
            case VarDecl declaration ->
                    resolveLocalIdentifierDeclaration(declaration,
                            identifierMap, structureMap);
            case Exp exp -> resolveExp(exp, identifierMap, structureMap);
            case null -> null;
        };
    }


    private static Map<String, Entry> copyIdentifierMap(Map<String, Entry> m) {
        Map<String, Entry> copy = HashMap.newHashMap(m.size());
        for (Map.Entry<String, Entry> e : m.entrySet()) {
            Entry v = e.getValue();
            copy.put(e.getKey(), new Entry(v.name(), false, v.hasLinkage()));
        }
        return copy;
    }


    private static Map<String, StructureEntry> copyStructureMap(
            Map<String, StructureEntry> m) {
        Map<String, StructureEntry> copy = HashMap.newHashMap(m.size());
        for (var e : m.entrySet()) {
            var v = e.getValue();
            copy.put(e.getKey(), new StructureEntry(v.name(), false));
        }
        return copy;

    }


    private static VarDecl resolveLocalIdentifierDeclaration(VarDecl decl,
                                                             Map<String,
                                                                     Entry> identifierMap,
                                                             Map<String,
                                                                     StructureEntry> structureMap) {
        if (identifierMap.get(decl.name().name()) instanceof Entry prevEntry) {
            if (prevEntry.fromCurrentScope()) {
                if (!(prevEntry.hasLinkage() && decl.storageClass() == EXTERN)) {
                    fail("Conflicting local declaration");
                }
            }

        }
        if (decl.storageClass() == EXTERN) {
            identifierMap.put(decl.name().name(),
                    new Entry(decl.name().name(), true, true));
            return new VarDecl(decl.name(), decl.init(),
                    resolveType(decl.varType(), structureMap),
                    decl.storageClass());
        }
        String uniqueName = makeTemporary(decl.name().name() + ".");
        identifierMap.put(decl.name().name(), new Entry(uniqueName, true,
                false));
        var init = decl.init();
        return new VarDecl(new Var(uniqueName, null), resolveInitializer(init
                , identifierMap, structureMap), resolveType(decl.varType(),
                structureMap), decl.storageClass());
    }

    private static Initializer resolveInitializer(Initializer init,
                                                  Map<String, Entry> identifierMap,
                                                  Map<String, StructureEntry> structureMap) {
        return switch (init) {
            case null -> null;
            case CompoundInit(ArrayList<Initializer> inits, Type type) -> {
                inits.replaceAll(i -> resolveInitializer(i, identifierMap,
                        structureMap));
                yield new CompoundInit(inits, type);
            }
            case SingleInit(Exp exp, Type targetType) ->
                    new SingleInit(resolveExp(exp, identifierMap,
                            structureMap), targetType);
        };

    }

    private static <T extends Exp> T resolveExp(T exp,
                                                Map<String, Entry> identifierMap,
                                                Map<String, StructureEntry> structureMap) {
        @SuppressWarnings("unchecked") T r = (T) switch (exp) {
            case null -> null;
            case Assignment(Exp left, Exp right, Type type) ->
                    isLvalue(left) ? new Assignment(resolveExp(left,
                            identifierMap, structureMap), resolveExp(right,
                            identifierMap, structureMap), type) : fail(
                                    "Invalid lvalue");
            case CompoundAssignment(CompoundAssignmentOperator op, Exp left,
                                    Exp right, Type tempType, Type type) ->
                    op instanceof CompoundAssignmentOperator && !isLvalue(left) ? fail("Invalid lvalue") : new CompoundAssignment(op, resolveExp(left, identifierMap, structureMap), resolveExp(right, identifierMap, structureMap), tempType, type);
            case BinaryOp(BinaryOperator op, Exp left, Exp right, Type type) ->
                    op instanceof CompoundAssignmentOperator && !isLvalue(left) ? fail("Invalid lvalue") : new BinaryOp(op, resolveExp(left, identifierMap, structureMap), resolveExp(right, identifierMap, structureMap), type);
            case Constant constant -> constant;
            case Str str -> str;
            case UnaryOp(UnaryOperator op, Exp arg, Type type) ->
                    (op == POST_INCREMENT || op == POST_DECREMENT) && !isLvalue(arg) ? fail("Invalid lvalue") : new UnaryOp(op, resolveExp(arg, identifierMap, structureMap), type);
            case AddrOf(Exp arg, Type type) ->
                    new AddrOf(resolveExp(arg, identifierMap, structureMap),
                            type);
            case Dereference(Exp arg, Type type) ->
                    new Dereference(resolveExp(arg, identifierMap,
                            structureMap), type);
            case Var(String name, Type type) ->
                    identifierMap.get(name) instanceof Entry e ?
                            new Var(e.name(), type) : fail("Undeclared " +
                            "variable:" + exp);
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type) ->
                    new Conditional(resolveExp(condition, identifierMap,
                            structureMap), resolveExp(ifTrue, identifierMap,
                            structureMap), resolveExp(ifFalse, identifierMap,
                            structureMap), type);
            case FunctionCall(Var name, List<Exp> args, Type type) ->
                    identifierMap.get(name.name()) instanceof Entry newFunctionName ? new FunctionCall(new Var(newFunctionName.name(), type), resolveArgs(identifierMap, structureMap, args), type) : fail("Undeclared function:" + name);
            case Cast(Type type, Exp e) -> {
                Type resolvedType = resolveType(type, structureMap);
                yield new Cast(resolvedType, resolveExp(e, identifierMap,
                        structureMap));
            }
            case Subscript(Exp array, Exp index, Type type) ->
                    new Subscript(resolveExp(array, identifierMap,
                            structureMap), resolveExp(index, identifierMap,
                            structureMap), type);
            case SizeOf(Exp e) ->
                    new SizeOf(resolveExp(e, identifierMap, structureMap));
            case SizeOfT(Type type) ->
                    new SizeOfT(resolveType(type, structureMap));
            case Arrow(Exp pointer, String member, Type type) ->
                    new Arrow(resolveExp(pointer, identifierMap,
                            structureMap), member, type);
            case Dot(Exp structure, String member, Type type) ->
                    new Dot(resolveExp(structure, identifierMap,
                            structureMap), member, type);
        };
        return r;
    }

    private static <T extends Exp> List<T> resolveArgs(
            Map<String, Entry> identifierMap,
            Map<String, StructureEntry> structureMap, List<T> args) {
        List<T> newArgs = new ArrayList<>();
        for (T arg : args) {
            newArgs.add(resolveExp(arg, identifierMap, structureMap));
        }
        return newArgs;
    }

    private static Exp fail(String s) {
        throw new Err(s);
    }


}
