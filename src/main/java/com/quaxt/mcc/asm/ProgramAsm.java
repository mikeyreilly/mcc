package com.quaxt.mcc.asm;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.parser.Var;
import com.quaxt.mcc.tacky.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static com.quaxt.mcc.ArithmeticOperator.SUB;
import static com.quaxt.mcc.asm.Codegen.BACKEND_SYMBOL_TABLE;
import static com.quaxt.mcc.asm.IntegerReg.SP;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;

public record ProgramAsm(List<TopLevelAsm> topLevelAsms) {
    private static void printIndent(PrintWriter out, String s) {
        out.println("\t" + s);
    }

    private static String formatOperand(Instruction s, Operand o) {
        return switch (o) {
            case Imm(long i) -> "$" + i;
            case Pseudo p -> p.identifier;
            case IntegerReg reg -> "%" + switch (s) {
                case Push _, Pop _ -> reg.q;
                case SetCC _ -> reg.b;
                default -> reg.d;
            };
            case Memory(IntegerReg reg, long offset) ->
                    offset + "(%" + reg.q + ")";
            case Data(String identifier, long offset) -> {
                boolean isConstant =
                        BACKEND_SYMBOL_TABLE.get(identifier) instanceof ObjEntry e && e.isConstant();
                StringBuilder sb = new StringBuilder();
                if (isConstant) sb.append(".L");
                sb.append(identifier);
                if (offset < 0) sb.append(offset);
                else if (offset > 0) {
                    sb.append("+").append(offset);
                }
                sb.append("(%rip)");
                yield sb.toString();
            }
            case DoubleReg reg -> reg.toString();
            case Indexed(IntegerReg base, IntegerReg index, int scale) ->
                    "(%" + base.q + ",%" + index.q + "," + scale + ")";
            case LabelAddress(String label) -> "$" + label;
            default ->
                    throw new IllegalStateException("Unexpected value: " + o);
        };
    }


    private static String formatOperand(TypeAsm t, Instruction s, Operand o) {
        if (o instanceof IntegerReg reg) {
            return "%" + switch (t) {
                case BYTE -> reg.b;
                case WORD -> reg.w;
                case LONGWORD -> reg.d;
                case QUADWORD -> reg.q;
                case ByteArray _ -> reg.q;
                default ->
                        throw new IllegalArgumentException("wrong type (" + t + ") for integer register (" + reg + ")");
            };
        }
        if (o instanceof DoubleReg reg) {
            return "%" + switch (t) {
                case DOUBLE, FLOAT, QUADWORD -> reg.toString();
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


        out.println("                .ident	\"GCC: (Ubuntu 11.4.0-1ubuntu1~22" + ".04) 11.4.0\"");
        out.println("                .section	.note.GNU-stack,\"\"," +
                "@progbits");
    }

    private void emitStaticConstantAsm(PrintWriter out, StaticConstant v) {
        String name = v.label();
        out.println("                .section .rodata");
        if (v.alignment() != 1)
            out.println("                .balign " + v.alignment());
        out.println(".L" + name + ":");
        writeValue(out, v.init());
    }

    private static void writeValue(PrintWriter out, StaticInit init) {
        out.println("                " + switch (init) {
            case DoubleInit(double d) -> ".quad " + Double.doubleToLongBits(d);
            case FloatInit(float d) -> ".long " + Float.floatToIntBits(d);
            case ShortInit(short l) -> ".value " + l;
            case UShortInit(short l) -> ".value " + Integer.toUnsignedString(l & 0xffff);
            case IntInit(int l) -> ".long " + l;
            case LongInit(long l) -> ".quad " + l;
            case UIntInit(int l) -> ".long " + Integer.toUnsignedString(l);
            case ULongInit(long l) -> ".quad " + Long.toUnsignedString(l);
            case ZeroInit(long l) -> ".zero " + l;
            case CharInit(byte i) -> ".byte " + (i & 0xff);
            case PointerInit(String label, long offset) -> offset == 0
                    ? ".quad " + label
                    : offset > 0
                    ? ".quad " + label + " + " + offset
                    : ".quad " + label + " - " + -offset;
            case StringInit(String s, boolean nullTerminated) -> {
                StringBuilder sb = new StringBuilder();
                sb.append(nullTerminated ? ".asciz \"" : ".ascii \"");
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c == '\\') sb.append("\\\\");
                    else if (c == '\"') sb.append("\\\"");
                    else if (c == '\n') sb.append("\\n");
                    else if (c < 32 || c > 126) {
                        String octal = Integer.toString(c & 0xff, 8);
                        sb.append("\\000", 0, 4 - octal.length());
                        sb.append(octal);
                    } else sb.append(c);
                }
                sb.append('\"');
                yield sb;
            }
            case UCharInit(byte i) -> ".byte " + (i & 0xff);
            case BoolInit(byte i) -> ".byte " + (i & 0xff);
        });
    }

