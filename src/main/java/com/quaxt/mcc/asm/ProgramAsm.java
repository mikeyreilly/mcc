package com.quaxt.mcc.asm;

import com.quaxt.mcc.*;
import com.quaxt.mcc.debug.Dwarf;
import com.quaxt.mcc.parser.Position;
import com.quaxt.mcc.semantic.FunType;
import com.quaxt.mcc.semantic.SemanticAnalysis;
import com.quaxt.mcc.tacky.*;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.Mcc.*;
import static com.quaxt.mcc.asm.Codegen.BACKEND_SYMBOL_TABLE;
import static com.quaxt.mcc.asm.IntegerReg.BP;
import static com.quaxt.mcc.asm.IntegerReg.SP;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;

public record ProgramAsm(List<TopLevelAsm> topLevelAsms, ArrayList<Position> positions) {

    /**
     * FrameSlot offsets are relative to an abstract local-frame base. spDelta
     * is the current %rsp minus that base at this instruction.
     */
    private static String formatOperand(Instruction s, Operand o, long spDelta) {
        return switch (o) {
            case Imm(long i) -> "$" + i;
            case Pseudo p -> p.identifier;
            case FrameSlot(long offset, int _) ->
                    (offset - spDelta) + "(%" + SP.q + ")";
            case IncomingStackArg(long offset) -> offset + "(%" + BP.q + ")";
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


    private static String formatOperand(TypeAsm t, Instruction s, Operand o, long spDelta) {
        if (o instanceof IntegerReg reg) {
            return "%" + switch (t) {
                case BYTE -> reg.b;
                case WORD -> reg.w;
                case LONGWORD -> reg.d;
                case QUADWORD -> reg.q;
                case ByteArray(long size, int alignment) ->
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
        return formatOperand(s, o, spDelta);
    }

    public void emitAsm(PrintWriter out, Path srcFile) {
        if (Mcc.target.isWindowsMsvc()) {
            emitMasm(out);
            return;
        }
        String textStart = null;
        String textEnd = null;

        for (TopLevelAsm t : topLevelAsms) {
            if (t instanceof FunctionIr functionAsm) {
                prepareStackMetadata(functionAsm, Mcc.addDebugInfo);
            }
        }

        if (Mcc.addDebugInfo) {
            textStart = makeTemporary(".Ltext.");
            textEnd = makeTemporary(".LtextEnd.");
            // We do this first because it can add new StaticConstantAsms (strings) to toplevel
            Dwarf.emitDebugInfo(out, topLevelAsms, srcFile, textStart, textEnd, positions);
        }
        out.println("                .text");
        if (Mcc.addDebugInfo) {
            out.println(textStart + ":");
        }
        for (TopLevelAsm t : topLevelAsms) {
            switch (t) {
                case FunctionIr functionAsm -> {
                    emitFunctionAsm(out, functionAsm);
                }
                default -> {}
            }
        }
        if (Mcc.addDebugInfo) {
            out.println(textEnd + ":");
        }
        for (TopLevelAsm t : topLevelAsms) {
            switch (t) {
                case FunctionIr _ -> {
                }
                case StaticVariableAsm staticVariableAsm -> {
                    emitStaticVariableAsm(out, staticVariableAsm);
                }
                case StaticConstant sc -> {
                    emitStaticConstantAsm(out, sc);
                }
                case DebugString(String name, String string)  -> {
                    out.println("                .section .debug_str");
                    out.println(name + ":");
                    out.println("                " + stringDirective(string, true));
                }
                case DebugLineString(String name, String string)  -> {
                    out.println("                .section .debug_line_str");
                    out.println(name + ":");
                    out.println("                " + stringDirective(string, true));
                }
            }
        }
        out.println("                .ident	\"GCC: (Ubuntu 11.4.0-1ubuntu1~22" + ".04) 11.4.0\"");
        out.println("                .section	.note.GNU-stack,\"\"," +
                "@progbits");
    }

    private void emitMasm(PrintWriter out) {
        out.println("option casemap:none");
        for (String functionName : referencedExternalFunctions()) {
            out.println("EXTERN " + masmSymbol(functionName) + ":PROC");
        }
        out.println(".code");
        for (TopLevelAsm t : topLevelAsms) {
            if (t instanceof FunctionIr functionAsm) {
                prepareStackMetadata(functionAsm, false);
                emitMasmFunction(out, functionAsm);
            }
        }
        for (TopLevelAsm t : topLevelAsms) {
            switch (t) {
                case StaticVariableAsm staticVariableAsm -> emitMasmStaticVariable(out, staticVariableAsm);
                case StaticConstant sc -> emitMasmStaticConstant(out, sc);
                default -> {}
            }
        }
        out.println("END");
    }

    private Set<String> referencedExternalFunctions() {
        Set<String> referenced = new LinkedHashSet<>();
        for (TopLevelAsm topLevelAsm : topLevelAsms) {
            switch (topLevelAsm) {
                case FunctionIr functionAsm -> {
                    for (Instruction instruction : functionAsm.instructions) {
                        if (instruction instanceof Call(LabelAddress(String functionName), FunType _)
                                && isExternalFunction(functionName)) {
                            referenced.add(functionName);
                        }
                    }
                }
                case StaticVariableAsm staticVariableAsm -> {
                    for (StaticInit init : staticVariableAsm.init()) {
                        addReferencedFunction(init, referenced);
                    }
                }
                case StaticConstant staticConstant ->
                        addReferencedFunction(staticConstant.init(), referenced);
                default -> {}
            }
        }
        return referenced;
    }

    private static void addReferencedFunction(StaticInit init,
                                              Set<String> referenced) {
        if (init instanceof PointerInit(String label, long _)
                && isExternalFunction(label)) {
            referenced.add(label);
        }
    }

    private static boolean isExternalFunction(String name) {
        return !name.startsWith("__builtin")
                && Mcc.SYMBOL_TABLE.get(name) instanceof SymbolTableEntry entry
                && entry.attrs() instanceof FunAttributes(boolean defined, boolean _)
                && !defined;
    }

    private void emitMasmStaticConstant(PrintWriter out, StaticConstant v) {
        out.println(".const");
        if (v.alignment() > 1) out.println("ALIGN " + v.alignment());
        out.println(masmLocal("L" + v.label()) + " LABEL BYTE");
        writeMasmValue(out, v.init());
    }

    private void emitMasmStaticVariable(PrintWriter out, StaticVariableAsm v) {
        boolean zeroOnly = v.init().size() == 1 &&
                v.init().getFirst() instanceof ZeroInit;
        out.println(zeroOnly ? ".data?" : ".data");
        if (v.global()) out.println("PUBLIC " + masmSymbol(v.name()));
        if (v.alignment() > 1) out.println("ALIGN " + v.alignment());
        out.println(masmSymbol(v.name()) + " LABEL BYTE");
        if (zeroOnly) {
            ZeroInit zeroInit = (ZeroInit) v.init().getFirst();
            out.println("    BYTE " + zeroInit.bytes() + " DUP (?)");
        } else {
            for (StaticInit init : v.init()) writeMasmValue(out, init);
        }
    }

    private static void writeMasmValue(PrintWriter out, StaticInit init) {
        String s = switch (init) {
            case DoubleInit(double d) -> "QWORD " + Double.doubleToLongBits(d);
            case FloatInit(float d) -> "DWORD " + Float.floatToIntBits(d);
            case ShortInit(short l) -> "WORD " + l;
            case UShortInit(short l) -> "WORD " + Integer.toUnsignedString(l & 0xffff);
            case IntInit(int l) -> "DWORD " + l;
            case LongInit(long l) -> "DWORD " + (int) l;
            case LongLongInit(long l) -> "QWORD " + l;
            case UIntInit(int l) -> "DWORD " + Integer.toUnsignedString(l);
            case ULongInit(long l) -> "DWORD " + Integer.toUnsignedString((int) l);
            case ULongLongInit(long l) -> "QWORD " + Long.toUnsignedString(l);
            case ZeroInit(long l) -> "BYTE " + l + " DUP (0)";
            case CharInit(byte i) -> "BYTE " + (i & 0xff);
            case UCharInit(byte i) -> "BYTE " + (i & 0xff);
            case BoolInit(byte i) -> "BYTE " + (i & 0xff);
            case PointerInit(String label, long offset) -> {
                boolean isConstant =
                        BACKEND_SYMBOL_TABLE.get(label) instanceof ObjEntry e && e.isConstant();
                String symbol = isConstant ? masmLocal("L" + label) : masmSymbol(label);
                yield "QWORD " + symbol + (offset == 0 ? "" :
                        offset > 0 ? " + " + offset : " - " + -offset);
            }
            case StringInit(String s0, boolean nullTerminated) ->
                    masmStringBytes(s0, nullTerminated);
        };
        out.println("    " + s);
    }

    private static String masmStringBytes(String s, boolean nullTerminated) {
        ArrayList<String> bytes = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) bytes.add(Integer.toString(s.charAt(i) & 0xff));
        if (nullTerminated) bytes.add("0");
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < bytes.size(); i += 32) {
            lines.add("BYTE " + String.join(",",
                    bytes.subList(i, Math.min(i + 32, bytes.size()))));
        }
        return String.join(System.lineSeparator() + "    ", lines);
    }

    private void emitMasmFunction(PrintWriter out, FunctionIr functionAsm) {
        String name = masmSymbol(functionAsm.name);
        if (functionAsm.global) out.println("PUBLIC " + name);
        out.println(name + " PROC");
        printIndent(out, "push rbp");
        printIndent(out, "mov rbp, rsp");
        long stackSize = functionAsm.stackSize;
        IntegerReg[] calleeSavedRegs = functionAsm.calleeSavedRegs;
        long calleeSavedBytes = roundAwayFromZero(8L * calleeSavedRegs.length, 16);
        long totalStackBytes = roundAwayFromZero(calleeSavedBytes + stackSize, 16);
        if (totalStackBytes != 0)
            printIndent(out, "sub rsp, " + totalStackBytes);
        if (calleeSavedRegs.length % 2 == 1) {
            printIndent(out, "sub rsp, 8");
        }
        for (int i = calleeSavedRegs.length - 1; i >= 0; i--) {
            printIndent(out, "push " + calleeSavedRegs[i].q);
        }
        for (int i = 0; i < functionAsm.instructions.size(); i++) {
            emitMasmInstruction(out, functionAsm.instructions.get(i),
                    functionAsm.instructionStackDeltas[i], functionAsm);
        }
        out.println(name + " ENDP");
    }

    private static String masmSymbol(String s) {
        String base = s.replace('.', '$');
        SymbolTableEntry entry = Mcc.SYMBOL_TABLE.get(s);
        if (!"main".equals(s) && entry != null &&
                entry.attrs() instanceof FunAttributes(boolean defined, boolean _) &&
                defined) {
            return "mcc$" + base;
        }
        if (entry != null && entry.attrs() instanceof StaticAttributes) {
            return "mcc$" + base;
        }
        return base;
    }

    private static String masmLocal(String s) {
        return "$" + masmSymbol(s).replace("$L", "L");
    }

    private static String masmLabel(String s) {
        return s.startsWith(".") ? masmLocal(s.substring(1)) : masmSymbol(s);
    }

    private static String sizePrefix(TypeAsm t) {
        return switch (t) {
            case BYTE -> "BYTE PTR ";
            case WORD -> "WORD PTR ";
            case LONGWORD, FLOAT -> "DWORD PTR ";
            case QUADWORD, DOUBLE -> "QWORD PTR ";
            case ByteArray(long size, int _) ->
                    size <= 1 ? "BYTE PTR " : size <= 2 ? "WORD PTR " :
                            size <= 4 ? "DWORD PTR " : "QWORD PTR ";
        };
    }

    private static String masmOperand(TypeAsm t, Instruction s, Operand o,
                                      long spDelta, boolean withSize) {
        return switch (o) {
            case Imm(long i) -> Long.toString(i);
            case IntegerReg reg -> switch (t) {
                case BYTE -> reg.b;
                case WORD -> reg.w;
                case LONGWORD -> reg.d;
                case QUADWORD -> reg.q;
                case ByteArray(long size, int _) ->
                        size <= 1 ? reg.b : size <= 2 ? reg.w :
                                size <= 4 ? reg.d : reg.q;
                default -> throw new IllegalArgumentException("bad int reg type " + t);
            };
            case DoubleReg reg -> reg.toString();
            case FrameSlot(long offset, int _) ->
                    (withSize ? sizePrefix(t) : "") + "[rsp" + signed(offset - spDelta) + "]";
            case IncomingStackArg(long offset) ->
                    (withSize ? sizePrefix(t) : "") + "[rbp" + signed(offset) + "]";
            case Memory(IntegerReg reg, long offset) ->
                    (withSize ? sizePrefix(t) : "") + "[" + reg.q + signed(offset) + "]";
            case Data(String identifier, long offset) -> {
                boolean isConstant =
                        BACKEND_SYMBOL_TABLE.get(identifier) instanceof ObjEntry e && e.isConstant();
                String label = isConstant ? masmLocal("L" + identifier) : masmSymbol(identifier);
                yield (withSize ? sizePrefix(t) : "") + "[" + label +
                        (offset == 0 ? "" : signed(offset)) + "]";
            }
            case Indexed(IntegerReg base, IntegerReg index, int scale) ->
                    (withSize ? sizePrefix(t) : "") + "[" + base.q + "+" +
                            index.q + "*" + scale + "]";
            case LabelAddress(String label) -> masmSymbol(label);
            case Pseudo p -> p.identifier;
            default -> throw new IllegalStateException("Unexpected value: " + o);
        };
    }

    private static String signed(long offset) {
        return offset == 0 ? "" : offset > 0 ? "+" + offset : Long.toString(offset);
    }

    private void emitMasmInstruction(PrintWriter out, Instruction instruction,
                                     long spDelta, FunctionIr functionAsm) {
        String s = switch (instruction) {
            case Pos _, Comment _ -> null;
            case LabelIr(String label) -> masmLabel(label) + ":";
            case Literal(String string) -> string;
            case Mov(TypeAsm t, Operand src, Operand dst) -> {
                String op = t == QUADWORD && (src instanceof DoubleReg || dst instanceof DoubleReg)
                        ? "movq" : (t == DOUBLE || t == FLOAT) ? "mov" + t.suffix() : "mov";
                yield op + " " + masmOperand(t, instruction, dst, spDelta, true) +
                        ", " + masmOperand(t, instruction, src, spDelta, src instanceof Memory || src instanceof Data || src instanceof FrameSlot || src instanceof IncomingStackArg);
            }
            case Xchg(TypeAsm t, Operand src, Operand dst) ->
                    "xchg " + masmOperand(t, instruction, dst, spDelta, true) +
                            ", " + masmOperand(t, instruction, src, spDelta, true);
            case Lea(Operand src, Operand dst) ->
                    "lea " + masmOperand(QUADWORD, instruction, dst, spDelta, false) +
                            ", " + masmOperand(QUADWORD, instruction, src, spDelta, true);
            case Push(Operand arg) -> "push " + masmOperand(QUADWORD, instruction, arg, spDelta, false);
            case Pop(IntegerReg arg) -> "pop " + arg.q;
            case Nullary.RET -> {
                StringBuilder b = new StringBuilder();
                for (IntegerReg r : functionAsm.calleeSavedRegs) {
                    b.append("\tpop ").append(r.q).append('\n');
                }
                b.append("\tmov rsp, rbp\n\tpop rbp\n\tret");
                yield b.toString();
            }
            case Nullary.MFENCE -> "mfence";
            case Unary(UnaryOperator op, TypeAsm t, Operand operand) -> {
                String mnemonic = switch (op) {
                    case DIV -> "div";
                    case IDIV -> "idiv";
                    case BITWISE_NOT, NOT -> "not";
                    case UNARY_MINUS -> "neg";
                    case UNARY_SHR -> "shr";
                    case BSWAP -> t == WORD ? "rol" : "bswap";
                    default -> throw new AssertionError("can't format " + op);
                };
                yield mnemonic + " " + masmOperand(t, instruction, operand, spDelta, true)
                        + (op == UnaryOperator.BSWAP && t == WORD ? ", 8" : "");
            }
            case Binary(ArithmeticOperator op, TypeAsm t, Operand src, Operand dst) -> {
                String mnemonic = switch (op) {
                    case ADD, DOUBLE_ADD -> t == DOUBLE ? "addsd" : t == FLOAT ? "addss" : "add";
                    case SUB, DOUBLE_SUB -> t == DOUBLE ? "subsd" : t == FLOAT ? "subss" : "sub";
                    case IMUL -> "imul";
                    case AND, BITWISE_AND -> "and";
                    case OR, BITWISE_OR -> "or";
                    case BITWISE_XOR -> t == DOUBLE ? "xorpd" : t == FLOAT ? "xorps" : "xor";
                    case SHL -> "shl";
                    case SAR -> "sar";
                    case SHR -> "shr";
                    case BSR -> "bsr";
                    case DOUBLE_MUL -> t == FLOAT ? "mulss" : "mulsd";
                    case DOUBLE_DIVIDE -> t == FLOAT ? "divss" : "divsd";
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };
                TypeAsm srcType = switch (op) {
                    case SHL, SAR, SHR -> BYTE;
                    default -> t;
                };
                yield mnemonic + " " + masmOperand(t, instruction, dst, spDelta, true) +
                        ", " + masmOperand(srcType, instruction, src, spDelta, true);
            }
            case Cmp(TypeAsm t, Operand subtrahend, Operand minuend) -> {
                String mnemonic = t == DOUBLE ? "comisd" : t == FLOAT ? "comiss" : "cmp";
                yield mnemonic + " " + masmOperand(t, instruction, minuend, spDelta, true) +
                        ", " + masmOperand(t, instruction, subtrahend, spDelta, true);
            }
            case Jump(String label) -> "jmp " + masmLabel(label);
            case JmpCC(CC cc, String label) -> "j" + cc.name().toLowerCase(Locale.ENGLISH) + " " + masmLabel(label);
            case SetCC(CmpOperator cmpOperator, boolean unsigned, Operand o) ->
                    "set" + (unsigned ? cmpOperator.unsignedCode : cmpOperator.code) +
                            " " + masmOperand(BYTE, instruction, o, spDelta, true);
            case Call(Operand address, FunType _) -> {
                if (address instanceof LabelAddress(String functionName)) {
                    yield "call " + masmSymbol(functionName);
                }
                yield "call " + masmOperand(QUADWORD, instruction, address, spDelta, true);
            }
            case Cdq(TypeAsm t) -> t == QUADWORD ? "cqo" : "cdq";
            case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) ->
                    "movsx " + masmOperand(dstType, instruction, dst, spDelta, false) +
                            ", " + masmOperand(srcType, instruction, src, spDelta, true);
            case MovZeroExtend(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) ->
                    "movzx " + masmOperand(dstType, instruction, dst, spDelta, false) +
                            ", " + masmOperand(srcType, instruction, src, spDelta, true);
            case Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) -> {
                String mnemonic;
                if (srcType.isInteger()) {
                    mnemonic = dstType == DOUBLE ? "cvtsi2sd" : "cvtsi2ss";
                } else if (srcType == DOUBLE) {
                    mnemonic = dstType == FLOAT ? "cvtsd2ss" :
                            dstType == QUADWORD ? "cvttsd2si" : "cvttsd2si";
                } else {
                    mnemonic = dstType == DOUBLE ? "cvtss2sd" :
                            dstType == QUADWORD ? "cvttss2si" : "cvttss2si";
                }
                yield mnemonic + " " + masmOperand(dstType, instruction, dst, spDelta, false) +
                        ", " + masmOperand(srcType, instruction, src, spDelta, true);
            }
            case Test(TypeAsm t, Operand src1, Operand src2) ->
                    "test " + masmOperand(t, instruction, src2, spDelta, true) +
                            ", " + masmOperand(t, instruction, src1, spDelta, true);
        };
        if (s == null) return;
        if (instruction instanceof LabelIr) out.println(s);
        else {
            for (String line : s.split("\\R")) printIndent(out, line);
        }
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
            case StringInit(String s, boolean nullTerminated) ->
                    stringDirective(s, nullTerminated);
            case UCharInit(byte i) -> ".byte " + (i & 0xff);
            case BoolInit(byte i) -> ".byte " + (i & 0xff);
        });
    }

    private static StringBuilder stringDirective(String s, boolean nullTerminated) {
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
        return sb;
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

    private void emitFunctionAsm(PrintWriter out, FunctionIr functionAsm) {
        String name = functionAsm.name;
        if (functionAsm.global)
            out.println("                .globl	" + name);
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
            printIndent(out, ".cfi_offset "+BP.dwarfNumber+", -16\n");
        }
        printIndent(out, "movq\t%rsp, %rbp");
        if (Mcc.addDebugInfo) {
            // From now on CFA is at an offset of RBP so it is RBP+16
            printIndent(out, ".cfi_def_cfa_register "+BP.dwarfNumber);
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


        if (adjustedStackBytes != 0) {
            emitInstruction(out, new Binary(SUB, QUADWORD,
                    new Imm(adjustedStackBytes), SP), 0, functionAsm);
        }
        emitInstruction(out, new Comment("Check alignment"), 0, functionAsm);
        var stackAlignment = functionAsm.stackAlignment;
        if (stackAlignment != 0) {
            emitInstruction(out, new Binary(BITWISE_AND, QUADWORD, new Imm(-stackAlignment), SP), 0, functionAsm);
        }
        // push in reverse direction so we can pop in forward direction
        if (calleeSavedRegs.length % 2 == 1) {
            emitInstruction(out, new Binary(SUB, QUADWORD, new Imm(8), SP),
                    0, functionAsm);
        }
        for (int i = calleeSavedRegs.length - 1; i >= 0; i--) {
            IntegerReg r = calleeSavedRegs[i];
            emitInstruction(out, new Push(r), 0, functionAsm);
        }
        for (int i = 0; i < instructions.size(); i++) {
            emitFrameBaseBoundaryLabel(out, functionAsm, i);
            emitInstruction(out, instructions.get(i),
                    functionAsm.instructionStackDeltas[i], functionAsm);
        }
        emitFrameBaseBoundaryLabel(out, functionAsm, instructions.size());
        if (addDebugInfo)
            out.println(".L"+name+".end" + ":");
        out.println(".size "+name+", .-"+name);
        printIndent(out,".cfi_endproc");
    }


    private void emitInstruction(PrintWriter out,
                                        Instruction instruction,
                                        long spDelta, FunctionIr functionAsm) {
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
                    case ByteArray(long size, int alignment) ->
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
                        formatOperand(t, instruction, src, spDelta) + ", " +
                        formatOperand(t, instruction, dst, spDelta);
            }
            case Xchg(TypeAsm t, Operand src, Operand dst) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            src, spDelta) + ", " + formatOperand(t, instruction, dst, spDelta);
            case Lea(Operand src, Operand dst) ->
                    "leaq\t" + formatOperand(QUADWORD, instruction, src, spDelta) + ","
                            + " " + formatOperand(QUADWORD, instruction, dst, spDelta);
            case Push(Operand arg) ->{
                    yield "pushq\t" + formatOperand(QUADWORD, instruction, arg, spDelta);
            }

            case Nullary.RET -> {
                // epilogue
                if (Mcc.addDebugInfo) {
                    printIndent(out, ".cfi_remember_state");
                }
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
                    printIndent(out,".cfi_def_cfa " +SP.dwarfNumber+ ", 8");
                }

                printIndent(out, "ret");
                if (Mcc.addDebugInfo) {
                    printIndent(out, ".cfi_restore_state");
                }
                yield null;
            }
            case Nullary.MFENCE -> "mfence";

            case Unary(UnaryOperator op, TypeAsm t, Operand operand) ->
                    op == UnaryOperator.BSWAP && t == WORD ?
                            "rolw\t" + "$8, " +
                                    formatOperand(t, instruction, operand, spDelta) :
                            instruction.format(t) +
                                    formatOperand(t, instruction, operand, spDelta);
            case Cmp(TypeAsm t, Operand subtrahend, Operand minuend) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            subtrahend, spDelta) + ", " + formatOperand(t, instruction
                            , minuend, spDelta);
            case Binary(ArithmeticOperator op, TypeAsm t, Operand src,
                        Operand dst) -> {
                String srcF = formatOperand(switch(op){
                    case SHL, SAR, SHR -> BYTE;
                    default -> t;
                }, instruction, src, spDelta);
                String dstF = formatOperand(t, instruction, dst, spDelta);
                yield instruction.format(t) + srcF + ", " + dstF;
            }
            case Jump(String label) -> "jmp\t" + label;
            case LabelIr(String label) -> label + ":";
            case SetCC(CmpOperator cmpOperator, boolean unsigned, Operand o) ->
                    "set" + (unsigned ? cmpOperator.unsignedCode :
                            cmpOperator.code) + "\t" + formatOperand(instruction, o, spDelta);
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
                                spDelta);
            }
            case Cdq(TypeAsm t) -> instruction.format(t);
            case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                       Operand dst) -> {
                String srcF = formatOperand(srcType, instruction, src, spDelta);
                String dstF = formatOperand(dstType, instruction, dst, spDelta);
                yield "movs" + srcType.suffix() + dstType.suffix() + "\t" + srcF + ", " + dstF;
            }
            case MovZeroExtend(TypeAsm srcType, TypeAsm dstType, Operand src,
                               Operand dst) -> {
                String srcF = formatOperand(srcType, instruction, src, spDelta);
                String dstF = formatOperand(dstType, instruction, dst, spDelta);
                yield "movz" + srcType.suffix() + dstType.suffix() + "\t" + srcF + ", " + dstF;
            }
            case Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) -> {
                if (srcType.isInteger()) {
                    String srcF = formatOperand(srcType, instruction, src, spDelta);
                    String dstF = formatOperand(dstType, instruction, dst, spDelta);
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
                    String srcF = formatOperand(srcType, instruction, src, spDelta);
                    String dstF = formatOperand(dstType, instruction, dst, spDelta);

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
                    "popq\t" + formatOperand(instruction, arg, spDelta);
                yield r;
            }

            case Test(TypeAsm t, Operand src1, Operand src2) ->
                    instruction.format(t) + formatOperand(t, instruction,
                            src1, spDelta) + formatOperand(t, instruction, src2, spDelta);
        };
        if (s == null) return;
        if (instruction instanceof LabelIr) {
            out.println(s);
        } else {
            printIndent(out, s);
        }
    }

    private static void prepareStackMetadata(FunctionIr functionAsm,
                                             boolean addDebugInfo) {
        long initialSpDelta = bodyInitialSpDelta(functionAsm);
        functionAsm.instructionStackDeltas =
                StackStateAnalysis.instructionDeltas(functionAsm.instructions,
                        initialSpDelta);
        if (addDebugInfo) {
            prepareFrameBaseRanges(functionAsm);
        }
    }

    private static long bodyInitialSpDelta(FunctionIr functionAsm) {
        int calleeSavedCount = functionAsm.calleeSavedRegs.length;
        long oddPadding = calleeSavedCount % 2 == 1 ? 8 : 0;
        return -(oddPadding + 8L * calleeSavedCount);
    }

    private static void prepareFrameBaseRanges(FunctionIr functionAsm) {
        List<Instruction> instructions = functionAsm.instructions;
        String[] boundaryLabels = new String[instructions.size() + 1];
        ArrayList<FrameBaseRange> ranges = new ArrayList<>();
        int rangeStart = -1;
        long rangeDelta = 0;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction == Nullary.RET) {
                if (rangeStart != -1) {
                    ranges.add(new FrameBaseRange(labelAt(boundaryLabels,
                            rangeStart), labelAt(boundaryLabels, i),
                            rangeDelta));
                    rangeStart = -1;
                }
                continue;
            }
            long delta = functionAsm.instructionStackDeltas[i];
            if (rangeStart == -1) {
                rangeStart = i;
                rangeDelta = delta;
                labelAt(boundaryLabels, i);
            } else if (delta != rangeDelta) {
                ranges.add(new FrameBaseRange(labelAt(boundaryLabels,
                        rangeStart), labelAt(boundaryLabels, i), rangeDelta));
                rangeStart = i;
                rangeDelta = delta;
            }
        }
        if (rangeStart != -1) {
            ranges.add(new FrameBaseRange(labelAt(boundaryLabels, rangeStart),
                    labelAt(boundaryLabels, instructions.size()), rangeDelta));
        }
        functionAsm.frameBaseBoundaryLabels = boundaryLabels;
        functionAsm.frameBaseRanges = ranges;
        functionAsm.frameBaseLocListLabel =
                ranges.isEmpty() ? null : makeTemporary(".LframeBase.");
    }

    private static String labelAt(String[] labels, int index) {
        if (labels[index] == null) {
            labels[index] = makeTemporary(".LframeBaseBoundary.");
        }
        return labels[index];
    }

    private static void emitFrameBaseBoundaryLabel(PrintWriter out,
                                                   FunctionIr functionAsm,
                                                   int instructionIndex) {
        String[] labels = functionAsm.frameBaseBoundaryLabels;
        if (labels != null && instructionIndex < labels.length &&
                labels[instructionIndex] != null) {
            out.println(labels[instructionIndex] + ":");
        }
    }

}
