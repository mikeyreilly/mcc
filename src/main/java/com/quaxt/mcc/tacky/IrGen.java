package com.quaxt.mcc.tacky;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Nullary;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.atomics.MemoryOrder;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.parser.StructOrUnionSpecifier;
import com.quaxt.mcc.semantic.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.optimizer.Optimizer.optimizeFunction;
import static com.quaxt.mcc.parser.StorageClass.*;
import static com.quaxt.mcc.semantic.Primitive.*;
import static com.quaxt.mcc.semantic.SemanticAnalysis.convertConst;

public class IrGen {

    public static ProgramIr programIr(Program program) {
        List<TopLevel> tackyDefs = new ArrayList<>();
        Map<String, FunctionIr> inlineFunctions = new HashMap<>();

        for (Function function : program.functions()) {
            if (function.body != null && !Mcc.IGNORE_LIST.contains(function.name))
                tackyDefs.add(compileFunction(function, inlineFunctions));
        }
        convertSymbolsToTacky(tackyDefs);
        return new ProgramIr(tackyDefs, program.positions());
    }

    private static void convertSymbolsToTacky(List<TopLevel> tackyDefs) {
        for (Map.Entry<String, SymbolTableEntry> e : SYMBOL_TABLE.entrySet()) {
            String name = e.getKey();
            SymbolTableEntry value = e.getValue();
            switch (value.attrs()) {
                case ConstantAttr(StaticInit init) -> {
                    tackyDefs.add(new StaticConstant(name, value.type(), init));
                }
                case FunAttributes funAttributes -> {}
                case IdentifierAttributes.LocalAttr localAttr -> {}
                case StaticAttributes(InitialValue init, boolean global, StorageClass sc) -> {
                    if (init instanceof InitialValue.Tentative) {
                        if (sc != TYPEDEF) {
                            tackyDefs.add(new StaticVariable(name, global,
                                    value.type(),
                                    Collections.singletonList(new ZeroInit(Mcc.size(value.type())))));
                        }
                    } else if (init instanceof Initial(
                            List<StaticInit> initList)) {
                        tackyDefs.add(new StaticVariable(name, global,
                                value.type(), initList));
                    }
                }
            }

        }
    }

    private static FunctionIr  compileFunction(Function function,
                                               Map<String, FunctionIr> inlineFunctions) {
        List<InstructionIr> instructions = new ArrayList<>();
        compileBlock(function.body, instructions, inlineFunctions);
        FunctionIr f =
                new FunctionIr(function.name,
                        SYMBOL_TABLE.get(function.name).attrs().global(),
                        function.parameters, instructions, function.funType,
                        function.callsVaStart, function.inline && !function.callsVaStart);
        ReturnIr ret = new ReturnIr(IntInit.ZERO);
        instructions.add(ret);
        if (f.inline()) {
            EnumSet<Optimization> optimizations = EnumSet.allOf(Optimization.class);
            f = optimizeFunction(f, optimizations);
            inlineFunctions.put(f.name(), f);
        }
        return f;
    }

    private static void compileBlock(Block block,
                                     List<InstructionIr> instructions,
                                     Map<String, FunctionIr> inlineFunctions) {
        for (BlockItem i : block.blockItems()) {
            compileBlockItem(i, instructions, inlineFunctions);
        }
    }

    private static void compileDeclaration(Declaration d,
                                           List<InstructionIr> instructions,
                                           Map<String, FunctionIr> inlineFunctions) {
        switch (d) {
            case EnumSpecifier enumSpecifier -> throw new Todo();
            case Function function -> {
                if (function.body != null) compileFunction(function, inlineFunctions);
            }
            case VarDecl(Var name, Initializer init, Type _,
                         StorageClass storageClass,
                         StructOrUnionSpecifier structOrUnionSpecifier,
                         Constant _, int pos) -> {
                if (storageClass == STATIC || storageClass == EXTERN) return;
                if (init != null) {
                    instructions.add(new Pos(pos));
                    assign(new VarIr(name.name()), init, instructions, 0, inlineFunctions);
                    return;
                }
                //MR-TODO I don't think this is needed
                emitTacky(null, instructions, inlineFunctions);
            }
            case StructOrUnionSpecifier _ -> {} // nothing to do: StructDecls are not in IR
        }
    }

    private static long assign(VarIr name, Initializer init,
                               List<InstructionIr> instructions, long offset,
                               Map<String, FunctionIr> inlineFunctions) {
        switch (init) {
            case CompoundInit(ArrayList<Initializer> inits,
                              Type compoundInitType) when compoundInitType instanceof Structure(boolean isUnion,
                                                                                                String tag,
            StructDef _) -> {
                StructDef sd =
                        Mcc.TYPE_TABLE.get(tag);
                List<MemberEntry> members = sd.namedMembers();


                int initsLen = inits.size();
                int membersLen = members.size();
                if (isUnion && initsLen > 0) {
                    long biggest = Mcc.size(members.getFirst().type());
                    MemberEntry best = members.getFirst();
                    for (int i = 1; i < membersLen; i++) {
                        var m = members.get(i);
                        long s = Mcc.size(m.type());
                        if (s > biggest) {
                            biggest = s;
                            best = m;
                        }
                    }
                    Initializer memInit = inits.getFirst();
                    initMember(memInit, best, name,
                            instructions,  offset, inlineFunctions);
                } else {
                    for (int i = 0; i < initsLen; i++) {
                        Initializer memInit = inits.get(i);
                        MemberEntry member = members.get(i);
                        initMember(memInit, member, name, instructions,
                                offset, inlineFunctions);
                    }
                }
                long structSize = Mcc.size(compoundInitType);
                if (initsLen == 0 && 0L < structSize) {
                    memsetToOffset(name, instructions, offset, 0, structSize, false);
                }
                return offset + structSize;
            }
            case CompoundInit(ArrayList<Initializer> inits,
                              Type compoundInitType) -> {
                for (Initializer innerInit : inits) {
                    switch (innerInit) {
                        case CompoundInit _ ->
                                offset = assign(name, innerInit, instructions
                                        , offset, inlineFunctions);
                        case SingleInit(Exp exp, Type targetType) -> {
                            if (exp instanceof Str(String s,
                                                   Type type) && type instanceof Array(
                                    Type _, Constant arraySize)) {
                                initializeArrayWithStringLiteral(name,
                                        instructions, offset, s, arraySize);
                            } else {
                                var val = emitTackyAndConvert(exp,
                                        instructions, inlineFunctions);
                                instructions.add(new CopyToOffset(val, name,
                                        offset));
                            }
                            offset += Mcc.size(exp.type());
                        }

                        case ZeroInit(long bytes) -> {
                            if (offset!=0) {
                                VarIr ptr =
                                        makeTemporary("ptr", new Pointer(VOID));
                                instructions.add(new GetAddress(name, ptr));
                                instructions.add(new AddPtr(ptr, new IntInit((int) offset), 1, ptr));
                                instructions.add(new Memset(ptr, 0, bytes, true));
                            }else instructions.add(new Memset(name, 0, bytes, false));
                        }
                    }
                }
                return offset;
            }
            case SingleInit(Exp exp, Type targetType) -> {
                // String literal as array initializer p. 440
                if (exp instanceof Str(String s,
                                       Type type) && type instanceof Array(
                        Type _, Constant arraySize)) {
                    initializeArrayWithStringLiteral(name, instructions,
                            offset, s, arraySize);
                } else {
                    assign(name, exp, instructions, inlineFunctions);
                }
                return offset + Mcc.size(targetType);
            }

            case ZeroInit zeroInit -> {throw new Todo();}
        }

    }

