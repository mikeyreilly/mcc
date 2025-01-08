package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;
import com.quaxt.mcc.CmpOperator;
import com.quaxt.mcc.UnaryOperator;
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
                case AllocateStack _, Nullary _, Jump _, JmpCC _,
                     LabelIr _ -> oldInst;
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
                default ->
                        throw new IllegalStateException("Unexpected value: " + oldInst);
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
                case Binary(
                        ArithmeticOperator op, Operand src, Operand dst
                ) -> {
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
                                instructions.add(i + 2, new Mov(Reg.R11, dst));
                            }
                        }

                    }

                }

                case Cmp(Operand src, Operand dst) -> {
                    if (src instanceof Stack && dst instanceof Stack) {
                        instructions.set(i, new Mov(src, Reg.R10));
                        instructions.add(i + 1, new Cmp(Reg.R10, dst));
                    } else {
                        if (dst instanceof Imm) {
                            instructions.set(i, new Mov(dst, Reg.R11));
                            instructions.add(i + 1, new Cmp(src, Reg.R11));
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
                    instructionAsms.add(new Mov(src, Reg.AX));
                    instructionAsms.add(RET);
                }
                case UnaryIr(UnaryOperator op, ValIr srcIr, ValIr dstIr) -> {
                    Operand dst = toOperand(dstIr);
                    Operand src = toOperand(srcIr);
                    if (op == UnaryOperator.NOT) {
                        instructionAsms.add(new Cmp(new Imm(0), src));
                        instructionAsms.add(new Mov(new Imm(0), dst));
                        instructionAsms.add(new SetCC(EQUALS, dst));

                    } else {

                        instructionAsms.add(new Mov(src, dst));
                        instructionAsms.add(new Unary(op, dst));
                    }
                }
                case BinaryIr(
                        ArithmeticOperator op, ValIr v1, ValIr v2, VarIr dstName
                ) -> {
                    switch (op) {
                        case ADD, SUB, IMUL -> {
                            instructionAsms.add(new Mov(toOperand(v1), toOperand(dstName)));
                            instructionAsms.add(new Binary(op, toOperand(v2), toOperand(dstName)));
                        }
                        case DIVIDE -> {
                            instructionAsms.add(new Mov(toOperand(v1), Reg.AX));
                            instructionAsms.add(CDQ);
                            instructionAsms.add(new Unary(UnaryOperator.IDIV, toOperand(v2)));
                            instructionAsms.add(new Mov(Reg.AX, toOperand(dstName)));

                        }
                        case REMAINDER -> {
                            instructionAsms.add(new Mov(toOperand(v1), Reg.AX));
                            instructionAsms.add(CDQ);
                            instructionAsms.add(new Unary(UnaryOperator.IDIV, toOperand(v2)));
                            instructionAsms.add(new Mov(Reg.DX, toOperand(dstName)));
                        }


                        default ->
                                throw new IllegalStateException("Unexpected value: " + op);
                    }

                }
////
                case BinaryIr(
                        CmpOperator op, ValIr v1, ValIr v2, VarIr dstName
                ) -> {

                    instructionAsms.add(new Cmp(toOperand(v2), toOperand(v1)));
                    instructionAsms.add(new Mov(new Imm(0), toOperand(dstName)));
                    instructionAsms.add(new SetCC(op, toOperand(dstName)));


                }
////
                case Copy(ValIr val, VarIr dst) -> {
                    instructionAsms.add(new Mov(toOperand(val), toOperand(dst)));
                }
                case Jump jump -> {
                    instructionAsms.add(jump);
                }
                case JumpIfNotZero(ValIr v, String label) -> {

                    instructionAsms.add(new Cmp(new Imm(0), toOperand(v)));
                    instructionAsms.add(new JmpCC(NOT_EQUALS, label));
                }
                case JumpIfZero(ValIr v, String label) -> {
                    instructionAsms.add(new Cmp(new Imm(0), toOperand(v)));
                    instructionAsms.add(new JmpCC(EQUALS, label));
                }
                case LabelIr labelIr -> {
                    instructionAsms.add(labelIr);
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
