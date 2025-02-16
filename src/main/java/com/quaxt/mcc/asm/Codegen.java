package com.quaxt.mcc.asm;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.EQUALS;
import static com.quaxt.mcc.CmpOperator.NOT_EQUALS;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.asm.Nullary.RET;
import static com.quaxt.mcc.asm.Reg.*;
import static com.quaxt.mcc.asm.TypeAsm.LONGWORD;
import static com.quaxt.mcc.asm.TypeAsm.QUADWORD;
import static com.quaxt.mcc.semantic.Primitive.*;

public class Codegen {

    public static ProgramAsm generateProgramAssembly(ProgramIr programIr) {
        ArrayList<TopLevelAsm> l = new ArrayList<>();
        for (TopLevel topLevel : programIr.topLevels()) {
            switch (topLevel) {
                case FunctionIr f -> l.add(generateAssembly(f));

                case StaticVariable(String name, boolean global, Type t,
                                    StaticInit init) -> {
                    int alignment = switch (t) {
                        case UINT, INT -> 4;
                        default -> 8;
                    };
                    l.add(new StaticVariableAsm(name, global, alignment, init));
                }
            }
        }

        generateBackendSymbolTable();

        for (TopLevelAsm topLevelAsm : l) {
            if (topLevelAsm instanceof FunctionAsm functionAsm) {

                List<Instruction> instructionAsms = functionAsm.instructions();
                AtomicInteger offset = replacePseudoRegisters(instructionAsms);
                fixUpInstructions(offset, instructionAsms);
            }
        }

        return new ProgramAsm(l);
    }

    static Map<String, SymTabEntryAsm> BACKEND_SYMBOL_TABLE = new HashMap<>();

    private static void generateBackendSymbolTable() {
        for (Map.Entry<String, SymbolTableEntry> e : SYMBOL_TABLE.entrySet()) {
            SymbolTableEntry v = e.getValue();
            IdentifierAttributes attrs = v.attrs();

            BACKEND_SYMBOL_TABLE.put(e.getKey(), switch (attrs) {
                case FunAttributes(boolean defined, boolean _) ->
                        new FunEntry(defined);
                case IdentifierAttributes.LocalAttr _ ->
                        new ObjEntry(toTypeAsm(v.type()), false);
                case StaticAttributes _ ->
                        new ObjEntry(toTypeAsm(v.type()), true);
            });
        }
    }

