package com.quaxt.mcc.tacky;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.quaxt.mcc.ArithmeticOperator.AND;
import static com.quaxt.mcc.ArithmeticOperator.OR;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.parser.StorageClass.EXTERN;
import static com.quaxt.mcc.parser.StorageClass.STATIC;
import static com.quaxt.mcc.semantic.Primitive.*;

import com.quaxt.mcc.semantic.Pointer;
import com.quaxt.mcc.semantic.Type;

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
            if (value.attrs() instanceof StaticAttributes(InitialValue init,
                                                          boolean global)) {
                if (init instanceof StaticInit staticInit) {
                    tackyDefs.add(new StaticVariable(name, global, value.type(), staticInit));
                } else if (init instanceof InitialValue.Tentative) {
                    tackyDefs.add(new StaticVariable(name, global, value.type(), value.type().zero()));
                }
            }
        }
    }

    private static FunctionIr compileFunction(Function function) {
        List<InstructionIr> instructions = new ArrayList<>();
        compileBlock(function.body(), instructions);
        FunctionIr f = new FunctionIr(function.name(), SYMBOL_TABLE.get(function.name()).attrs().global(), function.parameters(), instructions);
        ReturnInstructionIr ret = new ReturnInstructionIr(new ConstInt(0));
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
            case VarDecl(Var name, Exp init, Type _,
                         StorageClass storageClass) -> {
                if (storageClass == STATIC || storageClass == EXTERN) return;
                if (init != null) {
                    assign(name, init, instructions);
                    return;
                }
                emitTacky(init, instructions);
            }
        }
    }

    private static void compileBlockItems(List<BlockItem> blockItems, List<InstructionIr> instructions) {
        for (BlockItem i : blockItems) {
            switch (i) {

                case Declaration d -> compileDeclaration(d, instructions);
                case Statement statement -> compileStatement(statement, instructions);
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
            case Return r -> {
                ValIr retVal = emitTackyAndConvert(r.exp(), instructions);
                ReturnInstructionIr ret = new ReturnInstructionIr(retVal);
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

            case Break aBreak -> instructions.add(new Jump(breakLabel(aBreak.label)));
            case Continue aContinue -> instructions.add(new Jump(continueLabel(aContinue.label)));
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
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type): {
                ValIr c = emitTackyAndConvert(condition, instructions);
                LabelIr e2Label = newLabel("e2");
                instructions.add(new JumpIfZero(c, e2Label.label()));
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
                        instructions.add(new BinaryIr(op, v1, v2, dstName));
                        return new PlainOperand(dstName);
                    }
                }
            case Assignment(Exp left, Exp right, Type _):
                return assign(left, right, instructions);
            case Var(String name, Type _):
                return new PlainOperand(new VarIr(name));
            case FunctionCall(Var name, List<Exp> args, Type type): {
                VarIr result = makeTemporary("tmp.", type);
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
                if (t == innerType) {
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
                        VarIr dst = makeTemporary("addr.", expr.type());
                        instructions.add(new GetAddress(obj, dst));
                        yield new PlainOperand(dst);
                    }
                    case DereferencedPointer(ValIr ptr) ->
                            new PlainOperand(ptr);
                };
            }
            default:
                throw new IllegalStateException("Unexpected exp: " + expr);
        }
    }


    private static ValIr emitTackyAndConvert(Exp e, List<InstructionIr> instructions) {
        ExpResult result = emitTacky(e, instructions);
        return switch (result) {
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
        var rval = emitTackyAndConvert(right, instructions);
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
