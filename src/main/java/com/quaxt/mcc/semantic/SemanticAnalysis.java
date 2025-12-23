package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.CharInit;
import com.quaxt.mcc.parser.StructOrUnionSpecifier;
import com.quaxt.mcc.tacky.*;
import com.quaxt.mcc.UCharInit;

import java.util.*;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.InitialValue.NoInitializer.NO_INITIALIZER;
import static com.quaxt.mcc.InitialValue.Tentative.TENTATIVE;
import static com.quaxt.mcc.Mcc.*;
import static com.quaxt.mcc.UnaryOperator.*;
import static com.quaxt.mcc.optimizer.Optimizer.optimizeInstructions;
import static com.quaxt.mcc.parser.BuiltInFunction.BUILTIN_ADD_OVERFLOW;
import static com.quaxt.mcc.parser.BuiltInFunction.BUILTIN_SUB_OVERFLOW;
import static com.quaxt.mcc.parser.Nullptr.NULLPTR;
import static com.quaxt.mcc.parser.StorageClass.*;
import static com.quaxt.mcc.semantic.NullptrT.NULLPTR_T;
import static com.quaxt.mcc.semantic.Primitive.*;

public class SemanticAnalysis {

    public record Entry(String name, boolean fromCurrentScope, boolean hasLinkage) {}

    enum TagEntryType {UNION, STRUCT, ENUM}

