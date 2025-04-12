package com.quaxt.mcc.tacky;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.parser.StorageClass.EXTERN;
import static com.quaxt.mcc.parser.StorageClass.STATIC;
import static com.quaxt.mcc.semantic.Primitive.*;

import com.quaxt.mcc.semantic.*;

public class IrGen {

    public static ProgramIr programIr(Program program) {
        List<TopLevel> tackyDefs = new ArrayList<>();
        for (Function function : program.functions()) {
            if (function.body() != null)
                tackyDefs.add(compileFunction(function));
        }
        convertSymbolsToTacky(tackyDefs);
        return new ProgramIr(tackyDefs);
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
                case StaticAttributes(InitialValue init, boolean global) -> {
                    if (init instanceof InitialValue.Tentative) {
                        tackyDefs.add(new StaticVariable(name, global, value.type(), Collections.singletonList(new ZeroInit(value.type().size()))));
                    } else if (init instanceof Initial(
                            List<StaticInit> initList)) {
                        tackyDefs.add(new StaticVariable(name, global, value.type(), initList));
                    }
                }
            }

        }
    }

    private static FunctionIr compileFunction(Function function) {
        List<InstructionIr> instructions = new ArrayList<>();
        compileBlock(function.body(), instructions);
        FunctionIr f = new FunctionIr(function.name(), SYMBOL_TABLE.get(function.name()).attrs().global(), function.parameters(), instructions);
        ReturnIr ret = new ReturnIr(new ConstInt(0));
        instructions.add(ret);
        return f;
    }

    private static void compileBlock(Block block, List<InstructionIr> instructions) {
        compileBlockItems(block.blockItems(), instructions);
    }

    private static void compileDeclaration(Declaration d, List<InstructionIr> instructions) {
        switch (d) {
            case Function function -> {
                if (function.body() != null) compileFunction(function);
            }
            case VarDecl(Var name, Initializer init, Type _,
                         StorageClass storageClass) -> {
                if (storageClass == STATIC || storageClass == EXTERN) return;
                if (init != null) {
                    assign(name, init, instructions, 0);
                    return;
                }
                emitTacky(null, instructions);
            }
        }
    }

    private static int assign(Var name, Initializer init, List<InstructionIr> instructions, int offset) {
        switch (init) {
            case CompoundInit(ArrayList<Initializer> inits) -> {

                ExpResult lval = emitTacky(name, instructions);
                switch (lval) {
                    case PlainOperand(VarIr dst) -> {

                        for (Initializer innerInit : inits) {
                            switch (innerInit) {
                                case CompoundInit _ ->
                                        offset = assign(name, innerInit, instructions, offset);
                                case SingleInit(Exp exp) -> {

                                    if (exp instanceof Str(String s,
                                                           Type type) && type instanceof Array(
                                            Type _, Constant arraySize)) {
                                        initializeArrayWithStringLiteral(name, instructions, offset, s, arraySize);
                                    } else {
                                        var val = emitTackyAndConvert(exp, instructions);
                                        instructions.add(new CopyToOffset(val, dst, offset));
                                    }

                                    offset += exp.type().size();
                                }
                            }
                        }

                    }
                    default ->
                            throw new IllegalStateException("Unexpected value: " + lval);
                }
                ;


            }
            case SingleInit(Exp exp) -> {
                // String literal as array initializer p. 440
                if (exp instanceof Str(String s,
                                       Type type) && type instanceof Array(
                        Type _, Constant arraySize)) {
                    initializeArrayWithStringLiteral(name, instructions, offset, s, arraySize);
                } else {
                    ExpResult r = assign(name, exp, instructions);
                }
                offset += exp.type().size();
            }
        }
        return offset;
    }

    private static void initializeArrayWithStringLiteral(Var name, List<InstructionIr> instructions, int offset, String s, Constant arraySize) {
        ExpResult lval = emitTacky(name, instructions);
        switch (lval) {
            case PlainOperand(VarIr dst) -> {
                int arrayLen = arraySize.toInt();
                int howManyCharsToCopy = Math.min(s.length(), arrayLen);
                for (int i = 0; i < howManyCharsToCopy; i++) {
                    instructions.add(new CopyToOffset(new ConstChar((byte) (s.charAt(i) & 0xff)), dst, offset + i));
                }
                for (int i = howManyCharsToCopy; i < arrayLen; i++) {
                    instructions.add(new CopyToOffset(ConstChar.zero(), dst, offset + i));
                }
            }
            default ->
                    throw new IllegalStateException("Unexpected value: " + lval);
        }
    }


    private static void compileBlockItems(List<BlockItem> blockItems, List<InstructionIr> instructions) {
        for (BlockItem i : blockItems) {
            switch (i) {

                case Declaration d -> compileDeclaration(d, instructions);
                case Statement statement ->
                        compileStatement(statement, instructions);
            }
        }
    }

    private static void compileIfElse(Exp condition, Statement ifTrue, Statement ifFalse, List<InstructionIr> instructions) {
        ValIr c = emitTackyAndConvert(condition, instructions);
        LabelIr e2Label = newLabel("e2");
        instructions.add(new JumpIfZero(c, e2Label.label()));
        compileStatement(ifTrue, instructions);
        LabelIr endLabel = newLabel("end");
        instructions.add(new Jump(endLabel.label()));
        instructions.add(e2Label);
        compileStatement(ifFalse, instructions);
        instructions.add(endLabel);
    }

    private static void compileIf(Exp condition, Statement ifTrue, List<InstructionIr> instructions) {
        ValIr c = emitTackyAndConvert(condition, instructions);
        LabelIr endLabel = newLabel("end");
        instructions.add(new JumpIfZero(c, endLabel.label()));
        compileStatement(ifTrue, instructions);
        instructions.add(endLabel);
    }


    private static void compileStatement(Statement i, List<InstructionIr> instructions) {
        switch (i) {
            case Return(Exp exp) -> {
                ValIr retVal = exp == null ? null : emitTackyAndConvert(exp, instructions);
                ReturnIr ret = new ReturnIr(retVal);
                instructions.add(ret);
            }

            case Exp exp -> emitTacky(exp, instructions);

            case If(Exp condition, Statement ifTrue, Statement ifFalse) -> {
                if (ifFalse != null) {
                    compileIfElse(condition, ifTrue, ifFalse, instructions);
                } else {
                    compileIf(condition, ifTrue, instructions);

                }
            }
            case NullStatement _ -> {
            }
            case Block b -> compileBlock(b, instructions);

            case Break aBreak ->
                    instructions.add(new Jump(breakLabel(aBreak.label)));
            case Continue aContinue ->
                    instructions.add(new Jump(continueLabel(aContinue.label)));
            case DoWhile(Statement body, Exp condition, String label) -> {
                LabelIr start = newLabel("start");
                instructions.add(start);
                compileStatement(body, instructions);
                LabelIr continueLabel = new LabelIr(continueLabel(label));
                instructions.add(continueLabel);
                ValIr v = emitTackyAndConvert(condition, instructions);
                instructions.add(new JumpIfNotZero(v, start.label()));
                LabelIr breakLabel = new LabelIr(breakLabel(label));
                instructions.add(breakLabel);
            }
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> {

                switch (init) {
                    case Declaration d -> compileDeclaration(d, instructions);
                    case Exp e -> emitTacky(e, instructions);
                    case null -> {
                    }
                }

                LabelIr start = new LabelIr(startLabel(label));
                LabelIr continueLabel = new LabelIr(continueLabel(label));
                LabelIr breakLabel = new LabelIr(breakLabel(label));
                instructions.add(start);
                if (condition != null) {
                    ValIr v = emitTackyAndConvert(condition, instructions);
                    instructions.add(new JumpIfZero(v, breakLabel.label()));
                }
                compileStatement(body, instructions);
                instructions.add(continueLabel);
                emitTacky(post, instructions);
                instructions.add(new Jump(start.label()));
                instructions.add(breakLabel);
            }
            case While(Exp condition, Statement body, String label) -> {
                LabelIr continueLabel = new LabelIr(continueLabel(label));
                instructions.add(continueLabel);
                ValIr v = emitTackyAndConvert(condition, instructions);
                LabelIr breakLabel = new LabelIr(breakLabel(label));
                instructions.add(new JumpIfZero(v, breakLabel.label()));
                compileStatement(body, instructions);
                instructions.add(new Jump(continueLabel.label()));
                instructions.add(breakLabel);
            }
        }
    }

    private static String startLabel(String label) {
        return "start_" + label;
    }

    private static String continueLabel(String label) {
        return "continue_" + label;
    }

    private static String breakLabel(String label) {
        return "break_" + label;
    }


    private static ExpResult emitTacky(Exp expr, List<InstructionIr> instructions) {
        switch (expr) {
            case null:
                return null;
            case ConstInt c:
                return new PlainOperand(c);
            case ConstLong c:
                return new PlainOperand(c);
            case ConstUInt c:
                return new PlainOperand(c);
            case ConstULong c:
                return new PlainOperand(c);
            case ConstDouble c:
                return new PlainOperand(c);
            case ConstChar c:
                return new PlainOperand(c);
            case ConstUChar c:
                return new PlainOperand(c);
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type): {
                ValIr cond = emitTackyAndConvert(condition, instructions);
                LabelIr e2Label = newLabel("e2");
                instructions.add(new JumpIfZero(cond, e2Label.label()));
                if (type == VOID) {
                    emitTackyAndConvert(ifTrue, instructions);
                    LabelIr endLabel = newLabel("end");
                    instructions.add(new Jump(endLabel.label()));
                    instructions.add(e2Label);
                    emitTackyAndConvert(ifFalse, instructions);
                    instructions.add(endLabel);
                    return null;//not used by caller (see p480)
                } else {
                    ValIr e1 = emitTackyAndConvert(ifTrue, instructions);
                    VarIr result = makeTemporary("result", type);
                    instructions.add(new Copy(e1, result));
                    LabelIr endLabel = newLabel("end");
                    instructions.add(new Jump(endLabel.label()));
                    instructions.add(e2Label);
                    ValIr e2 = emitTackyAndConvert(ifFalse, instructions);
                    instructions.add(new Copy(e2, result));
                    instructions.add(endLabel);
                    return new PlainOperand(result);
                }
            }
            case UnaryOp(UnaryOperator op, Exp exp, Type type): {
                ValIr src = emitTackyAndConvert(exp, instructions);
                VarIr dst = makeTemporary("tmp.", type);
                instructions.add(new UnaryIr(op, src, dst));
                return new PlainOperand(dst);
            }
            case BinaryOp(BinaryOperator op, Exp left, Exp right, Type _):
                switch (op) {
                    case AND -> {
                        VarIr result = makeTemporary("tmp.", INT);

                        LabelIr falseLabel = newLabel("andFalse");
                        LabelIr endLabel = newLabel("andEnd");
                        ValIr v1 = emitTackyAndConvert(left, instructions);
                        instructions.add(new JumpIfZero(v1, falseLabel.label()));

                        ValIr v2 = emitTackyAndConvert(right, instructions);
                        instructions.add(new JumpIfZero(v2, falseLabel.label()));
                        instructions.add(new Copy(new ConstInt(1), result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(falseLabel);
                        instructions.add(new Copy(new ConstInt(0), result));
                        instructions.add(endLabel);

                        return new PlainOperand(result);
                    }
                    case OR -> {
                        VarIr result = makeTemporary("tmp.", INT);

                        LabelIr trueLabel = newLabel("true");
                        LabelIr endLabel = newLabel("end");
                        ValIr v1 = emitTackyAndConvert(left, instructions);
                        instructions.add(new JumpIfNotZero(v1, trueLabel.label()));

                        ValIr v2 = emitTackyAndConvert(right, instructions);
                        instructions.add(new JumpIfNotZero(v2, trueLabel.label()));
                        instructions.add(new Copy(new ConstInt(0), result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(trueLabel);
                        instructions.add(new Copy(new ConstInt(1), result));
                        instructions.add(endLabel);

                        return new PlainOperand(result);
                    }
                    default -> {
                        ValIr v1 = emitTackyAndConvert(left, instructions);
                        ValIr v2 = emitTackyAndConvert(right, instructions);
                        VarIr dstName = makeTemporary("tmp.", expr.type());
                        ValIr ptr = null;
                        ValIr other = null;
                        int scale = 0;
                        if (left.type() instanceof Pointer(Type referenced)) {
                            ptr = v1;
                            other = v2;
                            scale = referenced.size();
                        } else if (right.type() instanceof Pointer(
                                Type referenced)) {
                            ptr = v2;
                            other = v1;
                            scale = referenced.size();
                        }
                        if (scale != 0) {
                            switch (op) {
                                case SUB -> {
                                    if (right.type() instanceof Pointer) {
                                        // ptr - ptr (left has to be pointer because type checker doesn't allow non-ptr - ptr)
                                        var diff = makeTemporary("tmp.", LONG);
                                        instructions.add(new BinaryIr(SUB, ptr, other, diff));
                                        instructions.add(new BinaryIr(DIVIDE, diff, new ConstInt(scale), dstName));
                                    } else { // ptr - int
                                        var j = makeTemporary("tmp.", LONG);
                                        instructions.add(new UnaryIr(UnaryOperator.UNARY_MINUS, other, j));
                                        instructions.add(new AddPtr(ptr, j, scale, dstName));
                                    }
                                }
                                case ADD ->
                                        instructions.add(new AddPtr(ptr, other, scale, dstName));

                                case CmpOperator _ ->
                                        instructions.add(new BinaryIr(op, v1, v2, dstName));
                                default ->
                                        throw new IllegalStateException("Unexpected value: " + op);
                            }
                        } else
                            instructions.add(new BinaryIr(op, v1, v2, dstName));
                        return new PlainOperand(dstName);
                    }
                }
            case Assignment(Exp left, Exp right, Type _):
                return assign(left, right, instructions);
            case Var(String name, Type _):
                return new PlainOperand(new VarIr(name));
            case FunctionCall(Var name, List<Exp> args, Type type): {
                VarIr result = type == VOID ? null : makeTemporary("tmp.", type);
                ArrayList<ValIr> argVals = new ArrayList<>();
                for (Exp e : args) {
                    argVals.add(emitTackyAndConvert(e, instructions));
                }
                instructions.add(new FunCall(name.name(), argVals, result));
                return new PlainOperand(result);
            }
            case Cast(Type t, Exp inner): {
                ValIr result = emitTackyAndConvert(inner, instructions);
                Type innerType = inner.type();
                // for the purposes of casting we treat pointers exactly like unsigned long (p. 375)
                if (t instanceof Pointer) t = ULONG;
                if (innerType instanceof Pointer) innerType = ULONG;
                if (t == innerType || t == VOID) {
                    return new PlainOperand(result);
                }
                VarIr dst = makeTemporary("dst", t);
                if (t == DOUBLE) {
                    instructions.add(innerType.isSigned() ? new IntToDouble(result, dst) : new UIntToDouble(result, dst));
                } else if (innerType == DOUBLE) {
                    instructions.add(t.isSigned() ? new DoubleToInt(result, dst) : new DoubleToUInt(result, dst));
                } else if (t.size() == innerType.size()) {
                    instructions.add(new Copy(result, dst));
                } else if (t.size() < innerType.size()) {
                    instructions.add(new TruncateIr(result, dst));
                } else if (innerType.isSigned()) {
                    instructions.add(new SignExtendIr(result, dst));
                } else {
                    instructions.add(new ZeroExtendIr(result, dst));
                }
                return new PlainOperand(dst);
            }
            case Dereference(Exp exp, Type _): {
                ValIr result = emitTackyAndConvert(exp, instructions);
                return new DereferencedPointer(result);
            }
            case AddrOf(Exp inner, Type _): {
                ExpResult v = emitTacky(inner, instructions);
                return switch (v) {
                    case PlainOperand(ValIr obj) -> {
                        assert (expr.type() instanceof Pointer);
                        VarIr dst = makeTemporary("addr.", expr.type());
                        instructions.add(new GetAddress(obj, dst));
                        yield new PlainOperand(dst);
                    }
                    case DereferencedPointer(ValIr ptr) ->
                            new PlainOperand(ptr);
                };
            }

            case Subscript(Exp left, Exp right, Type type): {
                ValIr v1 = emitTackyAndConvert(left, instructions);
                ValIr v2 = emitTackyAndConvert(right, instructions);
                VarIr dstName = makeTemporary("tmp.", new Pointer(expr.type()));
                ValIr ptr;
                ValIr other;
                int scale;
                // type checker ensures either left or right will be pointer
                // but it could also swap the left and right when they are in the
                // "wrong" (index-first) order and it would make this code simpler.
                // On the other hand,  it's not all that complicated and it is nice
                // having the AST closely resemble the corresponding code
                if (left.type() instanceof Pointer(Type referenced)) {
                    ptr = v1;
                    other = v2;
                    scale = referenced.size();
                } else if (right.type() instanceof Pointer(
                        Type referenced)) { // else condition just for pattern match
                    ptr = v2;
                    other = v1;
                    scale = referenced.size();
                } else throw new AssertionError("");
                instructions.add(new AddPtr(ptr, other, scale, dstName));
                return new DereferencedPointer(dstName);
            }

            case Str(String s, Type type): {
                //string literals in expressions p. 441
                String uniqueName = Mcc.makeTemporary("string.");
                SYMBOL_TABLE.put(uniqueName, new SymbolTableEntry(type, new ConstantAttr(new StringInit(s, true))));
                return emitTacky(SemanticAnalysis.typeCheckExpression(new Var(uniqueName, type)), instructions);
            }
            case SizeOf(Exp exp): {
                return new PlainOperand(new ConstULong(exp.type().size()));
            }
            case SizeOfT(Type t):{
                return new PlainOperand(new ConstULong(t.size()));
            }

        }
    }


    private static ValIr emitTackyAndConvert(Exp e, List<InstructionIr> instructions) {
        ExpResult result = emitTacky(e, instructions);
        return switch (result) {
            case null -> null;
            case DereferencedPointer(ValIr ptr) -> {
                VarIr dst = makeTemporary("ptr", e.type());
                instructions.add(new Load(ptr, dst));
                yield dst;
            }
            case PlainOperand(ValIr v) -> v;
        };
    }

    private static ExpResult assign(Exp left, Exp right, List<InstructionIr> instructions) {
        ExpResult lval = emitTacky(left, instructions);
        ValIr rval = emitTackyAndConvert(right, instructions);
        return switch (lval) {
            case PlainOperand(VarIr obj) -> {
                instructions.add(new Copy(rval, obj));
                yield lval;
            }
            case DereferencedPointer(ValIr ptr) -> {
                instructions.add(new Store(rval, ptr));
                yield new PlainOperand(rval);
            }
            default ->
                    throw new IllegalStateException("Unexpected value: " + lval);
        };

    }

    static AtomicLong labelCount = new AtomicLong(0L);

    public static LabelIr newLabel(String prefix) {
        return new LabelIr(".L" + prefix + '.' + labelCount.getAndIncrement());
    }

    private static VarIr makeTemporary(String prefix, Type t) {
        String name = Mcc.makeTemporary(prefix);
        SYMBOL_TABLE.put(name, new SymbolTableEntry(t, LOCAL_ATTR));
        return new VarIr(name);
    }

}
