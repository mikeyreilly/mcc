package com.quaxt.mcc.asm;

import com.quaxt.mcc.*;
import com.quaxt.mcc.atomics.MemoryOrder;
import com.quaxt.mcc.optimizer.Optimizer;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.registerallocator.RegisterAllocator;
import com.quaxt.mcc.semantic.*;
import com.quaxt.mcc.tacky.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.EQUALS;
import static com.quaxt.mcc.CmpOperator.NOT_EQUALS;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.Mcc.*;
import static com.quaxt.mcc.UnaryOperator.UNARY_SHR;
import static com.quaxt.mcc.asm.DoubleReg.*;
import static com.quaxt.mcc.asm.JmpCC.newJmpCC;
import static com.quaxt.mcc.asm.Nullary.MFENCE;
import static com.quaxt.mcc.asm.Nullary.RET;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;
import static com.quaxt.mcc.asm.IntegerReg.*;
import static com.quaxt.mcc.tacky.IrGen.newLabel;

public class Codegen {
    static HashMap<String, StaticConstant> CONSTANT_TABLE = new HashMap<>();
    public static Map<String, SymTabEntryAsm> BACKEND_SYMBOL_TABLE =
            new DebugHashMap<>();

    public static void clear() {
        CONSTANT_TABLE.clear();
        BACKEND_SYMBOL_TABLE.clear();
    }

    private static final Imm UPPER_BOUND_LONG_IMMEDIATE = new Imm(1L << 63);

    public final static IntegerReg[] INTEGER_RETURN_REGISTERS =
            new IntegerReg[]{AX, DX};
    public final static IntegerReg[] INTEGER_REGISTERS = new IntegerReg[]{DI,
            SI, DX, CX, R8, R9};
    public final static DoubleReg[] DOUBLE_REGISTERS = new DoubleReg[]{XMM0,
            XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7};

    private static String doubleToHexString(double d) {
        return Double.toHexString(d).replaceAll("-", "_");
    }

    private static String floatToHexString(float d) {
        return Float.toHexString(d).replaceAll("-", "_")+'F';
    }


    public static ProgramAsm generateProgramAssembly(ProgramIr programIr) {
        ArrayList<TopLevelAsm> topLevels = new ArrayList<>();
        for (TopLevel topLevel : programIr.topLevels()) {
            switch (topLevel) {
                case FunctionIr f -> {
                    FunctionAsm functionAsm = convertFunction(f);
                    topLevels.add(functionAsm);

                }

                case StaticVariable(String name, boolean global, Type t,
                                    List<StaticInit> init) -> topLevels.add(new StaticVariableAsm(name, global,
                                            variableAlignment(t), init));
                case com.quaxt.mcc.tacky.StaticConstant(String name, Type t,
                                                        StaticInit init) ->
                        topLevels.add(new StaticConstant(name,
                                variableAlignment(t), init));
            }
        }
        topLevels.addAll(CONSTANT_TABLE.values());
        generateBackendSymbolTable();
        for (TopLevelAsm topLevelAsm : topLevels) {

            if (topLevelAsm instanceof FunctionAsm functionAsm) {
                if(!registerAllocatorDisabled){
                    RegisterAllocator.allocateRegisters(functionAsm);
                }
                functionAsm.stackSize = replacePseudoRegisters(functionAsm);
                fixUpInstructions(functionAsm);
            }
        }

        return new ProgramAsm(topLevels, programIr.positions());
    }

    private static void generateBackendSymbolTable() {
        for (Map.Entry<String, SymbolTableEntry> e : SYMBOL_TABLE.entrySet()) {
            SymbolTableEntry v = e.getValue();
            IdentifierAttributes attrs = v.attrs();
            var entry = switch (attrs) {
                case FunAttributes(boolean defined, boolean _) ->
                        new FunEntry(defined,
                                defined && classifyReturnValueLight(((FunType) v.type()).ret()));
                case IdentifierAttributes.LocalAttr _ ->
                        new ObjEntry(toTypeAsm(v.type()), false, false);
                case StaticAttributes sa ->
                        sa.storageClass()== StorageClass.TYPEDEF ? null : new ObjEntry(toTypeAsm(v.type()), true, false);
                case ConstantAttr _ ->
                        new ObjEntry(toTypeAsm(v.type()), true, true);
            };
            if (entry != null)
                BACKEND_SYMBOL_TABLE.put(e.getKey(), entry);
        }
        for (StaticConstant v : CONSTANT_TABLE.values()) {
            BACKEND_SYMBOL_TABLE.put(v.label(), new ObjEntry(DOUBLE, true,
                    true));
        }
    }

    private static ArithmeticOperator convertOp(ArithmeticOperator op1,
                                                TypeAsm typeAsm) {
        return typeAsm == DOUBLE || typeAsm == FLOAT ? switch (op1) {
            case SUB -> DOUBLE_SUB;
            case ADD -> DOUBLE_ADD;
            case IMUL -> DOUBLE_MUL;
            case DIVIDE -> DOUBLE_DIVIDE;
            default -> op1;
        } : op1;
    }

