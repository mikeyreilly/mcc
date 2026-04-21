package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;
import com.quaxt.mcc.Err;
import com.quaxt.mcc.optimizer.BasicBlock;
import com.quaxt.mcc.optimizer.CfgNode;
import com.quaxt.mcc.optimizer.EntryNode;
import com.quaxt.mcc.optimizer.ExitNode;
import com.quaxt.mcc.optimizer.Optimizer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static com.quaxt.mcc.optimizer.PropagateCopies.addNodesInReversePostOrder;

public final class StackStateAnalysis {
    private StackStateAnalysis() {
    }

    public static long[] instructionDeltas(List<Instruction> instructions,
                                           long initialSpDelta) {
        List<CfgNode> cfg = Optimizer.makeCFG(instructions);
        HashMap<Integer, Long> blockIns = new HashMap<>();
        HashMap<Integer, Long> blockOuts = new HashMap<>();
        HashMap<Integer, long[]> blockInstructionDeltas = new HashMap<>();

        var alreadySeen = new HashSet<Integer>();
        ArrayDeque<BasicBlock> workList = new ArrayDeque<>();
        addNodesInReversePostOrder(cfg.getFirst(), workList, alreadySeen);
        for (CfgNode node : cfg) {
            if (node instanceof BasicBlock<?> basicBlock &&
                    !alreadySeen.contains(basicBlock.nodeId())) {
                workList.add(basicBlock);
            }
        }

        while (!workList.isEmpty()) {
            BasicBlock<Instruction> block = workList.removeFirst();
            Long incoming = meet(block, blockOuts, initialSpDelta);
            if (incoming == null) {
                continue;
            }
            Long oldOut = blockOuts.get(block.nodeId());
            blockIns.put(block.nodeId(), incoming);
            long outgoing = transfer(block, incoming, blockInstructionDeltas);
            blockOuts.put(block.nodeId(), outgoing);
            if (!Long.valueOf(outgoing).equals(oldOut)) {
                for (CfgNode succ : block.successors()) {
                    if (succ instanceof BasicBlock<?> basicBlock &&
                            !workList.contains(basicBlock)) {
                        workList.add((BasicBlock<Instruction>) basicBlock);
                    }
                }
            }
        }

        return flattenAnnotations(cfg, blockIns, blockInstructionDeltas,
                initialSpDelta, instructions.size());
    }

    private static Long meet(BasicBlock<Instruction> block,
                             HashMap<Integer, Long> blockOuts,
                             long initialSpDelta) {
        Long incoming = null;
        boolean sawPredecessorValue = false;
        for (CfgNode pred : block.predecessors()) {
            Long predOut = switch (pred) {
                case EntryNode _ -> initialSpDelta;
                case BasicBlock<?> basicBlock -> blockOuts.get(basicBlock.nodeId());
                case ExitNode _ -> throw new Err("Malformed control flow graph");
            };
            if (predOut == null) {
                continue;
            }
            sawPredecessorValue = true;
            if (incoming == null) {
                incoming = predOut;
            } else if (!incoming.equals(predOut)) {
                throw new Err("inconsistent stack depth at block " +
                        block.nodeId() + ": " + incoming + " vs " + predOut);
            }
        }
        if (!sawPredecessorValue && block.predecessors().isEmpty()) {
            return initialSpDelta;
        }
        return incoming;
    }

    private static long transfer(BasicBlock<Instruction> block,
                                 long incoming,
                                 HashMap<Integer, long[]> blockInstructionDeltas) {
        List<Instruction> instructions = block.instructions();
        long[] deltas = new long[instructions.size()];
        long current = incoming;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            deltas[i] = current;
            current = transfer(instruction, current);
        }
        blockInstructionDeltas.put(block.nodeId(), deltas);
        return current;
    }

    private static long transfer(Instruction instruction, long current) {
        return switch (instruction) {
            case Push _ -> current - 8;
            case Pop _ -> current + 8;
            case Binary(ArithmeticOperator op, TypeAsm type, Operand src,
                        Operand dst) when dst == IntegerReg.SP -> {
                if (type != PrimitiveTypeAsm.QUADWORD ||
                        !(src instanceof Imm(long n))) {
                    throw new Err("unsupported %rsp update: " + instruction);
                }
                yield switch (op) {
                    case ADD -> current + n;
                    case SUB -> current - n;
                    default -> throw new Err("unsupported %rsp update: " +
                            instruction);
                };
            }
            default -> current;
        };
    }

    private static long[] flattenAnnotations(List<CfgNode> cfg,
                                             HashMap<Integer, Long> blockIns,
                                             HashMap<Integer, long[]> blockInstructionDeltas,
                                             long initialSpDelta,
                                             int instructionCount) {
        long[] result = new long[instructionCount];
        int outputIndex = 0;
        for (CfgNode node : cfg) {
            if (node instanceof BasicBlock<?> basicBlock) {
                long[] deltas = blockInstructionDeltas.get(basicBlock.nodeId());
                if (deltas == null) {
                    deltas = linearDeltas((BasicBlock<Instruction>) basicBlock,
                            blockIns.getOrDefault(basicBlock.nodeId(),
                                    initialSpDelta));
                }
                for (long delta : deltas) {
                    result[outputIndex++] = delta;
                }
            }
        }
        if (outputIndex != instructionCount) {
            throw new Err("stack analysis annotation count mismatch");
        }
        return result;
    }

    private static long[] linearDeltas(BasicBlock<Instruction> block,
                                       long initialSpDelta) {
        long[] deltas = new long[block.instructions().size()];
        long current = initialSpDelta;
        for (int i = 0; i < block.instructions().size(); i++) {
            deltas[i] = current;
            current = transfer(block.instructions().get(i), current);
        }
        return deltas;
    }
}
