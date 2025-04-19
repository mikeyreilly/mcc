package com.quaxt.mcc.asm;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.semantic.*;
import com.quaxt.mcc.tacky.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.EQUALS;
import static com.quaxt.mcc.CmpOperator.NOT_EQUALS;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.asm.DoubleReg.*;
import static com.quaxt.mcc.asm.Nullary.RET;
import static com.quaxt.mcc.asm.Reg.*;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.*;
import static com.quaxt.mcc.semantic.Primitive.UCHAR;
import static com.quaxt.mcc.tacky.IrGen.newLabel;
import static com.quaxt.mcc.UnaryOperator.SHR;

public class Codegen {
    static HashMap<Double, StaticConstant> CONSTANT_TABLE = new HashMap<>();
    private static final Data NEGATIVE_ZERO;
    private static final Data UPPER_BOUND;

    private static String toHexString(double d) {
        return Double.toHexString(d).replaceAll("-", "_");
    }

    private static final Imm UPPER_BOUND_LONG_IMMEDIATE = new Imm(1L << 63);

    static {
        double negative_zero = -0.0;
        // can't just call resolve constant because 16-byte alignment
        CONSTANT_TABLE.put(negative_zero, new StaticConstant("c." + toHexString(negative_zero), 16, new DoubleInit(negative_zero)));
        NEGATIVE_ZERO = resolveConstant(-0.0d);
        UPPER_BOUND = resolveConstant(0x1.0p63);
    }

    public static ProgramAsm generateProgramAssembly(ProgramIr programIr) {
        ArrayList<TopLevelAsm> topLevels = new ArrayList<>();
        for (TopLevel topLevel : programIr.topLevels()) {
            switch (topLevel) {
                case FunctionIr f -> topLevels.add(generateAssembly(f));

                case StaticVariable(String name, boolean global, Type t,
                                    List<StaticInit> init) -> {

                    topLevels.add(new StaticVariableAsm(name, global, alignment(t), init));
                }
                case com.quaxt.mcc.tacky.StaticConstant(String name, Type t,
                                                        StaticInit init) ->
                        topLevels.add(new StaticConstant(name, alignment(t), init));
            }
        }
        topLevels.addAll(CONSTANT_TABLE.values());
        generateBackendSymbolTable();

        for (TopLevelAsm topLevelAsm : topLevels) {
            if (topLevelAsm instanceof FunctionAsm functionAsm) {

                List<Instruction> instructionAsms = functionAsm.instructions();
                AtomicInteger offset = replacePseudoRegisters(instructionAsms);
                fixUpInstructions(offset, instructionAsms);
            }
        }

        return new ProgramAsm(topLevels);
    }

    private static int alignment(Type t) {
        return switch (t) {
            case Array(Type element, Constant arraySize) ->
                    (long) Mcc.size(t) < 16 ? alignment(element) : 16;
            case FunType _ -> 8;
            case Pointer _ -> 8;
            case Primitive primitive -> switch (primitive) {
                case CHAR -> 1;
                case UCHAR -> 1;
                case SCHAR -> 1;
                case INT -> 4;
                case UINT -> 4;
                case LONG -> 8;
                case ULONG -> 8;
                case DOUBLE -> 8;
                case VOID -> throw new Err("no alignment for void");
            };
            default -> throw new Todo();
        };
    }

    public static Map<String, SymTabEntryAsm> BACKEND_SYMBOL_TABLE = new HashMap<>();

    private static void generateBackendSymbolTable() {
        for (Map.Entry<String, SymbolTableEntry> e : SYMBOL_TABLE.entrySet()) {
            SymbolTableEntry v = e.getValue();
            IdentifierAttributes attrs = v.attrs();

            BACKEND_SYMBOL_TABLE.put(e.getKey(), switch (attrs) {
                case FunAttributes(boolean defined, boolean _) ->
                        new FunEntry(defined);
                case IdentifierAttributes.LocalAttr _ ->
                        new ObjEntry(toTypeAsm(v.type()), false, false);
                case StaticAttributes _ ->
                        new ObjEntry(toTypeAsm(v.type()), true, false);
                case ConstantAttr _ ->
                        new ObjEntry(toTypeAsm(v.type()), true, true);
            });
        }
        for (StaticConstant v : CONSTANT_TABLE.values()) {
            BACKEND_SYMBOL_TABLE.put(v.label(), new ObjEntry(DOUBLE, true, true));
        }
    }