    private static void memsetToOffset(VarIr name,
                                  List<InstructionIr> instructions,
                                  long offset,
                                  int c,
                                       long byteCount, boolean viaPointer) {
        if (offset !=0) {
            VarIr ptr =
                    makeTemporary("ptr", new Pointer(VOID));
            instructions.add(new AddPtr(name, new IntInit((int) offset), 1, ptr));
            instructions.add(new Memset(ptr, c, byteCount, true));
        } else {
            instructions.add(new Memset(name, c, byteCount, viaPointer));
        }
    }

    private static void initMember(Initializer memInit, MemberEntry member, VarIr name,
                                   List<InstructionIr> instructions, long offset,
                                   Map<String, FunctionIr> inlineFunctions) {

        switch (memInit) {
            case CompoundInit _ ->
                    assign(name, memInit, instructions,
                            offset + member.byteOffset(), inlineFunctions);
            case SingleInit(Exp exp, Type initType) -> {
                if (exp instanceof Str(String s,
                                       Type type) && type instanceof Array(
                        Type _, Constant arraySize)) {
                    initializeArrayWithStringLiteral(name,
                            instructions,
                            offset + member.byteOffset(), s, arraySize);
                } else {
                    var val = emitTackyAndConvert(exp,
                            instructions, inlineFunctions);
                    if (member instanceof BitFieldMember(        String _,
                                                                 Type _,
                                                                 int _,
                                                                 int bitOffset,
                                                                 int bitWidth
                    )){
                        instructions.add(new CopyBitsToOffset(val, name,
                                offset + member.byteOffset(), bitOffset, bitWidth));
                    } else {
                        instructions.add(new CopyToOffset(val, name,
                                offset + member.byteOffset()));
                        long initTypeSize = Mcc.size(initType);
                        long memberTypeSize = Mcc.size(member.type());
                        if (initTypeSize < memberTypeSize) {
                            memsetToOffset(name,
                                    instructions,
                                    offset + member.byteOffset() + initTypeSize,
                                    0, memberTypeSize - initTypeSize, false);
                        }

                    }
                }

            }
            case ZeroInit zeroInit -> {throw new Todo();}
        }
    }


    private static void initializeArrayWithStringLiteral(VarIr dst,
                                                         List<InstructionIr> instructions,
                                                         long offset, String s,
                                                         Constant arraySize) {
        long arrayLen = arraySize.toLong();
        long howManyCharsToCopy = Math.min(s.length(), arrayLen);
        for (int i = 0; i < howManyCharsToCopy; i++) {
            instructions.add(new CopyToOffset(new CharInit((byte) (s.charAt(i) & 0xff)), dst, offset + i));
        }
        for (long i = howManyCharsToCopy; i < arrayLen; i++) {
            instructions.add(new CopyToOffset(CharInit.zero(), dst,
                    offset + i));
        }
    }


    private static void compileBlockItem(BlockItem i,List<InstructionIr> instructions,
                            Map<String, FunctionIr> inlineFunctions) {
        switch (i) {
            case Declaration d -> compileDeclaration(d, instructions, inlineFunctions);
            case Statement statement ->
                    compileStatement(statement, instructions, inlineFunctions);
        }
    }

    private static void compileIfElse(Exp condition, Statement ifTrue,
                                      Statement ifFalse,
                                      List<InstructionIr> instructions,
                                      Map<String, FunctionIr> inlineFunctions) {
        ValIr c = emitTackyAndConvert(condition, instructions, inlineFunctions);
        LabelIr e2Label = newLabel(Mcc.makeTemporary(".Le2."));
        instructions.add(new JumpIfZero(c, e2Label.label()));
        compileStatement(ifTrue, instructions, inlineFunctions);
        LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
        instructions.add(new Jump(endLabel.label()));
        instructions.add(e2Label);
        compileStatement(ifFalse, instructions, inlineFunctions);
        instructions.add(endLabel);
    }

    private static void compileIf(Exp condition, Statement ifTrue,
                                  List<InstructionIr> instructions,
                                  Map<String, FunctionIr> inlineFunctions) {
        ValIr c = emitTackyAndConvert(condition, instructions, inlineFunctions);
        LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
        instructions.add(new JumpIfZero(c, endLabel.label()));
        compileStatement(ifTrue, instructions, inlineFunctions);
        instructions.add(endLabel);
    }


