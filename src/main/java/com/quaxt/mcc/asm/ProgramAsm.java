package com.quaxt.mcc.asm;

import com.quaxt.mcc.*;
import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.LabelIr;

import java.io.PrintWriter;
import java.util.List;

import static com.quaxt.mcc.asm.Codegen.BACKEND_SYMBOL_TABLE;
import static com.quaxt.mcc.asm.TypeAsm.*;

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
            case Data data -> {
                boolean isConstant = BACKEND_SYMBOL_TABLE.get(data.identifier()) instanceof ObjEntry e && e.isConstant();
                yield (isConstant ? ".L" + data.identifier() : data.identifier()) + "(%rip)";
            }
            case DoubleReg reg -> reg.toString();
        };
    }

    private String formatOperand(TypeAsm t, Instruction s, Operand o) {
        if (o instanceof Reg reg) {
            return "%" + switch (t) {
                case LONGWORD -> reg.d;
                case QUADWORD -> reg.q;
                default ->
                        throw new IllegalArgumentException("wrong type (" + t + ") for integer register (" + reg + ")");
            };
        }
        if (o instanceof DoubleReg reg) {
            return "%" + switch (t) {
                case DOUBLE, QUADWORD -> reg.toString();
                default ->
                        throw new IllegalArgumentException("wrong type (" + t + ") for integer register (" + reg + ")");
            };
        }
        return formatOperand(s, o);
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
                case StaticConstant sc -> {
                    emitStaticConstantAsm(out, sc);
                }
            }
        }


        out.println("                .ident	\"GCC: (Ubuntu 11.4.0-1ubuntu1~22.04) 11.4.0\"");
        out.println("                .section	.note.GNU-stack,\"\",@progbits");
    }

    private void emitStaticConstantAsm(PrintWriter out, StaticConstant v) {
        long init = switch (v.init()) {
            case DoubleInit(double d) -> Double.doubleToLongBits(d);
            case null -> 0L;
        };
        String name = v.label();
        out.println("                .section .rodata");
        out.println("                .balign " + v.alignment());
        out.println(".L" + name + ":");
        out.println("                .quad " + init);
    }

    private void emitStaticVariableAsm(PrintWriter out, StaticVariableAsm v) {

        long init = switch (v.init()) {
            case IntInit(int i) -> i;
            case LongInit(long l) -> l;
            case UIntInit(int i) -> i;
            case ULongInit(long l) -> l;
            case DoubleInit(double d) -> -1;
        };

        boolean global = v.global();
        if (init == 0) {
            String name = v.name();
            if (global) out.println("                .globl	" + name);
            out.println("                .bss");
            out.println("                .balign " + v.alignment());
            out.println(name + ":");
            out.println("                .zero " + switch (v.init()) {
                case IntInit _ -> 4;
                case LongInit _ -> 8;
                case UIntInit _ -> 4;
                case ULongInit _ -> 8;
                case DoubleInit _ -> throw new AssertionError("init is set to non zero for DoubleInit");
            });
        } else {
            String name = v.name();
            if (global) out.println("                .globl	" + name);
            out.println("                .data");
            out.println("                .balign " + v.alignment());
            out.println(name + ":");
            out.println(switch (v.init()) {
                case IntInit _, UIntInit _ -> "                .long "+ init;
                case LongInit _, ULongInit _ -> "                .quad "+ init;
                case DoubleInit(double d) -> "                .quad "+ Double.doubleToLongBits(d);
            } );

        }

    }

    private void emitFunctionAsm(PrintWriter out, FunctionAsm functionAsm) {
        String name = functionAsm.name();
        if (functionAsm.global())
            out.println("                .globl	" + name);
        out.println("                .text");
        out.println(name + ":");
        List<Instruction> instructions = functionAsm.instructions();
        printIndent(out, "pushq\t%rbp");
        printIndent(out, "movq\t%rsp, %rbp");
        for (Instruction instruction : instructions) {
            String s = switch (instruction) {
                case Mov(TypeAsm t, Operand src, Operand dst) ->
                        instruction.format(t) + formatOperand(t, instruction, src) + ", " + formatOperand(t, instruction, dst);

                case Push(Operand arg) ->
                        "pushq\t" + formatOperand(instruction, arg);

                case Nullary.RET -> {
                    printIndent(out, "movq\t%rbp, %rsp");
                    printIndent(out, "popq\t%rbp");
                    yield "ret";
                }
                case Unary(UnaryOperator op, TypeAsm t, Operand operand) ->
                        instruction.format(t) + formatOperand(t, instruction, operand);
                case Cmp(TypeAsm t, Operand subtrahend, Operand minuend) ->
                        instruction.format(t) + formatOperand(t, instruction, subtrahend) + ", " + formatOperand(t, instruction, minuend);
                case Binary(
                        ArithmeticOperator op, TypeAsm t, Operand src,
                        Operand dst
                ) -> {
                    String srcF = formatOperand(t, instruction, src);
                    String dstF = formatOperand(t, instruction, dst);
                    yield instruction.format(t) + srcF + ", " + dstF;
                }
                case Jump(String label) -> "jmp\t" + label;
                case LabelIr(String label) -> label + ":";
                case SetCC(
                        CmpOperator cmpOperator,
                        boolean unsigned,
                        Operand o
                ) -> "set" + (unsigned ? cmpOperator.unsignedCode
                        : cmpOperator.code) + "\t"
                        + formatOperand(instruction, o);
                case JmpCC(
                        CmpOperator cmpOperator,
                        boolean unsigned,
                        String label
                ) -> "j" + (unsigned ? cmpOperator.unsignedCode
                        : cmpOperator.code) + "\t" + label;
                case Call(String functionName) ->
                        "call\t" + (Mcc.SYMBOL_TABLE.containsKey(functionName) ? functionName : functionName + "@PLT");
                case Cdq(TypeAsm t) -> instruction.format(t);
                case Movsx(Operand src, Operand dst) -> {
                    String srcF = formatOperand(LONGWORD, instruction, src);
                    String dstF = formatOperand(QUADWORD, instruction, dst);
                    yield "movslq\t" + srcF + ", " + dstF;
                }
                case MovZeroExtend movZeroExtend ->
                        throw new RuntimeException("can't happen because movZeroExtend is removed in fixup");
                case Cvttsd2si(TypeAsm dstType, Operand src, Operand dst) -> {
                    String srcF = formatOperand(DOUBLE, instruction, src);
                    String dstF = formatOperand(dstType, instruction, dst);
                    yield (dstType==QUADWORD?"cvttsd2siq\t":"cvttsd2sil\t") + srcF + ", " + dstF;
                }
                case Cvtsi2sd(TypeAsm srcType, Operand src, Operand dst) -> {
                    String srcF = formatOperand(srcType, instruction, src);
                    String dstF = formatOperand(DOUBLE, instruction, dst);
                    yield (srcType==QUADWORD?"cvtsi2sdq\t":"cvtsi2sdl\t") + srcF + ", " + dstF;
                }
            };
            if (instruction instanceof LabelIr) {
                out.println(s);
            } else {
                printIndent(out, s);
            }

        }

    }

}
