package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;
import com.quaxt.mcc.CmpOperator;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.LabelIr;

import java.io.PrintWriter;
import java.util.List;

public record ProgramAsm(List<FunctionAsm> functionAsms) {
    private static void printIndent(PrintWriter out, String s) {
        out.println("\t" + s);
    }

    private static String formatOperand(Instruction s, Operand o) {
        return switch (o) {
            case Imm(int i) -> "$" + i;
            case Pseudo _ ->
                    throw new IllegalArgumentException("broken compiler error: pseudo instructions should not occur here");
            case Reg reg -> "%" + switch (s) {
                case SetCC _ -> reg.b;
                default -> reg.d;
            };
            case Stack(int offset) -> offset + "(%rbp)";
        };
    }

    public void emitAsm(PrintWriter out) {
        for (FunctionAsm functionAsm : functionAsms) {
            String name = functionAsm.name();
            out.println("                .text");
            out.println("                .globl	" + name);
            out.println(name + ":");
            List<Instruction> instructions = functionAsm.instructions();
            printIndent(out, "pushq\t%rbp");
            printIndent(out, "movq\t%rsp, %rbp");
            for (Instruction instruction : instructions) {
                String s = switch (instruction) {
                    case AllocateStack(int i) -> "subq\t$" + i + ", %rsp";
                    case Mov(Operand src, Operand dst) ->
                            "movl" + "\t" + formatOperand(instruction, src) + ", " + formatOperand(instruction, dst);
                    case Nullary.RET -> {
                        printIndent(out, "movq\t%rbp, %rsp");
                        printIndent(out, "popq\t%rbp");
                        yield "ret";
                    }
                    case Unary(UnaryOperator op, Operand operand) -> {
                        String operandF = formatOperand(instruction, operand);
                        yield switch (op) {
                            case IDIV ->
                                    operand == Reg.R10 ? "idivl\t%r10d" : "idivl\t"
                                            + operandF;
                            case NEGATE -> "notl\t" + operandF;
                            case COMPLEMENT -> "negl\t" + operandF;
                            case NOT -> "notl\t" + operandF;
                        };
                    }
                    case Cmp(Operand subtrahend, Operand minuend) ->
                            "cmpl\t" + formatOperand(instruction, subtrahend) + ", " + formatOperand(instruction, minuend);
                    case Binary(
                            ArithmeticOperator op, Operand src, Operand dst
                    ) -> {
                        String srcF = formatOperand(instruction, src);
                        String dstF = formatOperand(instruction, dst);
                        yield switch (op) {
                            case SUB, ADD,
                                 IMUL,
                                 DIVIDE,
                                 REMAINDER,
                                 AND,
                                 OR -> {
                                yield op.toString().toLowerCase() + "l\t" + srcF + ", " + dstF;
                            }
                            default ->
                                    throw new IllegalStateException("Unexpected value: " + op);
                        };

                    }
                    case Nullary nullary -> nullary.code;

                    case Jump(String label) -> "jmp\t" + label;
//                case JumpIfNotZero(String label) -> "jnz\t" + label;
//                case JumpIfZero(String label) -> "jz\t" + label;
                    case LabelIr(String label) -> label + ":";
                    case SetCC(
                            CmpOperator cmpOperator,
                            Operand o
                    ) ->
                            "set" + cmpOperator.code + "\t" + formatOperand(instruction, o);
                    case JmpCC(
                            CmpOperator cmpOperator,
                            String label
                    ) -> "j" + cmpOperator.code + "\t" + label;
                    default ->
                            throw new IllegalStateException("Unexpected value: " + instruction);
                };
                if (instruction instanceof LabelIr) {
                    out.println(s);
                } else {
                    printIndent(out, s);
                }

            }

        }

        out.println("                .ident	\"GCC: (Ubuntu 11.4.0-1ubuntu1~22.04) 11.4.0\"");
        out.println("                .section	.note.GNU-stack,\"\",@progbits");
    }

}