    private static ArithmeticOperator convertOp(ArithmeticOperator op1, TypeAsm typeAsm) {
        return typeAsm == DOUBLE ? switch (op1) {
            case SUB -> DOUBLE_SUB;
            case ADD -> DOUBLE_ADD;
            case IMUL -> DOUBLE_MUL;
            case DIVIDE -> DOUBLE_DIVIDE;
            default -> op1;
        } : op1;
    }

    private static AtomicInteger replacePseudoRegisters(List<Instruction> instructions) {
        AtomicInteger offset = new AtomicInteger(-8);
        Map<String, Integer> varTable = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            Instruction oldInst = instructions.get(i);
            Instruction newInst = switch (oldInst) {
                case Nullary _, Cdq _, Jump _, JmpCC _, LabelIr _, Call _ ->
                        oldInst;
                case Mov(TypeAsm typeAsm, Operand src, Operand dst) ->
                        new Mov(typeAsm, dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
                case Unary(UnaryOperator op, TypeAsm typeAsm,
                           Operand operand) ->
                        new Unary(op, typeAsm, dePseudo(operand, varTable, offset));
                case Binary(ArithmeticOperator op, TypeAsm typeAsm, Operand src,
                            Operand dst) ->
                        new Binary(op, typeAsm, dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));

                case Cmp(TypeAsm typeAsm, Operand subtrahend,
                         Operand minuend) ->
                        new Cmp(typeAsm, dePseudo(subtrahend, varTable, offset), dePseudo(minuend, varTable, offset));
                case SetCC(CmpOperator cmpOperator, boolean signed,
                           Operand operand) ->
                        new SetCC(cmpOperator, signed, dePseudo(operand, varTable, offset));
                case Push(Operand operand) ->
                        new Push(dePseudo(operand, varTable, offset));
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) ->
                        new Movsx(srcType, dstType, dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) ->
                        new MovZeroExtend(srcType, dstType, dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
                case Cvttsd2si(TypeAsm dstType, Operand src, Operand dst) ->
                        new Cvttsd2si(dstType, dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
                case Cvtsi2sd(TypeAsm dstType, Operand src, Operand dst) ->
                        new Cvtsi2sd(dstType, dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
                case Lea(Operand src, Operand dst) ->
                        new Lea(dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
            };
            instructions.set(i, newInst);
        }
        return offset;
    }

    private static void fixUpInstructions(AtomicInteger offset, List<Instruction> instructions) {
        // Fix up instructions
        int stackSize = -offset.get();
        // round up to next multiple of 16 (makes it easier to maintain
        // alignment during function calls
        int remainder = stackSize % 16;
        if (remainder != 0) {
            stackSize += (16 - remainder);
        }
        instructions.addFirst(new Binary(SUB, QUADWORD, new Imm(stackSize), SP));
        // Fix illegal MOV, iDiV, ADD, SUB, IMUL instructions
        for (int i = instructions.size() - 1; i >= 0; i--) {
            Instruction oldInst = instructions.get(i);
            switch (oldInst) {
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) -> {
                    // if srcType is not byte we rewrite as Mov
                    if (srcType == BYTE) {
                        //   boolean mustFixSrc = src instanceof Imm;
                        boolean mustFixDst = !(dst instanceof Reg);

                        if (src instanceof Imm(long i1)) {
                            src = new Imm(i1 & 0xff);
                            if (mustFixDst) {
                                instructions.set(i, new Mov(srcType, src, srcReg(dstType)));
                                instructions.set(i + 1, new MovZeroExtend(srcType, dstType, srcReg(dstType), dstReg(dstType)));
                                instructions.set(i + 2, new Mov(dstType, dstReg(dstType), dst));
                            } else {
                                instructions.set(i, new Mov(srcType, src, srcReg(dstType)));
                                instructions.set(i + 1, new MovZeroExtend(srcType, dstType, srcReg(dstType), dst));
                            }
                        } else if (mustFixDst) {
                            instructions.set(i, new MovZeroExtend(srcType, dstType, src, dstReg(dstType)));
                            instructions.add(i + 1, new Mov(dstType, dstReg(dstType), dst));
                        }
                    } else {
                        if (src instanceof Imm(long i1)) {
                            if (srcType == LONGWORD) src = new Imm((int) i1);
                        }

                        if (dst instanceof Reg) {
                            instructions.set(i, new Mov(srcType, src, dst));
                        } else {
                            instructions.set(i, new Mov(srcType, src, dstReg(dstType)));
                            instructions.add(i + 1, new Mov(dstType, dstReg(dstType), dst));
                        }
                    }
                }
                case Unary(UnaryOperator op, TypeAsm typeAsm,
                           Operand operand) -> {
                    if ((op == UnaryOperator.IDIV || op == UnaryOperator.DIV) && operand instanceof Imm) {
                        instructions.set(i, new Mov(typeAsm, operand, srcReg(typeAsm)));
                        instructions.add(i + 1, new Unary(op, typeAsm, srcReg(typeAsm)));
                    }
                }
                case Mov(TypeAsm typeAsm, Operand src, Operand dst) -> {
                    if (isRam(src) && isRam(dst)) {
                        instructions.set(i, new Mov(typeAsm, src, srcReg(typeAsm)));
                        instructions.add(i + 1, new Mov(typeAsm, srcReg(typeAsm), dst));
                    } else if (isRam(dst) && typeAsm == QUADWORD && src instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(typeAsm, src, srcReg(typeAsm)));
                        instructions.add(i + 1, new Mov(typeAsm, srcReg(typeAsm), dst));
                    }
                }
                case Lea(Operand src, Operand dst) -> {
                    if (!(dst instanceof Reg)) {
                        instructions.set(i, new Lea(src, dstReg(QUADWORD)));
                        instructions.add(i + 1, new Mov(QUADWORD, dstReg(QUADWORD), dst));
                    }
                }
                case Push(Operand operand) -> {
                    if (operand instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(QUADWORD, operand, srcReg(QUADWORD)));
                        instructions.add(i + 1, new Push(srcReg(QUADWORD)));
                    }
                }
                case Binary(ArithmeticOperator op, TypeAsm typeAsm, Operand src,
                            Operand dst) -> {
                    if (src instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(typeAsm, src, srcReg(typeAsm)));
                        instructions.add(i + 1, new Binary(op, typeAsm, srcReg(typeAsm), dst));
                        i = i + 2; // will be i+1 at start of next iteration -> want to catch conditions below
                    } else {
                        switch (op) {
                            case ADD, SUB -> {
                                if (isRam(src) && isRam(dst)) {
                                    instructions.set(i, new Mov(typeAsm, src, srcReg(typeAsm)));
                                    instructions.add(i + 1, new Binary(op, typeAsm, srcReg(typeAsm), dst));
                                }

                            }
                            case IMUL, DOUBLE_SUB, DOUBLE_ADD, DOUBLE_MUL,
                                 DOUBLE_DIVIDE, BITWISE_XOR -> {
                                if (isRam(dst)) {
                                    instructions.set(i, new Mov(typeAsm, dst, dstReg(typeAsm)));
                                    instructions.add(i + 1, new Binary(op, typeAsm, src, dstReg(typeAsm)));
                                    instructions.add(i + 2, new Mov(typeAsm, dstReg(typeAsm), dst));
                                }
                            }

                        }
                    }

                }

                case Cmp(TypeAsm typeAsm, Operand src, Operand dst) -> {
                    if (typeAsm == DOUBLE && !(dst instanceof DoubleReg)) {
                        instructions.set(i, new Mov(typeAsm, dst, dstReg(typeAsm)));
                        instructions.add(i + 1, new Cmp(typeAsm, src, dstReg(typeAsm)));
                    } else if (isRam(src) && isRam(dst)) {
                        instructions.set(i, new Mov(typeAsm, src, srcReg(typeAsm)));
                        instructions.add(i + 1, new Cmp(typeAsm, srcReg(typeAsm), dst));
                    } else if (dst instanceof Imm) {
                        if (src instanceof Imm imm && imm.isAwkward()) {
                            instructions.set(i, new Mov(typeAsm, src, srcReg(typeAsm)));
                            instructions.add(i + 1, new Mov(typeAsm, dst, dstReg(typeAsm)));
                            instructions.add(i + 2, new Cmp(typeAsm, srcReg(typeAsm), dstReg(typeAsm)));
                        } else {
                            instructions.set(i, new Mov(typeAsm, dst, dstReg(typeAsm)));
                            instructions.add(i + 1, new Cmp(typeAsm, src, dstReg(typeAsm)));
                        }
                    } else if (src instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(typeAsm, src, srcReg(typeAsm)));
                        instructions.add(i + 1, new Cmp(typeAsm, srcReg(typeAsm), dst));
                    }
                }
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) -> {
                    if (src instanceof Imm) {
                        instructions.set(i, new Mov(srcType, src, R10));
                        if (isRam(dst)) {
                            instructions.add(i + 1, new Movsx(srcType, dstType, R10, R11));
                            instructions.add(i + 2, new Mov(dstType, R11, dst));
                        } else {
                            instructions.add(i + 1, new Movsx(srcType, dstType, R10, dst));
                        }
                    } else {
                        if (isRam(dst)) {
                            instructions.set(i, new Movsx(srcType, dstType, src, R11));
                            instructions.add(i + 1, new Mov(dstType, R11, dst));
                        }
                    }
                }
                case Cvttsd2si(TypeAsm dstType, Operand src, Operand dst) -> {
                    if (isRam(dst)) {
                        instructions.set(i, new Cvttsd2si(dstType, src, R11));
                        instructions.add(i + 1, new Mov(dstType, R11, dst));
                    }
                }
                case Cvtsi2sd(TypeAsm dstType, Operand src, Operand dst) -> {

                    if (src instanceof Imm) {
                        instructions.set(i, new Mov(dstType, src, R10));
                        if (isRam(dst)) {
                            instructions.add(i + 1, new Cvtsi2sd(dstType, R10, XMM15));
                            instructions.add(i + 2, new Mov(QUADWORD, XMM15, dst));
                        } else {
                            instructions.add(i + 1, new Cvtsi2sd(dstType, R10, dst));
                        }
                    } else if (isRam(dst)) {
                        instructions.set(i, new Cvtsi2sd(dstType, src, XMM15));
                        instructions.add(i + 1, new Mov(QUADWORD, XMM15, dst));
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

    private static Type valToType(ValIr val) {
        return switch (val) {
            case Constant constant -> constant.type();
            case VarIr(String identifier) ->
                    SYMBOL_TABLE.get(identifier).type();
        };
    }

    private static TypeAsm toTypeAsm(Type type) {
        return switch (type) {
            case Primitive.CHAR, UCHAR, Primitive.SCHAR -> BYTE;
            case Primitive.INT, Primitive.UINT -> LONGWORD;
            case Primitive.LONG, Primitive.ULONG -> QUADWORD;
            case Primitive.DOUBLE -> DOUBLE;
            case Pointer _ -> QUADWORD;
            case Array array -> {
                long size = Mcc.size(array);
                long alignment;
                alignment = size < 16 && array.element().isScalar() ? Mcc.size(array.element()) : 16;
                yield new ByteArray((int) size, (int) alignment);
            }
            default ->
                    throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private static boolean isRam(Operand src) {
        return src instanceof Memory || src instanceof Indexed || src instanceof Data;
    }

    private final static Reg[] INTEGER_REGISTERS = new Reg[]{DI, SI, DX, CX, R8, R9};
    private final static DoubleReg[] DOUBLE_REGISTERS = new DoubleReg[]{XMM0, XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7};

    public static Data resolveConstant(double d) {
        StaticConstant c = CONSTANT_TABLE.computeIfAbsent(d, _ -> new StaticConstant("c." + toHexString(d), 8, new DoubleInit(d)));
        return new Data(c.label());
    }

    private static Operand toOperand(ValIr val) {
        return switch (val) {
            case ConstChar(byte i) -> new Imm(i);
            case ConstUChar(byte i) -> new Imm(i);
            case ConstInt(int i) -> new Imm(i);
            case VarIr(String identifier) ->
                    valToType(val) instanceof Array ? new PseudoMem(identifier, 0) : new Pseudo(identifier);
            case ConstLong(long l) -> new Imm(l);
            case ConstUInt(int i) -> new Imm(i);
            case ConstULong(long l) -> new Imm(l);
            case ConstDouble(double d) -> resolveConstant(d);
            default -> throw new Todo("can't yet handle" + val);
        };
    }

    private static Operand toOperand(ValIr val, int offset) {
        return switch (val) {
            case VarIr(
                    String identifier) when valToType(val) instanceof Array ->
                    new PseudoMem(identifier, offset);
            default -> throw new AssertionError(val);
        };
    }

    private static Operand dePseudo(Operand in, Map<String, Integer> varTable, AtomicInteger offsetA) {
        int offsetFromStartOfArray;
        String identifier;
        switch (in) {
            case Imm _, Reg _, Memory _, DoubleReg _, Data _, Indexed _:
                return in;
            case Pseudo(String pIdentifier):
                identifier = pIdentifier;
                offsetFromStartOfArray = 0;
                break;
            case PseudoMem(String pIdentifier, int pOffsetFromStartOfArray):
                identifier = pIdentifier;
                offsetFromStartOfArray = pOffsetFromStartOfArray;
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (BACKEND_SYMBOL_TABLE.get(identifier) instanceof ObjEntry(
                TypeAsm type, boolean isStatic, boolean _)) {
            int size = type.size();
            int alignment = type.alignment();


            if (isStatic) return new Data(identifier);

            Integer varOffset = varTable.get(identifier);
            if (varOffset == null) {
                // it starts ar -8 - we can use this for the first var
                // when that var is written it will update bytes stack-8 to stack-1
                varOffset = offsetA.get();
                varOffset -= size;
                int remainder = varOffset % alignment;
                if (remainder != 0) {
                    varOffset -= (alignment + remainder);
                }
                varTable.put(identifier, varOffset);
                //System.out.println(identifier+"\t"+size+"\t"+varOffset+"\t"+remainder);
                offsetA.set(varOffset);
            }
            return new Memory(BP, varOffset + offsetFromStartOfArray);


        } else throw new IllegalArgumentException(identifier);
    }

    private static void codegenFunCall(FunCall funCall, List<Instruction> instructionAsms) {
        // so for classify we can classify operands here
        if (funCall instanceof FunCall(String name, ArrayList<ValIr> args,
                                       ValIr dst)) {
            List<TypedOperand> operands = new ArrayList<>();
            for (ValIr arg : args) {
                TypeAsm typeAsm = valToAsmType(arg);
                Operand operand = toOperand(arg);
                operands.add(new TypedOperand(typeAsm, operand));
            }
            ParameterClassification classifiedArgs = classifyParameters(operands);
            ArrayList<TypedOperand> integerArguments = classifiedArgs.integerArguments();
            ArrayList<Operand> doubleArguments = classifiedArgs.doubleArguments();
            ArrayList<TypedOperand> stackArguments = classifiedArgs.stackArguments();
            int stackArgCount = stackArguments.size();
            int stackPadding = stackArgCount % 2 == 1 ? 8 : 0;
            if (stackPadding != 0) {
                instructionAsms.add(new Binary(SUB, QUADWORD, new Imm(stackPadding), SP));
            }

            for (int i = 0; i < integerArguments.size(); i++) {
                var integerArg = integerArguments.get(i);
                Reg r = INTEGER_REGISTERS[i];
                instructionAsms.add(new Mov(integerArg.type(), integerArg.operand(), r));
            }
            for (int i = 0; i < doubleArguments.size(); i++) {
                Operand doubleArg = doubleArguments.get(i);
                DoubleReg r = DOUBLE_REGISTERS[i];
                instructionAsms.add(new Mov(DOUBLE, doubleArg, r));
            }
            for (int i = stackArguments.size() - 1; i >= 0; i--) {
                TypedOperand to = stackArguments.get(i);
                Operand operand = to.operand();
                if (operand instanceof Imm || operand instanceof Reg || to.type() == QUADWORD || to.type() == DOUBLE) {
                    instructionAsms.add(new Push(operand));
                } else {
                    instructionAsms.add(new Mov(LONGWORD, operand, AX));
                    instructionAsms.add(new Push(AX));
                }
            }
            instructionAsms.add(new Call(name));
            int bytesToRemove = 8 * stackArgCount + stackPadding;
            if (bytesToRemove != 0) {
                instructionAsms.add(new Binary(ADD, QUADWORD, new Imm(bytesToRemove), SP));
            }
            if (dst != null) {// dst is null for void functions
                TypeAsm returnType = valToAsmType(dst);

                instructionAsms.add(new Mov(returnType, returnType == DOUBLE ? XMM0 : AX, toOperand(dst)));
            }
        }
    }

    public static FunctionAsm generateAssembly(FunctionIr functionIr) {
        // here we can convert arguments to pseudos (which are operands)
        List<Instruction> ins = new ArrayList<>();
        List<Var> functionType = functionIr.type();
        List<TypedOperand> operands = new ArrayList<>();
        for (Var param : functionType) {
            operands.add(new TypedOperand(toTypeAsm(param.type()), new Pseudo(param.name())));
        }
        ParameterClassification classifiedParameters = classifyParameters(operands);
        ArrayList<TypedOperand> integerArguments = classifiedParameters.integerArguments();
        ArrayList<Operand> doubleArguments = classifiedParameters.doubleArguments();
        ArrayList<TypedOperand> stackArguments = classifiedParameters.stackArguments();

        for (int i = 0; i < integerArguments.size(); i++) {
            TypedOperand to = integerArguments.get(i);
            ins.add(new Mov(to.type(), INTEGER_REGISTERS[i], to.operand()));
        }

        for (int i = 0; i < doubleArguments.size(); i++) {
            Operand operand = doubleArguments.get(i);
            ins.add(new Mov(DOUBLE, DOUBLE_REGISTERS[i], operand));
        }
        for (int i = 0; i < stackArguments.size(); i++) {
            TypedOperand to = stackArguments.get(i);
            ins.add(new Mov(to.type(), new Memory(BP, 16 + i * 8), to.operand()));
        }

        for (InstructionIr inst : functionIr.instructions()) {
            switch (inst) {
                case AddPtr(ValIr ptrV, ValIr indexV, int scale,
                            ValIr dstV) -> {
                    Operand ptr = toOperand(ptrV);
                    Operand index = toOperand(indexV);
                    Operand dst = toOperand(dstV);
                    ins.add(new Mov(QUADWORD, ptr, AX));
                    ins.add(new Mov(QUADWORD, index, DX));
                    switch (scale) {
                        case 1, 2, 4, 8 ->
                                ins.add(new Lea(new Indexed(AX, DX, scale), dst));
                        default -> {
                            // MR-TODO we can save an instruction when index is a constant. See table 15-2 p. 416
                            ins.add(new Binary(IMUL, QUADWORD, new Imm(scale), DX));
                            ins.add(new Lea(new Indexed(AX, DX, 1), dst));
                        }
                    }
                }
                case BinaryIr(ArithmeticOperator op1, ValIr v1, ValIr v2,
                              VarIr dstName) -> {
                    Type type = valToType(v1);
                    TypeAsm typeAsm = toTypeAsm(type);
                    assert (typeAsm == valToAsmType(v2));
                    if (typeAsm == DOUBLE) {
                        ins.add(new Mov(typeAsm, toOperand(v1), toOperand(dstName)));
                        ins.add(new Binary(convertOp(op1, typeAsm), typeAsm, toOperand(v2), toOperand(dstName)));
                    } else {
                        switch (op1) {
                            case ADD, SUB, IMUL -> {
                                ins.add(new Mov(typeAsm, toOperand(v1), toOperand(dstName)));
                                ins.add(new Binary(op1, typeAsm, toOperand(v2), toOperand(dstName)));
                            }
                            case DIVIDE, REMAINDER -> {
                                if (type.isSigned()) {
                                    ins.add(new Mov(typeAsm, toOperand(v1), AX));
                                    ins.add(new Cdq(typeAsm));
                                    ins.add(new Unary(UnaryOperator.IDIV, typeAsm, toOperand(v2)));
                                    ins.add(new Mov(typeAsm, op1 == DIVIDE ? AX : DX, toOperand(dstName)));
                                } else {
                                    ins.add(new Mov(typeAsm, toOperand(v1), AX));
                                    ins.add(new Mov(typeAsm, new Imm(0), DX));
                                    ins.add(new Unary(UnaryOperator.DIV, typeAsm, toOperand(v2)));
                                    ins.add(new Mov(typeAsm, op1 == DIVIDE ? AX : DX, toOperand(dstName)));
                                }
                            }
                            default ->
                                    throw new IllegalStateException("Unexpected value: " + op1);
                        }
                    }
                }
                case BinaryIr(CmpOperator op1, ValIr v1, ValIr v2,
                              VarIr dstName) -> {
                    Type type = valToType(v1);
                    TypeAsm typeAsm = toTypeAsm(type);
                    assert (typeAsm == valToAsmType(v2));
                    ins.add(new Cmp(typeAsm, toOperand(v2), toOperand(v1)));
                    // dstName will hold the result of the comparison, which is always a LONGWORD
                    ins.add(new Mov(LONGWORD, new Imm(0), toOperand(dstName)));
                    ins.add(new SetCC(op1, type.unsignedOrDoubleOrPointer(), toOperand(dstName)));
                }
                case Copy(ValIr val, VarIr dst1) -> {
                    TypeAsm typeAsm = valToAsmType(val);
                    assert (typeAsm == valToAsmType(dst1));
                    ins.add(new Mov(typeAsm, toOperand(val), toOperand(dst1)));
                }
                case CopyToOffset(ValIr srcV, VarIr dstV, long offset) -> {
                    Operand src = toOperand(srcV);
                    Operand dst = toOperand(dstV, (int)offset);
                    TypeAsm typeAsm = valToAsmType(srcV);
                    ins.add(new Mov(typeAsm, src, dst));
                }
                case DoubleToInt(ValIr src, VarIr dst) -> {
                    var dstType = valToType(dst);
                    var dstTypeAsm = toTypeAsm(dstType);
                    if (dstType == Primitive.CHAR || dstType == Primitive.SCHAR) {
                        ins.add(new Cvttsd2si(LONGWORD, toOperand(src), AX));
                        ins.add(new Mov(BYTE, AX, toOperand(dst)));
                    } else
                        ins.add(new Cvttsd2si(dstTypeAsm, toOperand(src), toOperand(dst)));
                }
                case DoubleToUInt(ValIr src, ValIr dst) -> {
                    Type dstType = valToType(dst);
                    if (dstType == UCHAR) {
                        ins.add(new Cvttsd2si(LONGWORD, toOperand(src), AX));
                        ins.add(new Mov(BYTE, AX, toOperand(dst)));
                    } else if (dstType == Primitive.INT) {
                        ins.add(new Cvttsd2si(QUADWORD, toOperand(src), AX));
                        ins.add(new Mov(LONGWORD, AX, toOperand(dst)));
                    } else {
                        //p.335
                        LabelIr label1 = newLabel("aub");
                        LabelIr label2 = newLabel("endCmp");
                        ins.add(new Cmp(DOUBLE, UPPER_BOUND, toOperand(src)));
                        ins.add(new JmpCC(CmpOperator.GREATER_THAN_OR_EQUAL, true, label1.label()));
                        ins.add(new Cvttsd2si(QUADWORD, toOperand(src), toOperand(dst)));
                        ins.add(new Jump(label2.label()));
                        ins.add(label1);
                        ins.add(new Mov(DOUBLE, toOperand(src), XMM0));
                        ins.add(new Binary(DOUBLE_SUB, DOUBLE, UPPER_BOUND, XMM0));
                        ins.add(new Cvttsd2si(QUADWORD, XMM0, toOperand(dst)));
                        ins.add(new Mov(QUADWORD, UPPER_BOUND_LONG_IMMEDIATE, AX));
                        ins.add(new Binary(ADD, QUADWORD, AX, toOperand(dst)));
                        ins.add(label2);
                    }
                }
                case FunCall funCall -> codegenFunCall(funCall, ins);
                case GetAddress(ValIr srcV, VarIr dstV) -> {
                    Operand src = toOperand(srcV);
                    Operand dst = toOperand(dstV);
                    ins.add(new Lea(src, dst));
                }
                case IntToDouble(ValIr src, VarIr dst) -> {
                    Type srcType = valToType(src);
                    if (srcType == Primitive.CHAR || srcType == Primitive.SCHAR) {
                        ins.add(new Movsx(BYTE, LONGWORD, toOperand(src), AX));
                        ins.add(new Cvtsi2sd(LONGWORD, AX, toOperand(dst)));
                    } else
                        ins.add(new Cvtsi2sd(toTypeAsm(valToType(src)), toOperand(src), toOperand(dst)));
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
                    ins.add(new JmpCC(NOT_EQUALS, type.unsignedOrDoubleOrPointer(), label));
                }
                case JumpIfZero(ValIr v, String label) -> {
                    Type type = valToType(v);
                    TypeAsm typeAsm = toTypeAsm(type);
                    if (typeAsm == DOUBLE) {
                        ins.add(new Binary(BITWISE_XOR, typeAsm, XMM0, XMM0));
                        ins.add(new Cmp(typeAsm, XMM0, toOperand(v)));
                    } else {
                        ins.add(new Cmp(typeAsm, new Imm(0), toOperand(v)));
                    }
                    ins.add(new JmpCC(EQUALS, type.unsignedOrDoubleOrPointer(), label));
                }
                case LabelIr labelIr -> ins.add(labelIr);
                case Load(ValIr ptrV, VarIr dstV) -> {
                    Operand ptr = toOperand(ptrV);
                    Operand dst = toOperand(dstV);
                    Type dstType = valToType(dstV);
                    ins.add(new Mov(QUADWORD, ptr, AX));
                    ins.add(new Mov(toTypeAsm(dstType), new Memory(AX, 0), dst));
                }
                case ReturnIr(ValIr val) -> {
                    if (val != null) {
                        Operand src1 = toOperand(val);
                        TypeAsm returnType = valToAsmType(val);
                        ins.add(new Mov(returnType, src1, returnType == DOUBLE ? XMM0 : AX));
                    }
                    ins.add(RET);
                }
                case SignExtendIr(ValIr src, VarIr dst) ->
                        ins.add(new Movsx(valToAsmType(src), valToAsmType(dst), toOperand(src), toOperand(dst)));
                case Store(ValIr srcV, ValIr ptrV) -> {
                    Operand src = toOperand(srcV);
                    Operand ptr = toOperand(ptrV);
                    ins.add(new Mov(QUADWORD, ptr, AX));
                    ins.add(new Mov(toTypeAsm(valToType(srcV)), src, new Memory(AX, 0)));
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
                    if (srcType == UCHAR) {
                        ins.add(new MovZeroExtend(BYTE, LONGWORD, src, AX));
                        ins.add(new Cvtsi2sd(LONGWORD, AX, dst));
                    } else if (srcType == Primitive.CHAR || srcType == Primitive.SCHAR) {
                        ins.add(new Movsx(BYTE, LONGWORD, src, AX));
                        ins.add(new Cvtsi2sd(LONGWORD, AX, dst));
                    } else if (srcType == Primitive.INT) {
                        ins.add(new MovZeroExtend(valToAsmType(srcV), valToAsmType(dstV), src, AX));
                        ins.add(new Cvtsi2sd(QUADWORD, AX, dst));
                    } else {
                        LabelIr label1 = newLabel("outOfRange");
                        LabelIr label2 = newLabel("end");
                        ins.add(new Cmp(QUADWORD, new Imm(0), src));
                        ins.add(new JmpCC(CmpOperator.LESS_THAN, false, label1.label()));
                        ins.add(new Cvtsi2sd(QUADWORD, src, dst));
                        ins.add(new Jump(label2.label()));
                        ins.add(label1);
                        ins.add(new Mov(QUADWORD, src, AX));
                        ins.add(new Mov(QUADWORD, AX, DX));
                        ins.add(new Unary(SHR, QUADWORD, DX));
                        ins.add(new Binary(AND, QUADWORD, new Imm(1), AX));
                        ins.add(new Binary(OR, QUADWORD, AX, DX));
                        ins.add(new Cvtsi2sd(QUADWORD, DX, dst));
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
                            ins.add(new Binary(BITWISE_XOR, DOUBLE, XMM0, XMM0));
                            ins.add(new Cmp(DOUBLE, XMM0, src1));
                            ins.add(new Mov(valToAsmType(dstIr), new Imm(0), dst1));
                            ins.add(new SetCC(EQUALS, true, dst1));
                        } else {
                            ins.add(new Cmp(typeAsm, new Imm(0), src1));
                            ins.add(new Mov(typeAsm, new Imm(0), dst1));
                            ins.add(new SetCC(EQUALS, type.unsignedOrDoubleOrPointer(), dst1));
                        }
                    } else if (op1 == UnaryOperator.UNARY_MINUS && typeAsm == DOUBLE) {
                        ins.add(new Mov(typeAsm, src1, dst1));
                        ins.add(new Binary(BITWISE_XOR, typeAsm, NEGATIVE_ZERO, dst1));
                    } else {
                        ins.add(new Mov(typeAsm, src1, dst1));
                        ins.add(new Unary(op1, typeAsm, dst1));
                    }
                }
                case ZeroExtendIr(ValIr src, VarIr dst) ->
                        ins.add(new MovZeroExtend(valToAsmType(src), valToAsmType(dst), toOperand(src), toOperand(dst)));

                default ->
                        throw new IllegalStateException("Unexpected value: " + inst);
            }
        }
        return new FunctionAsm(functionIr.name(), functionIr.global(), ins);
    }

    private record TypedOperand(TypeAsm type, Operand operand) {}

    private record ParameterClassification(
            ArrayList<TypedOperand> integerArguments,
            ArrayList<Operand> doubleArguments,
            ArrayList<TypedOperand> stackArguments) {}

    /*classify parameters or arguments*/
    private static ParameterClassification classifyParameters(List<TypedOperand> operands) {
        ArrayList<TypedOperand> integerArguments = new ArrayList<>();
        ArrayList<Operand> doubleArguments = new ArrayList<>();
        ArrayList<TypedOperand> stackArguments = new ArrayList<>();
        for (TypedOperand to : operands) {
            TypeAsm type = to.type();
            Operand operand = to.operand();
            if (type == DOUBLE) {
                if (doubleArguments.size() < 8) doubleArguments.add(operand);
                else stackArguments.add(to);
            } else {
                if (integerArguments.size() < 6) integerArguments.add(to);
                else stackArguments.add(to);
            }
        }
        return new ParameterClassification(integerArguments, doubleArguments, stackArguments);

    }

}