    private void emitStaticVariableAsm(PrintWriter out, StaticVariableAsm v) {
        boolean global = v.global();
        if (v.init().size() == 1 && v.init().getFirst() instanceof ZeroInit(
                long bytes)) {
            String name = v.name();
            if (global) out.println("                .globl	" + name);
            out.println("                .bss");
            out.println("                .balign " + v.alignment());
            out.println(name + ":");
            out.println("                .zero " + bytes);
        } else {
            String name = v.name();
            if (global) out.println("                .globl	" + name);
            out.println("                .data");
            out.println("                .balign " + v.alignment());
            out.println(name + ":");
            for (var x : v.init()) {
                writeValue(out, x);
            }
        }
    }

    /**
     * If both args are same sign then rounds away from zero, if args are not
     * same sign does something you probably don't want.
     */
    public static long roundAwayFromZero(long dividend, long divisor) {
        long remainder = dividend % divisor;
        if (remainder == 0) return dividend;
        return dividend + divisor - remainder;
    }

    public static long calculateStackAdjustment(long bytesForLocals,
                                                long calleeSavedCount) {
        long calleeSavedBytes = 8 * calleeSavedCount;
        long totalStackBytes = calleeSavedBytes + bytesForLocals;
        long adjustedStackBytes = roundAwayFromZero(totalStackBytes, 16);

        return adjustedStackBytes - calleeSavedBytes;
    }

    private void emitFunctionAsm(PrintWriter out, FunctionAsm functionAsm) {
        String name = functionAsm.name;
        if (functionAsm.global)
            out.println("                .globl	" + name);
        out.println("                .text");
        out.println(name + ":");
        List<Instruction> instructions = functionAsm.instructions;
        printIndent(out, "pushq\t%rbp");
        printIndent(out, "movq\t%rsp, %rbp");
        long stackSize = functionAsm.stackSize;
        long remainder = stackSize % 16;
        if (remainder != 0) {
            stackSize += (16 - remainder);
        }
        IntegerReg[] calleeSavedRegs = functionAsm.calleeSavedRegs;
        int calleeSavedCount = calleeSavedRegs.length;
        //prologue
        var end = new LabelIr(Mcc.makeTemporary(".L"));
        if (functionAsm.callsVaStart) {
            out.println("""
                    movq	%rdi, -176(%rbp)
                    movq	%rsi, -168(%rbp)
                    movq	%rdx, -160(%rbp)
                    movq	%rcx, -152(%rbp)
                    movq	%r8, -144(%rbp)
                    movq	%r9, -136(%rbp)
                    testb	%al, %al
                    je\t""" + end.label() + """
                    
                            movaps	%xmm0, -128(%rbp)
                            movaps	%xmm1, -112(%rbp)
                            movaps	%xmm2, -96(%rbp)
                            movaps	%xmm3, -80(%rbp)
                            movaps	%xmm4, -64(%rbp)
                            movaps	%xmm5, -48(%rbp)
                            movaps	%xmm6, -32(%rbp)
                            movaps	%xmm7, -16(%rbp)
                    """ + end.label() + ":");
        }


        emitInstruction(out, new Binary(SUB, QUADWORD,
                new Imm(calculateStackAdjustment(stackSize, calleeSavedCount)), SP));
        // push in reverse direction so we can pop in forward direction
        for (int i = calleeSavedRegs.length - 1; i >= 0; i--) {
            IntegerReg r = calleeSavedRegs[i];
            emitInstruction(out, new Push(r));
        }
        for (Instruction instruction : instructions) {
            emitInstruction(out, instruction);

        }

    }

    public static String formatInstruction(Instruction instruction) {
        StringWriter sw = new StringWriter();
        try (PrintWriter p = new PrintWriter(sw)) {
            emitInstruction(p, instruction);
        }
        return sw.toString();
    }

