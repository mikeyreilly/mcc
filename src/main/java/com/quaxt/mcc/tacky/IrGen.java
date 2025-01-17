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
        compileBlockItems(function.blockItems(), instructions);
        FunctionIr f = new FunctionIr(function.name(), function.returnType(), instructions);
        ReturnInstructionIr ret = new ReturnInstructionIr(new IntIr(0));
        instructions.add(ret);
        return new ProgramIr(f);
    }

    private static void compileBlockItems(List<BlockItem> blockItems, List<InstructionIr> instructions) {
        for (BlockItem i : blockItems) {
            switch (i) {

                case Declaration(String left, Optional<Exp> init) -> {
                    if (init.isPresent()) {
                        assign(left, init.get(), instructions);
                        continue;
                    }
                    init.ifPresent(exp -> compileExp(exp, instructions));
                }
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
        }
    }


    private static ValIr compileExp(Exp expr, List<InstructionIr> instructions) {
        switch (expr) {
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