    public static void compileStatement(Statement i,
                                         List<InstructionIr> instructions,
                                         Map<String, FunctionIr> inlineFunctions) {
        switch (i) {
            case BuiltinC23VaStart(Var exp) -> {
                ValIr retVal = emitTackyAndConvert(exp,
                        instructions, inlineFunctions);
                instructions.add(new BuiltinC23VaStartIr((VarIr) retVal));
            }
            case Switch switchStatement -> {
                compileSwitch(switchStatement, instructions, inlineFunctions);
            }
            case Return(Exp exp) -> {
                ValIr retVal = exp == null ? null : emitTackyAndConvert(exp,
                        instructions, inlineFunctions);
                ReturnIr ret = new ReturnIr(retVal);
                instructions.add(ret);
            }

            case Exp exp -> emitTacky(exp, instructions, inlineFunctions);

            case If(Exp condition, Statement ifTrue, Statement ifFalse) -> {
                if (ifFalse != null) {
                    compileIfElse(condition, ifTrue, ifFalse, instructions, inlineFunctions);
                } else {
                    compileIf(condition, ifTrue, instructions, inlineFunctions);

                }
            }
            case NullStatement _ -> {
            }
            case Block b -> compileBlock(b, instructions, inlineFunctions);

            case Break aBreak ->
                    instructions.add(new Jump(breakLabel(aBreak.label)));
            case Goto aGoto -> instructions.add(new Jump(aGoto.label));
            case Continue aContinue ->
                    instructions.add(new Jump(continueLabel(aContinue.label)));
            case DoWhile(Statement body, Exp condition, String label) -> {
                LabelIr start = newLabel(Mcc.makeTemporary(".Lstart."));
                instructions.add(start);
                compileStatement(body, instructions, inlineFunctions);
                LabelIr continueLabel = newLabel(continueLabel(label));
                instructions.add(continueLabel);
                ValIr v = emitTackyAndConvert(condition, instructions, inlineFunctions);
                instructions.add(new JumpIfNotZero(v, start.label()));
                LabelIr breakLabel = newLabel(breakLabel(label));
                instructions.add(breakLabel);
            }
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> {

                switch (init) {
                    case Exp e -> emitTacky(e, instructions, inlineFunctions);
                    case null -> {
                    }
                    case DeclarationList(List<Declaration> list) -> {
                        for (var d: list){
                            compileDeclaration(d, instructions, inlineFunctions);
                        }
                    }
                }

                LabelIr start = newLabel(startLabel(label));
                LabelIr continueLabel = newLabel(continueLabel(label));
                LabelIr breakLabel = newLabel(breakLabel(label));
                instructions.add(start);
                if (condition != null) {
                    ValIr v = emitTackyAndConvert(condition, instructions, inlineFunctions);
                    instructions.add(new JumpIfZero(v, breakLabel.label()));
                }
                compileStatement(body, instructions, inlineFunctions);
                instructions.add(continueLabel);
                emitTacky(post, instructions, inlineFunctions);
                instructions.add(new Jump(start.label()));
                instructions.add(breakLabel);
            }
            case While(Exp condition, Statement body, String label) -> {
                LabelIr continueLabel = newLabel(continueLabel(label));
                instructions.add(continueLabel);
                ValIr v = emitTackyAndConvert(condition, instructions, inlineFunctions);
                LabelIr breakLabel = newLabel(breakLabel(label));
                instructions.add(new JumpIfZero(v, breakLabel.label()));
                compileStatement(body, instructions, inlineFunctions);
                instructions.add(new Jump(continueLabel.label()));
                instructions.add(breakLabel);
            }
            case LabelledStatement(String label, Statement statement) -> {
                instructions.add(newLabel(label));
                compileStatement(statement, instructions, inlineFunctions);
            }
            case CaseStatement(Switch enclosingSwitch, Constant<?> label, Statement statement) -> {
                String s = enclosingSwitch.labelFor(label);
                instructions.add(newLabel(s));
                compileStatement(statement, instructions, inlineFunctions);
            }
            case BuiltinVaEnd builtinVaEnd -> {
                // it's a NOOP
            }
        }
    }

    private static void compileSwitch(Switch switchStatement,
                                      List<InstructionIr> instructions,
                                      Map<String, FunctionIr> inlineFunctions) {
        ValIr switchVal = emitTackyAndConvert(switchStatement.exp,
                instructions, inlineFunctions);
        Type type = switchStatement.exp.type();
        for (Constant c : switchStatement.entries) {
            if (c != null) {
                Constant<?> converted =
                        (Constant<?>) convertConst(c, type);
                instructions.add(new Compare(type, converted, switchVal));
                instructions.add(new JumpIfZero(null,
                        switchStatement.labelFor(c)));
            } else instructions.add(new Jump(switchStatement.labelFor(null)));
        }
        String end = breakLabel(switchStatement.label);
        instructions.add(new Jump(end));
        compileStatement(switchStatement.body, instructions, inlineFunctions);
        instructions.add(newLabel(end));
    }

    private static String startLabel(String label) {
        return label + ".start";
    }

    private static String continueLabel(String label) {
        return label + ".continue";
    }

    private static String breakLabel(String label) {
        return label + ".break";
    }