    private static void emitInstruction(PrintWriter out,
                                        Instruction instruction) {
        String s = switch (instruction) {
            case Mov(TypeAsm t, Operand src, Operand dst) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            src) + ", " + formatOperand(t, instruction, dst);
            case Xchg(TypeAsm t, Operand src, Operand dst) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            src) + ", " + formatOperand(t, instruction, dst);
            case Lea(Operand src, Operand dst) ->
                    "leaq\t" + formatOperand(QUADWORD, instruction, src) + ","
                            + " " + formatOperand(QUADWORD, instruction, dst);
            case Push(Operand arg) ->
                    "pushq\t" + formatOperand(QUADWORD, instruction, arg);

            case Nullary.RET -> {
                printIndent(out, "movq\t%rbp, %rsp");
                printIndent(out, "popq\t%rbp");
                yield "ret";
            }
            case Unary(UnaryOperator op, TypeAsm t, Operand operand) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            operand);
            case Cmp(TypeAsm t, Operand subtrahend, Operand minuend) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            subtrahend) + ", " + formatOperand(t, instruction
                            , minuend);
            case Binary(ArithmeticOperator op, TypeAsm t, Operand src,
                        Operand dst) -> {
                String srcF = formatOperand(t, instruction, src);
                String dstF = formatOperand(t, instruction, dst);
                yield instruction.format(t) + srcF + ", " + dstF;
            }
            case Jump(String label) -> {
                yield "jmp\t" + label;
            }
            case LabelIr(String label) -> label + ":";
            case SetCC(CmpOperator cmpOperator, boolean unsigned, Operand o) ->
                    "set" + (unsigned ? cmpOperator.unsignedCode :
                            cmpOperator.code) + "\t" + formatOperand(instruction, o);
            case JmpCC(CmpOperator cmpOperator, boolean unsigned,
                       String label) -> // cmpOperator = null is used for
                // jump parity (ie. jump if last comparison was unordered) or
                // jump not parity (unsigned true - parity, unsigned false
                // -not parity). This violation of the principal of least
                // astonishment
                // is to spare me from adding a new CmpOperator just to deal
                // with NaN
                    "j" + (cmpOperator == null ? (unsigned ? "p" : "np") :
                            unsigned ? cmpOperator.unsignedCode :
                                    cmpOperator.code) + "\t" + label;
            case Call(String functionName) ->
                    "call\t" + (Mcc.SYMBOL_TABLE.containsKey(functionName) ?
                            functionName : functionName + "@PLT");
            case CallIndirect(Operand address) ->
                    "call\t*" + formatOperand(QUADWORD, instruction, address);
            case Cdq(TypeAsm t) -> instruction.format(t);
            case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                       Operand dst) -> {
                String srcF = formatOperand(srcType, instruction, src);
                String dstF = formatOperand(dstType, instruction, dst);
                yield "movs" + srcType.suffix() + dstType.suffix() + "\t" + srcF + ", " + dstF;
            }
            case MovZeroExtend(TypeAsm srcType, TypeAsm dstType, Operand src,
                               Operand dst) -> {
                String srcF = formatOperand(srcType, instruction, src);
                String dstF = formatOperand(dstType, instruction, dst);
                yield "movz" + srcType.suffix() + dstType.suffix() + "\t" + srcF + ", " + dstF;
            }
            case Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) -> {
                if (srcType.isInteger()){
                    String srcF = formatOperand(srcType, instruction, src);
                    String dstF = formatOperand(DOUBLE, instruction, dst);
                    yield (srcType == QUADWORD ? "cvtsi2sdq\t" : "cvtsi2sdl\t") + srcF + ", " + dstF;
                }else {
                    String srcF = formatOperand(srcType, instruction, src);
                    String dstF = formatOperand(dstType, instruction, dst);
                    if (srcType==DOUBLE)
                        yield (dstType ==
                                QUADWORD ? "cvttsd2siq\t" : "cvttsd2sil\t") + srcF +
                                ", " + dstF;
                    else yield (dstType ==
                            QUADWORD ? "cvttss2siq\t" : "cvttss2sil\t") + srcF +
                            ", " + dstF;

                }
            }
            case Comment(String comment) -> "# " + comment;
            case Pop(IntegerReg arg) ->
                    "popq\t" + formatOperand(instruction, arg);
            case Test(TypeAsm t, Operand src1, Operand src2) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            src1) + formatOperand(t, instruction, src2);
        };
        if (instruction instanceof LabelIr) {
            out.println(s);
        } else {
            printIndent(out, s);
        }
    }

}
