package com.quaxt.mcc.asm;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.tacky.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quaxt.mcc.asm.Nullary.CDQ;
import static com.quaxt.mcc.asm.Nullary.RET;

public class Codegen {
    public static ProgramAsm generateAssembly(ProgramIr programIr) {
        ProgramAsm programAsm = convertToAsm(programIr);
        // Replace Pseudo Registers
        List<Instruction> instructions = programAsm.functionAsm().instructions();
        AtomicInteger offset = new AtomicInteger(-8);
        Map<String, Integer> varTable = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            Instruction oldInst = instructions.get(i);
            Instruction newInst = switch (oldInst) {
                case AllocateStack _, Nullary _ -> oldInst;
                case Mov(Operand src, Operand dst) ->
                        new Mov(dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
                case Unary(UnaryOperator op, Operand operand) -> new Unary(op, dePseudo(operand, varTable, offset));
                case Binary(BinaryOperator op, Operand src, Operand dst) ->
                        new Binary(op, dePseudo(src, varTable, offset), dePseudo(dst, varTable, offset));
            };
            instructions.set(i, newInst);
        }
        // Fix up instructions

        instructions.addFirst(new AllocateStack(-offset.get()));
        // Fix illegal MOV, iDiV, ADD, SUB, IMUL instructions
        for (int i = instructions.size() - 1; i >= 0; i--) {
            Instruction oldInst = instructions.get(i);

            switch (oldInst) {
                case Unary(UnaryOperator op, Operand operand) -> {
                    if (op == UnaryOperator.IDIV && operand instanceof Imm) {
                        instructions.set(i, new Mov(operand, Reg.R10));
                        instructions.add(i + 1, new Unary(op, Reg.R10));
                    }
                }
                case Mov(Operand src, Operand dst) -> {
                    if (src instanceof Stack && dst instanceof Stack) {
                        instructions.set(i, new Mov(src, Reg.R10));
                        instructions.add(i + 1, new Mov(Reg.R10, dst));
                    }
                }
                case Binary(BinaryOperator op, Operand src, Operand dst) -> {
                    switch (op) {
                        case ADD, SUB -> {
                            if (src instanceof Stack && dst instanceof Stack) {
                                instructions.set(i, new Mov(src, Reg.R10));
                                instructions.add(i + 1, new Binary(op, Reg.R10, dst));
                            }
                        }
                        case IMUL -> {
                            if (dst instanceof Stack) {
                                instructions.set(i, new Mov(dst, Reg.R11));
                                instructions.add(i + 1, new Binary(op, src, Reg.R11));
                                instructions.set(i, new Mov(Reg.R11, dst));
                            }
                        }

                    }

                }
                default -> {
                }
            }

        }

        return programAsm;
    }

    public static ProgramAsm convertToAsm(ProgramIr program) {
        return new ProgramAsm(codeGenFunction(program.function()));
    }

    private static FunctionAsm codeGenFunction(FunctionIr function) {
        return new FunctionAsm(function.name(), codeGenInstructions(function.instructions()));
    }

    private static List<Instruction> codeGenInstructions(List<InstructionIr> instructions) {
        List<Instruction> instructionAsms = new ArrayList<>();
        for (InstructionIr inst : instructions) {
            switch (inst) {
                case ReturnInstructionIr(ValIr val) -> {
                    Operand src = toOperand(val);
                    instructionAsms.add(new Mov(src, Reg.RAX));
                    instructionAsms.add(RET);
                }
                case UnaryIr(UnaryOperator op, ValIr src, ValIr dstIr) -> {
                    Operand dst = toOperand(dstIr);
                    instructionAsms.add(new Mov(toOperand(src), dst));
                    instructionAsms.add(new Unary(op, dst));
                }
                case BinaryIr(BinaryOperator op, ValIr v1, ValIr v2, VarIr dstName) -> {
                    switch (op) {
                        case ADD, SUB, IMUL -> {
                            instructionAsms.add(new Mov(toOperand(v1), toOperand(dstName)));
                            instructionAsms.add(new Binary(op, toOperand(v2), toOperand(dstName)));
                        }
                        case DIVIDE -> {
                            instructionAsms.add(new Mov(toOperand(v1), Reg.RAX));
                            instructionAsms.add(CDQ);
                            instructionAsms.add(new Unary(UnaryOperator.IDIV, toOperand(v2)));
                            instructionAsms.add(new Mov(Reg.RAX, toOperand(dstName)));

                        }
                        case REMAINDER -> {
                            instructionAsms.add(new Mov(toOperand(v1), Reg.RAX));
                            instructionAsms.add(CDQ);
                            instructionAsms.add(new Unary(UnaryOperator.IDIV, toOperand(v2)));
                            instructionAsms.add(new Mov(Reg.RDX, toOperand(dstName)));
                        }
                    }

                }
            }
        }
        return instructionAsms;
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
                Integer varOffset = varTable.computeIfAbsent(identifier, (k) -> offset.getAndAdd(-8));
                yield new Stack(varOffset);
            }
        };
    }

}