    private static ExpResult emitTacky(Exp expr,
                                       List<InstructionIr> instructions,
                                       Map<String, FunctionIr> inlineFunctions) {
        switch (expr) {

            case null:
                return null;
            case Constant<?> c:
                return new PlainOperand(c);
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type): {
                ValIr cond = emitTackyAndConvert(condition, instructions, inlineFunctions);
                LabelIr e2Label = newLabel(Mcc.makeTemporary(".Le2."));
                instructions.add(new JumpIfZero(cond, e2Label.label()));
                if (type == VOID) {
                    emitTackyAndConvert(ifTrue, instructions, inlineFunctions);
                    LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
                    instructions.add(new Jump(endLabel.label()));
                    instructions.add(e2Label);
                    emitTackyAndConvert(ifFalse, instructions, inlineFunctions);
                    instructions.add(endLabel);
                    return null;//not used by caller (see p. 480)
                } else {
                    ValIr e1 = emitTackyAndConvert(ifTrue, instructions, inlineFunctions);
                    VarIr result = makeTemporary("result.", type);
                    instructions.add(new Copy(e1, result));
                    LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
                    instructions.add(new Jump(endLabel.label()));
                    instructions.add(e2Label);
                    ValIr e2 = emitTackyAndConvert(ifFalse, instructions, inlineFunctions);
                    instructions.add(new Copy(e2, result));
                    instructions.add(endLabel);
                    return new PlainOperand(result);
                }
            }
            case UnaryOp(UnaryOperator op, Exp exp, Type type): {
                if (op == UnaryOperator.POST_INCREMENT || op == UnaryOperator.POST_DECREMENT) {
                    boolean post = true;
                    ExpResult lval = emitTacky(exp, instructions, inlineFunctions);
                    var newOp = switch (op) {
                        case POST_INCREMENT -> ADD;
                        case POST_DECREMENT -> SUB;
                        default -> throw new Todo();
                    };
                    ValIr right = type == DOUBLE ? DoubleInit.ONE : IntInit.ONE;
                    return applyOperatorAndAssign(instructions, exp, lval,
                            right, newOp, post, exp.type(), exp.type());
                }
                ValIr src = emitTackyAndConvert(exp, instructions, inlineFunctions);
                VarIr dst = makeTemporary("tmp.", type);
                instructions.add(new UnaryIr(op, src, dst));
                return new PlainOperand(dst);
            }
            case CompoundAssignment(CompoundAssignmentOperator op, Exp left,
                                    Exp right, Type tempType,
                                    Type lvalueType): {


                boolean post = false;

                ExpResult lval = emitTacky(left, instructions, inlineFunctions);
                ArithmeticOperator newOp = switch (op) {
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
                    case SHR_EQ -> SHR;
                };
                ValIr rightVal = emitTackyAndConvert(right, instructions, inlineFunctions);
                return applyOperatorAndAssign(instructions, left, lval,
                        rightVal, newOp, post, tempType, lvalueType);


            }

            case BinaryOp(BinaryOperator op, Exp left, Exp right,
                          Type lvalueType):
                switch (op) {
                    case AND -> {
                        VarIr result = makeTemporary("tmp.", INT);

                        LabelIr falseLabel = newLabel(Mcc.makeTemporary(
                                ".LandFalse."));
                        LabelIr endLabel = newLabel(Mcc.makeTemporary(
                                ".LandEnd."));
                        ValIr v1 = emitTackyAndConvert(left, instructions, inlineFunctions);
                        instructions.add(new JumpIfZero(v1,
                                falseLabel.label()));

                        ValIr v2 = emitTackyAndConvert(right, instructions, inlineFunctions);
                        instructions.add(new JumpIfZero(v2,
                                falseLabel.label()));
                        instructions.add(new Copy(IntInit.ONE, result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(falseLabel);
                        instructions.add(new Copy(IntInit.ZERO, result));
                        instructions.add(endLabel);

                        return new PlainOperand(result);
                    }
                    case OR -> {
                        VarIr result = makeTemporary("tmp.", INT);

                        LabelIr trueLabel = newLabel(Mcc.makeTemporary(
                                ".Ltrue."));
                        LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend" +
                                "."));
                        ValIr v1 = emitTackyAndConvert(left, instructions, inlineFunctions);
                        instructions.add(new JumpIfNotZero(v1,
                                trueLabel.label()));

                        ValIr v2 = emitTackyAndConvert(right, instructions, inlineFunctions);
                        instructions.add(new JumpIfNotZero(v2,
                                trueLabel.label()));
                        instructions.add(new Copy(IntInit.ZERO, result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(trueLabel);
                        instructions.add(new Copy(IntInit.ONE, result));
                        instructions.add(endLabel);

                        return new PlainOperand(result);
                    }
                    default -> {
                        ValIr v1 = emitTackyAndConvert(left, instructions, inlineFunctions);
                        ValIr v2 = emitTackyAndConvert(right, instructions, inlineFunctions);
                        VarIr dstName = makeTemporary("tmp.", expr.type());
                        VarIr ptr = null;
                        ValIr other = null;
                        Type ptrRefType = null;
                        if (left.type() instanceof Pointer(Type referenced)) {
                            ptr = (VarIr) v1;
                            other = v2;
                            ptrRefType = referenced;
                        } else if (right.type() instanceof Pointer(
                                Type referenced)) {
                            ptr = (VarIr) v2;
                            other = v1;
                            ptrRefType = referenced;
                        }
                        if (ptr != null && op != COMMA) {
                            switch (op) {
                                case SUB -> {
                                    if (right.type() instanceof Pointer) {
                                        // ptr - ptr (left has to be pointer
                                        // because type checker doesn't allow
                                        // non-ptr - ptr)
                                        var diff = makeTemporary("tmp.", LONG);
                                        instructions.add(new BinaryIr(SUB,
                                                ptr, other, diff));
                                        instructions.add(new BinaryIr(DIVIDE,
                                                diff,
                                                new IntInit((int) (long) Mcc.size(ptrRefType)), dstName));
                                    } else { // ptr - int
                                        var j = makeTemporary("tmp.", LONG);
                                        instructions.add(new UnaryIr(UnaryOperator.UNARY_MINUS, other, j));
                                        instructions.add(new AddPtr(ptr, j,
                                                (int) (long) Mcc.size(ptrRefType), dstName));
                                    }
                                }
                                case ADD ->
                                        instructions.add(new AddPtr(ptr,
                                                other,
                                                (int) (long) Mcc.size(ptrRefType), dstName));

                                case CmpOperator _ ->
                                        instructions.add(new BinaryIr(op, v1,
                                                v2, dstName));
                                default ->
                                        throw new IllegalStateException(
                                                "Unexpected value: " + op);
                            }
                        } else {
                            var leftType = left.type();
                            instructions.add(new BinaryIr(op == SAR &&
                                    !leftType.isSigned() ? SHR : op, v1, v2, dstName));
                            if (leftType instanceof WidthRestricted(Type t, int width)
                                    && width < Mcc.size(t) * 8){
                                long mask = (1L << width) - 1L; // width ones in binary (works for width <= 63)
                                instructions.add(new BinaryIr(BITWISE_AND, dstName, t.fromLong(mask), dstName));
                            }
                        }
                        return new PlainOperand(dstName);
                    }
                }
            case Assignment(Exp left, Exp right, Type _):
                return assign(left, right, instructions, inlineFunctions);
            case Var(String name, Type _):
                return new PlainOperand(new VarIr(name));
            case FunctionCall(Exp name, List<Exp> args, boolean varargs, Type type,
                              int pos): {
                if (Mcc.addDebugInfo) {
                    instructions.add(new Pos(pos));
                }
                VarIr func = (VarIr) emitTackyAndConvert(name, instructions, inlineFunctions);
                VarIr result = type == VOID ? null : makeTemporary("tmp.",
                        type);
                ArrayList<ValIr> argVals = new ArrayList<>();
                for (Exp e : args) {
                    argVals.add(emitTackyAndConvert(e, instructions, inlineFunctions));
                }
                boolean indirect = SYMBOL_TABLE.get(func.identifier()).type() instanceof Pointer;

                if (func instanceof VarIr(String identifier)

                        && SYMBOL_TABLE.get(identifier).type() instanceof FunType // must be FunType not pointer etc.
                        && inlineFunctions.get(identifier) instanceof FunctionIr functionToInlineIr) {
                    //codegenInlineFunCall(funCall, functionToInlineIr, ins);
                    emitInlineFunCall(functionToInlineIr, argVals, result, instructions);
                } else {
                    instructions.add(new FunCall(func, argVals, varargs, indirect, result));
                }
                return new PlainOperand(result);
            }
            case Cast(Type t, Exp inner): {
                ValIr result = emitTackyAndConvert(inner, instructions, inlineFunctions);
                Type innerType = inner.type();
                // for the purposes of casting we treat pointers exactly like
                // unsigned long (p. 375)
                if (t instanceof Pointer) t = ULONG;
                if (innerType instanceof Pointer) innerType = ULONG;
                if (t == innerType || t == VOID) {
                    return new PlainOperand(result);
                }
                VarIr dst = makeTemporary("dst.", t);
                emitCast(instructions, t, innerType, result, dst);
                return new PlainOperand(dst);
            }
            case Dereference(Exp exp, Type t): {
                ValIr result = emitTackyAndConvert(exp, instructions, inlineFunctions);
                // deref of a function is a no-op (see c23 6.5.3.2 Address and indirection operators)
                return t instanceof FunType ? new PlainOperand(result) : new DereferencedPointer((VarIr) result);
            }
            case AddrOf(Exp inner, Type _): {
                ExpResult v = emitTacky(inner, instructions, inlineFunctions);
                return switch (v) {
                    case PlainOperand(ValIr obj) -> {
                        assert (expr.type() instanceof Pointer);
                        VarIr dst = makeTemporary("addr.", expr.type());
                        instructions.add(new GetAddress((VarIr) obj, dst));
                        yield new PlainOperand(dst);
                    }
                    case DereferencedPointer(ValIr ptr) ->
                            new PlainOperand(ptr);
                    case SubObject(VarIr base, int offset) -> {
                        var dst = makeTemporary("dst.", expr.type());
                        instructions.add(new GetAddress(base, dst));
                        if (offset != 0)
                            instructions.add(new AddPtr(dst,
                                    new LongInit(offset), 1, dst));
                        yield new PlainOperand(dst);
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + v);
                };
            }

            case Subscript(Exp left, Exp right, Type type): {
                ValIr v1 = emitTackyAndConvert(left, instructions, inlineFunctions);
                ValIr v2 = emitTackyAndConvert(right, instructions, inlineFunctions);
                VarIr dstName = makeTemporary("tmp.", new Pointer(expr.type()));
                VarIr ptr;
                ValIr other;
                long scale;
                // type checker ensures either left or right will be pointer
                // but it could also swap the left and right when they are in
                // the
                // "wrong" (index-first) order and it would make this code
                // simpler.
                // On the other hand,  it's not all that complicated and it
                // is nice
                // having the AST closely resemble the corresponding code
                if (left.type() instanceof Pointer(Type referenced)) {
                    ptr = (VarIr) v1;
                    other = v2;
                    scale = Mcc.size(type);
                } else if (right.type() instanceof Pointer(
                        Type referenced)) { // else condition just for
                    // pattern match

                    ptr = (VarIr) v2;
                    other = v1;
                    scale = Mcc.size(type);
                } else throw new AssertionError("");
                instructions.add(new AddPtr(ptr, other, (int) scale, dstName));
                return new DereferencedPointer(dstName);
            }

            case Str(String s, Type type): {
                //string literals in expressions p. 441
                String uniqueName = Mcc.makeTemporary("string.");
                SYMBOL_TABLE.put(uniqueName, new SymbolTableEntry(type,
                        new ConstantAttr(new StringInit(s, true))));
                return emitTacky(SemanticAnalysis.typeCheckExpression(new Var(uniqueName, type)), instructions, inlineFunctions);
            }
            case Alignof(Exp exp): {
                return new PlainOperand(new ULongInit(Mcc.typeAlignment(exp.type())));
            }
            case AlignofT(Type t): {
                return new PlainOperand(new ULongInit(Mcc.typeAlignment(t)));
            }
            case SizeOf(Exp exp): {
                return new PlainOperand(new ULongInit(Mcc.size(exp.type())));
            }
            case SizeOfT(Type t): {
                return new PlainOperand(new ULongInit(Mcc.size(t)));
            }
            case Offsetof(Structure structure, String member): {
                StructDef structDef = Mcc.TYPE_TABLE.get(structure.tag());
                MemberEntry memberEntry =  structDef.findMember(member);
                return new PlainOperand(new ULongInit(memberEntry.byteOffset()));
            }
            case Dot(Exp structure, String member, Type type): {
                StructDef structDef = Mcc.TYPE_TABLE.get(tag(structure));
                MemberEntry memberEntry = structDef.findMember(member);
                int memberOffset = memberEntry.byteOffset();
                ExpResult innerObject = emitTacky(structure, instructions, inlineFunctions);
                return switch (innerObject) {
                    case DereferencedPointer(ValIr ptr) -> switch (memberEntry){
                        case BitFieldMember(
                                String _,
                                Type _,
                                int _,
                                int bitOffset,
                                int bitWidth
                        ) -> new BitFieldSubObjectViaPointer((VarIr)ptr, memberOffset, bitOffset, bitWidth);
                        case OrdinaryMember _ -> {
                            if (memberOffset == 0) yield innerObject;
                            VarIr dstPtr = makeTemporary("ptr",
                                            new Pointer(expr.type()));
                            instructions.add(new AddPtr((VarIr) ptr,
                                    new LongInit(memberOffset), 1, dstPtr));
                            yield new DereferencedPointer(dstPtr);

                        }
                    };
                    case PlainOperand(VarIr v) -> switch (memberEntry){
                        case BitFieldMember(
                                String _,
                                Type _,
                                int _,
                                int bitOffset,
                                int bitWidth
                        ) -> new BitFieldSubObject(v, memberOffset, bitOffset, bitWidth);
                        case OrdinaryMember _ -> new SubObject(v, memberOffset);
                    };
                    case SubObject(VarIr v , int offset)-> switch (memberEntry){
                        case BitFieldMember(
                                String _,
                                Type _,
                                int _,
                                int bitOffset,
                                int bitWidth
                        ) -> new BitFieldSubObject(v, memberOffset+offset, bitOffset, bitWidth);
                        case OrdinaryMember _ -> new SubObject(v, memberOffset+offset);
                    };
                    default ->
                            throw new IllegalStateException("Unexpected " +
                                    "value: " + innerObject);
                };

            }
            case Arrow(Exp pointer, String member, Type type): {
                Pointer pointerType = (Pointer) (pointer.type());
                return emitTacky(new Dot(new Dereference(pointer,
                        pointerType.referenced()), member, type),
                        instructions, inlineFunctions);
            }
            case ExpressionStatement(Block block): {

                ArrayList<BlockItem> blockItems = block.blockItems();
                for (int j = 0, blockItemsSize = blockItems.size();
                     j < blockItemsSize - 1; j++) {
                    BlockItem i = blockItems.get(j);
                    compileBlockItem(i, instructions, inlineFunctions);
                }
                var l = blockItems.getLast();
                return switch(l){
                    case Exp e -> emitTacky(e, instructions, inlineFunctions);
                    case If _ -> {
                        compileBlockItem(l, instructions, inlineFunctions);
                        yield null;
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + l);
                };


            }
            case BuiltinVaArg(Var identifier, Type type): {
                VarIr src = (VarIr) emitTackyAndConvert(identifier,
                        instructions, inlineFunctions);
                VarIr dst = makeTemporary("tmp.", type);
                instructions.add(new BuiltinVaArgIr(src, dst, type));
                return new PlainOperand(dst);
            }
            case BuiltInFunctionCall(BuiltInFunction name, List<Exp> args,
                                     Type type): {
                switch (name) {
                    case ATOMIC_STORE_N -> {
                        ExpResult o = emitTacky(args.get(0), instructions, inlineFunctions);
                        if (o instanceof PlainOperand(VarIr ptr)){
                            ValIr val = emitTackyAndConvert(args.get(1), instructions, inlineFunctions);
                            ValIr memOrder = emitTackyAndConvert(args.get(2), instructions, inlineFunctions);
                            // need a tmp if val is constant
                            if (val instanceof Constant c) {
                                VarIr tmp = makeTemporary("tmp.", c.type());
                                instructions.add(new Copy(val, tmp));
                                val = tmp;
                            }
                            instructions.add(new AtomicStore(val, ptr, MemoryOrder.from(memOrder)));
                            return null;
                        } else throw new Todo();
                    }
                    case ATOMIC_LOAD_N -> {
                        ValIr result = emitTackyAndConvert(args.get(0), instructions, inlineFunctions);
                        return new DereferencedPointer((VarIr) result);
                    }
                    case BUILTIN_ADD_OVERFLOW -> {
                        return emitOverflowArithmetic(ADD, expr, instructions, args, inlineFunctions);
                    }
                    case BUILTIN_SUB_OVERFLOW -> {
                        return emitOverflowArithmetic(SUB, expr, instructions, args, inlineFunctions);
                    }
                    case BUILTIN_MUL_OVERFLOW -> {
                        return emitOverflowArithmetic(IMUL, expr, instructions, args, inlineFunctions);
                    }
                    case BUILTIN_BSWAP16,
                         BUILTIN_BSWAP32,
                         BUILTIN_BSWAP64 -> {
                        ValIr v1 =
                                emitTackyAndConvert(args.get(0), instructions, inlineFunctions);
                        VarIr result = makeTemporary("tmp.", expr.type());

                        instructions.add(new UnaryIr(UnaryOperator.BSWAP, v1, result));
                        return new PlainOperand(result);
                    }
                    case BUILTIN_CLZLL -> {
                        ValIr v1 =
                                emitTackyAndConvert(args.get(0), instructions, inlineFunctions);
                        VarIr result = makeTemporary("tmp.", expr.type());

                        instructions.add(new UnaryIr(UnaryOperator.CLZ, v1, result));
                        return new PlainOperand(result);
                    }
                    case SYNC_SYNCHRONIZE ->  {
                        instructions.add(Nullary.MFENCE);
                        return null;
                    }
                    case BUILTIN_NANF -> {
                        Exp arg = args.getFirst();
                        String number = arg instanceof Str(String s, Type _) ? s
                                : arg instanceof AddrOf(Exp exp, Type _) && exp instanceof Str(String s, Type _)?s:"0";
                        TokenList tl = Lexer.lex(number + " ");
                        int i = tl.isEmpty() ? 0 : (int) Parser.parseConstant(tl.getFirst()).toLong();
                        return new PlainOperand(new FloatInit(payloadToNaNFloat(i)));
                    }
                    case BUILTIN_INFF -> {
                        return new PlainOperand(new FloatInit(Float.POSITIVE_INFINITY));
                    }
                    case BUILTIN_MEMSET -> {

                        if (emitTacky(args.get(0), instructions, inlineFunctions) instanceof PlainOperand(
                                VarIr ptr)) {
                            int ch =
                                    (int) SemanticAnalysis.evaluateExpAsConstant(args.get(1)).toLong();
                            long byteCount =
                                    SemanticAnalysis.evaluateExpAsConstant(args.get(2)).toLong();
                            instructions.add(new Memset(ptr, ch,
                                    byteCount, true));
                        } else throw new Todo();
                        return null;

                    }
                }
            }


            default:
                throw new IllegalStateException("Unexpected value: " + expr);
        }
    }

    public static float payloadToNaNFloat(int payload) {
        int frac = payload & 0x7FFFFF;

        // Force quiet NaN bit (bit 22)
        frac |= 0x400000;

        int bits = (0xFF << 23) | frac;
        return Float.intBitsToFloat(bits);
    }

    public static double payloadToNaNDouble(long payload) {
        // Keep only 52 bits for the fraction
        long frac = payload & 0x000F_FFFF_FFFF_FFFFL;

        // Force quiet NaN (top fraction bit = 1)
        frac |= 0x0008_0000_0000_0000L;

        // sign = 0, exponent = 0x7FF (all 1s), fraction = payload
        long bits = (0x7FFL << 52) | frac;

        return Double.longBitsToDouble(bits);
    }

    private static void emitInlineFunCall(FunctionIr functionToInlineIr,
                                          ArrayList<ValIr> argVals,
                                          VarIr result,
                                          List<InstructionIr> instructions) {
        Map<VarIr, ValIr> substitutionTable = new HashMap<>();
        List<Var> params = functionToInlineIr.type();
        for(int i = 0; i < functionToInlineIr.type().size();i++){
            Var p = params.get(i);
            substitutionTable.put(new VarIr(p.name()), argVals.get(i));
        }
        java.util.function.Function<ValIr, ValIr> s = val->
            val instanceof VarIr v ? substitutionTable.getOrDefault(v, v): val;

        AtomicInteger labelSuffix = new AtomicInteger(0);

        Map<String, String> labelTable = new HashMap<>();

        java.util.function.Function<String, String> fixLabel = l ->
                labelTable.computeIfAbsent(l, k-> k + labelSuffix.incrementAndGet());
        int varArgIndex=functionToInlineIr.type().size();
        LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
        for (var instr : functionToInlineIr.instructions()) {
            InstructionIr i = switch(instr){
                case Copy(ValIr src, VarIr dst) -> new Copy(s.apply(src), dst);
                case BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                              VarIr dstName) ->
                        new BinaryIr(op, s.apply(v1), s.apply(v2), dstName);
                case ReturnIr(ValIr val) -> {
                    instructions.add(new Copy(s.apply(val), result));
                    yield new Jump(endLabel.label());
                }
                case BuiltinC23VaStartIr _ -> null;
                case LabelIr(String label) -> new LabelIr(fixLabel.apply(label));
                case JumpIfZero(ValIr v,String label) -> new JumpIfZero(s.apply(v), fixLabel.apply(label));
                case JumpIfNotZero(ValIr v,String label) -> new JumpIfNotZero(s.apply(v), fixLabel.apply(label));
                case Jump(String label) -> new Jump(fixLabel.apply(label));
                case CopyFromOffset(VarIr src, long offset, VarIr dst) -> new CopyFromOffset((VarIr) s.apply(src), offset, dst);
                case CopyBitsFromOffset(VarIr base, int byteOffset, int bitOffset,
                                        int bitWidth,
                                        VarIr dst) -> new CopyBitsFromOffset((VarIr) s.apply(base), byteOffset, bitOffset, bitWidth, dst);
                case CopyBitsToOffset(ValIr rval, VarIr base, long  byteOffset,
                                      int bitOffset,
                                      int bitWidth) -> new CopyBitsToOffset(s.apply(rval), (VarIr) s.apply(base), byteOffset, bitOffset, bitWidth);
                case IntToDouble(ValIr src, VarIr dst) -> new IntToDouble(s.apply(src), dst);
                case Load(ValIr ptr, VarIr dst)-> new Load(s.apply(ptr), dst);
                case Store(ValIr v, VarIr dst)-> new Store(s.apply(v), dst);
                case SignExtendIr(ValIr src, VarIr dst)  -> new SignExtendIr(s.apply(src), dst);
                case ZeroExtendIr(ValIr src, VarIr dst)  -> new ZeroExtendIr(s.apply(src), dst);
                case TruncateIr(ValIr src, VarIr dst)  -> new TruncateIr(s.apply(src), dst);
                case GetAddress(ValIr src, VarIr dst)  -> new GetAddress((VarIr) s.apply(src), dst);
                case AddPtr(VarIr ptr, ValIr index, int scale, VarIr dst) -> new  AddPtr((VarIr) s.apply(ptr), s.apply(index), scale, dst);
                case FunCall(VarIr name, ArrayList<ValIr> args, boolean varargs,
                             boolean indirect, VarIr dst) -> {
                    ArrayList<ValIr> newArgs = new ArrayList<>();
                    for (var a : args) {
                        newArgs.add(s.apply(a));
                    }
                    yield new FunCall((VarIr) s.apply(name), newArgs, varargs
                            , indirect, dst);
                }
                default -> throw new IllegalStateException(
                        "Unexpected value: " + instr);
            };
            if (i != null) instructions.add(i);
        }
        instructions.add(endLabel);
    }

