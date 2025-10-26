package com.quaxt.mcc.asm;

import com.quaxt.mcc.*;
import com.quaxt.mcc.optimizer.Optimizer;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.parser.StorageClass;
import com.quaxt.mcc.parser.Var;
import com.quaxt.mcc.registerallocator.RegisterAllocator;
import com.quaxt.mcc.semantic.*;
import com.quaxt.mcc.tacky.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.EQUALS;
import static com.quaxt.mcc.CmpOperator.NOT_EQUALS;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.Mcc.valToType;
import static com.quaxt.mcc.UnaryOperator.UNARY_SHR;
import static com.quaxt.mcc.asm.DoubleReg.*;
import static com.quaxt.mcc.asm.Nullary.RET;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;
import static com.quaxt.mcc.asm.IntegerReg.*;
import static com.quaxt.mcc.semantic.Primitive.UCHAR;
import static com.quaxt.mcc.tacky.IrGen.newLabel;

public class Codegen {
    static HashMap<Number, StaticConstant> CONSTANT_TABLE = new HashMap<>();
    private static final Data NEGATIVE_ZERO;
    private static final Data UPPER_BOUND;

    public static Map<String, SymTabEntryAsm> BACKEND_SYMBOL_TABLE =
            new HashMap<>();

    private static final Imm UPPER_BOUND_LONG_IMMEDIATE = new Imm(1L << 63);

    public final static IntegerReg[] INTEGER_RETURN_REGISTERS =
            new IntegerReg[]{AX, DX};
    public final static IntegerReg[] INTEGER_REGISTERS = new IntegerReg[]{DI,
            SI, DX, CX, R8, R9};
    public final static DoubleReg[] DOUBLE_REGISTERS = new DoubleReg[]{XMM0,
            XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7};

    static {
        double negative_zero = -0.0;
        // can't just call resolve constant because 16-byte alignment
        CONSTANT_TABLE.put(negative_zero,
                new StaticConstant("c." + doubleToHexString(negative_zero), 16,
                        new DoubleInit(negative_zero)));
        NEGATIVE_ZERO = resolveConstantDouble(-0.0d);
        UPPER_BOUND = resolveConstantDouble(0x1.0p63);
    }

    private static String doubleToHexString(double d) {
        return Double.toHexString(d).replaceAll("-", "_");
    }

    private static String floatToHexString(float d) {
        return Float.toHexString(d).replaceAll("-", "_");
    }