    public static FunctionAsm generateAssembly(FunctionIr functionIr) {
        List<Instruction> instructionAsms = new ArrayList<>();
        List<Identifier> functionType = functionIr.type();
        for (int i = 0; i < functionType.size() && i < 6; i++) {
            Identifier param = functionType.get(i);
            instructionAsms.add(new Mov(toTypeAsm(param.type()), registers[i], new Pseudo(param.name())));
        }
        for (int i = 6; i < functionType.size(); i++) {
            Identifier param = functionType.get(i);
            instructionAsms.add(new Mov(toTypeAsm(param.type()), new Stack(16 + (i - 6) * 8), new Pseudo(param.name())));
        }

        for (InstructionIr inst : functionIr.instructions()) {
            switch (inst) {
                case ReturnInstructionIr(ValIr val) -> {
                    Operand src1 = toOperand(val);
                    instructionAsms.add(new Mov(valToAsmType(val), src1, AX));
                    instructionAsms.add(RET);
                }
                case UnaryIr(UnaryOperator op1, ValIr srcIr, ValIr dstIr) -> {
                    Operand dst1 = toOperand(dstIr);
                    Operand src1 = toOperand(srcIr);
                    Type type = valToType(srcIr);
                    TypeAsm typeAsm = toTypeAsm(type);
                    if (op1 == UnaryOperator.NOT) {
                        instructionAsms.add(new Cmp(typeAsm, new Imm(0), src1));
                        instructionAsms.add(new Mov(typeAsm, new Imm(0), dst1));
                        instructionAsms.add(new SetCC(EQUALS, type.isSigned(), dst1));
                    } else {
                        instructionAsms.add(new Mov(typeAsm, src1, dst1));
                        instructionAsms.add(new Unary(op1, typeAsm, dst1));
                    }
                }
                case BinaryIr(
                        ArithmeticOperator op1, ValIr v1, ValIr v2,
                        VarIr dstName
                ) -> {
                    Type type = valToType(v1);
                    TypeAsm typeAsm = toTypeAsm(type);
                    assert (typeAsm == valToAsmType(v2));
                    switch (op1) {
                        case ADD, SUB, IMUL -> {
                            instructionAsms.add(new Mov(typeAsm, toOperand(v1), toOperand(dstName)));
                            instructionAsms.add(new Binary(op1, typeAsm, toOperand(v2), toOperand(dstName)));
                        }
                        case DIVIDE, REMAINDER -> {
                            // MR-TODO need to check if unsigned and if so do what's on p.288
                            if (type.isSigned()) {
                                instructionAsms.add(new Mov(typeAsm, toOperand(v1), AX));
                                instructionAsms.add(new Cdq(typeAsm));
                                instructionAsms.add(new Unary(UnaryOperator.IDIV, typeAsm, toOperand(v2)));
                                instructionAsms.add(new Mov(typeAsm, op1 == DIVIDE ? AX : DX, toOperand(dstName)));
                            } else {
                                instructionAsms.add(new Mov(typeAsm, toOperand(v1), AX));
                                instructionAsms.add(new Mov(typeAsm, new Imm(0), DX));
                                instructionAsms.add(new Unary(UnaryOperator.DIV, typeAsm, toOperand(v2)));
                                instructionAsms.add(new Mov(typeAsm, op1 == DIVIDE ? AX : DX, toOperand(dstName)));
                            }
                        }
                        default ->
                                throw new IllegalStateException("Unexpected value: " + op1);
                    }

                }
                case BinaryIr(
                        CmpOperator op1, ValIr v1, ValIr v2, VarIr dstName
                ) -> {
                    Type type = valToType(v1);
                    TypeAsm typeAsm = toTypeAsm(type);
                    assert (typeAsm == valToAsmType(v2));
                    instructionAsms.add(new Cmp(typeAsm, toOperand(v2), toOperand(v1)));
                    // dstName will hold the result of the comparison, which is always a LONGWORD
                    instructionAsms.add(new Mov(LONGWORD, new Imm(0), toOperand(dstName)));
                    instructionAsms.add(new SetCC(op1, type.isSigned(), toOperand(dstName)));
                }
                case Copy(ValIr val, VarIr dst1) -> {
                    TypeAsm typeAsm = valToAsmType(val);
                    assert (typeAsm == valToAsmType(dst1));
                    instructionAsms.add(new Mov(typeAsm, toOperand(val), toOperand(dst1)));
                }
                case Jump jump -> instructionAsms.add(jump);
                case JumpIfNotZero(ValIr v, String label) -> {
                    Type type = valToType(v);
                    TypeAsm typeAsm = toTypeAsm(type);
                    instructionAsms.add(new Cmp(typeAsm, new Imm(0), toOperand(v)));
                    instructionAsms.add(new JmpCC(NOT_EQUALS, type.isSigned(), label));
                }
                case JumpIfZero(ValIr v, String label) -> {
                    Type type = valToType(v);
                    TypeAsm typeAsm = toTypeAsm(type);
                    instructionAsms.add(new Cmp(typeAsm, new Imm(0), toOperand(v)));
                    instructionAsms.add(new JmpCC(EQUALS, type.isSigned(), label));
                }
                case LabelIr labelIr -> instructionAsms.add(labelIr);
                case FunCall funCall -> {
                    codegenFunCall(funCall, instructionAsms);
                }
                case SignExtendIr(ValIr src, VarIr dst) ->
                        instructionAsms.add(new Movsx(toOperand(src), toOperand(dst)));
                case TruncateIr(ValIr src, VarIr dst) ->
                        instructionAsms.add(new Mov(LONGWORD, toOperand(src), toOperand(dst)));
                case ZeroExtendIr(ValIr src, VarIr dst) -> {
                    instructionAsms.add(new MovZeroExtend(toOperand(src), toOperand(dst)));
                }
            }
        }
        return new FunctionAsm(functionIr.name(), functionIr.global(), instructionAsms);
    }