    private static PlainOperand emitOverflowArithmetic(ArithmeticOperator op, Exp expr,
                                                List<InstructionIr> instructions,
                                                List<Exp> args, Map<String, FunctionIr> inlineFunctions) {
        ValIr v1 =
                emitTackyAndConvert(args.get(0), instructions, inlineFunctions);
        ValIr v2 =
                emitTackyAndConvert(args.get(1), instructions, inlineFunctions);
        ValIr result =
                emitTackyAndConvert(args.get(2), instructions, inlineFunctions);
        VarIr overflow = makeTemporary("tmp.", expr.type());

        instructions.add(new BinaryWithOverflowIr(op, v1, v2
                , result, overflow));

        return new PlainOperand(overflow);
    }

    private static void emitCast(List<InstructionIr> instructions, Type to,
                                 Type innerType, ValIr src, VarIr dst) {
        if ((to == DOUBLE || to == FLOAT) && innerType.isInteger()) {
            instructions.add(innerType.isSigned() ?
                    new IntToDouble(src, dst) : new UIntToDouble(src, dst));
        } else if ((innerType == DOUBLE || innerType == FLOAT) && to.isInteger()) {
            instructions.add(to.isSigned() ? new DoubleToInt(src, dst) :
                    new DoubleToUInt(src, dst));
        } else if (innerType == DOUBLE && to == FLOAT) {
            instructions.add(new DoubleToFloat(src, dst));
        } else if (innerType == FLOAT && to == DOUBLE) {
            instructions.add(new FloatToDouble(src, dst));
        } else if (Mcc.size(to) == Mcc.size(innerType)) {
            instructions.add(new Copy(src, dst));
        } else {
            if (Mcc.size(to) < Mcc.size(innerType)) {
                instructions.add(new TruncateIr(src, dst));
            } else if (innerType.isSigned()) {
                instructions.add(new SignExtendIr(src, dst));
            } else {
                instructions.add(new ZeroExtendIr(src, dst));
            }
        }
    }

