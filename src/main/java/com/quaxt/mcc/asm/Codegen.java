package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;
import com.quaxt.mcc.CmpOperator;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.parser.Identifier;
import com.quaxt.mcc.tacky.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quaxt.mcc.CmpOperator.EQUALS;
import static com.quaxt.mcc.CmpOperator.NOT_EQUALS;
import static com.quaxt.mcc.asm.Nullary.CDQ;
import static com.quaxt.mcc.asm.Nullary.RET;
import static com.quaxt.mcc.asm.Reg.*;

public class Codegen {

    public static ProgramAsm generateProgramAssembly(ProgramIr programIr) {
        return new ProgramAsm(programIr.functions().stream().map(Codegen::generateAssembly).toList());
    }

    public static FunctionAsm generateAssembly(FunctionIr functionIr) {
        List<Instruction> instructionAsms = new ArrayList<>();
        List<Identifier> type = functionIr.type();
        for (int i = 0; i < type.size() && i < 6; i++) {
            Identifier param = type.get(i);
            instructionAsms.add(new Mov(registers[i], new Pseudo(param.name())));
        }
        for (int i = 6; i < type.size(); i++) {
            Identifier param = type.get(i);
            instructionAsms.add(new Mov(new Stack(16 + (i - 6) * 8), new Pseudo(param.name())));
        }

        for (InstructionIr inst : functionIr.instructions()) {
            switch (inst) {
                case ReturnInstructionIr(ValIr val) -> {
                    Operand src1 = toOperand(val);
                    instructionAsms.add(new Mov(src1, AX));
                    instructionAsms.add(RET);
                }
                case UnaryIr(UnaryOperator op1, ValIr srcIr, ValIr dstIr) -> {
                    Operand dst1 = toOperand(dstIr);
                    Operand src1 = toOperand(srcIr);
                    if (op1 == UnaryOperator.NOT) {
                        instructionAsms.add(new Cmp(new Imm(0), src1));
                        instructionAsms.add(new Mov(new Imm(0), dst1));
                        instructionAsms.add(new SetCC(EQUALS, dst1));

                    } else {

                        instructionAsms.add(new Mov(src1, dst1));
                        instructionAsms.add(new Unary(op1, dst1));
                    }
                }
                case BinaryIr(
                        ArithmeticOperator op1, ValIr v1, ValIr v2, VarIr dstName
                ) -> {
                    switch (op1) {
                        case ADD, SUB, IMUL -> {
                            instructionAsms.add(new Mov(toOperand(v1), toOperand(dstName)));
                            instructionAsms.add(new Binary(op1, toOperand(v2), toOperand(dstName)));
                        }
                        case DIVIDE -> {
                            instructionAsms.add(new Mov(toOperand(v1), AX));
                            instructionAsms.add(CDQ);
                            instructionAsms.add(new Unary(UnaryOperator.IDIV, toOperand(v2)));
                            instructionAsms.add(new Mov(AX, toOperand(dstName)));

                        }
                        case REMAINDER -> {
                            instructionAsms.add(new Mov(toOperand(v1), AX));
                            instructionAsms.add(CDQ);
                            instructionAsms.add(new Unary(UnaryOperator.IDIV, toOperand(v2)));
                            instructionAsms.add(new Mov(DX, toOperand(dstName)));
                        }


                        default ->
                                throw new IllegalStateException("Unexpected value: " + op1);
                    }

                }
                case BinaryIr(
                        CmpOperator op1, ValIr v1, ValIr v2, VarIr dstName
                ) -> {

                    instructionAsms.add(new Cmp(toOperand(v2), toOperand(v1)));
                    instructionAsms.add(new Mov(new Imm(0), toOperand(dstName)));
                    instructionAsms.add(new SetCC(op1, toOperand(dstName)));
                }
                case Copy(ValIr val, VarIr dst1) ->
                        instructionAsms.add(new Mov(toOperand(val), toOperand(dst1)));
                case Jump jump -> instructionAsms.add(jump);
                case JumpIfNotZero(ValIr v, String label) -> {
                    instructionAsms.add(new Cmp(new Imm(0), toOperand(v)));
                    instructionAsms.add(new JmpCC(NOT_EQUALS, label));
                }
                case JumpIfZero(ValIr v, String label) -> {
                    instructionAsms.add(new Cmp(new Imm(0), toOperand(v)));
                    instructionAsms.add(new JmpCC(EQUALS, label));
                }
                case LabelIr labelIr -> instructionAsms.add(labelIr);
                case FunCall funCall -> {
                    codegenFunCall(funCall, instructionAsms);
                }
            }
        }
        FunctionAsm functionAsm = new FunctionAsm(functionIr.name(), instructionAsms);
        // Replace Pseudo Registers
        List<Instruction> instructions = functionAsm.instructions();
        AtomicInteger offset = new AtomicInteger(-8);
        Map<String, Integer> varTable = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            Instruction oldInst = instructions.get(i);
            Instruction newInst = switch (oldInst) {
                case AllocateStack _, DeallocateStack _, Nullary _, Jump _,
                     JmpCC _,
                     LabelIr _, Call _ -> oldInst;
                case Mov(Operand src, Operand dst) ->
                        new Mov(dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
                case Unary(UnaryOperator op, Operand operand) ->
                        new Unary(op, dePseudo(operand, varTable, offset));
                case Binary(ArithmeticOperator op, Operand src, Operand dst) ->
                        new Binary(op, dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));


                case Cmp(Operand subtrahend, Operand minuend) ->
                        new Cmp(dePseudo(subtrahend, varTable, offset),
                                dePseudo(minuend, varTable, offset));
                case SetCC(
                        CmpOperator cmpOperator,
                        Operand operand
                ) -> new SetCC(cmpOperator,
                        dePseudo(operand, varTable, offset));
                case Push(Operand operand) ->
                        new Push(dePseudo(operand, varTable, offset));
            };
            instructions.set(i, newInst);
        }
        // Fix up instructions
        int stackSize = -offset.get();
        // round up to next multiple of 16 (makes it easier to maintain
        // alignment during function calls
        int remainder = stackSize % 16;
        if (remainder != 0) {
            stackSize += (16 - remainder);
        }
        instructions.addFirst(new AllocateStack(stackSize));
        // Fix illegal MOV, iDiV, ADD, SUB, IMUL instructions
        for (int i = instructions.size() - 1; i >= 0; i--) {
            Instruction oldInst = instructions.get(i);

            switch (oldInst) {
                case Unary(UnaryOperator op, Operand operand) -> {
                    if (op == UnaryOperator.IDIV && operand instanceof Imm) {
                        instructions.set(i, new Mov(operand, R10));
                        instructions.add(i + 1, new Unary(op, R10));
                    }
                }
                case Mov(Operand src, Operand dst) -> {
                    if (src instanceof Stack && dst instanceof Stack) {
                        instructions.set(i, new Mov(src, R10));
                        instructions.add(i + 1, new Mov(R10, dst));
                    }
                }
                case Binary(
                        ArithmeticOperator op, Operand src, Operand dst
                ) -> {
                    switch (op) {
                        case ADD, SUB -> {
                            if (src instanceof Stack && dst instanceof Stack) {
                                instructions.set(i, new Mov(src, R10));
                                instructions.add(i + 1, new Binary(op, R10, dst));
                            }
                        }
                        case IMUL -> {
                            if (dst instanceof Stack) {
                                instructions.set(i, new Mov(dst, R11));
                                instructions.add(i + 1, new Binary(op, src, R11));
                                instructions.add(i + 2, new Mov(R11, dst));
                            }
                        }

                    }

                }

                case Cmp(Operand src, Operand dst) -> {
                    if (src instanceof Stack && dst instanceof Stack) {
                        instructions.set(i, new Mov(src, R10));
                        instructions.add(i + 1, new Cmp(R10, dst));
                    } else {
                        if (dst instanceof Imm) {
                            instructions.set(i, new Mov(dst, R11));
                            instructions.add(i + 1, new Cmp(src, R11));
                        }
                    }

                }
                default -> {
                }
            }

        }

