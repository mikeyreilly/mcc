package com.quaxt.mcc.tacky;

import com.quaxt.mcc.*;
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
            case VarDecl(String name, Exp init, Type varType,
                         StorageClass storageClass) -> {
                if (storageClass == STATIC || storageClass == EXTERN) return;
                if (init != null) {
                    assign(name, init, instructions);
                    return;
                }
                compileExp(init, instructions);
            }
        }
    }

    private static void compileBlockItems(List<BlockItem> blockItems, List<InstructionIr> instructions) {
        for (BlockItem i : blockItems) {
            switch (i) {

                case Declaration d -> compileDeclaration(d, instructions);
                case Statement statement -> {
                    compileStatement(statement, instructions);
                }
            }
        }
    }

    private static void compileIfElse(Exp condition, Statement ifTrue, Statement ifFalse, List<InstructionIr> instructions) {
        ValIr c = compileExp(condition, instructions);
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
        ValIr c = compileExp(condition, instructions);
        LabelIr endLabel = newLabel("end");
        instructions.add(new JumpIfZero(c, endLabel.label()));
        compileStatement(ifTrue, instructions);
        instructions.add(endLabel);
    }


    private static void compileStatement(Statement i, List<InstructionIr> instructions) {
        switch (i) {
            case Return r -> {
                ValIr retVal = compileExp(r.exp(), instructions);
                ReturnInstructionIr ret = new ReturnInstructionIr(retVal);
                instructions.add(ret);
            }

            case Exp exp -> compileExp(exp, instructions);

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

            case Break aBreak -> {
                instructions.add(new Jump(breakLabel(aBreak.label)));
            }
            case Continue aContinue -> {
                instructions.add(new Jump(continueLabel(aContinue.label)));

            }
            case DoWhile(Statement body, Exp condition, String label) -> {
                LabelIr start = newLabel("start");
                instructions.add(start);
                compileStatement(body, instructions);
                LabelIr continueLabel = new LabelIr(continueLabel(label));
                instructions.add(continueLabel);
                ValIr v = compileExp(condition, instructions);
                instructions.add(new JumpIfNotZero(v, start.label()));
                LabelIr breakLabel = new LabelIr(breakLabel(label));
                instructions.add(breakLabel);
            }
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> {

                switch (init) {
                    case Declaration d -> compileDeclaration(d, instructions);
                    case Exp e -> compileExp(e, instructions);
                    case null -> {
                    }
                }

                LabelIr start = new LabelIr(startLabel(label));
                LabelIr continueLabel = new LabelIr(continueLabel(label));
                LabelIr breakLabel = new LabelIr(breakLabel(label));
                instructions.add(start);
                if (condition != null) {
                    ValIr v = compileExp(condition, instructions);
                    instructions.add(new JumpIfZero(v, breakLabel.label()));
                }
                compileStatement(body, instructions);
                instructions.add(continueLabel);
                compileExp(post, instructions);
                instructions.add(new Jump(start.label()));
                instructions.add(breakLabel);
            }
            case While(Exp condition, Statement body, String label) -> {
                LabelIr continueLabel = new LabelIr(continueLabel(label));
                instructions.add(continueLabel);
                ValIr v = compileExp(condition, instructions);
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


    private static ValIr compileExp(Exp expr, List<InstructionIr> instructions) {
        switch (expr) {
            case null:
                return null;
            case ConstInt c:
                return c;
            case ConstLong c:
                return c;
            case ConstUInt c:
                return c;
            case ConstULong c:
                return c;
            case ConstDouble c:
                return c;
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type): {
                ValIr c = compileExp(condition, instructions);
                LabelIr e2Label = newLabel("e2");
                instructions.add(new JumpIfZero(c, e2Label.label()));
                ValIr e1 = compileExp(ifTrue, instructions);
                VarIr result = makeTemporary("result", type);
                instructions.add(new Copy(e1, result));
                LabelIr endLabel = newLabel("end");
                instructions.add(new Jump(endLabel.label()));
                instructions.add(e2Label);
                ValIr e2 = compileExp(ifFalse, instructions);
                instructions.add(new Copy(e2, result));
                instructions.add(endLabel);
                return result;
            }
            case UnaryOp(UnaryOperator op, Exp exp, Type type): {
                ValIr src = compileExp(exp, instructions);
                VarIr dst = makeTemporary("tmp.", type);
                instructions.add(new UnaryIr(op, src, dst));
                return dst;
            }
            case BinaryOp(BinaryOperator op, Exp left, Exp right, Type type):
                switch (op) {
                    case AND -> {
                        VarIr result = makeTemporary("tmp.", INT);

                        LabelIr falseLabel = newLabel("andFalse");
                        LabelIr endLabel = newLabel("andEnd");
                        ValIr v1 = compileExp(left, instructions);
                        instructions.add(new JumpIfZero(v1, falseLabel.label()));

                        ValIr v2 = compileExp(right, instructions);
                        instructions.add(new JumpIfZero(v2, falseLabel.label()));
                        instructions.add(new Copy(new ConstInt(1), result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(falseLabel);
                        instructions.add(new Copy(new ConstInt(0), result));
                        instructions.add(endLabel);

                        return result;
                    }
                    case OR -> {
                        VarIr result = makeTemporary("tmp.", INT);

                        LabelIr trueLabel = newLabel("true");
                        LabelIr endLabel = newLabel("end");
                        ValIr v1 = compileExp(left, instructions);
                        instructions.add(new JumpIfNotZero(v1, trueLabel.label()));

                        ValIr v2 = compileExp(right, instructions);
                        instructions.add(new JumpIfNotZero(v2, trueLabel.label()));
                        instructions.add(new Copy(new ConstInt(0), result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(trueLabel);
                        instructions.add(new Copy(new ConstInt(1), result));
                        instructions.add(endLabel);

                        return result;
                    }
                    default -> {
                        ValIr v1 = compileExp(left, instructions);
                        ValIr v2 = compileExp(right, instructions);
                        VarIr dstName = makeTemporary("tmp.", expr.type());
                        instructions.add(new BinaryIr(op, v1, v2, dstName));
                        return dstName;
                    }
                }
            case Assignment(Var left, Exp right, Type type):
                return assign(left.name(), right, instructions);
            case Var(String name, Type type):
                return new VarIr(name);
            case FunctionCall(Var name, List<Exp> args, Type type): {
                VarIr result = makeTemporary("tmp.", type);
                ArrayList<ValIr> argVals = new ArrayList<>();
                for (Exp e : args) {
                    argVals.add(compileExp(e, instructions));
                }
                instructions.add(new FunCall(name.name(), argVals, result));
                return result;
            }
            case Cast(Type t, Exp inner): {
                ValIr result = compileExp(inner, instructions);
                Type innerType = inner.type();
                if (t == inner.type()) {
                    return result;
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
                return dst;
            }

            default:
                throw new IllegalStateException("Unexpected exp: " + expr);
        }
    }

    private static VarIr assign(String left, Exp right, List<InstructionIr> instructions) {
        ValIr result = compileExp(right, instructions);
        VarIr v = new VarIr(left);
        instructions.add(new Copy(result, v));
        return v;
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