    public record TagEntry(TagEntryType isUnion, String name,
                    boolean fromCurrentScope) {

    }

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
        return new Function(function.name, function.parameters,
                loopLabelStatement(function.body, null, null),
                function.funType, function.storageClass, function.callsVaStart,
                function.usesFunc, function.inline);

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
                    case StructOrUnionSpecifier structDecl -> structDecl;
                    case EnumSpecifier enumSpecifier -> throw new Todo();
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
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String _) -> {
                String newLabel = makeTemporary(".Lfor.");
                ForInit labeledForInit = switch (init) {
                    case null -> null;
                    case Exp exp -> loopLabelStatement(exp, newLabel, newLabel);
                    case DeclarationList dl -> {
                        List<Declaration> list = dl.list();
                        list.replaceAll(decl -> loopLabelVarDecl((VarDecl) decl, newLabel, newLabel));
                        yield dl;
                    }
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
            case Exp _, Goto _, BuiltinC23VaStart _, BuiltinVaEnd _ ->
                    statement;
            default ->
                    throw new IllegalStateException("Unexpected value: " + statement);
        };
    }

    private static VarDecl loopLabelVarDecl(VarDecl declaration,
                                            String currentLabel,
                                            String currentNonSwitchLabel) {
        Initializer init = declaration.init();
        return new VarDecl(declaration.name(), loopLabelInitializer(init,
                currentLabel, currentNonSwitchLabel), declaration.varType(),
                declaration.storageClass(), declaration.structOrUnionSpecifier());
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

                case StructOrUnionSpecifier structDecl -> {
                    typeCheckStructureDeclaration(structDecl);
                    yield structDecl;
                }
                case EnumSpecifier enumSpecifier -> enumSpecifier;
            });
        }
    }

    private static StructDef typeCheckStructureDeclaration(
            StructOrUnionSpecifier structDecl) {
        if (structDecl == null || structDecl.members() == null) return null;

        var existing = TYPE_TABLE.get(structDecl.tag());
        ArrayList<MemberEntry> memberEntries = new ArrayList<>();
        boolean isUnion = structDecl.isUnion();
        StructDef sd = new StructDef(isUnion,
                0, 0, memberEntries);

        for (MemberDeclaration member : structDecl.members()) {
            var innerSd = typeCheckStructureDeclaration(
                    member.structOrUnionSpecifier());
            if (innerSd != null &&
                    member.structOrUnionSpecifier().isAnonymous() &&
                    member.name() == null) {
                for (MemberEntry innerMember : innerSd.members()) {
                    sd = addMemberToStruct(innerMember.name(), innerMember.type(), sd,
                            toInteger(member.bitFieldWidth()));
                }
            } else {
                sd = addMemberToStruct(member.name(), member.type(), sd, toInteger(member.bitFieldWidth()));
            }
        }
        validateStructDefinition(structDecl);

        int structSize = roundUp(sd.size(), sd.alignment());

        sd = new StructDef(sd.isUnion(),sd.alignment(), structSize, sd.members());
        if (existing!=null && !sd.equals(existing)) {
                throw new Err("Incompatible redefinition of struct");

        }
        TYPE_TABLE.put(structDecl.tag(), sd);
        return sd;
    }

    private static Integer toInteger(Constant c) {
            if (c == null) return null;
            return (int)c.toLong();
    }

    private static StructDef addMemberToStruct(
            String name,
            Type memberType,
            StructDef sd,
            Integer bitFieldWidth
    ) {
        memberType = completeType(memberType);
        int memberAlignment = typeAlignment(memberType);
        int memberSize = (int) size(memberType);

        ArrayList<MemberEntry> members = sd.members();
        int structAlignment = Math.max(sd.alignment(), memberAlignment);

        // ---- Union case (easy) ----
        if (sd.isUnion()) {
            int offset = 0;
            MemberEntry m = (bitFieldWidth == null)
                    ? new OrdinaryMember(name, memberType, offset)
                    : new BitFieldMember(name, memberType, offset, 0, bitFieldWidth);
            members.add(m);
            int newSize = Math.max(sd.size(), memberSize);
            return new StructDef(true, structAlignment, newSize, members);
        }

        // ---- Ordinary member ----
        if (bitFieldWidth == null) {
            // Align struct size to member's alignment.
            int offset = roundUp(sd.size(), memberAlignment);
            members.add(new OrdinaryMember(name, memberType, offset));
            int newSize = offset + memberSize;
            return new StructDef(false, structAlignment, newSize, members);
        }

        // ---- Bit-field member ----
        int unitBits = memberSize * 8;

        MemberEntry last = members.isEmpty() ? null : members.get(members.size() - 1);

        int baseOffset, bitOffset;

        if (last instanceof BitFieldMember lastBF
                && lastBF.type().equals(memberType)) {

            int lastEndBit = lastBF.bitOffset() + lastBF.bitWidth();
            int lastUnitBits = 8 * (int) size(lastBF.type());

            // If it fits in same unit → pack it
            if (lastBF.bitWidth() != 0 && lastEndBit + bitFieldWidth <= lastUnitBits) {
                baseOffset = lastBF.byteOffset();
                bitOffset = lastEndBit;
            } else {
                // Not enough bits left → new unit after current one
                baseOffset = lastBF.byteOffset() + (int) size(lastBF.type());
                bitOffset = 0;
            }
        } else {
            // No previous bit-field or type changed → new unit
            baseOffset = roundUp(sd.size(), memberAlignment);
            bitOffset = 0;
        }

        MemberEntry m = new BitFieldMember(name, memberType, baseOffset, bitOffset, bitFieldWidth);
        members.add(m);

        // Compute new struct size:
        int endOfStruct = Math.max(sd.size(), baseOffset + memberSize);
        return new StructDef(false, structAlignment, endOfStruct, members);
    }



    private static int roundUp(int x, int n) {
        int rem = x % n;
        if (rem == 0) return x;
        return x - rem + n;
    }

    private static void validateStructDefinition(
            StructOrUnionSpecifier structDecl) {
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
            case Pointer _, NullptrT _ -> {
                size *= 8;
                break out;
            }
            case Primitive primitive -> {
                size *= size(primitive);
                break out;
            }

            case Structure(boolean isUnion, String tag, StructDef _) -> {
                size *= TYPE_TABLE.get(tag).size();
                break out;
            }

            default -> throw new IllegalStateException("Unexpected value: " + targetType);
        }
        return new ZeroInit(size);
    }

    private static Constant convertCompoundInitializerToStaticInitList(
            Initializer init, Type targetType, List<StaticInit> acc) {
        switch (init) {
            case null -> acc.add(createZeroInit(targetType));
            case CompoundInit(ArrayList<Initializer> inits, Type type) -> {
                final int initsSize = inits.size();
                switch (targetType) {
                    case Array(Type inner, Constant arraySize) -> {
                        long declaredLength =
                                arraySize == null ? -1L : arraySize.toLong();
                        if (arraySize != null && declaredLength < initsSize) {
                            throw new Err(
                                    "Length of initializer (" + initsSize +
                                            ") exceeds declared length of " +
                                            "array (" +
                                            arraySize + ")");
                        }
                        inits.forEach(i -> convertCompoundInitializerToStaticInitList(i, inner, acc));
                        if (arraySize != null && declaredLength < initsSize) {
                            throw new Err(
                                    "Length of initializer (" + initsSize +
                                            ") exceeds declared length of " +
                                            "array (" +
                                            arraySize + ")");
                        }
                        if (arraySize != null && declaredLength > initsSize) {
                            acc.add(createZeroInit(new Array(inner,
                                    new ULongInit(
                                    declaredLength - initsSize))));
                        }
                        return arraySize ==
                                null ? new IntInit(initsSize) : arraySize;
                    }

                    case Pointer _, NullptrT _, FunType _, Primitive _ ->
                            throw new Err("illegal compound initializer for " + "scalar type:" + targetType);

                    case Structure(boolean isUnion, String tag, StructDef _) -> {
                        StructDef structDef = TYPE_TABLE.get(tag);
                        if (initsSize > structDef.members().size() || (isUnion && initsSize > 1)) {
                            throw new Err("Too many elements in " + (isUnion
                                    ? "union " : "structure") + "initializer");
                        }
                        int currentOffset = 0;
                        int i = 0;

                        for (var initElement : inits) {
                            MemberEntry member = structDef.members().get(i);
                            if (member.byteOffset() != currentOffset) {
                                acc.add(new ZeroInit(member.byteOffset() - currentOffset));
                            }
                            convertCompoundInitializerToStaticInitList(initElement, member.type(), acc);
                            currentOffset =
                                    (int) (member.byteOffset() + size(member.type()));
                            i++;
                        }
                        if (structDef.size() != currentOffset) {
                            acc.add(new ZeroInit(structDef.size() - currentOffset));
                        }
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + targetType);
                }
            }
            case SingleInit(Exp exp, Type t) -> {
                if (exp instanceof Str(String s,
                                       Type _) && targetType instanceof Array(
                        Type element, Constant arraySize)) {
                    return handleStringLiteral(acc, s, element, arraySize, false);
                } else if (exp instanceof Str(String s,
                                              Type _) && targetType instanceof Pointer(
                        Type element)) {
                    if (element != CHAR) {
                        throw new Err("Can't initialize pointer to " + element + " with string literal");
                    }
                    handleStringLiteral(acc,s,element,null, true);

                } else if (exp instanceof AddrOf(Exp exp1, Type type1) &&
                        exp1 instanceof Str(String s, Type _) &&
                        targetType instanceof Pointer(Type element)) {
                    if (!element.isCharacter()) {
                        throw new Err("Can't initialize pointer to " + element +
                                " with string literal");
                    }
                    handleStringLiteral(acc, s, element, null, true);
                } else if (exp instanceof Cast(Type type, Exp exp1) &&
                        type instanceof Pointer(Type referenced) &&
                        referenced.isCharacter()) {
                    convertCompoundInitializerToStaticInitList(new SingleInit(exp1, t), targetType, acc);
                } else {
                    if (targetType instanceof Array) {
                        throw new Err("Can't initialize static array with a " + "scalar");
                    }
                    if (targetType instanceof Structure) {
                        throw new Err("Can't initialize structure with a " +
                                "scalar");
                    }
                    StaticInit v = switch (exp) {
                        case Constant cc -> (StaticInit)convertConst(cc, targetType);
                        default -> convertToStaticInit(exp, targetType);
                    };
                    acc.add(v);
                }
            }
        }
        return null;
    }

    private static StaticInit convertToStaticInit(Exp exp, Type targetType) {
        if (exp instanceof Cast(Type type, Exp inner)){
            if (type instanceof Pointer){
                return convertToStaticInit(inner, targetType);
            }
        }
        if (exp instanceof AddrOf(Exp inner, Type _)) {
            if (inner instanceof Subscript(Var e1, Exp e2, Type _) && evaluateExpAsConstant(e2) instanceof Constant c) {
                return new PointerInit(e1.name(),
                        c.toLong());
            }
            if (inner instanceof Subscript(Exp e2, Var e1, Type _)&& evaluateExpAsConstant(e2) instanceof Constant c) {
                return new PointerInit(e1.name(),
                        c.toLong());

            } else if (inner instanceof Var v) {
                return new PointerInit(v.name());
            }
        }
        if (evaluateExpAsConstant(exp) instanceof Constant c) {
            return (StaticInit) convertConst(c, targetType);
        }
        if (exp instanceof Var(String name, Type type) && targetType instanceof Pointer(Type referenced)  && referenced instanceof FunType){
            return new PointerInit(name);
        }

        throw new Err("Non constant initializer");

    }

    static Map<String, String> STRING_TABLE = new HashMap<>();

    /* returns the length of the String literal including trailing zero byte(s)*/
    private static Constant handleStringLiteral(List<StaticInit> acc, String s,
                                                Type element, Constant arrayLen,
                                                boolean pointerify) {
        if (!element.isCharacter())
            throw new Err("Can't initialize non-character type with a string "
                    + "literal");
        if (arrayLen != null && s.length() > arrayLen.toLong())
            throw new Err("Too many chars in string literal");
        // how many zeros past the first one that comes with asciz
        long zeroCount = arrayLen != null ? arrayLen.toLong() - s.length() - 1 : 1;
        if (pointerify){
            List<StaticInit> temp = new ArrayList<>();
            temp.add(new StringInit(s, zeroCount >= 0));
            if (zeroCount > 0) temp.add(new ZeroInit(zeroCount));
            String symbolName = addStringToSymbolTable(new Pointer(Primitive.CHAR), temp);
            acc.add(new PointerInit(symbolName, 0));
        } else {
            acc.add(new StringInit(s, zeroCount >= 0));
            if (zeroCount > 0) acc.add(new ZeroInit(zeroCount));
        }
        return arrayLen == null ? new LongInit(
                s.length() + zeroCount) : arrayLen;
    }

    private static VarDecl typeCheckFileScopeVariableDeclaration(VarDecl decl) {
        var structOrUnionSpecifier = decl.structOrUnionSpecifier();
        StructDef sd = null;
        if (structOrUnionSpecifier != null) {
            sd = typeCheckStructureDeclaration(structOrUnionSpecifier);
        }
        InitialValue initialValue;
        var varType = decl.varType();
        if (varType == VOID && decl.storageClass() != TYPEDEF) {
            fail("Can't declare void variable");
        }
        if (decl.init() == null) initialValue =
                decl.storageClass() == EXTERN ? NO_INITIALIZER : TENTATIVE;
        else {
            ArrayList<StaticInit> staticInits = new ArrayList<>();
            Constant initArrayLen =
                    convertCompoundInitializerToStaticInitList(decl.init(), varType, staticInits);
            if (varType instanceof Array(Type element, Constant arraySize) &&
                    arraySize == null) {
                varType = new Array(element, initArrayLen);
            }

            initialValue = new Initial(staticInits);
        }
        if (((sd != null && !isComplete(sd)) || !isComplete(varType)) &&
                ((decl.storageClass() != EXTERN &&
                        decl.storageClass() != TYPEDEF) ||
                        decl.init() != null)) {
//            if (!(varType instanceof Array))
            fail("Can't declare incomplete variable");
//            else {
//                System.out.println("todo");
//            }
        }

        validateTypeSpecifier(varType);
        if (varType instanceof Pointer &&
                decl.init() instanceof SingleInit(Exp exp, Type targetType) &&
                exp instanceof Constant c && !isNullPointerConstant(c)) {
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
                                                  boolean _, StorageClass _)) {

                if (oldInit instanceof Initial oldInitialConstant) {
                    if (initialValue instanceof Initial)
                        fail("Conflicting file scope variable definitions");
                    else initialValue = oldInitialConstant;
                } else if (!(initialValue instanceof Initial) &&
                        oldInit == TENTATIVE) initialValue = TENTATIVE;
            }
        }
        // when initializing a static pointer with a string
        if (varType instanceof Pointer(Type referenced)
                && referenced == CHAR && initialValue instanceof Initial(List<StaticInit> staticInits)
        && !isStringPointerInit(staticInits)) {
            pointerify(referenced, staticInits, initialValue, decl,
                    varType);
        } else {
            StaticAttributes attrs =
                    new StaticAttributes(initialValue, global,
                     decl.storageClass());
            SYMBOL_TABLE.put(decl.name().name(), new SymbolTableEntry(varType, attrs));
        }
        return decl;
    }

    private static double unsignedLongToDouble(long ul) {
        if (ul > 0) return (double) ul;
        long half = ul >>> 1;
        long parity = ul & 1;
        long roundedToOdd = half | parity;
        double d = (double) roundedToOdd;
        return d + d;
    }

    private static long doubleToUnsignedLong(double d) {
        if (d > 0x1.0p63) {
            return (long) (d - 0x1.0p63) + (1L << 63);
        }
        return (long) d;
    }

    public static Constant convertConst(Constant init, Type type) {
        if (init instanceof DoubleInit(double d)) {
            return switch (type) {
                case DOUBLE -> (DoubleInit) init;
                case FLOAT -> new FloatInit((float) d);
                case LONG -> new LongInit((long) d);
                case LONGLONG -> new LongLongInit((long) d);
                case INT -> new IntInit((int) d);
                case ULONG -> new ULongInit(doubleToUnsignedLong(d));
                case ULONGLONG -> new ULongLongInit(doubleToUnsignedLong(d));
                // casting directly to int would be wrong result for doubles
                // > 2^31
                case UINT -> new UIntInit((int) (long) d);
                case CHAR, SCHAR -> new CharInit((byte) (long) d);
                case SHORT -> new ShortInit((short) (long) d);
                case UCHAR -> new UCharInit((byte) doubleToUnsignedLong(d));
                case USHORT -> new UShortInit((short) doubleToUnsignedLong(d));
                default ->
                        throw new IllegalArgumentException("not a const:" + init);
            };
        }
        if (init instanceof FloatInit(float d)) {
            return switch (type) {
                case DOUBLE -> new DoubleInit(d);
                case FLOAT -> (FloatInit) init;
                case LONG -> new LongInit((long) d);
                case LONGLONG -> new LongLongInit((long) d);
                case INT -> new IntInit((int) d);
                case ULONG -> new ULongInit(doubleToUnsignedLong(d));
                case ULONGLONG -> new ULongLongInit(doubleToUnsignedLong(d));
                // casting directly to int would be wrong result for floats
                // > 2^31
                case UINT -> new UIntInit((int) (long) d);
                case CHAR, SCHAR -> new CharInit((byte) (long) d);
                case SHORT -> new ShortInit((short) (long) d);
                case UCHAR -> new UCharInit((byte) doubleToUnsignedLong(d));
                case USHORT -> new UShortInit((short) doubleToUnsignedLong(d));
                default ->
                        throw new IllegalArgumentException("not a const:" + init);
            };
        }
        long initL = switch (init) {
            case IntInit(int i) -> i;
            case CharInit(byte i) -> i;
            case UCharInit(byte i) -> i & 0xff;
            case BoolInit(byte i) -> i & 0xff;
            case ShortInit(short i) -> i;
            case UShortInit(short i) -> i & 0xffff;
            case LongInit(long l) -> l;
            case LongLongInit(long l) -> l;
            case UIntInit(int i) -> Integer.toUnsignedLong(i);
            case ULongInit(long l) -> l;
            case ULongLongInit(long l) -> l;
            case Nullptr _-> 0L;
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
            case LONGLONG -> new LongLongInit(initL);
            case INT -> new IntInit((int) initL);
            case BOOL -> new BoolInit(initL==0?(byte)0:(byte)1);
            case ULONG -> new ULongInit(initL);
            case ULONGLONG -> new ULongLongInit(initL);
            case UINT -> new UIntInit((int) initL);
            case CHAR, SCHAR -> new CharInit((byte) initL);
            case UCHAR -> new UCharInit((byte) initL);
            case SHORT -> new ShortInit((short) initL);
            case USHORT -> new UShortInit((short) initL);
            case Pointer _ -> new ULongInit((int) initL);
            default -> null;
        };
    }


    private static Function typeCheckFunctionDeclaration(Function decl,
                                                         boolean blockScope) {
        Type ret = decl.funType.ret();
        boolean varargs = decl.funType.varargs();
        if (ret instanceof Array) {
            fail("A function cannot return an array");
        }
        validateTypeSpecifier(ret);
        if (ret != VOID && !isComplete(ret) && decl.body != null) {
            fail("function return type is incomplete");
        }
        List<Type> oldParams = decl.funType.params();
        ArrayList<Type> adjustedParams = new ArrayList<>(oldParams.size());
        for (int i = 0; i < oldParams.size(); i++) {
            Type p = oldParams.get(i);
            validateTypeSpecifier(p);
            if (p == VOID) {
                fail("named parameter " + (i + 1) + " is void");
            }
            if (!isComplete(p) && !isComplete(decayArrayType(p)) && decl.body != null) {
                fail("function with incomplete type parameter");
            }
            adjustedParams.add(arrayToPointer(p));
        }
        if (blockScope && decl.storageClass == STATIC) {
            fail("invalid storage class for block scope function declaration "
                    + "‘" + decl.name + "’");
        }
        boolean defined = decl.body != null;
        boolean global = decl.storageClass != STATIC;
        SymbolTableEntry oldEntry = SYMBOL_TABLE.get(decl.name);
        boolean alreadyDefined = false;
        FunType funType = new FunType(adjustedParams, ret, varargs);
        if (oldEntry instanceof SymbolTableEntry ste) {
            var oldType = ste.type();
            if (oldType instanceof FunType) {
                alreadyDefined = oldEntry.attrs().defined();
                if (alreadyDefined && defined)
                    fail("already defined: " + decl.name);

                if (oldEntry.attrs().global() && decl.storageClass == STATIC)
                    fail("Static function declaration follows non-static");
                global = oldEntry.attrs().global();
                if (!funType.equals(oldType))
                    fail("Incompatible function declarations for " + decl.name);
            } else {
                fail("Incompatible function declarations for " + decl.name);
            }
        }
        FunAttributes attrs =
                new FunAttributes(alreadyDefined || decl.body != null, global);

        SYMBOL_TABLE.put(decl.name, new SymbolTableEntry(funType, attrs));

        Block typeCheckedBody;
        if (decl.body != null) {
            for (int i = 0; i < decl.parameters.size(); i++) {
                Var param = decl.parameters.get(i);
                SYMBOL_TABLE.put(param.name(),
                        new SymbolTableEntry(adjustedParams.get(i),
                                LOCAL_ATTR));
            }
            typeCheckedBody = typeCheckBlock(decl.body, decl);
        } else typeCheckedBody = null;
        List<Var> declParams = decl.parameters;
        for (int i = 0; i < declParams.size(); i++) {
            Var oldParam = declParams.get(i);
            declParams.set(i, new Var(oldParam.name(), adjustedParams.get(i)));
        }
        return new Function(decl.name, decl.parameters, typeCheckedBody,
                funType, decl.storageClass, decl.callsVaStart, decl.usesFunc,
                decl.inline);
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
            case EnumSpecifier enumSpecifier -> throw new Todo();
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
                    label = (Constant<?>) typeCheckExpression(label);
                    var t = label.type();
                    if (!t.isInteger()) {
                        throw new Err("case label is not an integer");
                    }
                    convertedConst =
                            (Constant<?>) convertConst(label,
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
                if (function.body != null)
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
                case DeclarationList dl -> {
                    List<Declaration> list = dl.list();
                    list.replaceAll(decl -> typeCheckLocalVariableDeclaration((VarDecl) decl));
                    yield dl;
                }
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
                Type returnType = enclosingFunction.funType.ret();
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
            case StructOrUnionSpecifier structDecl -> {
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
            case BuiltinC23VaStart(Var ap) -> {
                if (!enclosingFunction.funType.varargs()) {
                    throw new Err("va_start used in function with fixed " +
                            "arguments.");
                }
                enclosingFunction.callsVaStart = true;
                yield new BuiltinC23VaStart(requireVaList(ap));
            }
            case BuiltinVaEnd(Var ap) -> new BuiltinVaEnd(requireVaList(ap));
            case null -> null;
        };
    }

    private static Var requireVaList(Var ap) {
        Var typedAp = (Var) typeCheckExpression(ap);
        var t = typedAp.type();
        if (!t.equals(BUILTIN_VA_LIST) && !(t instanceof Pointer(Type referenced) && referenced.equals(BUILTIN_VA_LIST.element()))) {
            throw new Err(ap + " is of type " + t + " but va_list is required");
        }
        return typedAp;
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
            case FunType(List<Type> params, Type ret, boolean varargs) -> {
                validateTypeSpecifier(ret);
                for (Type p : params) {
                    validateTypeSpecifier(p);
                }
            }
            case Pointer(Type element) -> validateTypeSpecifier(element);
            default -> {}
        }
    }
    
    private static VarDecl typeCheckLocalVariableDeclaration(VarDecl _decl) {
        Type varType = typeCheckType(_decl.varType());
        final VarDecl decl=_decl.withType(varType);
        if (varType == VOID) fail("Can't declare void variable");
        var sd = typeCheckStructureDeclaration(decl.structOrUnionSpecifier());
        validateTypeSpecifier(varType);
        Initializer init = decl.init();
        if (init != null) {
            // this entry will likely be overwritten - we just need typeCheckInit
            // to find the type of the var we're declaring in case it is used in init

            SYMBOL_TABLE.computeIfAbsent(decl.name().name(),
                    k -> new SymbolTableEntry(decl.varType(), LOCAL_ATTR));
            init = typeCheckInit(init, varType);
            if (!isComplete(varType)) {
                varType = init.type();
            }
        }
        if (!isComplete(varType)) {
            if (decl.storageClass() != EXTERN)
                fail("Attempt to declare variable of incomplete type with " + "non-external storage class");
            if (init != null)
                fail("Attempt to define variable of incomplete type");
        }
        if (decl.storageClass() == EXTERN) {
            if (init != null)
                fail("Initializer on local extern variable declaration");
            if (SYMBOL_TABLE.get(decl.name().name()) instanceof SymbolTableEntry ste) {
                Type oldType = ste.type();
                if (!oldType.looseEquals(varType))
                    fail("inconsistent variable redefinition");

            } else {
                SYMBOL_TABLE.put(decl.name().name(),
                        new SymbolTableEntry(varType,
                                new StaticAttributes(NO_INITIALIZER, true, decl.storageClass())));
            }
            return decl;
        } else if (decl.storageClass() == STATIC) {
            InitialValue initialValue;
            ArrayList<StaticInit> staticInits = new ArrayList<>();

            convertCompoundInitializerToStaticInitList(init,
                    varType, staticInits);
            initialValue = new Initial(staticInits);

//            if (//isStringPointerInit(staticInits) &&
//                    varType instanceof Pointer(
//                    Type referenced)) {
//                if (referenced.isCharacter()) {
//
//                    /* TODO: this logic is probably not going to handle
//                        arrays of char* well*/
//                    pointerify(referenced, staticInits, initialValue, decl, varType);
//                } else if (!isNullPointer(init)) {
//                    throw new Err("Can't initialize pointer to " + referenced +
//                            " with string literal");
//                }
//
//            } else

            SYMBOL_TABLE.put(decl.name().name(), new SymbolTableEntry(varType
                    , new StaticAttributes(initialValue, false, decl.storageClass())));
            return new VarDecl(new Var(decl.name().name(), varType),
                    init, varType, decl.storageClass(),decl.structOrUnionSpecifier());
        } else {

            Type type = varType;
            SYMBOL_TABLE.put(decl.name().name(),
                    new SymbolTableEntry(varType, LOCAL_ATTR));
            if (init != null) {
                type = init.type();
                // the init type might have an array length when decl.varType
                // () doesn't
                SYMBOL_TABLE.put(decl.name().name(),
                        new SymbolTableEntry(type, LOCAL_ATTR));
            } else init = null;


            return new VarDecl(new Var(decl.name().name(), type), init, type,
                    decl.storageClass(), decl.structOrUnionSpecifier());
        }
    }

    private static boolean isNullPointer(Initializer init) {
        return init instanceof SingleInit(Exp exp, Type _)
                && exp instanceof ULongInit c && c.isZero();
    }

    private static String pointerify(Type referenced,
                                     List<StaticInit> staticInits,
                                     InitialValue initialValue,
                                     VarDecl decl,
                                     Type varType) {
        String uniqueName = makeTemporary(decl.name().name() +
                ".string.");
        SYMBOL_TABLE.put(uniqueName,
                new SymbolTableEntry(new Array(referenced, new IntInit((int) strlen(staticInits))),
                        new StaticAttributes(initialValue, false, decl.storageClass())));
        SYMBOL_TABLE.put(decl.name().name(),
                new SymbolTableEntry(varType,
                        new StaticAttributes(new Initial(Collections.singletonList(new PointerInit(uniqueName))), false, decl.storageClass())));
        return uniqueName;
    }
    private static String addStringToSymbolTable(Type referenced,
                                     List<StaticInit> staticInits) {
        String uniqueName = makeTemporary(
                "string.");
        SYMBOL_TABLE.put(uniqueName,
                new SymbolTableEntry(new Array(referenced, new IntInit((int) strlen(staticInits))),
                        new StaticAttributes(new Initial(staticInits), false, StorageClass.STATIC)));
        return uniqueName;
    }

    private static Type typeCheckType(Type type) {
        if (type instanceof Typeof(Exp exp)){
            return typeCheckAndConvert(exp).type();
        }
        if (type instanceof TypeofT(Type t)){
            return typeCheckType(t);
        }
        return type;
    }

    private static boolean isStringPointerInit(List<StaticInit> l) {
        if (l.isEmpty()) {
            return false;
        }
        return l.getFirst() instanceof PointerInit;
    }

    private static long strlen(List<StaticInit> l) {
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
                    if (arraySize == null) {
                        arraySize = new LongInit(s.length() + 1);
                        targetType = new Array(element, arraySize);
                    }
                    if (s.length() > arraySize.toLong())
                        throw new Err("Too many chars in string literal");
                    yield new SingleInit(new Str(s, targetType), targetType);
                }
                var typeCheckedExp = typeCheckAndConvert(exp);
                yield new SingleInit(convertByAssignment(typeCheckedExp,
                        targetType), targetType);
            }
            case CompoundInit(ArrayList<Initializer> inits, Type type) -> {
                int initsSize = inits.size();
                if (targetType instanceof Array(Type elementType,
                                                Constant arraySize)) {
                    long zerosToAdd = 0;
                    if (arraySize != null) {
                        long l = arraySize.toLong();
                        if (initsSize > l) {
                            throw new Err("wrong number of values in " +
                                    "initializer");
                        }
                        zerosToAdd = l - initsSize;
                    }
                    inits.replaceAll(i -> typeCheckInit(i, elementType));

                    for (long i = 0; i < zerosToAdd; i++) {
                        inits.add(zeroInitializer(elementType));
                    }
                    yield new CompoundInit(inits, arraySize == null ?
                            new Array(elementType, new ULongInit(initsSize))
                            : targetType);
                } else if (targetType instanceof Structure(boolean isUnion,
                                                           String tag, StructDef _)) {
                    StructDef structDef = TYPE_TABLE.get(tag);
                    List<MemberEntry> members = structDef.namedMembers();
                    if (initsSize > members.size() || (isUnion && initsSize > 1)) {
                        throw new Err("Too many elements in " + (isUnion ?
                                "union " : "structure") + "initializer");
                    }
                    ArrayList<Initializer> typeCheckedList = new ArrayList<>();
                    int i;
                    for (i = 0; i < initsSize; i++) {
                        typeCheckedList.add(typeCheckInit(inits.get(i),
                                members.get(i).type()));
                    }
                    for (; i < (isUnion ? 1 :members.size()); i++) {
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
            case NullptrT _ -> NULLPTR_T.zeroInitializer;
            case FunType funType -> throw new AssertionError(funType);

            case Structure(boolean isUnion, String tag, StructDef _) -> {
                StructDef structDef = TYPE_TABLE.get(tag);
                ArrayList<MemberEntry> members = structDef.members();
                ArrayList<Initializer> typeCheckedList = new ArrayList<>();
                for (MemberEntry member : members) {
                    typeCheckedList.add(zeroInitializer(member.type()));
                }
                yield new CompoundInit(typeCheckedList, elementType);
            }
            default -> throw new IllegalStateException("Unexpected value: " + elementType);
        };

    }

    private static Exp convertTo(Exp e, Type t) {
        if (e == null || e.type().equals(t)) return e;
        if (e instanceof Constant c) {
            var r = convertConst(c, t);
            if (r != null) return r;
        }
        return new Cast(t, e);
    }


    private static Exp convertByAssignment(Exp e, Type targetType) {
        if (e.type() instanceof FunType) {
            if (e instanceof Conditional(Exp condition, Exp ifTrue, Exp ifFalse, Type type)){
                ifTrue = isNullPointerConstant(ifTrue) ? ifTrue: typeCheckExpression(new AddrOf(ifTrue, null));
                ifFalse = isNullPointerConstant(ifFalse) ? ifFalse:  typeCheckExpression(new AddrOf(ifFalse, null));
                e = new Conditional(condition, ifTrue, ifFalse, ifTrue.type());
            }else e = typeCheckExpression(new AddrOf(e, null));
        }
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
        if(e == NULLPTR || targetType == NULLPTR_T){
            return convertTo(e, targetType);
        }
        throw new Err("Cannot convert type for assignment");
    }

    private static boolean isArithmeticType(Type t) {
        return t instanceof Primitive && t != VOID;
    }

    public static Exp typeCheckAndConvert(Exp exp) {
        if (exp == null) return null;
        var typedE = typeCheckExpression(exp);
        return switch (typedE.type()) {
            case Array(Type element, _) ->
                    new AddrOf(typedE, new Pointer(element));
            case Structure(boolean _, String tag, StructDef _) -> {
                if (TYPE_TABLE.containsKey(tag)) yield typedE;
                else throw new Err("Invalid use of incomplete structure type");
            }
            default -> typedE;
        };
    }

    /**6.5.2.2/6 Function calls*
 * The integer promotions are performed on each trailing argument,
 * and trailing arguments that have type float are promoted to double. These are called the default
 * argument promotions. No other conversions are performed implicitly.
 */
    public static Exp typeCheckAndConvertWithDefaultArgumentPromotion(Exp exp) {
        var typedE = typeCheckAndConvert(exp);
        return switch(typedE.type()){
            case BOOL, SCHAR, CHAR, UCHAR, SHORT, USHORT -> convertByAssignment(typedE, INT);
            case FLOAT -> convertByAssignment(typedE, DOUBLE);
            default -> typedE;
        };
    }

    public static Constant evaluateConstantExp(Constant c) {
        if (c instanceof ConstantExp(Exp exp)) {
            return evaluateExpAsConstant(exp);
        }
        return c;
    }

    public static Constant evaluateExpAsConstant(Exp exp) {
        List<InstructionIr> irs = new ArrayList<>();
        var typeCheckedSize = typeCheckExpression(exp);
        var r = new Return(typeCheckedSize);
        IrGen.compileStatement(r, irs, Collections.emptyMap());
        irs = optimizeInstructions(EnumSet.allOf(Optimization.class), irs);
        if (irs.size() == 1 &&
                irs.getFirst() instanceof ReturnIr(Constant val)) {
            return val;
        }
        return null;
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

                BinaryOp binaryOp =
                        (BinaryOp) typeCheckExpression(new BinaryOp(newOp,
                                left, right, t));

                Exp typedLeft =  binaryOp.left();
                if (!isLvalue(typedLeft)){
                    typedLeft = typeCheckAndConvert(left);
                    if (!isLvalue(typedLeft))
                        throw new Err("cannot assign to non-lvalue");

                }
                Exp typedRight = binaryOp.right();
                Type leftType = typedLeft.type();
                if (typedRight.type() == VOID) {
                    fail("can't assign void");
                }

                yield new CompoundAssignment(compoundOp, typedLeft,
                        typedRight, binaryOp.type(), leftType);
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
                if (op == COMMA) {
                    yield new BinaryOp(op, typedE1, typedE2, t2);
                }
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
                if ((op == REMAINDER && (t1 == DOUBLE || t2 == DOUBLE)) || ((op == REMAINDER || op == DIVIDE || op == IMUL) && (t1 instanceof Pointer || t2 instanceof Pointer)))
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
                if (innerType instanceof FunType && type instanceof Pointer(Type r) && r instanceof FunType){
                    yield new Cast(type, typedInner);
                } else if (!innerType.isScalar()) {
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
                Type t1 = decayFunType(typedIfTrue.type());
                Type t2 = decayFunType(typedIfFalse.type());
                Type commonType;
                if (t1.equals(t2)){
                    commonType=t1;
                } else if (t1 == VOID && t2 == VOID) commonType = VOID;
                else if (t1 instanceof Pointer || t2 instanceof Pointer)
                    commonType = getCommonPointerType(typedIfTrue,
                            typedIfFalse);
                else if (isArithmeticType(t1) && isArithmeticType(t1))
                    commonType = getCommonType(t1, t2);
                else if (t1 instanceof Structure(boolean isUnion1,
                                                 String tag1, StructDef _) && t2 instanceof Structure(
                        boolean isUnion2, String tag2, StructDef _) && tag1.equals(tag2)) {
                    commonType = t1;
                } else if (t1 instanceof FunType &&
                        isNullPointerConstant(typedIfFalse)) {
                    yield new Conditional(typedCondition, typedIfTrue, typedIfFalse, t1);
                } else if (t2 instanceof FunType &&
                        isNullPointerConstant(typedIfTrue)) {
                    yield new Conditional(typedCondition, typedIfTrue, typedIfFalse, t2);
                } else
                    throw new Err("Can't convert branches of conditional to " + "a" + " common type");
                yield new Conditional(typedCondition, convertTo(typedIfTrue,
                        commonType), convertTo(typedIfFalse, commonType),
                        commonType);
            }
            case ConstantExp c -> evaluateConstantExp(c);
            case Constant constant -> constant;
            case Str(String s, Type type) ->
                    new Str(s, new Array(CHAR, new IntInit(s.length() + 1)));
            case FunctionCall(Exp name, List<Exp> args, boolean _, Type _) -> {
                name = typeCheckExpression(name);
                Type fType = name.type();
                if (fType instanceof Pointer(Type referenced)){
                    fType = referenced;
                }
                yield switch (fType) {
                    case FunType(List<Type> params, Type ret,
                                 boolean varargs) -> {
                        if (varargs ? args.size() < params.size() :
                                params.size() != args.size())
                            fail("Function called with wrong number of " +
                                    "arguments");
                        for (int i = 0; i < args.size(); i++) {
                            Exp arg = args.get(i);
                            if (i < params.size()) {
                                Type paramType = params.get(i);
                                Exp typedArg = typeCheckAndConvert(arg);
                                args.set(i, convertByAssignment(typedArg, paramType));
                            } else
                                args.set(i, typeCheckAndConvertWithDefaultArgumentPromotion(arg));
                        }
                        yield new FunctionCall(typeCheckExpression(name), args, varargs, ret);
                    }
                    default ->
                            fail("variable " + name + " used as " +
                                    "function");
                };
            }
            case BuiltInFunctionCall(BuiltInFunction name, List<Exp> args, Type _) -> {
                int paramsSize = name.paramsSize();
                if (paramsSize != args.size())
                    fail("Function " + name +" called with wrong number of arguments");
                if (name == BUILTIN_ADD_OVERFLOW ||
                        name == BUILTIN_SUB_OVERFLOW) {
                    Exp typedArg1 = typeCheckAndConvert(args.get(0));
                    Exp typedArg2 = typeCheckAndConvert(args.get(1));
                    Exp typedArg3 = typeCheckAndConvert(args.get(2));

                    Type t1 = typedArg1.type();
                    Type t2 = typedArg2.type();
                    Type t3 = typedArg3.type();
                    if (t3 instanceof Pointer(Type referenced)){
                        t3=referenced;
                    } else {
                        t3=null;
                    }
                    if (!t1.isInteger() || !t2.isInteger() || t3 == null ||
                            !t3.isInteger()) {
                        throw new Err(
                                "argument to " + name + " has incorrect type");
                    }

                    Type commonType = getCommonType(t1, t2);
                    commonType = getCommonType(commonType, t3);
                    args.set(0, convertByAssignment(typedArg1, commonType));
                    args.set(1, convertByAssignment(typedArg2, commonType));
                    args.set(2, typeCheckAndConvert(args.get(2)));
                } else {
                    for (int i = 0; i < args.size(); i++) {
                        Exp arg = args.get(i);
                        Type paramType = name.getParamType(i);
                        if (paramType == null) {
                            args.set(i, typeCheckAndConvert(arg));
                        } else {
                            Exp typedArg = typeCheckAndConvert(arg);
                            args.set(i, convertByAssignment(typedArg,
                                    paramType));
                        }
                    }
                }
                Type r = name.determineReturnType(args);
                yield  new BuiltInFunctionCall(name, args, r);
            }
            case Var(String name, Type _) -> {
                var e = SYMBOL_TABLE.get(name);
                if (e == null) {
                    yield lookupEnumConstant(name);
                }
                Type t = completeType(e.type());
                SymbolTableEntry ste = SYMBOL_TABLE.get(name);
                SYMBOL_TABLE.put(name, new SymbolTableEntry(t, ste.attrs()));
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
                    case FunType _ -> typedInner;
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
                        boolean isUnion, String tag, StructDef _)) {
                    StructDef structDef = TYPE_TABLE.get(tag);
                    MemberEntry me = structDef.findMember(member);
                    if (me == null) {
                        throw new Err("Structure has no member with this name");
                    }
                    yield new Arrow(typedPointer, member, me.type());
                }
                throw new Err("Tried to get member of non-structure");
            }
            case Dot(Exp structure, String member, Type _) -> {
                Exp typedStructure = structure;
                if (!(typedStructure.type() instanceof Structure))
                    typedStructure = typeCheckAndConvert(structure);
                if (typedStructure.type() instanceof Structure(boolean isUnion,
                                                               String tag, StructDef _)) {
                    StructDef structDef = TYPE_TABLE.get(tag);
                    MemberEntry me = structDef.findMember(member);
                    if (me == null) {
                        throw new Err("Structure has no member with this name");
                    }
                    yield new Dot(typedStructure, member, me.type());
                }
                throw new Err("Tried to get member of non-structure");
            }
            case BuiltinVaArg(Var ap, Type type) ->
                    new BuiltinVaArg(requireVaList(ap), type);
            case Offsetof(Structure s, String member) -> {
                validateTypeSpecifier(s);
                StructDef structDef = TYPE_TABLE.get(s.tag());
                MemberEntry me = structDef.findMember(member);
                if (me == null) {
                    throw new Err("Structure has no member with this name");
                }
                yield exp;
            }
            case Generic(Exp controllingExp, ArrayList<Cast> genericAssocList,
                         Exp defaultExp) -> {
                Type controllingType= typeCheckExpression(controllingExp).type();
                for(Cast genericAssoc : genericAssocList) {
                    if (genericAssoc.type().equals(controllingType)) {
                        yield typeCheckExpression(genericAssoc.exp());
                    }
                }
                if (defaultExp == null) {
                    throw new Err("Selector of type " + controllingType +
                            " is not compatible with any association");
                }
                yield defaultExp;
            }
            case ExpressionStatement(Block block) -> new ExpressionStatement(typeCheckBlock(block, null));
        };
    }

    private static Type decayFunType(Type t) {
        return t instanceof FunType ? new Pointer(t) : t;
    }

    private static Type decayArrayType(Type t) {
        return t instanceof Array(Type r, Constant _) ? new Pointer(r) : t;
    }

    private static Type completeType(Type t) {
        if (t instanceof Array(Type refType, ConstantExp c)){
            Constant val = evaluateConstantExp(c);
            if (val == null) throw makeErr("Non constant array size", null);
            return new Array(refType, val);
        }
        return t;

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
            case Structure(boolean isUnion, String tag, StructDef _) ->
                    TYPE_TABLE.containsKey(tag);
            case Array(Type element, Constant arraySize) ->{
                yield arraySize !=null && isComplete(element);
            }
            default -> true;
        };
    }

    private static boolean isComplete(StructDef sd) {
        if (sd==null) return true;
        for(MemberEntry me:sd.members()){
            if (!isComplete(me.type())) return false;
        }
        return true;
    }

    private static boolean isPointerToComplete(Type t1) {
        return t1 instanceof Pointer(Type referenced) && isComplete(referenced);
    }

    private static Type getCommonPointerType(Exp e1, Exp e2) {
        Type t1 = e1.type();
        Type t2 = e2.type();
        if (t1 instanceof FunType) {
            t1 = decayFunType(t1);
        }
        if (t2 instanceof FunType){
            t2 = decayFunType(t2);
        }
        if (t1.equals(t2)) return t1;
        if (isNullPointerConstant(e1)) return t2;
        if (isNullPointerConstant(e2)) return t1;
        if (t1 instanceof Pointer(Type r1) && t2 instanceof Pointer(Type r2)) {
            if (r1 == VOID) return t1;
            if (r2 == VOID) return t2;
        }
        if (t1 == NULLPTR_T && t2 instanceof Pointer) return t2;
        if (t2 == NULLPTR_T && t1 instanceof Pointer) return t1;
        throw new Err("Expressions have incompatible types");
    }

    private static boolean isNullPointerConstant(Exp e) {
        return switch (e) {
            case IntInit(int i) -> i == 0;
            case LongInit(long l) -> l == 0L;
            case UIntInit(int i) -> i == 0;
            case ULongInit(long l) -> l == 0;
            case Cast(Type type, Exp exp) -> isNullPointerConstant(exp);
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

    public static Type getCommonType(Type t1, Type t2) {
        t1 = t1.isCharacter() ? INT : t1;
        t2 = t2.isCharacter() ? INT : t2;
        if (t1 == t2) return t1;
        if (t1 == DOUBLE || t2 == DOUBLE) return DOUBLE;
        if (t1 == FLOAT || t2 == FLOAT) return FLOAT;
        if (size(t1) == size(t2))
            return t1.isSigned() ? t2 : t1;
        if (size(t1) > size(t2)) return t1;
        return t2;
    }

    public static Type resolveType(Type typeSpecifier,

                                   Map<String, Entry> identifierMap, Map<String, TagEntry> structureMap, Function enclosingFunction) {
        return switch (typeSpecifier) {
            case Structure(boolean isUnion, String tag, StructDef sd) -> {
                TagEntry e = structureMap.get(tag);
                if (e != null) {
                    if ((e.isUnion() == TagEntryType.UNION) != isUnion && tag != null) {
                        throw new Err("incompatible with earlier declaration "
                                + "of " + (e.isUnion() == TagEntryType.UNION ? "union" : "struct") + " " + tag);
                    }
                    yield new Structure(isUnion, e.name(), sd);
                } else if ("__builtin_va_list_item.0".equals(tag)) {
                    yield new Structure(isUnion, "__builtin_va_list_item.0", null);
                } else
                    throw new Err("Specified an undeclared tag: tag=" + tag);
            }
            case Pointer(Type referenced) ->{
                if (referenced instanceof Structure s) {
                    ifStructureResolveAsDeclaration(s, identifierMap, structureMap, enclosingFunction);
                }
                referenced = resolveType(referenced, identifierMap, structureMap, enclosingFunction);
                yield new Pointer(referenced);
            }
            case Array(Type element, Constant size) ->{
                if (size instanceof ConstantExp c){
                    size = new ConstantExp(resolveExp(c.exp(), identifierMap, structureMap, enclosingFunction));
                }

                yield new Array(resolveType(element, identifierMap, structureMap, enclosingFunction), size);
            }
            case FunType(List<Type> params, Type ret, boolean varargs) ->
                    new FunType(params.stream().map(p -> resolveType(p, identifierMap, structureMap, enclosingFunction)).toList(), resolveType(ret, identifierMap, structureMap, enclosingFunction), varargs);
            case Primitive primitive -> primitive;
            case NullptrT nullptrT -> nullptrT;
            case Typeof(Exp exp) -> new Typeof(resolveExp(exp, identifierMap, structureMap, enclosingFunction));
            //MR-TODO maybe just yield the resolved innerType?
            case TypeofT(Type innerType) -> new TypeofT(resolveType(innerType, identifierMap, structureMap, enclosingFunction));
        };
    }

    public static Program resolveProgram(Program program,
                                      Map<String, TagEntry> structureMap,
                                      Map<String, Entry> identifierMap) {
        ArrayList<Declaration> decls = program.declarations();
        for (int i = 0; i < decls.size(); i++) {
            switch (decls.get(i)) {
                case EnumSpecifier decl ->
                        decls.set(i, resolveEnumSpecifier(decl, structureMap));
                case Function f ->
                        decls.set(i, resolveFunctionDeclaration(f, identifierMap, structureMap));
                case VarDecl varDecl ->
                        decls.set(i,
                                resolveFileScopeVariableDeclaration(varDecl, identifierMap, structureMap));
                case StructOrUnionSpecifier decl ->
                        decls.set(i, resolveStructureDeclaration(decl, identifierMap, structureMap, null));
            }

        }
        return program;
    }

    public static EnumSpecifier resolveEnumSpecifier(EnumSpecifier decl,
                                                      Map<String, TagEntry> structureMap) {
        if (decl == null) return null;
        if (structureMap == null) {
            // Hack-alert! We call this method with null structureMap just
            // from the parser so that Mcc.resolveEnumConstant will find
            // enumerations that have just been parsed while we are still parsing an enum
            ENUM_MAP.put("CURRENT-ENUM", decl);
            return decl;
        }
        TagEntry prevEntry = structureMap.get(decl.tag());
        String uniqueTag;
        if (prevEntry != null && prevEntry.fromCurrentScope()) {
            if (prevEntry.isUnion() != TagEntryType.ENUM) {
                    throw new Err("Attempt to redeclare " +prevEntry.isUnion()+" as struct");
            }
        }
        if (prevEntry == null || !prevEntry.fromCurrentScope()) {
            uniqueTag = makeTemporary(decl.tag() + ".");
            structureMap.put(decl.tag(), new TagEntry(TagEntryType.ENUM, uniqueTag, true));
            ENUM_MAP.put(uniqueTag, decl);
            return decl;
        } else {
            uniqueTag = prevEntry.name();
            return ENUM_MAP.get(uniqueTag);
        }
    }

    private static StructOrUnionSpecifier resolveStructureDeclaration(
            StructOrUnionSpecifier decl,
            Map<String, Entry> identifierMap,
            Map<String, TagEntry> structureMap, Function enclosingFunction) {
        if (decl == null) return null;
        TagEntry prevEntry = structureMap.get(decl.tag());
        String uniqueTag;
        if (prevEntry != null && prevEntry.fromCurrentScope()) {
            if (prevEntry.isUnion() == TagEntryType.UNION) {
                if (!decl.isUnion()) {
                    throw new Err("Attempt to redeclare union as struct");
                }
            } else {
                if (decl.isUnion()) {
                    throw new Err("Attempt to redeclare as union");
                }
            }
        }
        if (prevEntry == null || !prevEntry.fromCurrentScope()) {
            uniqueTag = makeTemporary(decl.tag() + ".");
            structureMap.put(decl.tag(), new TagEntry(decl.isUnion() ? TagEntryType.UNION : TagEntryType.STRUCT,
                    uniqueTag, true));
        } else {
            uniqueTag = prevEntry.name();
        }
        ArrayList<MemberDeclaration> processedMembers;
        if (decl.members() == null) processedMembers = null;
        else {
            processedMembers = new ArrayList<>();
            for (MemberDeclaration member : decl.members()) {
                for (var p : processedMembers) {
                    if (p.name()!=null && p.name().equals(member.name())) {
                        throw new Err("Duplicate structure member name");
                    }
                }
                StructOrUnionSpecifier sous = member.structOrUnionSpecifier();
                if (sous != null && sous.members() !=
                        null) { // sous without members would already be
                    // resolved
                    sous = resolveStructureDeclaration(
                            member.structOrUnionSpecifier(), identifierMap,
                            structureMap, enclosingFunction);
                }
                processedMembers.add(new MemberDeclaration(
                        resolveType(member.type(), identifierMap, structureMap,
                                enclosingFunction), member.name(), sous,
                        member.bitFieldWidth()));
            }
        }
        return new StructOrUnionSpecifier(decl.isUnion(), uniqueTag,
                processedMembers, decl.isAnonymous());
    }

    /*
    There are places where we can see a struct type name and we should act
    like the struct has been declared. For example if we see
    a struct member of type pointer to struct foo, and it's the first time we
     are seeing foo, act like there was a declaration
    struct foo.
    * */
    static void ifStructureResolveAsDeclaration(Type ref,
                                                Map<String, Entry> identifierMap,
                                                Map<String, TagEntry> structureMap,
                                                Function enclosingFunction) {
        if (ref instanceof Structure(boolean isUnion, String tag,
                                     StructDef structDef)) {
            if (structureMap.containsKey(tag)) return;
            resolveStructureDeclaration(new StructOrUnionSpecifier(isUnion,
                            tag, null,
                    tag == null), identifierMap, structureMap,
                    enclosingFunction);
        }
    }
    private static Declaration resolveFileScopeVariableDeclaration(
            VarDecl varDecl, Map<String, Entry> identifierMap,
            Map<String, TagEntry> structureMap) {
        return switch (varDecl) {
            case VarDecl(Var name, Initializer init, Type type,
                         StorageClass storageClass,
                         StructOrUnionSpecifier structOrUnionSpecifier, Constant _) -> {
                if (structOrUnionSpecifier != null){
                    structOrUnionSpecifier = resolveStructureDeclaration(structOrUnionSpecifier,
                            identifierMap, structureMap, null);
                }
                identifierMap.put(name.name(), new Entry(name.name(), true,
                        true));
                Type t = resolveType(type, identifierMap, structureMap, null);
                yield new VarDecl(new Var(name.name(), t), init, t,
                        storageClass, structOrUnionSpecifier);
            }
        };
    }

    private static Block resolveBlock(
            Block block,
            ArrayList<BlockItem> blockItems,
                                       Map<String, Entry> identifierMap,
                                      Map<String, TagEntry> structureMap, Function enclosingFunction) {

        for (BlockItem i : block.blockItems()) {
            blockItems.add(resolveIdentifiersBlockItem(i, identifierMap,
                    structureMap, enclosingFunction));
        }
        return new Block(blockItems);
    }

    private static Function resolveFunctionDeclaration(Function function,
                                                       Map<String, Entry> identifierMap,
                                                       Map<String, TagEntry> structureMap) {
        String name = function.name;
        if (identifierMap.get(name) instanceof Entry previousEntry) {
            if (previousEntry.fromCurrentScope() && !previousEntry.hasLinkage()) {
                throw new Err("Duplicate declaration: " + name);
            }
        }
        identifierMap.put(name, new Entry(name, true, true));
        Map<String, Entry> innerMap = copyIdentifierMap(identifierMap);
        Map<String, TagEntry> innerStructureMap =
                copyStructureMap(structureMap);
        List<Var> newArgs = resolveParams(function.parameters, innerMap,
                innerStructureMap, function);

        Block newBody=null;
        if (function.body instanceof Block block) {
            var newBlockItems = new ArrayList<BlockItem>();
            var t = new Array(CHAR, null);
            VarDecl func =
                    new VarDecl(new Var("__func__", t),
                            new SingleInit(new Str(function.name, t), t), t, STATIC,
                            null);

            newBlockItems.add(resolveIdentifiersBlockItem(func, innerMap,
                    structureMap, function));
            newBody =
                    resolveBlock(block, newBlockItems, innerMap,
                            innerStructureMap, function);
            if (!function.usesFunc) {
                newBody.blockItems().removeFirst();
            }
        }


        return new Function(function.name, newArgs, newBody,
                resolveFunType(function.funType, identifierMap, innerStructureMap, function),
                function.storageClass, function.callsVaStart, function.usesFunc,
                function.inline);
    }

    private static FunType resolveFunType(FunType funType,
                                          Map<String, Entry> identifierMap,
                                          Map<String, TagEntry> structureMap,
    Function enclosingFunction) {
        return new FunType(funType.params().stream().map(p -> resolveType(p, identifierMap, structureMap, enclosingFunction)).toList(),
                resolveType(funType.ret(), identifierMap, structureMap, enclosingFunction), funType.varargs());
    }

    private static List<Var> resolveParams(List<Var> parameters,
                                           Map<String, Entry> identifierMap,
                                           Map<String, TagEntry> innerStructureMap,
                                           Function enclosingFunction) {
        List<Var> newParams = new ArrayList<>();
        for (Var d : parameters) {
            if (d.name()!=null && identifierMap.get(d.name()) instanceof Entry e && e.fromCurrentScope()) {
                fail("Duplicate variable declaration");
            }
            String uniqueName = makeTemporary(d.name() + ".");
            identifierMap.put(d.name(), new Entry(uniqueName, true, false));
            newParams.add(new Var(uniqueName, resolveType(d.type(), identifierMap, innerStructureMap, enclosingFunction)));
        }
        return newParams;
    }

    private static BlockItem resolveIdentifiersBlockItem(BlockItem blockItem,
                                                         Map<String, Entry> identifierMap,
                                                         Map<String, TagEntry> structureMap, Function enclosingFunction) {
        return switch (blockItem) {
            case EnumSpecifier enumSpecifier -> throw new Todo();
            case VarDecl declaration ->
                    resolveLocalIdentifierDeclaration(declaration,
                            identifierMap, structureMap, enclosingFunction);
            case Statement statement ->
                    resolveStatement(statement, identifierMap, structureMap, enclosingFunction);
            case Function function ->
                    resolveFunctionDeclaration(function, identifierMap,
                            structureMap);
            case StructOrUnionSpecifier structDecl ->
                    resolveStructureDeclaration(structDecl, identifierMap,structureMap, enclosingFunction);
        };
    }

    private static Statement resolveStatement(Statement blockItem,
                                              Map<String, Entry> identifierMap,
                                              Map<String, TagEntry> structureMap, Function enclosingFunction) {
        return switch (blockItem) {
            case null -> null;
            case Exp exp -> resolveExp(exp, identifierMap, structureMap, enclosingFunction);
            case LabelledStatement(String label, Statement statement) ->
                    new LabelledStatement(labelForFunction(label,enclosingFunction.name), resolveStatement(statement,
                            identifierMap, structureMap, enclosingFunction));
            case Break b -> {
                b.label = labelForFunction(b.label, enclosingFunction.name);
                yield b;
            }
            case Continue b -> {
                b.label = labelForFunction(b.label, enclosingFunction.name);
                yield b;
            }
            case Goto b -> {
                b.label = labelForFunction(b.label, enclosingFunction.name);
                yield b;
            }
            case CaseStatement(Switch enclosingSwitch, Constant<?> label,
                               Statement statement) ->
                    new CaseStatement(enclosingSwitch, (Constant<?>) resolveExp(label, identifierMap,
                            structureMap, enclosingFunction),
                            resolveStatement(statement, identifierMap,
                                    structureMap, enclosingFunction));
            case Return(Exp exp) ->
                    new Return(resolveExp(exp, identifierMap, structureMap, enclosingFunction));
            case If(Exp condition, Statement ifTrue, Statement ifFalse) ->
                    new If(resolveExp(condition, identifierMap, structureMap, enclosingFunction)
                            , resolveStatement(ifTrue, identifierMap,
                            structureMap, enclosingFunction), resolveStatement(ifFalse,
                            identifierMap, structureMap, enclosingFunction));
            case Block block ->
                    resolveBlock(block, new ArrayList<>(), copyIdentifierMap(identifierMap),
                            copyStructureMap(structureMap), enclosingFunction);
            case NullStatement nullStatement -> nullStatement;
            case DoWhile(Statement body, Exp condition, String label) ->
                    new DoWhile(resolveStatement(body, identifierMap,
                            structureMap, enclosingFunction), resolveExp(condition,
                            identifierMap, structureMap, enclosingFunction), label);
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> {
                Map<String, Entry> newVariableMap =
                        copyIdentifierMap(identifierMap);
                Map<String, TagEntry> newStructureMap =
                        copyStructureMap(structureMap);
                yield new For(resolveForInit(init, newVariableMap,
                        newStructureMap, enclosingFunction), resolveExp(condition,
                        newVariableMap, newStructureMap, enclosingFunction), resolveExp(post,
                        newVariableMap, newStructureMap, enclosingFunction),
                        resolveStatement(body, newVariableMap,
                                newStructureMap, enclosingFunction), label);
            }
            case While(Exp condition, Statement body, String label) ->
                    new While(resolveExp(condition, identifierMap,
                            structureMap, enclosingFunction), resolveStatement(body,
                            identifierMap, structureMap, enclosingFunction), label);
            case Switch switchStatement -> {
                switchStatement.exp = resolveExp(switchStatement.exp,
                        identifierMap, structureMap, enclosingFunction);
                switchStatement.body = resolveStatement(switchStatement.body,
                        identifierMap, structureMap, enclosingFunction);
                yield switchStatement;
            }
            case BuiltinC23VaStart(Var ap) ->
                    new BuiltinC23VaStart((Var) resolveExp(ap, identifierMap,
                            structureMap, enclosingFunction));
            case BuiltinVaEnd(Var ap) ->
                    new BuiltinVaEnd((Var) resolveExp(ap, identifierMap,
                            structureMap, enclosingFunction));

        };

    }

    private static String labelForFunction(String label,
                                           String enclosingFunctionName) {
        return label == null ? null :
                ".L" + label + "." + enclosingFunctionName;
    }

    private static ForInit resolveForInit(ForInit init,
                                          Map<String, Entry> identifierMap,
                                          Map<String, TagEntry> structureMap, Function enclosingFunction) {
        return switch (init) {
            case DeclarationList dl -> {
                List<Declaration> list = dl.list();
                list.replaceAll(decl -> resolveLocalIdentifierDeclaration((VarDecl) decl, identifierMap, structureMap, enclosingFunction));
                yield dl;
            }
            case Exp exp -> resolveExp(exp, identifierMap, structureMap, enclosingFunction);
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


    private static Map<String, TagEntry> copyStructureMap(
            Map<String, TagEntry> m) {
        Map<String, TagEntry> copy = new DebugHashMap<>(m.size());
        for (var e : m.entrySet()) {
            var v = e.getValue();
            copy.put(e.getKey(), new TagEntry(v.isUnion(), v.name(),
                    false));
        }
        return copy;

    }


    private static VarDecl resolveLocalIdentifierDeclaration(VarDecl decl,
                                                             Map<String,
                                                                     Entry> identifierMap,
                                                             Map<String, TagEntry> structureMap,
                                                             Function enclosingFunction) {
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
                    resolveType(decl.varType(), identifierMap, structureMap, enclosingFunction),
                    decl.storageClass(), decl.structOrUnionSpecifier());
        }
        String uniqueName = makeTemporary(decl.name().name() + ".");
        identifierMap.put(decl.name().name(), new Entry(uniqueName, true,
                false));
        var init = decl.init();
        StructOrUnionSpecifier sous = decl.structOrUnionSpecifier();
        if (sous != null && sous.members() !=
                null) { // sous without members would already be resolved
            sous =
                    resolveStructureDeclaration(decl.structOrUnionSpecifier()
                            , identifierMap, structureMap, enclosingFunction);
        }

        return new VarDecl(new Var(uniqueName, null), resolveInitializer(init
                , identifierMap, structureMap, enclosingFunction),
         resolveType(decl.varType(), identifierMap, structureMap,
          enclosingFunction), decl.storageClass(), sous);
    }

    private static Initializer resolveInitializer(Initializer init,
                                                  Map<String, Entry> identifierMap,
                                                  Map<String, TagEntry> structureMap, Function enclosingFunction) {
        return switch (init) {
            case null -> null;
            case CompoundInit(ArrayList<Initializer> inits, Type type) -> {
                inits.replaceAll(i -> resolveInitializer(i, identifierMap,
                        structureMap, enclosingFunction));
                yield new CompoundInit(inits, type);
            }
            case SingleInit(Exp exp, Type targetType) ->
                    new SingleInit(resolveExp(exp, identifierMap,
                            structureMap, enclosingFunction), targetType);
        };

    }

    private static Exp  resolveExp(Exp exp,
                                                Map<String, Entry> identifierMap,
                                                Map<String, TagEntry> structureMap,
    Function enclosingFunction) {
        Exp r = switch (exp) {
            case null -> null;
            case Assignment(Exp left, Exp right, Type type) ->
                    isLvalue(left) ? new Assignment(resolveExp(left,
                            identifierMap, structureMap, enclosingFunction), resolveExp(right,
                            identifierMap, structureMap, enclosingFunction), type) : fail(
                                    "Invalid lvalue");
            case CompoundAssignment(CompoundAssignmentOperator op, Exp left,
                                    Exp right, Type tempType, Type type) ->
                    op instanceof CompoundAssignmentOperator && !isLvalue(left) ? fail("Invalid lvalue") : new CompoundAssignment(op, resolveExp(left, identifierMap, structureMap, enclosingFunction), resolveExp(right, identifierMap, structureMap, enclosingFunction), tempType, type);
            case BinaryOp(BinaryOperator op, Exp left, Exp right, Type type) ->
                    op instanceof CompoundAssignmentOperator && !isLvalue(left) ? fail("Invalid lvalue") : new BinaryOp(op, resolveExp(left, identifierMap, structureMap, enclosingFunction), resolveExp(right, identifierMap, structureMap, enclosingFunction), type);
            case ConstantExp(Exp cexp) -> new ConstantExp(resolveExp(cexp,
                    identifierMap,
                    structureMap, enclosingFunction));
            case Constant constant -> constant;
            case Str str -> str;
            case UnaryOp(UnaryOperator op, Exp arg, Type type) ->
                    (op == POST_INCREMENT || op == POST_DECREMENT) && !isLvalue(arg) ? fail("Invalid lvalue") : new UnaryOp(op, resolveExp(arg, identifierMap, structureMap, enclosingFunction), type);
            case AddrOf(Exp arg, Type type) ->
                    new AddrOf(resolveExp(arg, identifierMap, structureMap, enclosingFunction),
                            type);
            case Dereference(Exp arg, Type type) ->
                    new Dereference(resolveExp(arg, identifierMap,
                            structureMap, enclosingFunction), type);
            case Var(String name, Type type) -> {
                if (identifierMap.get(name) instanceof Entry e) {
                    if ("__func__".equals(name)) {
                        enclosingFunction.usesFunc = true;
                    }
                    yield new Var(e.name(), type);
                }
                Constant e = lookupEnumConstant(name);
                if (e != null) yield e;
                if ("__PRETTY_FUNCTION__".equals(name)) {
                    yield resolveExp(new Var("__func__", null), identifierMap
                            , structureMap, enclosingFunction);
                }
                yield fail("Undeclared variable:" + exp);
            }
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type) ->
                    new Conditional(resolveExp(condition, identifierMap,
                            structureMap, enclosingFunction), resolveExp(ifTrue, identifierMap,
                            structureMap, enclosingFunction), resolveExp(ifFalse, identifierMap,
                            structureMap, enclosingFunction), type);
            case FunctionCall(Exp name, List<Exp> args, boolean varargs,
                              Type type) ->
                            new FunctionCall(resolveExp(name, identifierMap, structureMap, enclosingFunction),
                                    resolveArgs(identifierMap, structureMap, args, enclosingFunction), varargs, type);
            case BuiltInFunctionCall(BuiltInFunction name, List<Exp> args,
                                     Type type) ->
                    new BuiltInFunctionCall(name,
                            resolveArgs(identifierMap, structureMap, args, enclosingFunction), type);
            case Cast(Type type, Exp e) -> {
                Type resolvedType = resolveType(type, identifierMap, structureMap, enclosingFunction);
                yield new Cast(resolvedType, resolveExp(e, identifierMap,
                        structureMap, enclosingFunction));
            }
            case Subscript(Exp array, Exp index, Type type) ->
                    new Subscript(resolveExp(array, identifierMap,
                            structureMap, enclosingFunction), resolveExp(index, identifierMap,
                            structureMap, enclosingFunction), type);
            case SizeOf(Exp e) ->
                    new SizeOf(resolveExp(e, identifierMap, structureMap, enclosingFunction));
            case SizeOfT(Type type) ->
                    new SizeOfT(resolveType(type, identifierMap, structureMap, enclosingFunction));
            case Offsetof(Structure structure, String member) ->
                    new Offsetof((Structure) resolveType(structure, identifierMap, structureMap, enclosingFunction), member);
            case Arrow(Exp pointer, String member, Type type) ->
                    new Arrow(resolveExp(pointer, identifierMap,
                            structureMap, enclosingFunction), member, type);
            case Dot(Exp structure, String member, Type type) ->
                    new Dot(resolveExp(structure, identifierMap,
                            structureMap, enclosingFunction), member, type);
            case BuiltinVaArg(Var e, Type type) -> {
                Type resolvedType = resolveType(type, identifierMap, structureMap, enclosingFunction);
                yield new BuiltinVaArg((Var) resolveExp(e, identifierMap,
                        structureMap, enclosingFunction), resolvedType);
            }
            case Generic(Exp controllingExp, ArrayList<Cast> genericAssocList,
                         Exp defaultExp) -> {
                for(int i =0;i< genericAssocList.size();i++){
                    Cast genericAssoc = genericAssocList.get(i);
                    Type resolvedType = resolveType(genericAssoc.type(), identifierMap, structureMap, enclosingFunction);
                    genericAssocList.set(i, new Cast(resolvedType,
                            resolveExp(genericAssoc.exp(), identifierMap,
                            structureMap, enclosingFunction)));
                }
                yield new Generic(resolveExp(controllingExp, identifierMap,
                        structureMap, enclosingFunction),genericAssocList,
                        resolveExp(defaultExp, identifierMap,
                                structureMap, enclosingFunction));
            }
            case ExpressionStatement(Block stmt) -> new ExpressionStatement(resolveBlock(stmt, new ArrayList<>(), identifierMap,
                    structureMap, enclosingFunction));
        };
        return r;
    }

    private static <T extends Exp> List<T> resolveArgs(
            Map<String, Entry> identifierMap,
            Map<String, TagEntry> structureMap, List<T> args, Function enclosingFunction) {
        List<T> newArgs = new ArrayList<>();
        for (T arg : args) {
            newArgs.add((T) resolveExp(arg, identifierMap, structureMap, enclosingFunction));
        }
        return newArgs;
    }

    private static Exp fail(String s) {
        throw new Err(s);
    }


}
