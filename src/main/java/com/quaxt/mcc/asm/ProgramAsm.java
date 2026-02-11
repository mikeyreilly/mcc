package com.quaxt.mcc.asm;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.Position;
import com.quaxt.mcc.semantic.FunType;
import com.quaxt.mcc.semantic.SemanticAnalysis;
import com.quaxt.mcc.tacky.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.asm.Codegen.BACKEND_SYMBOL_TABLE;
import static com.quaxt.mcc.asm.IntegerReg.BP;
import static com.quaxt.mcc.asm.IntegerReg.SP;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;

public record ProgramAsm(List<TopLevelAsm> topLevelAsms, ArrayList<Position> positions) {
    private static void printIndent(PrintWriter out, String s) {
        out.println("\t" + s);
    }

    private static String formatOperand(Instruction s, Operand o, AtomicInteger stackCorrection) {
        return switch (o) {
            case Imm(long i) -> "$" + i;
            case Pseudo p -> p.identifier;
            case IntegerReg reg -> "%" + switch (s) {
                case Push _, Pop _ -> reg.q;
                case SetCC _ -> reg.b;
                default -> reg.d;
            };
            case Memory(IntegerReg reg, long offset) ->
                    (reg == SP) ?
                    (offset + stackCorrection.get()) + "(%" + reg.q + ")" :
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


    private static String formatOperand(TypeAsm t, Instruction s, Operand o, AtomicInteger stackCorrection) {
        if (o instanceof IntegerReg reg) {
            return "%" + switch (t) {
                case BYTE -> reg.b;
                case WORD -> reg.w;
                case LONGWORD -> reg.d;
                case QUADWORD -> reg.q;
                case ByteArray(long size, long alignment) ->
                        size < 2 ? reg.b :
                                size < 3 ? reg.w : size < 5 ? reg.d : reg.q;
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
        return formatOperand(s, o, stackCorrection);
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
            case LongLongInit(long l) -> ".quad " + l;
            case UIntInit(int l) -> ".long " + Integer.toUnsignedString(l);
            case ULongInit(long l) -> ".quad " + Long.toUnsignedString(l);
            case ULongLongInit(long l) -> ".quad " + Long.toUnsignedString(l);
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

    private void emitFunctionAsm(PrintWriter out, FunctionAsm functionAsm) {
        String name = functionAsm.name;
        if (functionAsm.global)
            out.println("                .globl	" + name);
        out.println("                .text");
        FunType t= (FunType) Mcc.SYMBOL_TABLE.get(functionAsm.name).type();
        var alignment = t.alignment();
        if (alignment != null) {
            long l = SemanticAnalysis.evaluateExpAsConstant(alignment).toLong();
            out.println("                .balign " + l);
        }
        out.println(name + ":");
        printIndent(out,".cfi_startproc");
        List<Instruction> instructions = functionAsm.instructions;
        printIndent(out, "pushq\t%rbp");
        if (Mcc.addDebugInfo) {
            // The canonical frame address is RSP+16
            // The register numbers are in Figure 3.36: DWARF Register Number Mapping of System V AMD64 ABI
            // https://gitlab.com/x86-psABIs/x86-64-ABI/-/jobs/artifacts/master/raw/x86-64-ABI/abi.pdf?job=build
            printIndent(out, ".cfi_def_cfa_offset 16\n");
            // We store register 6 (RBP) at CFA - 16
            printIndent(out, ".cfi_offset 6, -16\n");
        }
        printIndent(out, "movq\t%rsp, %rbp");
        if (Mcc.addDebugInfo) {
            // From now on CFA is at an offset of RBP so it is RBP+16
            printIndent(out, ".cfi_def_cfa_register 6");
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

        // First reserve space for the callee saved regs
        // + stack size

        //MR-TODO there's a bug here - stack might not be sixteen byte aligned
        // (also we might want the stack to be > 16 byte aligned)

        long stackSize = functionAsm.stackSize;
        long calleeSavedBytes = roundAwayFromZero(8 * (long) calleeSavedCount, 16);
        long totalStackBytes =  calleeSavedBytes+stackSize;
        long adjustedStackBytes = roundAwayFromZero(totalStackBytes, 16);


        AtomicInteger stackCorrection = new AtomicInteger((int) - adjustedStackBytes);

        if (adjustedStackBytes != 0) {
            emitInstruction(out, new Binary(SUB, QUADWORD,
                    new Imm(adjustedStackBytes), SP), stackCorrection, functionAsm);
        }
        emitInstruction(out, new Comment("Check alignment"), stackCorrection, functionAsm);
        var stackAlignment = functionAsm.stackAlignment;
        if (stackAlignment != 0) {
            emitInstruction(out, new Binary(BITWISE_AND, QUADWORD, new Imm(-stackAlignment), SP), stackCorrection, functionAsm);
        }
        // push in reverse direction so we can pop in forward direction
        if (calleeSavedRegs.length % 2 == 1) {
            emitInstruction(out, new Binary(SUB, QUADWORD, new Imm(8), SP),
                    stackCorrection, functionAsm);
        }
        for (int i = calleeSavedRegs.length - 1; i >= 0; i--) {
            IntegerReg r = calleeSavedRegs[i];
            emitInstruction(out, new Push(r), stackCorrection, functionAsm);
        }
        for (Instruction instruction : instructions) {
            emitInstruction(out, instruction, stackCorrection, functionAsm);
        }
        printIndent(out,".cfi_endproc");
    }


    private void emitInstruction(PrintWriter out,
                                        Instruction instruction,
                                        AtomicInteger stackCorrection, FunctionAsm functionAsm) {
        String s = switch (instruction) {
            case Pos(int pos) -> {
                Position p = positions.get(pos);
                int file = p.file() + 1;
                int lineNumber = p.lineNumber();
                yield "\t.loc " + file + " " + lineNumber;
            }
            case Mov(TypeAsm t, Operand src, Operand dst) -> {

                String o;
                if (src instanceof Imm imm && imm.isAwkward()) {
                    o="movabs\t";
                }
                o="mov" + switch (t) {
                    case ByteArray(long size, long alignment) ->
                            size < 2 ? "b" :
                                    size < 3 ? "w" : size < 5 ? "l" : "q";
                    case BYTE -> "b";
                    case WORD -> "w";
                    case LONGWORD -> "l";
                    case QUADWORD -> "q";
                    case DOUBLE -> "sd";
                    case FLOAT -> "ss";
                } + "\t";


                yield o +
                        formatOperand(t, instruction, src, stackCorrection) + ", " +
                        formatOperand(t, instruction, dst, stackCorrection);
            }
            case Xchg(TypeAsm t, Operand src, Operand dst) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            src, stackCorrection) + ", " + formatOperand(t, instruction, dst, stackCorrection);
            case Lea(Operand src, Operand dst) ->
                    "leaq\t" + formatOperand(QUADWORD, instruction, src, stackCorrection) + ","
                            + " " + formatOperand(QUADWORD, instruction, dst, stackCorrection);
            case Push(Operand arg) ->{
                    String r = "pushq\t" + formatOperand(QUADWORD, instruction, arg, stackCorrection);
                    addTo(stackCorrection, 8, out);
                    yield r;
            }

            case Nullary.RET -> {
                // epilogue
                int calleeSavedCount=functionAsm.calleeSavedRegs.length;
                if (calleeSavedCount > 0) {
                    for (int j =  0; j < calleeSavedCount; j++) {
                        IntegerReg r = functionAsm.calleeSavedRegs[j];
                        printIndent(out, "popq\t%"+r.q);
                    }
                }
                printIndent(out, "movq\t%rbp, %rsp");
                printIndent(out, "popq\t%rbp");
                if (Mcc.addDebugInfo) {
                    printIndent(out,".cfi_def_cfa 7, 8");
                }

                yield "ret";
            }
            case Nullary.MFENCE -> "mfence";

            case Unary(UnaryOperator op, TypeAsm t, Operand operand) ->
                    op == UnaryOperator.BSWAP && t == WORD ?
                            "rolw\t" + "$8, " +
                                    formatOperand(t, instruction, operand, stackCorrection) :
                            instruction.format(t) +
                                    formatOperand(t, instruction, operand, stackCorrection);
            case Cmp(TypeAsm t, Operand subtrahend, Operand minuend) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            subtrahend, stackCorrection) + ", " + formatOperand(t, instruction
                            , minuend, stackCorrection);
            case Binary(ArithmeticOperator op, TypeAsm t, Operand src,
                        Operand dst) -> {
                String srcF = formatOperand(switch(op){
                    case SHL, SAR, SHR -> BYTE;
                    default -> t;
                }, instruction, src, stackCorrection);
                String dstF = formatOperand(t, instruction, dst, stackCorrection);
                if (dst == SP){
                    if (src instanceof Imm(long i)) {
                        if (op == ArithmeticOperator.ADD) {
                            addTo(stackCorrection, (int)-i, out);
                        } else if (op == ArithmeticOperator.SUB) {
                            addTo(stackCorrection, (int)i, out);
                        }
                    }
                }
                yield instruction.format(t) + srcF + ", " + dstF;
            }
            case Jump(String label) -> "jmp\t" + label;
            case LabelIr(String label) -> label + ":";
            case SetCC(CmpOperator cmpOperator, boolean unsigned, Operand o) ->
                    "set" + (unsigned ? cmpOperator.unsignedCode :
                            cmpOperator.code) + "\t" + formatOperand(instruction, o, stackCorrection);
            case JmpCC(CC cc,
                       String label) ->
                    "j" + cc.name().toLowerCase(Locale.ENGLISH) + "\t" +label;
            case Call(Operand address, FunType _) -> {
                if (address instanceof LabelAddress(String functionName)) {
                    yield "call\t" +
                            (Mcc.SYMBOL_TABLE.containsKey(functionName) ?
                                    functionName :
                                    functionName + "@PLT");
                } else yield "call\t" + "*" +
                        formatOperand(QUADWORD, instruction, address,
                                stackCorrection);
            }
            case Cdq(TypeAsm t) -> instruction.format(t);
            case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                       Operand dst) -> {
                String srcF = formatOperand(srcType, instruction, src, stackCorrection);
                String dstF = formatOperand(dstType, instruction, dst, stackCorrection);
                yield "movs" + srcType.suffix() + dstType.suffix() + "\t" + srcF + ", " + dstF;
            }
            case MovZeroExtend(TypeAsm srcType, TypeAsm dstType, Operand src,
                               Operand dst) -> {
                String srcF = formatOperand(srcType, instruction, src, stackCorrection);
                String dstF = formatOperand(dstType, instruction, dst, stackCorrection);
                yield "movz" + srcType.suffix() + dstType.suffix() + "\t" + srcF + ", " + dstF;
            }
            case Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) -> {
                if (srcType.isInteger()) {
                    String srcF = formatOperand(srcType, instruction, src, stackCorrection);
                    String dstF = formatOperand(dstType, instruction, dst, stackCorrection);
                    yield (srcType == QUADWORD
                            ? dstType == DOUBLE
                            ? "cvtsi2sdq\t"
                            : "cvtsi2ssq\t"
                            : dstType == DOUBLE
                            ? "cvtsi2sdl\t"
                            : "cvtsi2ssl\t") +
                            srcF +
                            ", " +
                            dstF;
                } else {
                    String srcF = formatOperand(srcType, instruction, src, stackCorrection);
                    String dstF = formatOperand(dstType, instruction, dst, stackCorrection);

                    if (srcType == DOUBLE) {
                        yield dstType == FLOAT ?
                                "vcvtsd2ss\t" + srcF + ", " + dstF + ", " +
                                        dstF : (dstType ==
                                QUADWORD ? "cvttsd2siq\t" : "cvttsd2sil\t") +
                                srcF + ", " + dstF;
                    } else if (srcType == FLOAT) {
                        yield dstType == DOUBLE ?
                                "vcvtss2sd\t" + srcF + ", " + dstF + ", " +
                                        dstF :
                                (dstType == QUADWORD ? "cvttss2siq\t" :
                                        "cvttss2sil" + "\t") + srcF + ", " +
                                        dstF;
                    }
                    else throw new Todo("can't happen");

                }
            }
            case Comment(String comment) -> "# " + comment;
            case Literal(String string) -> string;
            case Pop(IntegerReg arg) -> {
                String r=
                    "popq\t" + formatOperand(instruction, arg, stackCorrection);
                // We don't do `addTo(stackCorrection, -8, out)` because pop is
                // just used to restore registers prior to executing RET
                // instructions. "Fixing" the stackCorrection would leave us
                // with the wrong result in other basic blocks of the function
                // following the RET
                yield r;
            }

            case Test(TypeAsm t, Operand src1, Operand src2) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            src1, stackCorrection) + formatOperand(t, instruction, src2, stackCorrection);
        };
        if (instruction instanceof LabelIr) {
            out.println(s);
        } else {
            printIndent(out, s);
        }
    }

    private static void addTo(AtomicInteger stackCorrection, int i,
                              PrintWriter out) {
        stackCorrection.addAndGet(i);
        printIndent(out, "# stackCorrection="+stackCorrection.get());
    }

}