    private static String tag(Exp structure) {
        if (structure.type() instanceof Structure(boolean isUnion, String tag, StructDef _)) return tag;
        throw new AssertionError();
    }

    private static String ptrTag(Exp structure) {
        if (structure.type() instanceof Pointer(
                Type s) && s instanceof Structure(boolean isUnion, String tag, StructDef _)) return tag;
        throw new AssertionError();
    }

    public static ValIr emitTackyAndConvert(Exp e,
                                            List<InstructionIr> instructions,
                                            Map<String, FunctionIr> inlineFunctions) {
        ExpResult result = emitTacky(e, instructions, inlineFunctions);
        return convert(result, e, instructions);
    }

    private static ValIr convert(ExpResult result,
                                 Exp e,
                                 List<InstructionIr> instructions) {
        return switch (result) {
            case null -> null;
            case DereferencedPointer(ValIr ptr) -> {
                VarIr dst = makeTemporary("ptr.", e.type());
                instructions.add(new Load(ptr, dst));
                yield dst;
            }
            case PlainOperand(ValIr v) -> v;
            case SubObject(VarIr base, int offset) -> {
                VarIr dst = makeTemporary("dst.", e.type());
                instructions.add(new CopyFromOffset(base, offset, dst));
                yield dst;
            }
            case BitFieldSubObject(VarIr base, int byteOffset, int bitOffset,
                                   int bitWidth) -> {
                VarIr dst = makeTemporary("dst.", e.type());
                instructions.add(new CopyBitsFromOffset(base, byteOffset, bitOffset, bitWidth, dst));
                yield dst;
            }
            case BitFieldSubObjectViaPointer(VarIr ptr, int byteOffset, int bitOffset,
                                             int bitWidth) -> {
                VarIr dst = makeTemporary("dst.", e.type());
                instructions.add(new CopyBitsFromOffsetViaPointer(ptr, byteOffset, bitOffset, bitWidth, dst));
                yield dst;
            }
        };
    }