        return functionAsm;
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
                instructionAsms.add(new AllocateStack(stackPadding));
            }
            for (int i = 0; i < 6 && i < argc; i++) {
                Reg r = registers[i];
                ValIr arg = args.get(i);
                Operand operand = toOperand(arg);
                instructionAsms.add(new Mov(operand, r));
            }
            for (int i = argc - 1; i > 5; i--) {
                ValIr arg = args.get(i);
                Operand operand = toOperand(arg);
                if (operand instanceof Imm || operand instanceof Reg) {
                    instructionAsms.add(new Push(operand));
                } else {
                    instructionAsms.add(new Mov(operand, AX));
                    instructionAsms.add(new Push(operand));
                }

            }
            instructionAsms.add(new Call(name));
            int bytesToRemove = 8 * stackArgCount + stackPadding;
            if (bytesToRemove != 0) {
                instructionAsms.add(new DeallocateStack(bytesToRemove));
            }
            instructionAsms.add(new Mov(AX, toOperand(dst)));
        }
    }

    private static Operand toOperand(ValIr val) {
        return switch (val) {
            case IntIr(int i) -> new Imm(i);
            case VarIr(String identifier) -> new Pseudo(identifier);
        };
    }


    private static Operand dePseudo(Operand in, Map<String, Integer> varTable, AtomicInteger offset) {
        return switch (in) {
            case Imm _, Reg _, Stack _ -> in;
            case Pseudo(String identifier) -> {
                Integer varOffset = varTable.computeIfAbsent(identifier, _ -> {
                    var r = offset.getAndAdd(-8);
                    //System.out.println(identifier + "->" + r);
                    return r;
                });
                yield new Stack(varOffset);
            }
        };
    }

}