    public static ProgramAsm generateProgramAssembly(ProgramIr programIr) {
        ArrayList<TopLevelAsm> topLevels = new ArrayList<>();
        for (TopLevel topLevel : programIr.topLevels()) {
            switch (topLevel) {
                case FunctionIr f -> topLevels.add(convertFunction(f));

                case StaticVariable(String name, boolean global, Type t,
                                    List<StaticInit> init) -> {

                    topLevels.add(new StaticVariableAsm(name, global,
                            Mcc.variableAlignment(t), init));
                }
                case com.quaxt.mcc.tacky.StaticConstant(String name, Type t,
                                                        StaticInit init) ->
                        topLevels.add(new StaticConstant(name,
                                Mcc.variableAlignment(t), init));
            }
        }
        topLevels.addAll(CONSTANT_TABLE.values());
        generateBackendSymbolTable();

        for (TopLevelAsm topLevelAsm : topLevels) {
            if (topLevelAsm instanceof FunctionAsm functionAsm) {
                RegisterAllocator.allocateRegisters(functionAsm);
                AtomicLong offset = replacePseudoRegisters(functionAsm);
                functionAsm.stackSize = -offset.get();
                fixUpInstructions(offset, functionAsm);
            }
        }

        return new ProgramAsm(topLevels);
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

    private static AtomicLong replacePseudoRegisters(FunctionAsm functionAsm) {

        List<Instruction> instructions = functionAsm.instructions;

        boolean returnInMemory = functionAsm.returnInMemory;
        // for varargs functions we reserve 176 bytes (8 bytes for each of 6
        // GPRs and 16 bytes for each of XMM0-XMM8)
        long reservedSpace = functionAsm.callsVaStart ? 176 : 0;
        if (returnInMemory) reservedSpace += 8;
        AtomicLong offset = new AtomicLong(-reservedSpace);
        Map<String, Long> varTable = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            Instruction oldInst = instructions.get(i);
            Instruction newInst = switch (oldInst) {
                case CallIndirect(Operand p) ->
                        new CallIndirect(dePseudo(p, varTable, offset));
                case Nullary _, Cdq _, Jump _, JmpCC _, LabelIr _, Call _ ->
                        oldInst;
                case Mov(TypeAsm typeAsm, Operand src, Operand dst) ->
                        new Mov(typeAsm, dePseudo(src, varTable, offset),
                                dePseudo(dst, varTable, offset));
                case Unary(UnaryOperator op, TypeAsm typeAsm,
                           Operand operand) ->
                        new Unary(op, typeAsm, dePseudo(operand, varTable,
                                offset));
                case Binary(ArithmeticOperator op, TypeAsm typeAsm, Operand src,
                            Operand dst) ->
                        new Binary(op, typeAsm, dePseudo(src, varTable,
                                offset), dePseudo(dst, varTable, offset));

                case Cmp(TypeAsm typeAsm, Operand subtrahend,
                         Operand minuend) ->
                        new Cmp(typeAsm, dePseudo(subtrahend, varTable,
                                offset), dePseudo(minuend, varTable, offset));
                case SetCC(CmpOperator cmpOperator, boolean signed,
                           Operand operand) ->
                        new SetCC(cmpOperator, signed, dePseudo(operand,
                                varTable, offset));
                case Push(Operand operand) ->
                        new Push(dePseudo(operand, varTable, offset));
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) ->
                        new Movsx(srcType, dstType, dePseudo(src, varTable,
                                offset), dePseudo(dst, varTable, offset));
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) ->
                        new MovZeroExtend(srcType, dstType, dePseudo(src,
                                varTable, offset), dePseudo(dst, varTable,
                                offset));
                case Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) ->
                        new Cvt(srcType, dstType, dePseudo(src, varTable,
                                offset), dePseudo(dst, varTable, offset));
                case Lea(Operand src, Operand dst) ->
                        new Lea(dePseudo(src, varTable, offset), dePseudo(dst
                                , varTable, offset));
                case Comment comment -> comment;
                case Pop pop -> throw new Todo();
                case Test test -> throw new Todo();
            };
            instructions.set(i, newInst);
        }
        return offset;
    }

    private static void fixUpInstructions(AtomicLong offset,
                                          FunctionAsm function) {
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
                    // if srcType is not byte we rewrite as Mov
                    if (srcType == BYTE) {
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
                                 DOUBLE_DIVIDE, BITWISE_XOR -> {
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
                    if (typeAsm == DOUBLE && !(dst instanceof DoubleReg)) {
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
                            instructions.set(i, new Mov(dstType, src, R10));
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
                    }else if (isRam(dst)) {
                        instructions.set(i, new Cvt(srcType, dstType, src, R11));
                        instructions.add(i + 1, new Mov(dstType, R11, dst));
                    }
                }
                default -> {
                }
            }
        }
    }

    private static Operand dstReg(TypeAsm typeAsm) {
        return typeAsm == DOUBLE ? XMM15 : R11;
    }

    private static Operand srcReg(TypeAsm typeAsm) {
        return typeAsm == DOUBLE ? XMM14 : R10;
    }

    private static TypeAsm valToAsmType(ValIr val) {
        return toTypeAsm(valToType(val));
    }

    private static TypeAsm toTypeAsm(Type type) {
        return switch (type) {
            case Primitive.CHAR, UCHAR, Primitive.SCHAR -> BYTE;
            case Primitive.SHORT, Primitive.USHORT -> WORD;
            case Primitive.INT, Primitive.UINT -> LONGWORD;
            case Primitive.LONG, Primitive.ULONG -> QUADWORD;
            case Primitive.DOUBLE -> DOUBLE;
            case Primitive.FLOAT -> FLOAT;
            case Pointer _, FunType _ -> QUADWORD;
            case Array _, Structure _ ->
                    new ByteArray((int) Mcc.size(type),
                            Mcc.variableAlignment(type));
            default ->
                    throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private static boolean isRam(Operand src) {
        return src instanceof Memory || src instanceof Indexed || src instanceof Data;
    }

    public static Data resolveConstantDouble(double d) {
        StaticConstant c = CONSTANT_TABLE.computeIfAbsent(d,
                _ -> new StaticConstant("c." + doubleToHexString(d), 8,
                        new DoubleInit(d)));
        return new Data(c.label(), 0);
    }

    public static Data resolveConstantFloat(float d) {
        StaticConstant c = CONSTANT_TABLE.computeIfAbsent(d,
                _ -> new StaticConstant("f." + floatToHexString(d), 8,
                        new FloatInit(d)));
        return new Data(c.label(), 0);
    }

    private static Operand toOperand(ValIr val) {
        return switch (val) {
            case CharInit(byte i) -> new Imm(i);
            case UCharInit(byte i) -> new Imm(i & 0xff);
            case ShortInit(short i) -> new Imm(i);
            case UShortInit(short i) -> new Imm(i & 0xffff);
            case IntInit(int i) -> new Imm(i);
            case VarIr(String identifier) -> {
                Type t = valToType(val);
                if (t instanceof Array || t instanceof Structure)
                    yield new PseudoMem(identifier, 0);
                var ste = SYMBOL_TABLE.get(identifier);
                if (t instanceof FunType) {
                    yield new LabelAddress(identifier);
                }
                yield new Pseudo(identifier, toTypeAsm(t),
                        switch (ste.attrs()) {
                    case StaticAttributes _, ConstantAttr _ -> true;
                    default -> false;
                }, ste.aliased);
            }
            case LongInit(long l) -> new Imm(l);
            case UIntInit(int i) -> new Imm(Integer.toUnsignedLong(i));
            case ULongInit(long l) -> new Imm(l);
            case DoubleInit(double d) -> resolveConstantDouble(d);
            case FloatInit(float d) -> resolveConstantFloat(d);
            default ->
                    throw new IllegalStateException("Unexpected value: " + val);
        };
    }

    private static Operand toOperand(ValIr val, int offset) {
        return switch (val) {
            case VarIr(
                    String identifier) when valToType(val) instanceof Array ->
                    new PseudoMem(identifier, offset);
            case VarIr(
                    String identifier) when valToType(val) instanceof Structure ->
                    new PseudoMem(identifier, offset);
            default -> {
                if (offset == 0) yield toOperand(val);
                else throw new AssertionError(val);
            }
        };
    }

    private static Operand dePseudo(Operand in, Map<String, Long> varTable,
                                    AtomicLong offsetA) {
        long offsetFromStartOfArray;
        String identifier;
        switch (in) {
            case Imm _, IntegerReg _, Memory _, DoubleReg _, Data _, Indexed _, LabelAddress _:
                return in;
            case Pseudo p:
                identifier = p.identifier;
                offsetFromStartOfArray = 0;
                break;
            case PseudoMem(String pIdentifier, long pOffsetFromStartOfArray):
                identifier = pIdentifier;
                offsetFromStartOfArray = pOffsetFromStartOfArray;
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (BACKEND_SYMBOL_TABLE.get(identifier) instanceof ObjEntry(
                TypeAsm type, boolean isStatic, boolean _)) {
            long size = type.size();
            long alignment = type.alignment();


            if (isStatic) return new Data(identifier, offsetFromStartOfArray);

            Long varOffset = varTable.get(identifier);
            if (varOffset == null) {
                // it starts ar -8 - we can use this for the first var
                // when that var is written it will update bytes stack-8 to
                // stack-1
                varOffset = offsetA.get();
                varOffset -= size;
                long remainder = varOffset % alignment;
                if (remainder != 0) {
                    varOffset -= (alignment + remainder);
                }
                varTable.put(identifier, varOffset);
                offsetA.set(varOffset);
            }
            return new Memory(BP, varOffset + offsetFromStartOfArray);


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
        if (t == DOUBLE) {
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
                int structSize = (int) Mcc.size(st);
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
        if (funCall instanceof FunCall(String name, ArrayList<ValIr> args,
                                       boolean varargs, boolean indirect, ValIr dst)) {

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
            PARAMETER_CLASSIFICATION_MAP.put(name, classifiedArgs);
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

            //pass args in registers
            for (TypedOperand integerArg : integerArguments) {
                var assemblyType = integerArg.type();

                IntegerReg r = INTEGER_REGISTERS[regIndex];
                if (assemblyType instanceof ByteArray(long size,
                                                      long alignment)) {
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
                if (assemblyType instanceof ByteArray(long size,
                                                      long alignment)) {
                    instructionAsms.add(new Binary(SUB, QUADWORD, new Imm(8),
                            SP));
                    copyBytes(instructionAsms, operand, new Memory(SP, 0),
                            size);
                } else if (operand instanceof Imm || operand instanceof IntegerReg || assemblyType == QUADWORD || assemblyType == DOUBLE) {
                    instructionAsms.add(new Push(operand));
                } else {
                    instructionAsms.add(new Mov(assemblyType, operand, AX));
                    instructionAsms.add(new Push(AX));
                }
            }
            if (varargs) {
                instructionAsms.add(new Mov(LONGWORD,
                        new Imm(doubleArguments.size()), AX));
            }
            if (indirect) {
                instructionAsms.add(new CallIndirect(toOperand(new VarIr(name))));
            }
            else instructionAsms.add(new Call(name));
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
                    if (t instanceof ByteArray(long size, long alignment)) {
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

    private static void copyBytesFromReg(List<Instruction> ins,
                                         IntegerReg srcReg, Operand dstOp,
                                         long byteCount) {
        long offset = 0;
        while (offset < byteCount) {
            Operand dstByte = dstOp.plus(offset);
            ins.add(new Mov(BYTE, srcReg, dstByte));
            if (offset < byteCount - 1)
                ins.add(new Binary(UNSIGNED_RIGHT_SHIFT, QUADWORD, new Imm(8)
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
            Mcc.setAliased(v.identifier());
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
        PARAMETER_CLASSIFICATION_MAP.put(functionIr.name(),
                classifiedParameters);

        ArrayList<TypedOperand> integerArguments =
                classifiedParameters.integerArguments();
        ArrayList<Operand> doubleArguments =
                classifiedParameters.doubleArguments();
        ArrayList<TypedOperand> stackArguments =
                classifiedParameters.stackArguments();

        for (TypedOperand to : integerArguments) {
            TypeAsm paramType = to.type();
            if (paramType instanceof ByteArray(long size, long alignment)) {
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
            if (paramType instanceof ByteArray(long size, long alignment)) {
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
                case BinaryIr(ArithmeticOperator op1, ValIr v1, ValIr v2,
                              VarIr dstName) -> {
                    Type type = valToType(v1);
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
                            case SHL, SAR, UNSIGNED_RIGHT_SHIFT -> {
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
                                ins.add(new Mov(typeAsm, toOperand(v2),
                                        toOperand(dstName)));
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
                    Type type = valToType(v1);
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
                    TypeAsm typeAsm = valToAsmType(srcV);
                    assert (valToAsmType(dstV).equals(typeAsm));
                    if (typeAsm instanceof ByteArray(long size,
                                                     long alignment)) {
                        copyBytes(ins, src, dst, size);
                    } else ins.add(new Mov(typeAsm, src, dst));
                }
                case CopyToOffset(ValIr srcV, VarIr dstV, long offset1) -> {
                    Operand src = toOperand(srcV);
                    Operand dst = toOperand(dstV, (int) offset1);
                    TypeAsm typeAsm = valToAsmType(srcV);
                    if (typeAsm instanceof ByteArray(long size,
                                                     long alignment)) {
                        copyBytes(ins, src, dst, size);
                    } else ins.add(new Mov(typeAsm, src, dst));
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
                    int typeSizeBits= (int) (typeAsm.size()*8);
                    long typeSizeBitsOnes = ~(-1L << typeSizeBits);
                    long destMask=srcMask<<bitOffset;
                    // zero out the bits in DX at abc position
                    ins.add(new Binary(BITWISE_AND, typeAsm , new Imm(~destMask & typeSizeBitsOnes), DX));
                    ins.add(new Binary(BITWISE_OR, typeAsm , AX, DX));
                    ins.add(new Mov(typeAsm, DX, dst));



                }
                case DoubleToInt(ValIr src, VarIr dst) -> {
                    var dstType = valToType(dst);
                    var dstTypeAsm = toTypeAsm(dstType);
                    if (dstType == Primitive.CHAR || dstType == Primitive.SCHAR) {
                        ins.add(new Cvt(valToAsmType(src), LONGWORD, toOperand(src), AX));
                        ins.add(new Mov(BYTE, AX, toOperand(dst)));
                    } else
                        ins.add(new Cvt(valToAsmType(src), dstTypeAsm, toOperand(src),
                                toOperand(dst)));
                }
                case DoubleToUInt(ValIr src, ValIr dst) -> {
                    Type dstType = valToType(dst);
                    if (dstType == UCHAR) {
                        ins.add(new Cvt(valToAsmType(src), LONGWORD, toOperand(src), AX));
                        ins.add(new Mov(BYTE, AX, toOperand(dst)));
                    } else if (dstType == Primitive.INT) {
                        ins.add(new Cvt(valToAsmType(src), QUADWORD, toOperand(src), AX));
                        ins.add(new Mov(LONGWORD, AX, toOperand(dst)));
                    } else {
                        TypeAsm srcAsmType = valToAsmType(src);
                        //p.335
                        LabelIr label1 = newLabel(Mcc.makeTemporary(".Laub."));
                        LabelIr label2 = newLabel(Mcc.makeTemporary(".LendCmp"
                                + "."));
                        ins.add(new Cmp(DOUBLE, UPPER_BOUND, toOperand(src)));
                        ins.add(new JmpCC(CmpOperator.GREATER_THAN_OR_EQUAL,
                                true, label1.label()));
                        ins.add(new Cvt(srcAsmType, QUADWORD, toOperand(src),
                                toOperand(dst)));
                        ins.add(new Jump(label2.label()));
                        ins.add(label1);
                        ins.add(new Mov(DOUBLE, toOperand(src), XMM0));
                        ins.add(new Binary(DOUBLE_SUB, DOUBLE, UPPER_BOUND,
                                XMM0));
                        ins.add(new Cvt(srcAsmType, QUADWORD, XMM0, toOperand(dst)));
                        ins.add(new Mov(QUADWORD, UPPER_BOUND_LONG_IMMEDIATE,
                                AX));
                        ins.add(new Binary(ADD, QUADWORD, AX, toOperand(dst)));
                        ins.add(label2);
                    }
                }
                case FunCall funCall -> codegenFunCall(funCall, ins);
                case GetAddress(ValIr srcV, VarIr dstV) -> {
                    Operand src = toOperand(srcV);
                    Operand dst = toOperand(dstV);
                    if (src instanceof LabelAddress)
                        ins.add(new Mov(QUADWORD, src, dst));
                    else
                        ins.add(new Lea(src, dst));
                }
                case IntToDouble(ValIr src, VarIr dst) -> {
                    Type srcType = valToType(src);
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
                    Type type = valToType(v);
                    TypeAsm typeAsm = toTypeAsm(type);
                    if (typeAsm == DOUBLE) {
                        ins.add(new Binary(BITWISE_XOR, typeAsm, XMM0, XMM0));
                        ins.add(new Cmp(typeAsm, XMM0, toOperand(v)));
                    } else {
                        ins.add(new Cmp(typeAsm, new Imm(0), toOperand(v)));
                    }
                    ins.add(new JmpCC(NOT_EQUALS,
                            type.unsignedOrDoubleOrPointer(), label));
                    if (typeAsm == DOUBLE) // v is NaN which is not equal to
                        // zero (but jne treats it like it is)
                        ins.add(new JmpCC(null, true, label));
                }
                case JumpIfZero(ValIr v, String label) -> {
                    if (v != null) {
                        Type type = valToType(v);
                        TypeAsm typeAsm = toTypeAsm(type);
                        if (typeAsm == DOUBLE) {
                            ins.add(new Binary(BITWISE_XOR, typeAsm, XMM0,
                                    XMM0));
                            ins.add(new Cmp(typeAsm, XMM0, toOperand(v)));
                            LabelIr endLabel = newLabel(Mcc.makeTemporary(
                                    ".Lend."));
                            ins.add(new JmpCC(null, true, endLabel.label()));

                            ins.add(new JmpCC(EQUALS, true, label));
                            ins.add(endLabel);
                        } else {
                            ins.add(new Cmp(typeAsm, new Imm(0), toOperand(v)));
                            ins.add(new JmpCC(EQUALS,
                                    type.unsignedOrDoubleOrPointer(), label));
                        }


                    } else {
                        ins.add(new JmpCC(EQUALS, false, label));
                    }

                }
                case LabelIr labelIr -> ins.add(labelIr);
                case Load(ValIr ptrV, VarIr dstV) -> {
                    Operand ptr = toOperand(ptrV);
                    Operand dst = toOperand(dstV);
                    TypeAsm dstType = toTypeAsm(valToType(dstV));
                    ins.add(new Mov(QUADWORD, ptr, AX));
                    if (dstType instanceof ByteArray(long size,
                                                     long alignment)) {
                        Operand src = new Memory(AX, 0);
                        copyBytes(ins, src, dst, size);
                    } else {
                        ins.add(new Comment("load:  " + dst));
                        ins.add(new Mov(dstType, new Memory(AX, 0), dst));
                    }
                }
                case ReturnIr(ValIr val) -> {
                    returnValueClassification = convertReturn(val, ins);
                }
                case SignExtendIr(ValIr src, VarIr dst) ->
                        ins.add(new Movsx(valToAsmType(src),
                                valToAsmType(dst), toOperand(src),
                                toOperand(dst)));
                case Store(ValIr srcV, ValIr ptrV) -> {
                    Operand src = toOperand(srcV);
                    Operand ptr = toOperand(ptrV);
                    ins.add(new Mov(QUADWORD, ptr, AX));
                    TypeAsm srcType = toTypeAsm(valToType(srcV));
                    if (srcType instanceof ByteArray(long size,
                                                     long alignment)) {
                        Operand dst = new Memory(AX, 0);
                        copyBytes(ins, src, dst, size);
                    } else {
                        ins.add(new Comment("store: " + src));
                        ins.add(new Mov(srcType, src, new Memory(AX, 0)));
                    }
                }
                case TruncateIr(ValIr srcV, VarIr dstV) -> {
                    var src = toOperand(srcV);
                    var dst = toOperand(dstV);
                    ins.add(new Mov(toTypeAsm(valToType(dstV)), src, dst));
                }
                case UIntToDouble(ValIr srcV, ValIr dstV) -> {
                    var src = toOperand(srcV);
                    var dst = toOperand(dstV);
                    Type srcType = valToType(srcV);
                    TypeAsm dstType = valToAsmType(dstV);
                    if (srcType == UCHAR) {
                        ins.add(new MovZeroExtend(BYTE, LONGWORD, src, AX));
                        ins.add(new Cvt(LONGWORD, dstType, AX, dst));
                    } else if (srcType == Primitive.CHAR || srcType == Primitive.SCHAR) {
                        ins.add(new Movsx(BYTE, LONGWORD, src, AX));
                        ins.add(new Cvt(LONGWORD, dstType, AX, dst));
                    } else if (srcType == Primitive.INT) {
                        ins.add(new MovZeroExtend(valToAsmType(srcV),
                                valToAsmType(dstV), src, AX));
                        ins.add(new Cvt(QUADWORD, dstType, AX, dst));
                    } else if (srcType == Primitive.UINT) {
                        ins.add(new MovZeroExtend(valToAsmType(srcV),
                                valToAsmType(dstV), src, AX));
                        ins.add(new Cvt(QUADWORD, dstType, AX, dst));
                    } else {
                        // see description on p. 320
                        LabelIr label1 = newLabel(Mcc.makeTemporary(
                                ".LoutOfRange."));
                        LabelIr label2 = newLabel(Mcc.makeTemporary(".Lend."));
                        var asmSrcType = srcType == Primitive.UINT ?
                                LONGWORD : QUADWORD;
                        ins.add(new Cmp(QUADWORD, new Imm(0), src));
                        ins.add(new JmpCC(CmpOperator.LESS_THAN, false,
                                label1.label()));
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
                case UnaryIr(UnaryOperator op1, ValIr srcIr, ValIr dstIr) -> {
                    Operand dst1 = toOperand(dstIr);
                    Operand src1 = toOperand(srcIr);
                    Type type = valToType(srcIr);
                    TypeAsm typeAsm = toTypeAsm(type);
                    if (op1 == UnaryOperator.NOT) {
                        if (typeAsm == DOUBLE) {
                            ins.add(new Binary(BITWISE_XOR, DOUBLE, XMM0,
                                    XMM0));
                            compareDouble(EQUALS, DOUBLE, XMM0, src1, dst1,
                                    ins);
                        } else {
                            ins.add(new Cmp(typeAsm, new Imm(0), src1));
                            ins.add(new Mov(valToAsmType(dstIr), new Imm(0),
                                    dst1));
                            ins.add(new SetCC(EQUALS,
                                    type.unsignedOrDoubleOrPointer(), dst1));
                        }
                    } else if (op1 == UnaryOperator.UNARY_MINUS && typeAsm == DOUBLE) {
                        ins.add(new Mov(typeAsm, src1, dst1));
                        ins.add(new Binary(BITWISE_XOR, typeAsm,
                                NEGATIVE_ZERO, dst1));
                    } else {
                        ins.add(new Mov(typeAsm, src1, dst1));
                        ins.add(new Unary(op1, typeAsm, dst1));
                    }
                }
                case ZeroExtendIr(ValIr src, VarIr dst) ->
                        ins.add(new MovZeroExtend(valToAsmType(src),
                                valToAsmType(dst), toOperand(src),
                                toOperand(dst)));
                case CopyFromOffset(ValIr srcV, long offset1, VarIr dstV) -> {
                    Operand src = toOperand(srcV, (int) offset1);
                    TypeAsm typeAsm = valToAsmType(dstV);
                    if (typeAsm instanceof ByteArray(long size,
                                                     long alignment)) {
                        Operand dst = toOperand(dstV, 0);
                        copyBytes(ins, src, dst, size);
                    } else ins.add(new Mov(typeAsm, src, toOperand(dstV)));
                }
                case CopyBitsFromOffset(ValIr srcV, long offset1,int bitOffset,
                                        int bitWidth, VarIr dstV) -> {
                    Operand src = toOperand(srcV, (int) offset1);
                    TypeAsm typeAsm = valToAsmType(dstV);
                    var dst=toOperand(dstV);
                    ins.add(new Mov(typeAsm, src, dst));
                    ins.add(new Binary(UNSIGNED_RIGHT_SHIFT, typeAsm , new Imm(bitOffset), dst));
                    //bit mask to just keep width bits
                    long mask = (1L << bitWidth) - 1;
                    ins.add(new Binary(BITWISE_AND, typeAsm , new Imm(mask), dst));
                }
                case Ignore.IGNORE -> {}
                case BuiltinC23VaStartIr(VarIr vaList) -> {
//                    field   offset
//                    gp_offset	0
//                    fp_offset	4
//                    overflow_arg_area	8
//                    reg_save_area	16

                    // gp_offset The element holds the offset in bytes from
                    // reg_save_area to the
                    //place where the next available general purpose argument
                    // register is saved

                    ins.add(new Mov(LONGWORD, Imm.ZERO,
                            new PseudoMem(vaList.identifier(), 0)));

                    //fp_offset The element holds the offset in bytes from
                    // reg_save_area to the
                    //place where the next available floating point argument
                    // register is saved.
                    ins.add(new Mov(LONGWORD, new Imm(48),
                            new PseudoMem(vaList.identifier(), 4)));
                    // overflow_arg_area
                    ins.add(new Lea(new Memory(BP, offset),
                            new PseudoMem(vaList.identifier(), 8)));

                    // reg_save_area The element points to the start of the
                    // register save area.
                    ins.add(new Lea(new Memory(BP, -176),
                            new PseudoMem(vaList.identifier(), 16)));


                    //      throw new Todo("implement prologue first");
                }
                case BuiltinVaArgIr(VarIr vaList, VarIr dst, Type type) -> {
                    emitBuiltInVarArg(vaList, dst, ins, type);
                }
                default ->
                        throw new IllegalStateException("Unexpected value: " + inst);
            }
        }
        return new FunctionAsm(functionIr.name(), functionIr.global(),
                returnInMemory, ins, toRegisters(returnValueClassification),
                functionIr.callsVaStart());
    }

    private static void emitBuiltInVarArg(VarIr vaList, VarIr dst,
                                          List<Instruction> ins, Type type) {

        int numGp = 0;
        int numFp = 0;
        boolean floatFirst = false;
        boolean canBePassedInRegisters = false;
        long typeSize = Mcc.size(type);

        if (type.isScalar()) {
            canBePassedInRegisters = true;
            if (type == Primitive.DOUBLE) numFp++;
            else numGp++;
        } else if (typeSize <= 16) {
            canBePassedInRegisters = true;
            if (type instanceof Structure structure) {
                List<StructureType> classes = classifyStructure(structure);
                for (int i = 0; i < classes.size(); i++) {
                    var c = classes.get(i);
                    if (c == StructureType.SSE) {
                        numFp++;
                        floatFirst = i == 0;
                    } else if (c == StructureType.INTEGER) numGp++;
                }
            }
        }

        LabelIr stackLabel = newLabel(Mcc.makeTemporary(".Lstack."));
        LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));

        Operand gpOffset = new PseudoMem(vaList.identifier(), 0);
        Operand fpOffset = new PseudoMem(vaList.identifier(), 4);

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
                ins.add(new Cmp(LONGWORD, new Imm(48 - numGp * 8L), gpOffset));
                // if not use stack
                ins.add(new JmpCC(CmpOperator.GREATER_THAN, true, stackLabel.label()));
            }
            if (numFp>0) {
                // is register available?
                ins.add(new Cmp(LONGWORD, new Imm(176 - numFp * 16L), fpOffset));
                // if not use stack
                ins.add(new JmpCC(CmpOperator.GREATER_THAN, true, stackLabel.label()));
            }
// 4. Fetch type from l->reg_save_area with an offset of l->gp_offset and/or
// l->fp_offset. This may require copying to a temporary location in case the
// parameter is passed in different register classes or requires an alignment
// greater
// than 8 for general purpose registers and 16 for XMM registers.
            for (int i = numFp + numGp - 1; i >= 0; i--) {
                boolean isFp = numGp == 0 || (numFp == 1 && (floatFirst ?
                        i == 0 : i == 1));

                // add gp_offset/fp_offset to reg_save_area
                var regSaveArea = new PseudoMem(vaList.identifier(), 16);
                ins.add(new Mov(QUADWORD, regSaveArea, AX));
                ins.add(new Mov(LONGWORD, isFp ? fpOffset : gpOffset, DX));
                ins.add(new Binary(ADD, QUADWORD, DX, AX));
                // mov what's at address gp_offset+reg_save_area to dst
                int offset=i*8;
// 6. Return the fetched type.
                ins.add(new Mov(QUADWORD, new Memory(AX, 0),
                        toOperand(dst, offset)));
            }

// 5. Set:
// l->gp_offset = l->gp_offset + num_gp ∗ 8
// l->fp_offset = l->fp_offset + num_fp ∗ 16.            if (numGp > 0)
                ins.add(new Binary(ADD, LONGWORD, new Imm(8L * numGp), gpOffset));
            if (numFp > 0)
                ins.add(new Binary(ADD, LONGWORD, new Imm(16L * numFp), fpOffset));
            ins.add(new Jump(endLabel.label()));


        }



// 7. Align l->overflow_arg_area upwards to a 16 byte boundary if alignment
// needed by
// type exceeds 8 byte boundary.



        ins.add(stackLabel);

        // 8. Fetch type from l->overflow_arg_area.
// 9. Set l->overflow_arg_area to:
// l->overflow_arg_area + sizeof(type)
        var overFlowArgArea = new PseudoMem(vaList.identifier(), 8);
        ins.add(new Mov(QUADWORD, overFlowArgArea, AX));
//        ins.add(new Mov(QUADWORD, new Memory(AX, 0), toOperand(dst)));
        copyBytes(ins,new Memory(AX, 0),toOperand(dst,0), typeSize);
// 10. Align l->overflow_arg_area upwards to an 8 byte boundary.
// 11. Return the fetched type.
        long nextSlotOffset=ProgramAsm.roundAwayFromZero(typeSize, Math.max(Mcc.typeAlignment(type), 8));
        ins.add(new Binary(ADD, QUADWORD, new Imm(nextSlotOffset), overFlowArgArea));
        ins.add(endLabel);

    }

    private static void compareDouble(CmpOperator op1, TypeAsm typeAsm,
                                      Operand subtrahend, Operand minuend,
                                      Operand dst, List<Instruction> ins) {
        ins.add(new Cmp(typeAsm, subtrahend, minuend));
        if (op1 == EQUALS || op1 == NOT_EQUALS) {
            LabelIr isANLabel = newLabel(Mcc.makeTemporary(".Lan."));

            ins.add(new JmpCC(null, false, // null false -> jnp (i.e. jump if
                    // not NaN)
                    isANLabel.label()));
            // for NaN : == is always false and != is always true
            ins.add(new Mov(LONGWORD, op1 == EQUALS ? Imm.ZERO : Imm.ONE, dst));
            LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
            ins.add(new Jump(endLabel.label()));

            ins.add(isANLabel);
            ins.add(new Mov(LONGWORD, Imm.ZERO, dst));
            ins.add(new SetCC(op1, true, dst));

            ins.add(endLabel);

        } else {
            ins.add(new Mov(LONGWORD, Imm.ZERO, dst));
            LabelIr isNaNLabel = newLabel(Mcc.makeTemporary(".Lnan."));
            ins.add(new JmpCC(null, true, isNaNLabel.label()));
            ins.add(new SetCC(op1, true, dst));
            ins.add(isNaNLabel);
        }
    }

    final static Pair<Integer, Integer> NO_REGS = new Pair(0, 0);

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
            Type t = valToType(val);
            copyBytes(ins, retVal, returnStorage, (int) Mcc.size(t));
        } else {
            int regIndex = 0;
            for (TypedOperand to : intDests) {
                Operand op = to.operand();
                TypeAsm t = to.type();
                var r = INTEGER_RETURN_REGISTERS[regIndex];
                if (t instanceof ByteArray(long size, long alignment)) {
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
            if (type == DOUBLE) {
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
                    case PseudoMem(String id, long off) -> {
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

    public static final Map<String, ParameterClassification> PARAMETER_CLASSIFICATION_MAP = new HashMap<>();

    private static TypeAsm getEightbyteType(long offset, long structSize) {
        long bytesFromEnd = structSize - offset;
        if (bytesFromEnd >= 8) return QUADWORD;
        if (bytesFromEnd == 4) return LONGWORD;
        if (bytesFromEnd == 1) return BYTE;
        return new ByteArray(bytesFromEnd, 8);
    }


    enum StructureType {MEMORY, SSE, INTEGER}

    public static List<StructureType> classifyStructure(Type t) {
        long size = Mcc.size(t);
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
            ArrayList<MemberEntry> members = Mcc.members(s);
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
            ArrayList<MemberEntry> members = Mcc.members(s);
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
            long elemSize = Mcc.size(element);
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

    private static void flattenTypes(List<Type> types,
                                     ArrayList<MemberEntry> members) {
        for (var m : members) {
            Type type = m.type();
            switch (type) {
                case Array(Type element, Constant arraySize) ->
                        types.addAll(Collections.nCopies((int) arraySize.toLong(), element));
                case Structure(boolean isUnion, String tag, StructDef _) ->
                        flattenTypes(types, Mcc.TYPE_TABLE.get(tag).members());
                default -> types.add(type);
            }
        }
    }

}