    private static void assign(VarIr dst, Exp right,
                               List<InstructionIr> instructions,
                               Map<String, FunctionIr> inlineFunctions) {
        ValIr rval = emitTackyAndConvert(right, instructions, inlineFunctions);
        instructions.add(new Copy(rval, dst));
    }

    private static ExpResult applyOperatorAndAssign(
            List<InstructionIr> instructions, Exp exp, ExpResult lval,
            ValIr right, ArithmeticOperator newOp, boolean post,
            Type commonType, Type lvalueType) {
        return switch (lval) {
            case PlainOperand(VarIr obj) -> {
                VarIr old = makeTemporary("old.", exp.type());
                instructions.add(new Copy(obj, old));
                handleCompoundOperatorHelper(newOp, instructions, obj, right,
                        commonType, lvalueType);
                yield post ? new PlainOperand(old) : lval;
            }
            case DereferencedPointer(VarIr ptr) -> {
                VarIr newVal = makeTemporary("newVal.", exp.type());
                VarIr old = makeTemporary("old.", exp.type());
                instructions.add(new Load(ptr, newVal));
                instructions.add(new Copy(newVal, old));
                handleCompoundOperatorHelper(newOp, instructions, newVal,
                        right, commonType, lvalueType);

                instructions.add(new Store(newVal, ptr));
                yield post ? new PlainOperand(old) : lval;
            }
            case SubObject(VarIr base, int offset) -> {
                VarIr newVal = makeTemporary("newVal.", exp.type());
                VarIr old = makeTemporary("old.", exp.type());

                instructions.add(new CopyFromOffset(base, offset, newVal));
                instructions.add(new Copy(newVal, old));
                handleCompoundOperatorHelper(newOp, instructions, newVal,
                        right, commonType, lvalueType);

                instructions.add(new CopyToOffset(newVal, base, offset));
                yield post ? new PlainOperand(old) : lval;
            }
            case BitFieldSubObject(VarIr base, int byteOffset, int bitOffset,
                                   int bitWidth) -> {
                VarIr newVal = makeTemporary("newVal.", exp.type());
                VarIr old = makeTemporary("old.", exp.type());

                instructions.add(new CopyBitsFromOffset(base, byteOffset, bitOffset, bitWidth, newVal));
                instructions.add(new Copy(newVal, old));
                handleCompoundOperatorHelper(newOp, instructions, newVal,
                        right, commonType, lvalueType);

                instructions.add(new CopyBitsToOffset(newVal, base, byteOffset, bitOffset, bitWidth));
                yield post ? new PlainOperand(old) : lval;
            }
            default -> throw new Todo();
        };
    }

