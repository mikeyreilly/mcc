package com.quaxt.mcc.tacky;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.parser.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.quaxt.mcc.ArithmeticOperator.AND;
import static com.quaxt.mcc.ArithmeticOperator.OR;


public class IrGen {
    public static ProgramIr programIr(Program program) {
        List<InstructionIr> instructions = new ArrayList<>();
        Function function = program.function();
        compileBlock(function.block(), instructions);
        FunctionIr f = new FunctionIr(function.name(), function.returnType(), instructions);
        ReturnInstructionIr ret = new ReturnInstructionIr(new IntIr(0));
        instructions.add(ret);
        return new ProgramIr(f);
    }

    private static void compileBlock(Block block, List<InstructionIr> instructions) {
        compileBlockItems(block.blockItems(), instructions);
    }

    private static void compileDeclaration(Declaration d, List<InstructionIr> instructions) {
        if (d instanceof Declaration(String left, Optional<Exp> init)) {
            if (init.isPresent()) {
                assign(left, init.get(), instructions);
                return;
            }
            init.ifPresent(exp -> compileExp(exp, instructions));
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

            case If(
                    Exp condition, Statement ifTrue, Optional<Statement> ifFalse
            ) -> {
                if (ifFalse.isPresent()) {
                    compileIfElse(condition, ifTrue, ifFalse.get(), instructions);
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
            case Compound compound -> {
                throw new RuntimeException("mr-todo delete Compound");
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
            case For(
                    ForInit init, Exp condition, Exp post, Statement body,
                    String label
            ) -> {

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
            case Int(int i): {
                return new IntIr(i);
            }
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse): {
                ValIr c = compileExp(condition, instructions);
                LabelIr e2Label = newLabel("e2");
                instructions.add(new JumpIfZero(c, e2Label.label()));
                ValIr e1 = compileExp(ifTrue, instructions);
                VarIr result = makeTemporary("result");
                instructions.add(new Copy(e1, result));
                LabelIr endLabel = newLabel("end");
                instructions.add(new Jump(endLabel.label()));
                instructions.add(e2Label);
                ValIr e2 = compileExp(ifFalse, instructions);
                instructions.add(new Copy(e2, result));
                instructions.add(endLabel);
                return result;
            }
            case UnaryOp(UnaryOperator op, Exp exp): {
                ValIr src = compileExp(exp, instructions);
                VarIr dst = makeTemporary("tmp.");
                instructions.add(new UnaryIr(op, src, dst));
                return dst;
            }
            case BinaryOp(BinaryOperator op, Exp left, Exp right):
                switch (op) {
                    case AND -> {
                        VarIr result = makeTemporary("tmp.");

                        LabelIr falseLabel = newLabel("false");
                        LabelIr endLabel = newLabel("end");
                        ValIr v1 = compileExp(left, instructions);
                        instructions.add(new JumpIfZero(v1, falseLabel.label()));

                        ValIr v2 = compileExp(right, instructions);
                        instructions.add(new JumpIfZero(v2, falseLabel.label()));
                        instructions.add(new Copy(new IntIr(1), result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(falseLabel);
                        instructions.add(new Copy(new IntIr(0), result));
                        instructions.add(endLabel);

                        return result;
                    }
                    case OR -> {
                        VarIr result = makeTemporary("tmp.");

                        LabelIr trueLabel = newLabel("true");
                        LabelIr endLabel = newLabel("end");
                        ValIr v1 = compileExp(left, instructions);
                        instructions.add(new JumpIfNotZero(v1, trueLabel.label()));

                        ValIr v2 = compileExp(right, instructions);
                        instructions.add(new JumpIfNotZero(v2, trueLabel.label()));
                        instructions.add(new Copy(new IntIr(0), result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(trueLabel);
                        instructions.add(new Copy(new IntIr(1), result));
                        instructions.add(endLabel);

                        return result;
                    }
                    default -> {
                        ValIr v1 = compileExp(left, instructions);
                        ValIr v2 = compileExp(right, instructions);
                        VarIr dstName = makeTemporary("tmp.");
                        instructions.add(new BinaryIr(op, v1, v2, dstName));
                        return dstName;
                    }
                }
            case Assignment(Var left, Exp right):
                return assign(left.value(), right, instructions);
            case Var(String name):
                return new VarIr(name);
            default:
                throw new IllegalStateException("Unexpected value: " + expr);
        }
    }

    private static VarIr assign(String left, Exp right, List<InstructionIr> instructions) {
        ValIr result = compileExp(right, instructions);
        VarIr v = new VarIr(left);
        instructions.add(new Copy(result, v));
        return v;
    }


    static AtomicLong labelCount = new AtomicLong(0L);


    private static LabelIr newLabel(String prefix) {
        return new LabelIr(".L" + prefix + labelCount.getAndIncrement());
    }

    private static VarIr makeTemporary(String prefix) {
        return new VarIr(Mcc.makeTemporary(prefix));
    }

}
