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
        emitInstructions(function.blockItems(), instructions);
        FunctionIr f = new FunctionIr(function.name(), function.returnType(), instructions);
        ReturnInstructionIr ret = new ReturnInstructionIr(new IntIr(0));
        instructions.add(ret);
        return new ProgramIr(f);
    }

    private static void emitInstructions(List<BlockItem> blockItems, List<InstructionIr> instructions) {
        for (BlockItem i : blockItems) {
            emitInstructions(i, instructions);
        }
    }


    private static ValIr emitInstructions(BlockItem statement, List<InstructionIr> instructions) {
        switch (statement) {
            case Return r -> {
                ValIr retVal = emitInstructions(r.exp(), instructions);
                ReturnInstructionIr ret = new ReturnInstructionIr(retVal);
                instructions.add(ret);
            }
            case Declaration(String _, Optional<Exp> init) -> {
                init.ifPresent(exp -> emitInstructions(exp, instructions));
            }
            case Exp exp -> {
                emitInstructions(exp, instructions);
            }
            default -> {
            }
        }
        return null;
    }


private static ValIr emitInstructions(Exp expr, List<InstructionIr> instructions) {
    switch (expr) {
        case Int(int i): {
            return new IntIr(i);
        }
        case UnaryOp(UnaryOperator op, Exp exp): {
            ValIr src = emitInstructions(exp, instructions);
            VarIr dst = makeTemporary();
            instructions.add(new UnaryIr(op, src, dst));
            return dst;
        }
        case BinaryOp(BinaryOperator op, Exp left, Exp right):
            switch (op) {
                case AND -> {
                    VarIr result = makeTemporary();

                    LabelIr falseLabel = newLabel("false");
                    LabelIr endLabel = newLabel("end");
                    ValIr v1 = emitInstructions(left, instructions);
                    instructions.add(new JumpIfZero(v1, falseLabel.label()));

                    ValIr v2 = emitInstructions(right, instructions);
                    instructions.add(new JumpIfZero(v2, falseLabel.label()));
                    instructions.add(new Copy(new IntIr(1), result));
                    instructions.add(new Jump(endLabel.label()));

                    instructions.add(falseLabel);
                    instructions.add(new Copy(new IntIr(0), result));
                    instructions.add(endLabel);

                    return result;
                }
                case OR -> {
                    VarIr result = makeTemporary();

                    LabelIr trueLabel = newLabel("true");
                    LabelIr endLabel = newLabel("end");
                    ValIr v1 = emitInstructions(left, instructions);
                    instructions.add(new JumpIfNotZero(v1, trueLabel.label()));

                    ValIr v2 = emitInstructions(right, instructions);
                    instructions.add(new JumpIfNotZero(v2, trueLabel.label()));
                    instructions.add(new Copy(new IntIr(0), result));
                    instructions.add(new Jump(endLabel.label()));

                    instructions.add(trueLabel);
                    instructions.add(new Copy(new IntIr(1), result));
                    instructions.add(endLabel);

                    return result;
                }
                default -> {
                    ValIr v1 = emitInstructions(left, instructions);
                    ValIr v2 = emitInstructions(right, instructions);
                    VarIr dstName = makeTemporary();
                    instructions.add(new BinaryIr(op, v1, v2, dstName));
                    return dstName;
                }
            }
        case Assignment(Var left, Exp right):
            ValIr result = emitInstructions(right, instructions);
            VarIr v = new VarIr(left.value());
            instructions.add(new Copy(result, v));
            return v;
        case Var(String name):
            return new VarIr(name);
        default:
            throw new IllegalStateException("Unexpected value: " + expr);
    }
}


static AtomicLong labelCount = new AtomicLong(0L);


private static LabelIr newLabel(String prefix) {
    return new LabelIr(prefix + labelCount.getAndIncrement());
}

private static VarIr makeTemporary() {
    return new VarIr(Mcc.makeTemporary("tmp."));
}

}