    private static AtomicInteger replacePseudoRegisters(List<Instruction> instructions) {
        AtomicInteger offset = new AtomicInteger(-8);
        Map<String, Integer> varTable = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            Instruction oldInst = instructions.get(i);
            Instruction newInst = switch (oldInst) {
                case Nullary _, Cdq _, Jump _,
                     JmpCC _,
                     LabelIr _, Call _ -> oldInst;
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
                        new Cmp(typeAsm, dePseudo(subtrahend, varTable, offset),
                                dePseudo(minuend, varTable, offset));
                case SetCC(
                        CmpOperator cmpOperator,
                        boolean signed,
                        Operand operand
                ) -> new SetCC(cmpOperator, signed,
                        dePseudo(operand, varTable, offset));
                case Push(Operand operand) ->
                        new Push(dePseudo(operand, varTable, offset));
                case Movsx(Operand src, Operand dst) ->
                        new Movsx(dePseudo(src, varTable, offset),
                                dePseudo(dst, varTable, offset));
                case MovZeroExtend(Operand src, Operand dst) ->
                        new MovZeroExtend(dePseudo(src, varTable, offset),
                                dePseudo(dst, varTable, offset));
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
                case MovZeroExtend(Operand src, Operand dst) -> {
                    if (dst instanceof Reg) {
                        instructions.set(i, new Mov(LONGWORD, src, dst));
                    } else {
                        instructions.set(i, new Mov(LONGWORD, src, R11));
                        instructions.add(i + 1, new Mov(QUADWORD, R11, dst));
                    }
                }
                case Unary(UnaryOperator op, TypeAsm typeAsm,
                           Operand operand) -> {
                    if ((op == UnaryOperator.IDIV || op == UnaryOperator.DIV)
                            && operand instanceof Imm) {
                        instructions.set(i, new Mov(typeAsm, operand, R10));
                        instructions.add(i + 1, new Unary(op, typeAsm, R10));
                    }
                }
                case Mov(TypeAsm typeAsm, Operand src, Operand dst) -> {
                    if (isRam(src) && isRam(dst)) {
                        instructions.set(i, new Mov(typeAsm, src, R10));
                        instructions.add(i + 1, new Mov(typeAsm, R10, dst));
                    } else if (isRam(dst) && typeAsm == QUADWORD && src instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(typeAsm, src, R10));
                        instructions.add(i + 1, new Mov(typeAsm, R10, dst));
                    }
                }
                case Push(Operand operand) -> {
                    if (operand instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(QUADWORD, operand, R10));
                        instructions.add(i + 1, new Push(R10));
                    }
                }
                case Binary(
                        ArithmeticOperator op, TypeAsm typeAsm, Operand src,
                        Operand dst
                ) -> {
                    if (src instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(typeAsm, src, R10));
                        instructions.add(i + 1, new Binary(op, typeAsm, R10, dst));
                        i = i + 2; // will be i+1 at start of next iteration -> want to catch conditions below
                    } else {
                        switch (op) {
                            case ADD, SUB -> {
                                if (isRam(src) && isRam(dst)) {
                                    instructions.set(i, new Mov(typeAsm, src, R10));
                                    instructions.add(i + 1, new Binary(op, typeAsm, R10, dst));
                                }

                            }
                            case IMUL -> {
                                if (isRam(dst)) {
                                    instructions.set(i, new Mov(typeAsm, dst, R11));
                                    instructions.add(i + 1, new Binary(op, typeAsm, src, R11));
                                    instructions.add(i + 2, new Mov(typeAsm, R11, dst));
                                }
                            }

                        }
                    }

                }

                case Cmp(TypeAsm typeAsm, Operand src, Operand dst) -> {
                    if (isRam(src) && isRam(dst)) {
                        instructions.set(i, new Mov(typeAsm, src, R10));
                        instructions.add(i + 1, new Cmp(typeAsm, R10, dst));
                    } else if (dst instanceof Imm) {
                        if (src instanceof Imm imm && imm.isAwkward()) {
                            instructions.set(i, new Mov(typeAsm, src, R10));
                            instructions.add(i + 1, new Mov(typeAsm, dst, R11));
                            instructions.add(i + 2, new Cmp(typeAsm, R10, R11));
                        } else {
                            instructions.set(i, new Mov(typeAsm, dst, R11));
                            instructions.add(i + 1, new Cmp(typeAsm, src, R11));
                        }
                    } else if (src instanceof Imm imm && imm.isAwkward()) {
                        instructions.set(i, new Mov(typeAsm, src, R10));
                        instructions.add(i + 1, new Cmp(typeAsm, R10, dst));
                    }
                }
                case Movsx(Operand src, Operand dst) -> {
                    if (src instanceof Imm) {
                        instructions.set(i, new Mov(LONGWORD, src, R10));
                        if (isRam(dst)) {
                            instructions.add(i + 1, new Movsx(R10, R11));
                            instructions.add(i + 2, new Mov(QUADWORD, R11, dst));
                        } else {
                            instructions.add(i + 1, new Movsx(R10, dst));
                        }
                    } else {
                        if (isRam(dst)) {
                            instructions.set(i, new Movsx(src, R11));
                            instructions.add(i + 1, new Mov(QUADWORD, R11, dst));
                        }
                    }
                }
                default -> {
                }

            }

        }
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
            case INT, UINT -> LONGWORD;
            case LONG, ULONG -> QUADWORD;
            default ->
                    throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private static boolean isRam(Operand src) {
        return src instanceof Stack || src instanceof Data;
    }

    private static Reg[] registers = new Reg[]{DI, SI, DX, CX, R8, R9};

    private static void codegenFunCall(FunCall funCall, List<Instruction> instructionAsms) {

        if (funCall instanceof FunCall(
                String name, ArrayList<ValIr> args, ValIr dst
        )) {
            int argc = args.size();
            int stackArgCount = argc > 6 ? (argc - 6) : 0;
            int stackPadding = stackArgCount % 2 == 1 ? 8 : 0;
            if (stackPadding != 0) {
                instructionAsms.add(new Binary(SUB, QUADWORD, new Imm(stackPadding), SP));
            }
            for (int i = 0; i < 6 && i < argc; i++) {
                Reg r = registers[i];
                ValIr arg = args.get(i);
                TypeAsm typeAsm = valToAsmType(arg);
                Operand operand = toOperand(arg);
                instructionAsms.add(new Mov(typeAsm, operand, r));
            }
            for (int i = argc - 1; i > 5; i--) {
                ValIr arg = args.get(i);
                Operand operand = toOperand(arg);
                if (operand instanceof Imm || operand instanceof Reg || valToAsmType(arg) == QUADWORD) {
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
            TypeAsm typeAsm = valToAsmType(dst);
            instructionAsms.add(new Mov(typeAsm, AX, toOperand(dst)));
        }
    }

    private static Operand toOperand(ValIr val) {
        return switch (val) {
            case ConstInt(int i) -> new Imm(i);
            case VarIr(String identifier) -> new Pseudo(identifier);
            case ConstLong(long l) -> new Imm(l);
            case ConstUInt(int i) -> new Imm(i);
            case ConstULong(long l) -> new Imm(l);
            case ConstDouble(double d) -> new ImmDouble(d);
        };
    }


    private static Operand dePseudo(Operand in, Map<String, Integer> varTable, AtomicInteger offset) {
        return switch (in) {
            case Imm _, ImmDouble _, Reg _, Stack _ -> in;
            case Pseudo(String identifier) -> {
                if (BACKEND_SYMBOL_TABLE.get(identifier) instanceof ObjEntry(
                        TypeAsm type, boolean isStatic)) {
                    if (isStatic) yield new Data(identifier);

                    Integer varOffset = varTable.get(identifier);
                    if (varOffset == null) {
                        // it starts ar -8 - we can use this for the first var
                        // when that var is written it will update bytes stack-8 to stack-1
                        int size = type.size();
                        varOffset = offset.get();
                        varOffset -= size;
                        int remainder = varOffset % size;
                        if (remainder != 0) {
                            varOffset -= (size + remainder);
                        }
                        varTable.put(identifier, varOffset);
                        //System.out.println(identifier+"\t"+size+"\t"+varOffset+"\t"+remainder);
                        offset.set(varOffset);
                    }
                    yield new Stack(varOffset);
                } else throw new IllegalArgumentException(identifier);

            }

            case Data data -> throw new Todo();
        };
    }

}