    private static void handleCompoundOperatorHelper(ArithmeticOperator newOp,
                                                     List<InstructionIr> instructions,
                                                     VarIr left, ValIr right,
                                                     Type commonType,
                                                     Type lvalueType) {

        if (lvalueType instanceof Pointer(Type ptrRefType)) {
            if (newOp == SUB) {
                var minusRight = makeTemporary("neg.", LONG);
                instructions.add(new UnaryIr(UnaryOperator.UNARY_MINUS, right
                        , minusRight));
                right = minusRight;
            }
            instructions.add(new AddPtr(left, right,
                    (int) Mcc.size(ptrRefType), left));
        } else {
            // var leftType=Mcc.valToType(left);
            if (!commonType.equals(lvalueType)) {
                VarIr tmp = makeTemporary("tmp.", commonType);
                VarIr newLeft = makeTemporary("left.", commonType);
                emitCast(instructions, commonType, lvalueType, left, newLeft);
                instructions.add(new BinaryIr(newOp, newLeft, right, tmp));
                emitCast(instructions, lvalueType, commonType, tmp, left);
            } else instructions.add(new BinaryIr(newOp, left, right, left));
        }
    }

    private static ExpResult assign(Exp left, Exp right,
                                    List<InstructionIr> instructions,
                                    Map<String, FunctionIr> inlineFunctions) {
        ExpResult lval = emitTacky(left, instructions, inlineFunctions);
         ValIr rval = emitTackyAndConvert(right, instructions, inlineFunctions);
        return switch (lval) {
            case PlainOperand(VarIr obj) -> {
                instructions.add(new Copy(rval, obj));
                yield lval;
            }
            case DereferencedPointer(VarIr ptr) -> {
                instructions.add(new Store(rval, ptr));
                //yield new PlainOperand(rval);
                yield lval;
            }
            case SubObject(VarIr base, int offset) -> {
                instructions.add(new CopyToOffset(rval, base, offset));
                //yield new PlainOperand(rval);
                yield lval;
            }
            case BitFieldSubObject(VarIr base, int byteOffset, int bitOffset,
                                   int bitWidth)->{
                instructions.add(new CopyBitsToOffset(rval, base, byteOffset, bitOffset, bitWidth));
                yield lval;
            }

            case BitFieldSubObjectViaPointer(VarIr base, int byteOffset, int bitOffset,
                                             int bitWidth)->{
                instructions.add(new CopyBitsToOffsetViaPointer(rval, base, byteOffset, bitOffset, bitWidth));
                yield new PlainOperand(rval);
            }
            default ->
                    throw new IllegalStateException("Unexpected value: " + lval);
        };

    }

    static AtomicLong labelCount = new AtomicLong(0L);

    public static LabelIr newLabel(String prefix) {
        return new LabelIr(prefix);
    }

    private static VarIr makeTemporary(String prefix, Type t) {
        String name = Mcc.makeTemporary(prefix);
        SYMBOL_TABLE.put(name, new SymbolTableEntry(t, LOCAL_ATTR));
        return new VarIr(name);
    }

}