    private static long replacePseudoRegisters(FunctionAsm functionAsm) {

        List<Instruction> instructions = functionAsm.instructions;

        boolean returnInMemory = functionAsm.returnInMemory;
        // for varargs functions we reserve 176 bytes (8 bytes for each of 6
        // GPRs and 16 bytes for each of XMM0-XMM7)
        long reservedSpace = functionAsm.callsVaStart ? 176 : 0;
        if (returnInMemory) reservedSpace += 8;
        AtomicLong offset = new AtomicLong(0);
        Map<String, Long> varTable = new LinkedHashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            Instruction oldInst = instructions.get(i);
            Instruction newInst = switch (oldInst) {
                case Call(Operand p, FunType t) ->
                        new Call(dePseudo(p, varTable, offset, functionAsm), t);
                case Nullary _, Cdq _, Jump _, JmpCC _, LabelIr _ ,   Literal _
                      -> oldInst;
                case Mov(TypeAsm typeAsm, Operand src, Operand dst) ->
                        new Mov(typeAsm, dePseudo(src, varTable, offset, functionAsm),
                                dePseudo(dst, varTable, offset, functionAsm));
                case Xchg(TypeAsm typeAsm, Operand src, Operand dst) ->
                        new Xchg(typeAsm, dePseudo(src, varTable, offset, functionAsm),
                                dePseudo(dst, varTable, offset, functionAsm));
                case Unary(UnaryOperator op, TypeAsm typeAsm,
                           Operand operand) ->
                        new Unary(op, typeAsm, dePseudo(operand, varTable,
                                offset, functionAsm));
                case Binary(ArithmeticOperator op, TypeAsm typeAsm, Operand src,
                            Operand dst) ->
                        new Binary(op, typeAsm, dePseudo(src, varTable,
                                offset, functionAsm), dePseudo(dst, varTable, offset, functionAsm));

                case Cmp(TypeAsm typeAsm, Operand subtrahend,
                         Operand minuend) ->
                        new Cmp(typeAsm, dePseudo(subtrahend, varTable,
                                offset, functionAsm), dePseudo(minuend, varTable, offset, functionAsm));
                case SetCC(CmpOperator cmpOperator, boolean signed,
                           Operand operand) ->
                        new SetCC(cmpOperator, signed, dePseudo(operand,
                                varTable, offset, functionAsm));
                case Push(Operand operand) ->
                        new Push(dePseudo(operand, varTable, offset, functionAsm));
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) ->
                        new Movsx(srcType, dstType, dePseudo(src, varTable,
                                offset, functionAsm), dePseudo(dst, varTable, offset, functionAsm));
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) ->
                        new MovZeroExtend(srcType, dstType, dePseudo(src,
                                varTable, offset, functionAsm), dePseudo(dst, varTable,
                                offset, functionAsm));
                case Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) ->
                        new Cvt(srcType, dstType, dePseudo(src, varTable,
                                offset, functionAsm), dePseudo(dst, varTable, offset, functionAsm));
                case Lea(Operand src, Operand dst) ->
                        new Lea(dePseudo(src, varTable, offset, functionAsm), dePseudo(dst
                                , varTable, offset, functionAsm));
                case Comment _, Pos _ -> oldInst;
                default -> throw new IllegalStateException("Unexpected value: " + oldInst);
            };
            instructions.set(i, newInst);
        }
        return offset.get() + reservedSpace;
    }

    private static void fixUpInstructions(FunctionAsm function) {
        var instructions = function.instructions;
        IntegerReg[] calleeSavedRegs = function.calleeSavedRegs;
        int calleeSavedCount = calleeSavedRegs.length;

        // Fix illegal MOV, iDiV, ADD, SUB, IMUL instructions
        for (int i = instructions.size() - 1; i >= 0; i--) {
            Instruction oldInst = instructions.get(i);
            switch (oldInst) {
                case RET -> {
                    if (calleeSavedCount > 0) {
                        for (int j = calleeSavedCount - 1; j >= 0; j--) {
                            IntegerReg r = calleeSavedRegs[j];
                            instructions.add(i, new Pop(r));
                        }
                    }
                }
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) -> {
                    // if srcType is not byte or word we rewrite as Mov
                    if (srcType == BYTE || srcType == WORD) {
                        //   boolean mustFixSrc = src instanceof Imm;
                        boolean mustFixDst = !(dst instanceof IntegerReg);

                        if (src instanceof Imm(long i1)) {
                            src = new Imm(i1 & 0xff);
                            if (mustFixDst) {
                                instructions.set(i, new Mov(srcType, src,
                                        srcReg(dstType)));
                                instructions.set(i + 1,
                                        new MovZeroExtend(srcType, dstType,
                                                srcReg(dstType),
                                                dstReg(dstType)));
                                instructions.set(i + 2, new Mov(dstType,
                                        dstReg(dstType), dst));
                            } else {
                                instructions.set(i, new Mov(srcType, src,
                                        srcReg(dstType)));
                                instructions.set(i + 1,
                                        new MovZeroExtend(srcType, dstType,
                                                srcReg(dstType), dst));
                            }
                        } else if (mustFixDst) {
                            instructions.set(i, new MovZeroExtend(srcType,
                                    dstType, src, dstReg(dstType)));
                            instructions.add(i + 1, new Mov(dstType,
                                    dstReg(dstType), dst));
                        }
                    } else {
                        if (src instanceof Imm(long i1)) {
                            if (srcType == LONGWORD) src = new Imm((int) i1);
                        }

                        if (dst instanceof IntegerReg) {
                            instructions.set(i, new Mov(srcType, src, dst));
                        } else {
                            instructions.set(i, new Mov(srcType, src,
                                    dstReg(dstType)));
                            instructions.add(i + 1, new Mov(dstType,
                                    dstReg(dstType), dst));
                        }
                    }
                }
                case Unary(UnaryOperator op, TypeAsm typeAsm,
                           Operand operand) -> {
                    if ((op == UnaryOperator.IDIV || op == UnaryOperator.DIV) && operand instanceof Imm) {
                        instructions.set(i, new Mov(typeAsm, operand,
                                srcReg(typeAsm)));
                        instructions.add(i + 1, new Unary(op, typeAsm,
                                srcReg(typeAsm)));
                    }
                    if ((op == UnaryOperator.BSWAP) && isRam(operand)) {
                            instructions.set(i, new Mov(typeAsm, operand,
                                    dstReg(typeAsm)));
                            instructions.add(i + 1, new Unary(op,
                                    typeAsm, dstReg(typeAsm)));
                            instructions.add(i + 2, new Mov(typeAsm, dstReg(typeAsm), operand));

                    }
                }
                case Mov(TypeAsm typeAsm, Operand src, Operand dst) -> {
                    if (isRam(src) && isRam(dst)) {
                        instructions.set(i, new Mov(typeAsm, src,
                                srcReg(typeAsm)));
                        instructions.add(i + 1, new Mov(typeAsm,
                                srcReg(typeAsm), dst));
                    } else if (src instanceof Imm imm) {
                        if (typeAsm == QUADWORD) {
                            if (imm.isAwkward() && isRam(dst)) {
                                instructions.set(i, new Mov(typeAsm, src,
                                        srcReg(typeAsm)));
                                instructions.add(i + 1, new Mov(typeAsm,
                                        srcReg(typeAsm), dst));
                            }
                        } else {
                            instructions.set(i, new Mov(typeAsm,
                                    imm.truncate(typeAsm), dst));
                        }
                    }
                }
                case Lea(Operand src, Operand dst) -> {
                    if (!(dst instanceof IntegerReg)) {
                        instructions.set(i, new Lea(src, dstReg(QUADWORD)));
                        instructions.add(i + 1, new Mov(QUADWORD,
                                dstReg(QUADWORD), dst));
                    }
                }
                case Push(Operand operand) -> {
                    if (operand instanceof DoubleReg) {
                        instructions.set(i, new Binary(SUB, QUADWORD,
                                new Imm(8), SP));
                        instructions.add(i + 1, new Mov(DOUBLE, operand,
                                new Memory(SP, 0)));
                    } else if (operand instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(QUADWORD, operand,
                                srcReg(QUADWORD)));
                        instructions.add(i + 1, new Push(srcReg(QUADWORD)));
                    }
                }
                case Binary(ArithmeticOperator op, TypeAsm typeAsm, Operand src,
                            Operand dst) -> {
                    if (src instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(typeAsm, src,
                                srcReg(typeAsm)));
                        instructions.add(i + 1, new Binary(op, typeAsm,
                                srcReg(typeAsm), dst));
                        i = i + 2; // will be i+1 at start of next iteration
                        // -> want to catch conditions below
                    } else {
                        switch (op) {
                            case ADD, SUB, BITWISE_OR, BITWISE_AND -> {
                                if (isRam(src) && isRam(dst)) {
                                    instructions.set(i, new Mov(typeAsm, src,
                                            srcReg(typeAsm)));
                                    instructions.add(i + 1, new Binary(op,
                                            typeAsm, srcReg(typeAsm), dst));
                                }

                            }
                            case IMUL, DOUBLE_SUB, DOUBLE_ADD, DOUBLE_MUL,
                                 DOUBLE_DIVIDE, BITWISE_XOR, BSR -> {
                                if (isRam(dst)) {
                                    instructions.set(i, new Mov(typeAsm, dst,
                                            dstReg(typeAsm)));
                                    instructions.add(i + 1, new Binary(op,
                                            typeAsm, src, dstReg(typeAsm)));
                                    instructions.add(i + 2, new Mov(typeAsm,
                                            dstReg(typeAsm), dst));
                                }
                            }

                        }
                    }

                }

                case Cmp(TypeAsm typeAsm, Operand src, Operand dst) -> {
                    if ((typeAsm == DOUBLE || typeAsm == FLOAT) && !(dst instanceof DoubleReg)) {
                        instructions.set(i, new Mov(typeAsm, dst,
                                dstReg(typeAsm)));
                        instructions.add(i + 1, new Cmp(typeAsm, src,
                                dstReg(typeAsm)));
                    } else if (isRam(src) && isRam(dst)) {
                        instructions.set(i, new Mov(typeAsm, src,
                                srcReg(typeAsm)));
                        instructions.add(i + 1, new Cmp(typeAsm,
                                srcReg(typeAsm), dst));
                    } else if (dst instanceof Imm) {
                        if (src instanceof Imm imm && imm.isAwkward()) {
                            instructions.set(i, new Mov(typeAsm, src,
                                    srcReg(typeAsm)));
                            instructions.add(i + 1, new Mov(typeAsm, dst,
                                    dstReg(typeAsm)));
                            instructions.add(i + 2, new Cmp(typeAsm,
                                    srcReg(typeAsm), dstReg(typeAsm)));
                        } else {
                            instructions.set(i, new Mov(typeAsm, dst,
                                    dstReg(typeAsm)));
                            instructions.add(i + 1, new Cmp(typeAsm, src,
                                    dstReg(typeAsm)));
                        }
                    } else if (src instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(typeAsm, src,
                                srcReg(typeAsm)));
                        instructions.add(i + 1, new Cmp(typeAsm,
                                srcReg(typeAsm), dst));
                    }
                }
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) -> {
                    if (src instanceof Imm) {
                        instructions.set(i, new Mov(srcType, src, R10));
                        if (isRam(dst)) {
                            instructions.add(i + 1, new Movsx(srcType,
                                    dstType, R10, R11));
                            instructions.add(i + 2, new Mov(dstType, R11, dst));
                        } else {
                            instructions.add(i + 1, new Movsx(srcType,
                                    dstType, R10, dst));
                        }
                    } else {
                        if (isRam(dst)) {
                            instructions.set(i, new Movsx(srcType, dstType,
                                    src, R11));
                            instructions.add(i + 1, new Mov(dstType, R11, dst));
                        }
                    }
                }
                case Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) -> {
                    if (srcType.isInteger()) {
                        if (src instanceof Imm) {
                            instructions.set(i, new Mov(srcType, src, R10));
                            if (isRam(dst)) {
                                instructions.add(i + 1, new Cvt(srcType, dstType, R10
                                        , XMM15));
                                instructions.add(i + 2, new Mov(QUADWORD, XMM15,
                                        dst));
                            } else {
                                instructions.add(i + 1, new Cvt(srcType, dstType, R10, dst));
                            }
                        } else if (isRam(dst)) {
                            instructions.set(i, new Cvt(srcType, dstType, src, XMM15));
                            instructions.add(i + 1, new Mov(QUADWORD, XMM15, dst));
                        }
                    } else if (isRam(dst)) {
                        instructions.set(i, new Cvt(srcType, dstType, src, dstReg(dstType)));
                        instructions.add(i + 1, new Mov(dstType, dstReg(dstType), dst));
                    }
                }
                default -> {
                }
            }
        }
    }

    private static Operand dstReg(TypeAsm typeAsm) {
        return typeAsm == DOUBLE || typeAsm == FLOAT ? XMM15 : R11;
    }

    private static Operand srcReg(TypeAsm typeAsm) {
        return typeAsm == DOUBLE || typeAsm == FLOAT ? XMM14 : R10;
    }

    private static TypeAsm valToAsmType(ValIr val) {
        return toTypeAsm(type(val));
    }

    public static TypeAsm toTypeAsm(Type type) {
        return switch (type) {
            case Primitive.CHAR, Primitive.UCHAR, Primitive.SCHAR, Primitive.BOOL,
            // it's easier to just pretend it's a byte than to actually discard these here
            // the optimizer should eliminate them anyway
                 Primitive.VOID -> BYTE;
            case Primitive.SHORT, Primitive.USHORT -> WORD;
            case Primitive.INT, Primitive.UINT -> LONGWORD;
            case Primitive.LONG, Primitive.ULONG, Primitive.LONGLONG, Primitive.ULONGLONG  -> QUADWORD;
            case Primitive.DOUBLE -> DOUBLE;
            case Primitive.FLOAT -> FLOAT;
            case Pointer _, FunType _ -> QUADWORD;
            case Array _, Structure _ ->
                    new ByteArray((int) size(type),
                            variableAlignment(type));
            case WidthRestricted(Type element, int _) -> toTypeAsm(element);
            case Aligned(Type inner, Exp _) -> toTypeAsm(inner);
            default ->
                    throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private static boolean isRam(Operand src) {
        return src instanceof Memory || src instanceof Indexed || src instanceof Data;
    }

    public static Data resolveConstantDouble(double d) {
        String k="c." + doubleToHexString(d);
        StaticConstant c = CONSTANT_TABLE.computeIfAbsent(k,
                _ -> new StaticConstant(k,
                        /* -0.0 is 16 byte aligned because it is used in xorpd*/
                        d == -0.0 ? 16 : 8,
                        new DoubleInit(d)));
        return new Data(c.label(), 0);
    }

    public static Data resolveConstantFloat(float f) {
        String k = "f." + floatToHexString(f);
        StaticConstant c = CONSTANT_TABLE.computeIfAbsent(k,
                _ -> new StaticConstant(k,
                        /* -0.0 is 16 byte aligned because it is used in xorps*/
                        f == -0.0f ? 16 : 8,
                        new FloatInit(f)));
        return new Data(c.label(), 0);
    }

    private static Operand toOperand(ValIr val) {
        return switch (val) {
            case null -> null;
            case CharInit(byte i) -> new Imm(i);
            case UCharInit(byte i) -> new Imm(i & 0xff);
            case BoolInit(byte i) -> new Imm(i & 0xff);
            case ShortInit(short i) -> new Imm(i);
            case UShortInit(short i) -> new Imm(i & 0xffff);
            case IntInit(int i) -> new Imm(i);
            case VarIr(String identifier) -> {
                Type t = type(val);
                var ste = SYMBOL_TABLE.get(identifier);
                int alignment = 0;
                if (t instanceof Aligned(Type inner, Exp a)) {
                    alignment =
                            (int) SemanticAnalysis.evaluateExpAsConstant(a).toLong();
                    t = inner;
                }
                if (t instanceof Array) {
                    yield new PseudoMem(identifier, 0, alignment);
                }
                if (t instanceof Structure(boolean isUnion, String tag, StructDef structDef)) {
                    yield new PseudoMem(identifier, 0, TYPE_TABLE.get(tag).alignment());
                }
                if (t instanceof FunType && ste.attrs() instanceof FunAttributes) {
                    yield new LabelAddress(identifier);
                }
                var p =
                        new Pseudo(identifier, toTypeAsm(t), ste.isStatic(),
                                ste.aliased);
                p.alignment = alignment;
                yield p;
            }
            case LongInit(long l) -> new Imm(l);
            case LongLongInit(long l) -> new Imm(l);
            case UIntInit(int i) -> new Imm(Integer.toUnsignedLong(i));
            case ULongInit(long l) -> new Imm(l);
            case ULongLongInit(long l) -> new Imm(l);
            case DoubleInit(double d) -> resolveConstantDouble(d);
            case FloatInit(float d) -> resolveConstantFloat(d);
            default ->
                    throw new IllegalStateException("Unexpected value: " + val);
        };
    }

    private static Operand toOperand(ValIr val, int offset) {
        return switch(val){
            case VarIr(String identifier)  ->
                    new PseudoMem(identifier, offset);
            default -> throw new AssertionError(val);
        };

    }


    /* MR-TODO there is a big mess here:
    * this function can't tell when a PseudoMem with offset 0 is the first byte
    * of an aggregate or if it is a pointer
    * */
    private static Operand dePseudo(Operand in, Map<String, Long> varTable,
                                    AtomicLong offsetA, FunctionAsm functionAsm) {
        long offsetFromStartOfArray;
        String identifier;
        int alignment = 0;
        switch (in) {
            case Imm _, IntegerReg _, Memory _, DoubleReg _, Data _, Indexed _, LabelAddress _:
                return in;
            case Pseudo p: {
                identifier = p.identifier;
                offsetFromStartOfArray = 0;
                alignment = p.alignment;
                break;
            }
            case PseudoMem p:
                identifier = p.identifier();
                alignment=p.alignment();
                offsetFromStartOfArray = p.offset();
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (BACKEND_SYMBOL_TABLE.get(identifier) instanceof ObjEntry(
                TypeAsm type, boolean isStatic, boolean _)) {
            assert (!(in instanceof Pseudo p) || type.size() == p.type.size());

            long size = type.size();
            if (alignment == 0)
                alignment = type.alignment();


            if (isStatic) return new Data(identifier, offsetFromStartOfArray);
            if (alignment > 16 && alignment > functionAsm.stackAlignment)
                functionAsm.stackAlignment = alignment;

            Long varOffset = varTable.get(identifier);
            if (varOffset == null) {
                // it starts ar -8 - we can use this for the first var
                // when that var is written it will update bytes stack-8 to
                // stack-1
                varOffset = offsetA.get();
                long remainder = varOffset % alignment;
                if (remainder != 0) {
                    varOffset += (alignment - remainder);
                }
                varTable.put(identifier, varOffset);
                var r = new Memory(SP, varOffset + offsetFromStartOfArray);
                varOffset += size;
                offsetA.set(varOffset);
                return r;
            }
            return new Memory(SP, varOffset + offsetFromStartOfArray);

        } else throw new IllegalArgumentException(identifier);
    }

    private static boolean classifyReturnValueLight(Type t) {
        if (t == Primitive.VOID || t.isScalar()) {
            return false;
        } else {
            Structure st = (Structure) t;
            List<StructureType> classes = classifyStructure(st);
            return classes.getFirst() == StructureType.MEMORY;
        }
    }


    private static ReturnValueClassification classifyReturnValue(ValIr retVal) {
        TypeAsm t = valToAsmType(retVal);
        Operand v = toOperand(retVal);
        if (t == DOUBLE || t == FLOAT) {
            return new ReturnValueClassification(Collections.emptyList(),
                    Collections.singletonList(v), false);
        } else if (t.isScalar()) {
            return new ReturnValueClassification(Collections.singletonList(new TypedOperand(t, v)), Collections.emptyList(), false);
        } else {
            String nameOfRetVal = ((VarIr) retVal).identifier();
            Structure st = (Structure) SYMBOL_TABLE.get(nameOfRetVal).type();
            List<StructureType> classes = classifyStructure(st);

            if (classes.getFirst() == StructureType.MEMORY) {
                return new ReturnValueClassification(Collections.emptyList(),
                        Collections.emptyList(), true);
            } else {
                List<TypedOperand> intDests = new ArrayList<>();
                List<Operand> doubleDests = new ArrayList<>();
                int structSize = (int) size(st);
                int offset = 0;
                for (var c : classes) {
                    var operand = new PseudoMem(nameOfRetVal, offset);
                    switch (c) {
                        case SSE -> doubleDests.add(operand);
                        case INTEGER ->
                                intDests.add(new TypedOperand(getEightbyteType(offset, structSize), operand));
                        case MEMORY -> throw new AssertionError();
                    }
                    offset += 8;
                }
                return new ReturnValueClassification(intDests, doubleDests,
                        false);
            }
        }

    }


    private static void codegenFunCall(FunCall funCall,
                                       List<Instruction> instructionAsms) {
        // so for classify we can classify operands here
        if (funCall instanceof FunCall(VarIr name, ArrayList<ValIr> args,
                                       boolean varargs, _, ValIr dst)) {

            final boolean returnInMemory;
            List<TypedOperand> intDests = Collections.emptyList();
            List<Operand> doubleDests = Collections.emptyList();
            int regIndex = 0;
            if (dst != null) {
                ReturnValueClassification r = classifyReturnValue(dst);
                intDests = r.intDests;
                doubleDests = r.doubleDests;
                returnInMemory = r.returnInMemory;
            } else {
                returnInMemory = false;
            }

            if (returnInMemory) {
                var dstOperand = toOperand(dst);
                instructionAsms.add(new Lea(dstOperand, DI));
                regIndex = 1;
            }

            final List<TypedOperand> operands = new ArrayList<>();
            for (ValIr arg : args) {
                TypeAsm typeAsm = valToAsmType(arg);
                Operand operand = toOperand(arg);
                operands.add(new TypedOperand(typeAsm, operand));
            }
            ParameterClassification classifiedArgs =
                    classifyParameters(operands, returnInMemory);
            PARAMETER_CLASSIFICATION_MAP.put(funType(name), classifiedArgs);
            ArrayList<TypedOperand> integerArguments =
                    classifiedArgs.integerArguments();
            ArrayList<Operand> doubleArguments =
                    classifiedArgs.doubleArguments();
            ArrayList<TypedOperand> stackArguments =
                    classifiedArgs.stackArguments();
            int stackArgCount = stackArguments.size();
            int stackPadding = stackArgCount % 2 == 1 ? 8 : 0;
            if (stackPadding != 0) {
                instructionAsms.add(new Binary(SUB, QUADWORD,
                        new Imm(stackPadding), SP));
            }
            int correction = stackPadding;


            //pass args in registers
            for (TypedOperand integerArg : integerArguments) {
                var assemblyType = integerArg.type();

                IntegerReg r = INTEGER_REGISTERS[regIndex];
                if (assemblyType instanceof ByteArray(long size, _)) {
                    copyBytesToReg(instructionAsms, integerArg.operand(), r,
                            size);
                } else {
                    instructionAsms.add(new Mov(assemblyType,
                            integerArg.operand(), r));
                }
                regIndex++;
            }
            for (int i = 0; i < doubleArguments.size(); i++) {
                Operand doubleArg = doubleArguments.get(i);
                DoubleReg r = DOUBLE_REGISTERS[i];
                instructionAsms.add(new Mov(DOUBLE, doubleArg, r));
            }
            for (int i = stackArguments.size() - 1; i >= 0; i--) {
                TypedOperand to = stackArguments.get(i);
                TypeAsm assemblyType = to.type();
                Operand operand = to.operand();
                if (assemblyType instanceof ByteArray(long size, _)) {
                    instructionAsms.add(new Binary(SUB, QUADWORD, new Imm(8),
                            SP));
                    copyBytes(instructionAsms, operand, new Memory(SP, 0),
                            size);
                } else if (operand instanceof Imm ||
                        operand instanceof IntegerReg ||
                        assemblyType == QUADWORD || assemblyType == DOUBLE) {
                    instructionAsms.add(new Push(operand));
                    correction += 8;
                } else {
                    instructionAsms.add(new Mov(assemblyType, operand, AX));
                    instructionAsms.add(new Push(AX));
                    correction += 8;
                }
            }
            if (varargs) {
                instructionAsms.add(new Mov(LONGWORD,
                        new Imm(doubleArguments.size()), AX));
            }
            instructionAsms.add(new Call(toOperand(name), funType(name)));
            int bytesToRemove = 8 * stackArgCount + stackPadding;
            if (bytesToRemove != 0) {
                instructionAsms.add(new Binary(ADD, QUADWORD,
                        new Imm(bytesToRemove), SP));
            }
            //retrieve return value
            if (dst != null && !returnInMemory) {// dst is null for void
                // functions
                // TypeAsm returnType = valToAsmType(dst);
                //   instructionAsms.add(new Mov(returnType, returnType ==
                //   DOUBLE ? XMM0 : AX, toOperand(dst)));
                regIndex = 0;
                for (var intDest : intDests) {
                    TypeAsm t = intDest.type();
                    var op = intDest.operand();
                    IntegerReg r = INTEGER_RETURN_REGISTERS[regIndex];
                    if (t instanceof ByteArray(long size, _)) {
                        copyBytesFromReg(instructionAsms, r, op, size);
                    } else {
                        instructionAsms.add(new Mov(t, r, op));
                    }
                    regIndex++;
                }
                regIndex = 0;
                for (var op : doubleDests) {
                    DoubleReg r = DOUBLE_REGISTERS[regIndex];
                    instructionAsms.add(new Mov(DOUBLE, r, op));
                    regIndex++;
                }
            }
        }
    }

//    /**
//     * When pushing operands onto the stack for a function call it messes up our
//     * local variables because they are offsets from SP - this function adjusts them
//     * so the correct values can be pushed
//     * */
//    private static Operand translate(Operand operand, int bytesToAdd) {
//        if (operand instanceof Memory(IntegerReg reg, long offset) && reg == SP) {
//            return new Memory(SP, offset+bytesToAdd);
//        } else if (operand instanceof PseudoMem(String identifier, long offset, int alignment)) {
//            return new PseudoMem(identifier, offset+bytesToAdd, alignment);
//        } else if (operand instanceof Pseudo p) {
//            return new PseudoMem(p.identifier, bytesToAdd, p.alignment);
//        }
//        return operand;
//    }

    private static void copyBytesFromReg(List<Instruction> ins,
                                         IntegerReg srcReg, Operand dstOp,
                                         long byteCount) {
        long offset = 0;
        while (offset < byteCount) {
            Operand dstByte = dstOp.plus(offset);
            ins.add(new Mov(BYTE, srcReg, dstByte));
            if (offset < byteCount - 1)
                ins.add(new Binary(SHR, QUADWORD, new Imm(8)
                        , srcReg));
            offset++;
        }

    }

    private static void copyBytesToReg(List<Instruction> ins, Operand srcOp,
                                       IntegerReg dstReg, long byteCount) {
        long offset = byteCount - 1;
        while (offset >= 0) {
            Operand srcByte = srcOp.plus(offset);
            ins.add(new Mov(BYTE, srcByte, dstReg));
            if (offset > 0)
                ins.add(new Binary(SHL, QUADWORD, new Imm(8), dstReg));
            offset--;
        }

    }

    public static FunctionAsm convertFunction(FunctionIr functionIr) {
        ReturnValueClassification returnValueClassification = null;
        // here we can convert arguments to pseudos (which are operands)
        List<InstructionIr> instructionIrs = functionIr.instructions();
        Set<VarIr> aliasedVars = Optimizer.addressTakenAnalysis(instructionIrs);
        for (var v : aliasedVars) {
            setAliased(v.identifier());
        }
        List<Var> functionType = functionIr.type();
        List<TypedOperand> operands = new ArrayList<>();
        for (Var param : functionType) {
            var t = toTypeAsm(param.type());
            String name = param.name();
            operands.add(new TypedOperand(t, new Pseudo(name, t, false,
                    SYMBOL_TABLE.get(name).aliased)));
        }

        boolean returnInMemory =
                classifyReturnValueLight(functionIr.returnType());
        int regIndex = 0;
        List<Instruction> ins = new ArrayList<>();

        if (returnInMemory) {
            ins.add(new Mov(QUADWORD, DI, new Memory(BP, -8)));
            regIndex = 1;
        }

        ParameterClassification classifiedParameters =
                classifyParameters(operands, returnInMemory);
        PARAMETER_CLASSIFICATION_MAP.put(functionIr.funType(),
                classifiedParameters);

        ArrayList<TypedOperand> integerArguments =
                classifiedParameters.integerArguments();
        ArrayList<Operand> doubleArguments =
                classifiedParameters.doubleArguments();
        ArrayList<TypedOperand> stackArguments =
                classifiedParameters.stackArguments();

        for (TypedOperand to : integerArguments) {
            TypeAsm paramType = to.type();
            if (paramType instanceof ByteArray(long size, _)) {
                copyBytesFromReg(ins, INTEGER_REGISTERS[regIndex],
                        to.operand(), size);
            } else
                ins.add(new Mov(to.type(), INTEGER_REGISTERS[regIndex],
                        to.operand()));
            regIndex++;
        }

        for (int i = 0; i < doubleArguments.size(); i++) {
            Operand operand = doubleArguments.get(i);
            ins.add(new Mov(DOUBLE, DOUBLE_REGISTERS[i], operand));
        }
        int offset = 16;
        for (int i = 0; i < stackArguments.size(); i++) {
            TypedOperand to = stackArguments.get(i);
            TypeAsm paramType = to.type();
            if (paramType instanceof ByteArray(long size, _)) {
                Operand src = new Memory(BP, offset);
                copyBytes(ins, src, to.operand(), size);
            } else
                ins.add(new Mov(to.type(), new Memory(BP, 16 + i * 8L),
                        to.operand()));
            offset += 8;
        }
        // so I guess we now have overflow_arg_area = Memory(BP, offset)
        for (InstructionIr inst : instructionIrs) {
            switch (inst) {
                case AddPtr(ValIr ptrV, ValIr indexV, int scale,
                            ValIr dstV) -> {
                    Operand ptr = toOperand(ptrV);
                    Operand index = toOperand(indexV);
                    Operand dst = toOperand(dstV);
                    ins.add(new Mov(QUADWORD, ptr, AX));
                    if (index instanceof Imm(long l)) {
                        ins.add(new Lea(new Memory(AX, l * scale), dst));
                    } else {
                        ins.add(new Mov(QUADWORD, index, DX));
                        switch (scale) {
                            case 1, 2, 4, 8 ->
                                    ins.add(new Lea(new Indexed(AX, DX,
                                            scale), dst));
                            default -> {
                                ins.add(new Binary(IMUL, QUADWORD,
                                        new Imm(scale), DX));
                                ins.add(new Lea(new Indexed(AX, DX, 1), dst));
                            }
                        }
                    }
                }

                case BinaryWithOverflowIr(ArithmeticOperator op1, ValIr v1, ValIr v2, ValIr v3,
                              VarIr dstName) -> {
                    Type inputType = type(v1);
                    Type outputType = type(v3);
                    if (outputType instanceof Pointer(Type referenced))
                        outputType = referenced;
                    TypeAsm srcTypeAsm = toTypeAsm(inputType);
                    TypeAsm dstTypeAsm = toTypeAsm(outputType);

                    // do the arithmatic in DX
                    ins.add(new Mov(srcTypeAsm, toOperand(v1),
                            DX));
                    ins.add(new Binary(
                            convertOp(op1, srcTypeAsm),
                            srcTypeAsm, toOperand(v2),
                            DX));



                    Operand dst = toOperand(dstName);
                    LabelIr label1 = newLabel(makeTemporary(".Lno."));
                    ins.add(new Mov(BYTE, Imm.ZERO, dst));

                    ins.add(new JmpCC(CC.NO, label1.label()));
                    ins.add(new Mov(BYTE, Imm.ONE, dst));
                    ins.add(label1);
                    // if we have to downcast and result can't fit then that is overflow
                    if (size(outputType) < size(inputType)) {
                        LabelIr label2 = newLabel(makeTemporary(".Lno."));
                        ins.add(new Movsx(dstTypeAsm, srcTypeAsm, DX, AX));
                        ins.add(new Cmp(QUADWORD, DX, AX));
                        ins.add(new JmpCC(CC.E, label2.label()));
                        ins.add(new Mov(BYTE, Imm.ONE, dst));
                        ins.add(label2);
                    }
                    ins.add(new Mov(QUADWORD, toOperand(v3), AX));
                    ins.add(new Mov(toTypeAsm(outputType), DX, new Memory(AX, 0)));

                }
                case BinaryIr(ArithmeticOperator op1, ValIr v1, ValIr v2,
                              VarIr dstName) -> {
                    Type type;// v1 might
                    if (v1 == null) {
                        type = type(v2);} else {
                        type = type(v1);
                    }
                    // be null because of VOID v1 in COMMA op
                    TypeAsm typeAsm = toTypeAsm(type);
                    if (typeAsm == DOUBLE || typeAsm == FLOAT) {
                        if (op1 == COMMA) {
                            ins.add(new Mov(typeAsm, toOperand(v2),
                                    toOperand(dstName)));
                        } else {
                            ins.add(new Mov(typeAsm, toOperand(v1),
                                    toOperand(dstName)));
                            ins.add(new Binary(convertOp(op1, typeAsm),
                                    typeAsm, toOperand(v2),
                                    toOperand(dstName)));
                        }
                    } else {
                        switch (op1) {
                            case SHL, SAR, SHR -> {
                                ins.add(new Mov(typeAsm, toOperand(v1),
                                        toOperand(dstName)));
                                // v1 is what we're shifting, v2 is how much
                                // to shift
                                ins.add(new Mov(BYTE, toOperand(v2), CX));
                                ins.add(new Binary(op1, typeAsm, CX,
                                        toOperand(dstName)));
                            }
                            case ADD, SUB, IMUL, BITWISE_AND, BITWISE_XOR,
                                 BITWISE_OR -> {
                                ins.add(new Mov(typeAsm, toOperand(v1),
                                        toOperand(dstName)));
                                ins.add(new Binary(op1, typeAsm,
                                        toOperand(v2), toOperand(dstName)));
                            }
                            case COMMA -> {
                                if (v2 != null) { // comma operator can have VOID type
                                    ins.add(new Mov(typeAsm, toOperand(v2), toOperand(dstName)));
                                }
                            }
                            case DIVIDE, REMAINDER -> {
                                if (type.isSigned()) {
                                    ins.add(new Mov(typeAsm, toOperand(v1),
                                            AX));
                                    ins.add(new Cdq(typeAsm));
                                    ins.add(new Unary(UnaryOperator.IDIV,
                                            typeAsm, toOperand(v2)));
                                    ins.add(new Mov(typeAsm, op1 == DIVIDE ?
                                            AX : DX, toOperand(dstName)));
                                } else {
                                    ins.add(new Mov(typeAsm, toOperand(v1),
                                            AX));
                                    ins.add(new Mov(typeAsm, new Imm(0), DX));
                                    ins.add(new Unary(UnaryOperator.DIV,
                                            typeAsm, toOperand(v2)));
                                    ins.add(new Mov(typeAsm, op1 == DIVIDE ?
                                            AX : DX, toOperand(dstName)));
                                }
                            }
                            default ->
                                    throw new IllegalStateException(
                                            "Unexpected value: " + op1);
                        }
                    }
                }
                case BinaryIr(CmpOperator op1, ValIr v1, ValIr v2,
                              VarIr dstName) -> {
                    Type type = type(v1);
                    TypeAsm typeAsm = toTypeAsm(type);
                    assert (typeAsm == valToAsmType(v2));

                    // dstName will hold the result of the comparison, which
                    // is always a LONGWORD

                    if (typeAsm == DOUBLE) {
                        Operand subtrahend = toOperand(v2);
                        Operand minuend = toOperand(v1);
                        Operand dst = toOperand(dstName);
                        compareDouble(op1, typeAsm, subtrahend, minuend, dst,
                                ins);
                    } else {
                        ins.add(new Cmp(typeAsm, toOperand(v2), toOperand(v1)));
                        ins.add(new Mov(LONGWORD, Imm.ZERO,
                                toOperand(dstName)));
                        ins.add(new SetCC(op1,
                                type.unsignedOrDoubleOrPointer(),
                                toOperand(dstName)));
                    }
                }
                case Compare(Type type, ValIr v1, ValIr v2) -> {
                    TypeAsm typeAsm = toTypeAsm(type);
                    ins.add(new Cmp(typeAsm, toOperand(v2), toOperand(v1)));
                }
                case Copy(ValIr srcV, VarIr dstV) -> {
                    Operand src = toOperand(srcV);
                    Operand dst = toOperand(dstV);
                    TypeAsm typeAsm = valToAsmType(dstV);
                    assert (valToAsmType(dstV).equals(typeAsm));
                    if (typeAsm instanceof ByteArray(long size, _)) {
                        copyBytes(ins, src, dst, size);
                    } else ins.add(new Mov(typeAsm, src, dst));
                }
                case CopyToOffset(ValIr srcV, VarIr dstV, long offset1) -> {
                    Operand src = toOperand(srcV);
                    Operand dst = toOperand(dstV, (int) offset1);
                    TypeAsm typeAsm = valToAsmType(srcV);
                    if (typeAsm instanceof ByteArray(long size, _)) {
                        copyBytes(ins, src, dst, size);
                    } else ins.add(new Mov(typeAsm, src, dst));
                }
                case Memset(VarIr dstV, int c,
                            long byteCount, boolean viaPointer) -> {
                    Operand dst = toOperand(dstV);
                    if (!viaPointer && dst instanceof PseudoMem(String identifier, long offset1, int alignment) && offset1==0L) {
                        SymbolTableEntry e = Mcc.SYMBOL_TABLE.get(identifier);
                        dst = new Pseudo(identifier, toTypeAsm(e.type()), e.isStatic(), e.aliased, alignment);
                    }
                    memsetBytes(ins, c, dst, byteCount, viaPointer);
                }

                case DoubleToInt(ValIr src, VarIr dst) -> {
                    var dstType = type(dst);
                    var dstTypeAsm = toTypeAsm(dstType);
                    if (dstType == Primitive.CHAR || dstType == Primitive.SCHAR) {
                        ins.add(new Cvt(valToAsmType(src), LONGWORD, toOperand(src), AX));
                        ins.add(new Mov(BYTE, AX, toOperand(dst)));
                    } else
                        ins.add(new Cvt(valToAsmType(src), dstTypeAsm, toOperand(src),
                                toOperand(dst)));
                }
                case DoubleToUInt(ValIr src, ValIr dst) -> {
                    Type dstType = type(dst);
                    switch (dstType) {
                        case Primitive.UCHAR -> {
                            ins.add(new Cvt(valToAsmType(src), LONGWORD, toOperand(src), AX));
                            ins.add(new Mov(BYTE, AX, toOperand(dst)));
                        }
                        case Primitive.USHORT -> {
                            ins.add(new Cvt(valToAsmType(src), LONGWORD, toOperand(src), AX));
                            ins.add(new Mov(WORD, AX, toOperand(dst)));
                        }
                        case Primitive.UINT -> {
                            ins.add(new Cvt(valToAsmType(src), QUADWORD, toOperand(src), AX));
                            ins.add(new Mov(LONGWORD, AX, toOperand(dst)));
                        }
                        case null, default -> {
                            TypeAsm srcAsmType = valToAsmType(src);
                            //p.335
                            LabelIr label1 =
                                    newLabel(makeTemporary(".Laub."));
                            LabelIr label2 = newLabel(makeTemporary(
                                    ".LendCmp" + "."));
                            var UPPER_BOUND = resolveConstantDouble(0x1.0p63);
                            ins.add(new Cmp(DOUBLE, UPPER_BOUND, toOperand(src)));
                            ins.add(new OldJmpCC(CmpOperator.GREATER_THAN_OR_EQUAL, true, label1.label()).toJmpCC2());
                            ins.add(new Cvt(srcAsmType, QUADWORD, toOperand(src), toOperand(dst)));
                            ins.add(new Jump(label2.label()));
                            ins.add(label1);
                            ins.add(new Mov(DOUBLE, toOperand(src), XMM0));
                            ins.add(new Binary(DOUBLE_SUB, DOUBLE, UPPER_BOUND, XMM0));
                            ins.add(new Cvt(srcAsmType, QUADWORD, XMM0, toOperand(dst)));
                            ins.add(new Mov(QUADWORD, UPPER_BOUND_LONG_IMMEDIATE, AX));
                            ins.add(new Binary(ADD, QUADWORD, AX, toOperand(dst)));
                            ins.add(label2);
                        }
                    }
                }
                case FunCall funCall -> // check if we can inline
                        codegenFunCall(funCall, ins);
                case GetAddress(ValIr srcV, VarIr dstV) -> {
                    Operand src = toOperand(srcV);
                    Operand dst = toOperand(dstV);
                    if (src instanceof LabelAddress)
                        ins.add(new Mov(QUADWORD, src, dst));
                    else
                        ins.add(new Lea(src, dst));
                }
                case IntToDouble(ValIr src, VarIr dst) -> {
                    Type srcType = type(src);
                    var srcTypeAsm = toTypeAsm(srcType);
                    var dstTypeAsm = valToAsmType(dst);
                    if (srcType == Primitive.CHAR || srcType == Primitive.SCHAR) {
                        ins.add(new Movsx(BYTE, LONGWORD, toOperand(src), AX));
                        ins.add(new Cvt(LONGWORD, dstTypeAsm, AX, toOperand(dst)));
                    } else
                        ins.add(new Cvt(srcTypeAsm, dstTypeAsm,
                                toOperand(src), toOperand(dst)));
                }
                case Jump jump -> ins.add(jump);
                case JumpIfNotZero(ValIr v, String label) -> {
                    Type type = type(v);
                    TypeAsm typeAsm = toTypeAsm(type);
                    if (typeAsm == DOUBLE) {
                        ins.add(new Binary(BITWISE_XOR, typeAsm, XMM0, XMM0));
                        ins.add(new Cmp(typeAsm, XMM0, toOperand(v)));
                    } else {
                        ins.add(new Cmp(typeAsm, new Imm(0), toOperand(v)));
                    }
                    ins.add(new OldJmpCC(NOT_EQUALS,
                            type.unsignedOrDoubleOrPointer(), label).toJmpCC2());
                    if (typeAsm == DOUBLE) // v is NaN which is not equal to
                        // zero (but jne treats it like it is)
                        ins.add(new OldJmpCC(null, true, label).toJmpCC2());
                }
                case JumpIfZero(ValIr v, String label) -> {
                    if (v != null) {
                        Type type = type(v);
                        TypeAsm typeAsm = toTypeAsm(type);
                        if (typeAsm == DOUBLE) {
                            ins.add(new Binary(BITWISE_XOR, typeAsm, XMM0,
                                    XMM0));
                            ins.add(new Cmp(typeAsm, XMM0, toOperand(v)));
                            LabelIr endLabel = newLabel(makeTemporary(
                                    ".Lend."));
                            ins.add(newJmpCC(null, true, endLabel.label()));

                            ins.add(newJmpCC(EQUALS, true, label));
                            ins.add(endLabel);
                        } else {
                            ins.add(new Cmp(typeAsm, new Imm(0), toOperand(v)));
                            ins.add(newJmpCC(EQUALS,
                                    type.unsignedOrDoubleOrPointer(), label));
                        }


                    } else {
                        ins.add(newJmpCC(EQUALS, false, label));
                    }

                }
                case LabelIr labelIr -> ins.add(labelIr);
                case Load(ValIr ptrV, VarIr dstV) -> {
                    Operand ptr = toOperand(ptrV);
                    Operand dst = toOperand(dstV);
                    TypeAsm dstType = toTypeAsm(type(dstV));
                    ins.add(new Mov(QUADWORD, ptr, AX));
                    if (dstType instanceof ByteArray(long size, _)) {
                        Operand src = new Memory(AX, 0);
                        copyBytes(ins, src, dst, size);
                    } else {
                        ins.add(new Mov(dstType, new Memory(AX, 0), dst));
                    }
                }
                case ReturnIr(ValIr val) ->
                        returnValueClassification = convertReturn(val, ins);
                case SignExtendIr(ValIr src, VarIr dst) ->
                        ins.add(new Movsx(valToAsmType(src),
                                valToAsmType(dst), toOperand(src),
                                toOperand(dst)));
                case Store(ValIr srcV, ValIr ptrV) -> {
                    Operand src = toOperand(srcV);
                    Operand ptr = toOperand(ptrV);
                    ins.add(new Mov(QUADWORD, ptr, AX));
                    TypeAsm srcType = toTypeAsm(type(srcV));
                    if (srcType instanceof ByteArray(long size, _)) {
                        Operand dst = new Memory(AX, 0);
                        copyBytes(ins, src, dst, size);
                    } else {
                        ins.add(new Mov(srcType, src, new Memory(AX, 0)));
                    }
                }
            case AtomicStore(ValIr srcV, ValIr ptrV, MemoryOrder memOrder) -> {
                Operand src = toOperand(srcV);
                Operand ptr = toOperand(ptrV);
                ins.add(new Mov(QUADWORD, ptr, AX));
                TypeAsm srcType = toTypeAsm(type(srcV));
                switch (memOrder) {
                    case ACQ_REL, SEQ_CST -> {
                        ins.add(new Xchg(srcType, src, DX));
                        ins.add(new Xchg(srcType, new Memory(AX, 0), DX));
                    }
                    default -> ins.add(new Mov(srcType, src, new Memory(AX, 0)));
                }
            }
                case TruncateIr(ValIr srcV, VarIr dstV) -> {
                    var src = toOperand(srcV);
                    var dst = toOperand(dstV);
                    Type dstType = type(dstV);
                    if (dstType == Primitive.BOOL){
                        TypeAsm srcAsmType = toTypeAsm(type(srcV));
                        ins.add(new Cmp(srcAsmType, new Imm(0), src));
                        ins.add(new SetCC(NOT_EQUALS, true, dst));
                    }else {
                        TypeAsm targetType = toTypeAsm(dstType);
                        ins.add(new Mov(targetType, src, dst));
                    }
                }
                case FloatToDouble(ValIr srcV, ValIr dstV) -> {
                    var src = toOperand(srcV);
                    var dst = toOperand(dstV);
                    ins.add(new Cvt(FLOAT, DOUBLE, src, dst));
                }
                case DoubleToFloat(ValIr srcV, ValIr dstV) -> {
                    var src = toOperand(srcV);
                    var dst = toOperand(dstV);
                    ins.add(new Cvt(DOUBLE, FLOAT, src, dst));
                }
                case UIntToDouble(ValIr srcV, ValIr dstV) -> {
                    var src = toOperand(srcV);
                    var dst = toOperand(dstV);
                    Type srcType = Mcc.type(srcV);
                    TypeAsm dstType = valToAsmType(dstV);
                    switch (srcType) {
                        case Primitive.UCHAR -> {
                            ins.add(new MovZeroExtend(BYTE, LONGWORD, src, AX));
                            ins.add(new Cvt(LONGWORD, dstType, AX, dst));
                        }
                        case Primitive.CHAR, Primitive.SCHAR -> {
                            ins.add(new Movsx(BYTE, LONGWORD, src, AX));
                            ins.add(new Cvt(LONGWORD, dstType, AX, dst));
                        }
                        case Primitive.INT -> {
                            ins.add(new Movsx(valToAsmType(srcV), valToAsmType(dstV), src, AX));
                            ins.add(new Cvt(QUADWORD, dstType, AX, dst));
                        }
                        case Primitive.UINT -> {
                            ins.add(new MovZeroExtend(valToAsmType(srcV), valToAsmType(dstV), src, AX));
                            ins.add(new Cvt(QUADWORD, dstType, AX, dst));
                        }
                        case null, default -> {
                            // see description on p. 320
                            LabelIr label1 =
                                    newLabel(makeTemporary(".LoutOfRange."));
                            LabelIr label2 =
                                    newLabel(makeTemporary(".Lend."));
                            var asmSrcType = srcType ==
                                    Primitive.UINT ? LONGWORD : QUADWORD;
                            ins.add(new Cmp(QUADWORD, new Imm(0), src));
                            ins.add(newJmpCC(CmpOperator.LESS_THAN, false, label1.label()));
                            ins.add(new Cvt(asmSrcType, dstType, src, dst));
                            ins.add(new Jump(label2.label()));
                            ins.add(label1);
                            ins.add(new Mov(asmSrcType, src, AX));
                            ins.add(new Mov(QUADWORD, AX, DX));
                            ins.add(new Unary(UNARY_SHR, QUADWORD, DX));
                            ins.add(new Binary(AND, QUADWORD, new Imm(1), AX));
                            ins.add(new Binary(OR, QUADWORD, AX, DX));
                            ins.add(new Cvt(QUADWORD, dstType, DX, dst));
                            ins.add(new Binary(DOUBLE_ADD, DOUBLE, dst, dst));
                            ins.add(label2);
                        }
                    }
                }
                case UnaryIr(UnaryOperator op1, ValIr srcIr, ValIr dstIr) -> {
                    Operand dst1 = toOperand(dstIr);
                    Operand src1 = toOperand(srcIr);
                   // Type type = valToType(dstIr);
                   // TypeAsm typeAsm = toTypeAsm(type);
                    if (op1 == UnaryOperator.NOT) {
                        Type srcType = type(srcIr);
                        TypeAsm srcTypeAsm = toTypeAsm(srcType);
                        if (srcTypeAsm == DOUBLE) {
                            ins.add(new Binary(BITWISE_XOR, DOUBLE, XMM0,
                                    XMM0));
                            compareDouble(EQUALS, DOUBLE, XMM0, src1, dst1,
                                    ins);
                        } else {
                            ins.add(new Cmp(srcTypeAsm, new Imm(0), src1));
                            ins.add(new Mov(valToAsmType(dstIr), new Imm(0),
                                    dst1));
                            ins.add(new SetCC(EQUALS,
                                    type(dstIr).unsignedOrDoubleOrPointer(), dst1));
                        }
                    } else {
                        Type type = type(dstIr);
                        TypeAsm typeAsm = toTypeAsm(type);
                        if (op1 == UnaryOperator.UNARY_MINUS &&
                                typeAsm == DOUBLE) {
                            ins.add(new Mov(typeAsm, src1, dst1));
                            var NEGATIVE_ZERO = resolveConstantDouble(-0.0);
                            ins.add(new Binary(BITWISE_XOR, typeAsm, NEGATIVE_ZERO, dst1));
                        } else if (op1 == UnaryOperator.UNARY_MINUS &&
                                typeAsm == FLOAT) {
                            ins.add(new Mov(typeAsm, src1, dst1));
                            var NEGATIVE_ZERO = resolveConstantFloat(-0.0F);
                            ins.add(new Binary(BITWISE_XOR, typeAsm, NEGATIVE_ZERO, dst1));
                        } else if (op1 == UnaryOperator.CLZ) {
                            ins.add(new Mov(typeAsm, src1, dst1));
                            ins.add(new Binary(BSR, typeAsm, dst1, dst1));
                            long bits = typeAsm.size() * 8 - 1;
                            ins.add(new Binary(BITWISE_XOR, typeAsm, new Imm(bits), dst1));
                        } else {
                            ins.add(new Mov(typeAsm, src1, dst1));
                            ins.add(new Unary(op1, typeAsm, dst1));
                        }
                    }
                }
                case ZeroExtendIr(ValIr src, VarIr dst) ->
                        ins.add(new MovZeroExtend(valToAsmType(src),
                                valToAsmType(dst), toOperand(src),
                                toOperand(dst)));
                case CopyFromOffset(ValIr srcV, long offset1, VarIr dstV) -> {
                    Operand src = toOperand(srcV, (int) offset1);
                    TypeAsm typeAsm = valToAsmType(dstV);
                    if (typeAsm instanceof ByteArray(long size, _)) {
                        Operand dst = toOperand(dstV, 0);
                        copyBytes(ins, src, dst, size);
                    } else ins.add(new Mov(typeAsm, src, toOperand(dstV)));
                }
                case CopyBitsFromOffset(ValIr srcV, long offset1,int bitOffset,
                                        int bitWidth, VarIr dstV) -> {
                    Operand src = toOperand(srcV, (int) offset1);
                    Type destType = type(dstV);
                    TypeAsm typeAsm = toTypeAsm(destType);
                    var dst=toOperand(dstV);
                    ins.add(new Mov(typeAsm, src, dst));
                    ins.add(new Binary(SHR, typeAsm , new Imm(bitOffset), dst));
                    //bit mask to just keep width bits
                    long mask = (1L << bitWidth) - 1;

                    ins.add(new Binary(BITWISE_AND, typeAsm , new Imm(mask), dst));
                    if (destType.isSigned()) {
                        ins.add(new Binary(SHL,typeAsm , new Imm(typeAsm.size()*8-bitWidth), dst));
                        ins.add(new Binary(SAR,typeAsm , new Imm(typeAsm.size()*8-bitWidth), dst));
                    }
                }
                case CopyBitsFromOffsetViaPointer(ValIr srcV, long offset1,int bitOffset,
                                        int bitWidth, VarIr dstV) -> {
                    // srcV is a pointer, load the address srcV into DX
                    ins.add(new Mov(QUADWORD, toOperand(srcV), DX));

                    Operand src = new Memory(DX, (int) offset1);
                    Type destType = type(dstV);
                    TypeAsm typeAsm = toTypeAsm(destType);
                    var dst=toOperand(dstV);
                    ins.add(new Mov(typeAsm, src, dst));
                    ins.add(new Binary(SAR, typeAsm , new Imm(bitOffset), dst));
                    //bit mask to just keep width bits
                    long mask = (1L << bitWidth) - 1;
                    ins.add(new Binary(BITWISE_AND, typeAsm , new Imm(mask), dst));
                    // if the destination type is unsigned we're done -
                    // otherwise we may need to shift right then left to set
                    // leading ones and get the right signed result
                    if (destType.isSigned()) {
                        ins.add(new Binary(SHL,typeAsm , new Imm(typeAsm.size()-bitWidth), dst));
                        ins.add(new Binary(SAR,typeAsm , new Imm(typeAsm.size()-bitWidth), dst));
                    }
                }
                case CopyBitsToOffset(ValIr srcV, VarIr dstV, long offset1, int bitOffset,
                                      int bitWidth) -> {
                    Operand src = toOperand(srcV);
                    Operand dst = toOperand(dstV, (int) offset1);
                    TypeAsm typeAsm = valToAsmType(srcV);
                    ins.add(new Mov(typeAsm, src, AX));

                    long srcMask = (1L << bitWidth) - 1;
                    ins.add(new Binary(BITWISE_AND, typeAsm , new Imm(srcMask), AX));
                    ins.add(new Binary(SHL,  typeAsm, new Imm(bitOffset), AX));

                    // now we have the value we want to copy in AX, with just the bits we want
                    // suppose the bits we want are abc
                    // now we have 000abc000

                    ins.add(new Mov(typeAsm, dst, DX));
                    // AND it with a mask the keeps all but the bits we want to set
                    int typeSizeBits = (int) (typeAsm.size() * 8);
                    long typeSizeBitsOnes =
                            0xffff_ffff_ffff_ffffL >> (64 - typeSizeBits);
                    long destMask = srcMask << bitOffset;
                    // zero out the bits in DX at abc position
                    ins.add(new Binary(BITWISE_AND, typeAsm , new Imm(~destMask & typeSizeBitsOnes), DX));
                    ins.add(new Binary(BITWISE_OR, typeAsm , AX, DX));
                    ins.add(new Mov(typeAsm, DX, dst));
                }

                case CopyBitsToOffsetViaPointer(ValIr srcV, VarIr dstV, long offset1, int bitOffset,
                                      int bitWidth) -> {
                    Operand src = toOperand(srcV);
                   // Operand dst = toOperand(dstV, (int) offset1);



                    TypeAsm typeAsm = valToAsmType(srcV);
                    ins.add(new Mov(typeAsm, src, AX));

                    long srcMask = (1L << bitWidth) - 1;
                    ins.add(new Binary(BITWISE_AND, typeAsm , new Imm(srcMask), AX));
                    ins.add(new Binary(SHL,  typeAsm, new Imm(bitOffset), AX));

                    // now we have the value we want to copy in AX, with just the bits we want
                    // suppose the bits we want are abc
                    // now we have 000abc000

                    // dstV is a pointer, load the address dstV into DX
                    ins.add(new Mov(QUADWORD, toOperand(dstV), DX));

                    // load value at address dstV + offset1 into DX
                    ins.add(new Mov(typeAsm, new Memory(DX, offset1), DX));
                    // AND it with a mask the keeps all but the bits we want to set
                    int typeSizeBits= (int) (typeAsm.size()*8);
                    long typeSizeBitsOnes = ~(-1L << typeSizeBits);
                    long destMask=srcMask<<bitOffset;
                    // zero out the bits in DX at abc position
                    ins.add(new Binary(BITWISE_AND, typeAsm , new Imm(~destMask & typeSizeBitsOnes), DX));
                    ins.add(new Binary(BITWISE_OR, typeAsm , AX, DX));

                    // dstV is a pointer, load the address dstV into AX
                    ins.add(new Mov(QUADWORD, toOperand(dstV), AX));

                    ins.add(new Mov(typeAsm, DX, new Memory(AX, offset1)));
                }
                case Ignore.IGNORE -> {}
                case BuiltinC23VaStartIr(VarIr vaList) -> {
                    ins.add(new Comment("VA START START"));
//                    field   offset
//                    gp_offset	0
//                    fp_offset	4
//                    overflow_arg_area	8
//                    reg_save_area	16

                    // gp_offset The element holds the offset in bytes from
                    // reg_save_area to the
                    // place where the next available general purpose argument
                    // register is saved

                    ins.add(new Mov(LONGWORD, new Imm(integerArguments.size() * 8),
                            new PseudoMem(vaList.identifier(), 0)));

                    //fp_offset The element holds the offset in bytes from
                    // reg_save_area to the
                    //place where the next available floating point argument
                    // register is saved.
                    ins.add(new Mov(LONGWORD, new Imm(48 + doubleArguments.size() * 16),
                            new PseudoMem(vaList.identifier(), 4)));
                    // overflow_arg_area
                    ins.add(new Lea(new Memory(BP, offset),
                            new PseudoMem(vaList.identifier(), 8)));

                    // reg_save_area The element points to the start of the
                    // register save area.
                    ins.add(new Comment("Reg save area"));
                    ins.add(new Lea(new Memory(BP, -176),
                            new PseudoMem(vaList.identifier(), 16)));
                    ins.add(new Comment("VA START END"));
                }
                case BuiltinVaArgIr(VarIr vaList, VarIr dst, Type type) ->
                        emitBuiltInVarArg(vaList, dst, ins, type);
                case MFENCE -> ins.add(MFENCE);
                case Pos pos -> ins.add(pos);
                default ->
                        throw new IllegalStateException("Unexpected value: " + inst);
            }
        }
        return new FunctionAsm(functionIr.name(), functionIr.global(),
                returnInMemory, ins, toRegisters(returnValueClassification),
                functionIr.callsVaStart());
    }


    private static Operand vaListField(VarIr vaList, int offset, boolean vaListIsPointer,
                                       List<Instruction> ins) {
        if (vaListIsPointer) {
            Operand ptr = toOperand(vaList);
            String name = makeTemporary("tmp");
            Type t = switch(offset) {
                case 0, 4 -> Primitive.INT;
                case 8, 16 -> Primitive.LONG;
                default -> throw new IllegalArgumentException("bad offset " + offset);
            };
            TypeAsm asmType = toTypeAsm(t);
            Operand dst = new Pseudo(name, asmType, false, false);
            Mcc.SYMBOL_TABLE.put(name, new SymbolTableEntry(t, LOCAL_ATTR));
            ins.add(new Mov(QUADWORD, ptr, R10));
            ins.add(new Mov(asmType, new Memory(R10, offset), dst));
            return dst;
        }
        return new PseudoMem(vaList.identifier(), offset);
    }

    private static void emitBuiltInVarArg(VarIr vaList, VarIr dst,
                                          List<Instruction> ins, Type type) {
        ins.add(new Comment("VA_ARG START"));
        boolean vaListIsPointer = SYMBOL_TABLE.get(vaList.identifier()).type() instanceof Pointer;

        int numGp = 0;
        int numFp = 0;
        boolean floatFirst = false;
        boolean canBePassedInRegisters = false;
        long typeSize = size(type);
        List<StructureType> classes = null;

        if (type.isScalar()) {
            canBePassedInRegisters = true;
            if (type == Primitive.DOUBLE) numFp++;
            else numGp++;
        } else if (typeSize <= 16) {
            canBePassedInRegisters = true;
            classes = classifyStructure(type);
            for (int i = 0; i < classes.size(); i++) {
                var c = classes.get(i);
                if (c == StructureType.SSE) {
                    numFp++;
                    floatFirst = i == 0;
                } else if (c == StructureType.INTEGER) numGp++;
            }
        }
        LabelIr stackLabel = newLabel(makeTemporary(".Lstack."));
        LabelIr endLabel = newLabel(makeTemporary(".Lend."));

// 1. Determine whether type may be passed in the registers. If not go to
// step 7.
// 2. Compute num_gp to hold the number of general purpose registers needed
// to pass type
// and num_fp to hold the number of floating point registers needed.
// 3. Verify whether arguments fit into registers. In the case:
// l->gp_offset > 48 − num_gp ∗ 8
// or
// l->fp_offset > 176 − num_fp ∗ 16
// go to step 7.
        if (canBePassedInRegisters) {
            if (numGp>0) {
                // is register available?
                Operand gpOffset = vaListField(vaList, 0, vaListIsPointer, ins);
                ins.add(new Cmp(LONGWORD, new Imm(48 - numGp * 8L), gpOffset));
                // if not use stack
                ins.add(newJmpCC(CmpOperator.GREATER_THAN, true, stackLabel.label()));
            }
            if (numFp>0) {
                // is register available?
                Operand fpOffset = vaListField(vaList, 4, vaListIsPointer, ins);
                ins.add(new Cmp(LONGWORD, new Imm(176 - numFp * 16L), fpOffset));
                // if not use stack
                ins.add(newJmpCC(CmpOperator.GREATER_THAN, true, stackLabel.label()));
            }
// 4. Fetch type from l->reg_save_area with an offset of l->gp_offset and/or
// l->fp_offset. This may require copying to a temporary location in case the
// parameter is passed in different register classes or requires an alignment
// greater
// than 8 for general purpose registers and 16 for XMM registers.
            for (int i =0; i < numFp + numGp ; i++) {
                boolean isFp = numGp == 0 || (numFp == 1 && (floatFirst ?
                        i == 0 : i == 1));

                // add gp_offset/fp_offset to reg_save_area
                var regSaveArea = vaListField(vaList, 16, vaListIsPointer, ins);
                ins.add(new Mov(QUADWORD, regSaveArea, AX));
                ins.add(new Mov(LONGWORD, isFp ? vaListField(vaList, 4, vaListIsPointer, ins) : vaListField(vaList, 0, vaListIsPointer, ins), DX));
                ins.add(new Binary(ADD, QUADWORD, DX, AX));
                // mov what's at address gp_offset+reg_save_area to dst
                int offset = i*8;
// 6. Return the fetched type.
                Operand dstOperand = type.isScalar() ?toOperand(dst):toOperand(dst, offset);
                ins.add(new Comment("write to " + dstOperand));
                ins.add(new Mov(toTypeAsm(type), new Memory(AX, 0),
                        dstOperand));
                if (isFp) {
                    ins.add(new Binary(ADD, LONGWORD, new Imm(16L), vaListField(vaList, 4, vaListIsPointer, ins)));
                } else {
                    ins.add(new Binary(ADD, LONGWORD, new Imm(8L), vaListField(vaList, 0, vaListIsPointer, ins)));
                }
            }

// 5. Set:
// l->gp_offset = l->gp_offset + num_gp ∗ 8
// l->fp_offset = l->fp_offset + num_fp ∗ 16.
//            if (numGp > 0)
//                ins.add(new Binary(ADD, LONGWORD, new Imm(8L * numGp), vaListField(vaList, 0, vaListIsPointer, ins)));
//            if (numFp > 0)
//                ins.add(new Binary(ADD, LONGWORD, new Imm(16L * numFp), vaListField(vaList, 4, vaListIsPointer, ins)));
            ins.add(new Jump(endLabel.label()));


        }



// 7. Align l->overflow_arg_area upwards to a 16 byte boundary if alignment
// needed by
// type exceeds 8 byte boundary.



        ins.add(stackLabel);

        // 8. Fetch type from l->overflow_arg_area.
// 9. Set l->overflow_arg_area to:
// l->overflow_arg_area + sizeof(type)
        var overFlowArgArea = vaListField(vaList, 8, vaListIsPointer, ins);
        ins.add(new Mov(QUADWORD, overFlowArgArea, AX));
//        ins.add(new Mov(QUADWORD, new Memory(AX, 0), toOperand(dst)));
        ins.add(new Comment("VA_ARG copy bytes"));
        copyBytes(ins,new Memory(AX, 0),toOperand(dst//,0
        ), typeSize);
// 10. Align l->overflow_arg_area upwards to an 8 byte boundary.
// 11. Return the fetched type.
        long nextSlotOffset=ProgramAsm.roundAwayFromZero(typeSize, Math.max(typeAlignment(type), 8));
        ins.add(new Binary(ADD, QUADWORD, new Imm(nextSlotOffset), overFlowArgArea));
        ins.add(endLabel);
        ins.add(new Comment("VA_ARG END"));

    }

    private static void compareDouble(CmpOperator op1, TypeAsm typeAsm,
                                      Operand subtrahend, Operand minuend,
                                      Operand dst, List<Instruction> ins) {
        ins.add(new Cmp(typeAsm, subtrahend, minuend));
        if (op1 == EQUALS || op1 == NOT_EQUALS) {
            LabelIr isANLabel = newLabel(makeTemporary(".Lan."));

            ins.add(newJmpCC(null, false, // null false -> jnp (i.e. jump if
                    // not NaN)
                    isANLabel.label()));
            // for NaN : == is always false and != is always true
            ins.add(new Mov(LONGWORD, op1 == EQUALS ? Imm.ZERO : Imm.ONE, dst));
            LabelIr endLabel = newLabel(makeTemporary(".Lend."));
            ins.add(new Jump(endLabel.label()));

            ins.add(isANLabel);
            ins.add(new Mov(LONGWORD, Imm.ZERO, dst));
            ins.add(new SetCC(op1, true, dst));

            ins.add(endLabel);

        } else {
            ins.add(new Mov(LONGWORD, Imm.ZERO, dst));
            LabelIr isNaNLabel = newLabel(makeTemporary(".Lnan."));
            ins.add(new OldJmpCC(null, true, isNaNLabel.label()).toJmpCC2());
            ins.add(new SetCC(op1, true, dst));
            ins.add(isNaNLabel);
        }
    }

    final static Pair<Integer, Integer> NO_REGS = new Pair<>(0, 0);

    private static Pair<Integer, Integer> toRegisters(
            ReturnValueClassification returnValueClassification) {

        if (returnValueClassification == null || returnValueClassification.returnInMemory())
            return NO_REGS;
        List<TypedOperand> intDests = returnValueClassification.intDests();
        List<Operand> doubleDests = returnValueClassification.doubleDests();
        int intDestsSize = intDests.size();
        int doubleDestsSize = doubleDests.size();
        return new Pair<>(intDestsSize, doubleDestsSize);
    }

    private static ReturnValueClassification convertReturn(ValIr val,
                                                           List<Instruction> ins) {
        if (val == null) {
            ins.add(RET);
            return null;
        }
        Operand retVal = toOperand(val);
        ReturnValueClassification returnValueClassification =
                classifyReturnValue(val);
        List<TypedOperand> intDests = returnValueClassification.intDests();
        List<Operand> doubleDests = returnValueClassification.doubleDests();
        boolean returnInMemory = returnValueClassification.returnInMemory();
        if (returnInMemory) {
            ins.add(new Mov(QUADWORD, new Memory(BP, -8), AX));
            var returnStorage = new Memory(AX, 0);
            Type t = type(val);
            copyBytes(ins, retVal, returnStorage, (int) size(t));
        } else {
            int regIndex = 0;
            for (TypedOperand to : intDests) {
                Operand op = to.operand();
                TypeAsm t = to.type();
                var r = INTEGER_RETURN_REGISTERS[regIndex];
                if (t instanceof ByteArray(long size, _)) {
                    copyBytesToReg(ins, op, r, size);
                } else {
                    ins.add(new Mov(t, op, r));
                }
                regIndex++;
            }
            regIndex = 0;
            for (Operand op : doubleDests) {
                var r = DOUBLE_REGISTERS[regIndex];
                ins.add(new Mov(DOUBLE, op, r));
                regIndex++;
            }
        }
        ins.add(RET);
        return returnValueClassification;
    }

    private static void copyBytes(List<Instruction> ins, Operand src,
                                  Operand dst, long size) {
        int offset = 0;
        while (size >= 8) {
            ins.add(new Mov(QUADWORD, src.plus(offset), dst.plus(offset)));
            offset += 8;
            size -= 8;
        }
        while (size >= 4) {
            ins.add(new Mov(LONGWORD, src.plus(offset), dst.plus(offset)));
            offset += 4;
            size -= 4;
        }
        while (size > 0) {
            ins.add(new Mov(BYTE, src.plus(offset), dst.plus(offset)));
            offset++;
            size--;
        }
    }

    private static void memsetBytes(List<Instruction> ins, int c,
                                    Operand dstIn, long size,
                                    boolean viaPointer) {
        Operand dst = dstIn;
        if (!viaPointer) {
            ins.add(new Lea(dstIn, DX));
            dst = DX;
        }
        long offset = 0;
        long cl = c & 0xff;
        var q = new Imm(cl << 56 | cl << 48 | cl << 40 | cl << 32 | cl << 24 |
                cl << 16 | cl << 8 | cl);
        var l = new Imm(cl << 24 | cl << 16 | cl << 8 | cl);
        var w = new Imm(cl << 8 | cl);
        var b = new Imm(cl);
        if (size > 16) {
            long count = size-(size%8);
            ins.add(new Mov(QUADWORD, dst, SI));
            ins.add(new Mov(QUADWORD, q, AX));
            ins.add(new Mov(QUADWORD, SI, DI));
            ins.add(new Mov(LONGWORD, new Imm(count/8), CX));
            ins.add(new Literal("rep stosq"));
            size-=count;
            offset += count;
        }
        if (size == 0) return;
        if (!viaPointer) {
            ins.add(new Lea(dstIn, DX));
            dst = new Memory(DX, 0);
        } else {
            ins.add(new Mov(QUADWORD, dst, DX));
            dst = new Memory(DX, 0);
        }
        while (size >= 8) {
            ins.add(new Mov(QUADWORD, q, dst.plus(offset)));
            offset += 8;
            size -= 8;
        }
        while (size >= 4) {
            ins.add(new Mov(LONGWORD, l, dst.plus(offset)));
            offset += 4;
            size -= 4;
        }
        while (size >= 2) {
            ins.add(new Mov(WORD, w, dst.plus(offset)));
            offset += 2;
            size -= 2;
        }
        while (size > 0) {
            ins.add(new Mov(BYTE, b, dst.plus(offset)));
            offset++;
            size--;
        }
    }

    /*classify parameters or arguments*/
    private static ParameterClassification classifyParameters(
            List<TypedOperand> operands, boolean returnInMemory) {
        ArrayList<TypedOperand> integerArguments = new ArrayList<>();
        ArrayList<Operand> doubleArguments = new ArrayList<>();
        ArrayList<TypedOperand> stackArguments = new ArrayList<>();
        int regsAvailable = returnInMemory ? 5 : 6;
        for (TypedOperand to : operands) {
            TypeAsm type = to.type();
            Operand v = to.operand();
            if (type == DOUBLE || type==FLOAT) {
                if (doubleArguments.size() < 8) doubleArguments.add(v);
                else stackArguments.add(to);
            } else if (type.isScalar()) {
                if (integerArguments.size() < regsAvailable)
                    integerArguments.add(to);
                else stackArguments.add(to);
            } else { // struct

                long offset = 0;
                String identifier = switch (v) {
                    case Pseudo p -> p.identifier;
                    case PseudoMem(String id, long off, int alignment) -> {
                        assert (off == 0);
                        offset = off;
                        yield id;
                    }
                    default -> throw new AssertionError();
                };
                Type t = SYMBOL_TABLE.get(identifier).type();
                List<StructureType> classes = classifyStructure(t);
                boolean useStack = true;
                long structSize = type.size();
                if (classes.getFirst() != StructureType.MEMORY) {
                    ArrayList<TypedOperand> tentativeInts = new ArrayList<>();
                    ArrayList<Operand> tentativeDoubles = new ArrayList<>();
                    for (var c : classes) {
                        var operand = new PseudoMem(identifier, offset);
                        if (c == StructureType.SSE) {
                            tentativeDoubles.add(operand);
                        } else {
                            var eightbyteType = getEightbyteType(offset,
                                    structSize);
                            tentativeInts.add(new TypedOperand(eightbyteType,
                                    operand));
                        }
                        offset += 8;
                    }
                    if (tentativeDoubles.size() + doubleArguments.size() <= 8 && tentativeInts.size() + integerArguments.size() <= regsAvailable) {
                        doubleArguments.addAll(tentativeDoubles);
                        integerArguments.addAll(tentativeInts);
                        useStack = false;
                    }
                }
                if (useStack) {
                    offset = 0;
                    for (var _ : classes) {
                        var operand = new PseudoMem(identifier, offset);
                        var eightbyteType = getEightbyteType(offset,
                                structSize);
                        stackArguments.add(new TypedOperand(eightbyteType,
                                operand));
                        offset += 8;
                    }
                }
            }
        }
        return new ParameterClassification(integerArguments, doubleArguments,
                stackArguments);
    }

    public static final Map<FunType, ParameterClassification> PARAMETER_CLASSIFICATION_MAP = new HashMap<>();

    private static TypeAsm getEightbyteType(long offset, long structSize) {
        long bytesFromEnd = structSize - offset;
        if (bytesFromEnd >= 8) return QUADWORD;
        if (bytesFromEnd == 4) return LONGWORD;
        if (bytesFromEnd == 1) return BYTE;
        return new ByteArray(bytesFromEnd, 8);
    }


    public enum StructureType {MEMORY, SSE, INTEGER}

    public static List<StructureType> classifyStructure(Type t) {
        long size = size(t);
        if (size > 16) {
            long eightbyteCount = (size / 8) + (size % 8 == 0 ? 0 : 1);
            List<StructureType> result = new ArrayList<>();
            for (int i = 0; i < eightbyteCount; i++) {
                result.add(StructureType.MEMORY);
            }
            return result;
        } else {
            StructureType[] classes = classifyEightbytes(0, StructureType.SSE
                    , StructureType.SSE, t);
            if (size > 8) {
                List<StructureType> result = new ArrayList<>();
                result.add(classes[0]);
                result.add(classes[1]);
                return result;
            } else {
                return List.of(classes[0]);
            }
        }
    }

    private static StructureType[] classifyEightbytes(long offset,
                                                      StructureType first,
                                                      StructureType second,
                                                      Type type) {
        if (type == Primitive.DOUBLE) {
            return new StructureType[]{first, second};
        } else if (type.isScalar()) {
            if (offset < 8) {
                return new StructureType[]{StructureType.INTEGER, second};
            } else {
                return new StructureType[]{first, StructureType.INTEGER};
            }
        } else if (type instanceof Structure s && s.isUnion()) {
            ArrayList<MemberEntry> members = members(s);
            StructureType one = first, two = second;
            for (var memberEntry : members) {
                Type member = memberEntry.type();
                StructureType[] result = classifyEightbytes(offset, one, two,
                        member);
                one = result[0];
                two = result[1];
            }
            return new StructureType[]{one, two};
        } else if (type instanceof Structure s) { // structOrUnionSpecifier is not uniion
            ArrayList<MemberEntry> members = members(s);
            StructureType one = first, two = second;
            for (var memberEntry : members) {
                Type member = memberEntry.type();
                long memberOffset = offset + memberEntry.byteOffset();
                StructureType[] result = classifyEightbytes(memberOffset, one
                        , two, member);
                one = result[0];
                two = result[1];
            }
            return new StructureType[]{one, two};
        } else if (type instanceof Array(Type element, Constant arraySize)) {
            long elemSize = size(element);
            StructureType one = first, two = second;
            for (long i = 0; i < arraySize.toLong(); i++) {
                long currentOffset = offset + (i * elemSize);
                StructureType[] result = classifyEightbytes(currentOffset,
                        one, two, element);
                one = result[0];
                two = result[1];
            }
            return new StructureType[]{one, two};
        } else {
            throw new RuntimeException("Internal error");
        }
    }
}
