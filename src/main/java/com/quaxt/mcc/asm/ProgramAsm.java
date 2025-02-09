package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;
import com.quaxt.mcc.CmpOperator;
import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.LabelIr;

import java.io.PrintWriter;
import java.util.List;

public record ProgramAsm(List<TopLevelAsm> topLevelAsms) {
    private static void printIndent(PrintWriter out, String s) {
        out.println("\t" + s);
    }

    private static String formatOperand(Instruction s, Operand o) {
        return switch (o) {
            case Imm(long i) -> "$" + i;
            case Pseudo _ ->
                    throw new IllegalArgumentException("broken compiler error: pseudo instructions should not occur here");
            case Reg reg -> "%" + switch (s) {
                case Push _ -> reg.q;
                case SetCC _ -> reg.b;
                default -> reg.d;
            };
            case Stack(int offset) -> offset + "(%rbp)";
            case Data data -> data.identifier() + "(%rip)";
        };
    }

    public void emitAsm(PrintWriter out) {
        for (TopLevelAsm t : topLevelAsms) {
            switch (t) {
                case FunctionAsm functionAsm -> {
                    emitFunctionAsm(out, functionAsm);
                }
                case StaticVariableAsm staticVariableAsm -> {
                    emitStaticVariableAsm(out, staticVariableAsm);
                }
            }
        }


        out.println("                .ident	\"GCC: (Ubuntu 11.4.0-1ubuntu1~22.04) 11.4.0\"");
        out.println("                .section	.note.GNU-stack,\"\",@progbits");
    }

    private void emitStaticVariableAsm(PrintWriter out, StaticVariableAsm v) {
        throw new Todo();
//        int init = v.init();
//        boolean global = v.global();
//        if (init == 0) {
//            String name = v.name();
//            if (global) out.println("                .globl	" + name);
//            out.println("                .bss");
//            out.println("                .balign 4");
//            out.println(name + ":");
//            out.println("                .zero 4");
//        } else {
//            String name = v.name();
//            if (global) out.println("                .globl	" + name);
//            out.println("                .data");
//            out.println("                .balign 4");
//            out.println(name + ":");
//            out.println("                .long " + init);
//
//        }

    }

    private void emitFunctionAsm(PrintWriter out, FunctionAsm functionAsm) {
        throw new Todo();
//        String name = functionAsm.name();
//        if (functionAsm.global()) out.println("                .globl	" + name);
//        out.println("                .text");
//        out.println(name + ":");
//        List<Instruction> instructions = functionAsm.instructions();
//        printIndent(out, "pushq\t%rbp");
//        printIndent(out, "movq\t%rsp, %rbp");
//        for (Instruction instruction : instructions) {
//            String s = switch (instruction) {
//                case AllocateStack(int i) -> "subq\t$" + i + ", %rsp";
//                case DeallocateStack(int i) -> "addq\t$" + i + ", %rsp";
//                case Mov(Operand src, Operand dst) ->
//                        "movl" + "\t" + formatOperand(instruction, src) + ", " + formatOperand(instruction, dst);
//
//                case Push(Operand arg) ->{
//                    if (arg instanceof Data){
//                        yield "pushl\t" + formatOperand(instruction, arg);
//                    }else {
//                        yield "pushq\t" + formatOperand(instruction, arg);
//                    }
//
//                }
//
//                case Nullary.RET -> {
//                    printIndent(out, "movq\t%rbp, %rsp");
//                    printIndent(out, "popq\t%rbp");
//                    yield "ret";
//                }
//                case Unary(UnaryOperator op, Operand operand) -> {
//                    String operandF = formatOperand(instruction, operand);
//                    yield switch (op) {
//                        case IDIV ->
//                                operand == Reg.R10 ? "idivl\t%r10d" : "idivl\t"
//                                        + operandF;
//                        case NEGATE -> "notl\t" + operandF;
//                        case COMPLEMENT -> "negl\t" + operandF;
//                        case NOT -> "notl\t" + operandF;
//                    };
//                }
//                case Cmp(Operand subtrahend, Operand minuend) ->
//                        "cmpl\t" + formatOperand(instruction, subtrahend) + ", " + formatOperand(instruction, minuend);
//                case Binary(
//                        ArithmeticOperator op, Operand src, Operand dst
//                ) -> {
//                    String srcF = formatOperand(instruction, src);
//                    String dstF = formatOperand(instruction, dst);
//                    yield switch (op) {
//                        case SUB, ADD,
//                             IMUL,
//                             DIVIDE,
//                             REMAINDER,
//                             AND,
//                             OR -> {
//                            yield op.toString().toLowerCase() + "l\t" + srcF + ", " + dstF;
//                        }
//                        default ->
//                                throw new IllegalStateException("Unexpected value: " + op);
//                    };
//
//                }
//                case Nullary nullary -> nullary.code;
//
//                case Jump(String label) -> "jmp\t" + label;
//                case LabelIr(String label) -> label + ":";
//                case SetCC(
//                        CmpOperator cmpOperator,
//                        Operand o
//                ) ->
//                        "set" + cmpOperator.code + "\t" + formatOperand(instruction, o);
//                case JmpCC(
//                        CmpOperator cmpOperator,
//                        String label
//                ) -> "j" + cmpOperator.code + "\t" + label;
//                case Call(String functionName) ->
//                        "call\t" + (Mcc.SYMBOL_TABLE.containsKey(functionName) ? functionName : functionName + "@PLT");
//
//
//            };
//            if (instruction instanceof LabelIr) {
//                out.println(s);
//            } else {
//                printIndent(out, s);
//            }
//
//        }

    }

}
