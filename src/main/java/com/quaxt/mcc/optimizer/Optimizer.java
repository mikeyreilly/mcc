package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.tacky.*;

import java.util.*;

import static com.quaxt.mcc.Mcc.valToType;
import static com.quaxt.mcc.Optimization.ELIMINATE_UNREACHABLE_CODE;
import static com.quaxt.mcc.Optimization.FOLD_CONSTANTS;
import static com.quaxt.mcc.semantic.Primitive.*;
import static com.quaxt.mcc.semantic.SemanticAnalysis.convertConst;

public class Optimizer {
    public static ProgramIr optimize(ProgramIr programIr, EnumSet<Optimization> optimizations) {
        for (int i = 0; i < programIr.topLevels().size(); i++) {
            TopLevel topLevel = programIr.topLevels().get(i);
            if (topLevel instanceof FunctionIr f) {
                programIr.topLevels().set(i, optimizeFunction2(f, optimizations));
            }
        }
        return programIr;
    }

    private static TopLevel optimizeFunction2(FunctionIr f, EnumSet<Optimization> optimizations) {
        return new FunctionIr(f.name(), f.global(), f.type(), optimizeFunction(f.instructions(), optimizations), f.returnType());
    }

    private static List<InstructionIr> optimizeFunction(List<InstructionIr> instructions, EnumSet<Optimization> optimizations) {
        boolean updated = true;
        while (updated) {
            updated = false;
            if (optimizations.contains(FOLD_CONSTANTS)) {
                updated |= foldConstants(instructions);
            }
            List<Node> nodes = makeCFG(instructions);
            if (optimizations.contains(ELIMINATE_UNREACHABLE_CODE)) {
                updated |= eliminateUnreachableCode(nodes);
            }

        }
        return instructions;
    }


    private static boolean eliminateUnreachableCode(List<Node> nodes) {
        throw new Todo();
    }

    private static List<Node> makeCFG(List<InstructionIr> instructions) {
        List<List<InstructionIr>> blocks = partitionIntoBasicBlocks(instructions);
        EntryNode entryNode = new EntryNode(new ArrayList<>());
        List<Node> nodes = new ArrayList<>();
        ExitNode exitNode = new ExitNode(new ArrayList<>());
        nodes.add(entryNode);
        int max = blocks.size() - 1;
        Map<String, Integer> labelToNodeId = new HashMap<>();
        for (int i = 0; i <= max; i++) {
            var block = blocks.get(i);
            int blockId = i + 1;
            nodes.add(new BasicBlock(blockId, block, new ArrayList<>(), new ArrayList<>()));
            if (block.getFirst() instanceof LabelIr(String label)) {
                labelToNodeId.put(label, blockId);
            }
        }
        nodes.add(exitNode);
        addEdge(nodes, 0, 1);
        for (int i = 0; i <= max; i++) {
            int nodeId = i + 1;
            int nextId;
            if (i == max) {
                nextId = Integer.MAX_VALUE;
            } else {
                nextId = nodeId + 1;
            }
            BasicBlock node = (BasicBlock) nodes.get(nodeId);
            InstructionIr instr = node.instructions().getLast();
            switch (instr) {
                case ReturnIr _ -> {
                    addEdge(nodes, nodeId, Integer.MAX_VALUE);
                }
                case Jump(String label) -> {
                    addEdge(nodes, nodeId, labelToNodeId.get(label));
                }
                case JumpIfNotZero(ValIr _, String label) -> {
                    addEdge(nodes, nodeId, labelToNodeId.get(label));
                    addEdge(nodes, nodeId, nextId);
                }
                case JumpIfZero(ValIr _, String label) -> {
                    addEdge(nodes, nodeId, labelToNodeId.get(label));
                    addEdge(nodes, nodeId, nextId);
                }
                default -> {
                    addEdge(nodes, nodeId, nextId);
                }
            }
        }
        return nodes;
    }

    private static void addEdge(List<Node> nodes, int from, int to) {
        if (to == Integer.MAX_VALUE) to = nodes.size() - 1;
        nodes.get(from).successors().add(to);
        getNode(nodes, to).predecessors().add(from);

    }

    private static Node getNode(List<Node> nodes, int to) {
        var m = nodes.get(to);
        return m;
    }

    private static List<List<InstructionIr>> partitionIntoBasicBlocks(List<InstructionIr> instructions) {
        List<List<InstructionIr>> finishedBlocks = new ArrayList<>();
        List<InstructionIr> currentBlock = new ArrayList<>();
        for (InstructionIr instr : instructions) {
            if (instr instanceof LabelIr) {
                if (!currentBlock.isEmpty()) {
                    finishedBlocks.add(currentBlock);
                }
                currentBlock = new ArrayList<>();
                currentBlock.add(instr);
            } else switch (instr) {
                case Jump _, JumpIfNotZero _, JumpIfZero _, ReturnIr _ -> {
                    currentBlock.add(instr);
                    finishedBlocks.add(currentBlock);
                    currentBlock = new ArrayList<>();
                }
                default -> {
                    currentBlock.add(instr);
                }
            }
        }
        if (!currentBlock.isEmpty()) {
            finishedBlocks.add(currentBlock);
        }
        return finishedBlocks;
    }

    @SuppressWarnings("unchecked")
    private static boolean foldConstants(List<InstructionIr> instructions) {
        boolean updated = false;
        for (int i = 0; i < instructions.size(); i++) {
            var in = instructions.get(i);
            InstructionIr newIn = switch (in) {
                case UnaryIr(UnaryOperator op, ValIr v1,
                             VarIr dstName) when v1 instanceof Constant c1 -> {
                    Constant co = c1.apply(op);
                    if (co == null) yield null;
                    yield new Copy(co, dstName);
                }

                case DoubleToInt(ValIr src,
                                 VarIr dst) when src instanceof StaticInit c1 ->
                        new Copy((ValIr) convertConst(c1, valToType(dst)), dst);
                case DoubleToUInt(ValIr src,
                                  VarIr dst) when src instanceof StaticInit c1 ->
                        new Copy((ValIr) convertConst(c1, valToType(dst)), dst);
                case IntToDouble(ValIr src,
                                 VarIr dst) when src instanceof StaticInit c1 ->
                        new Copy((ValIr) convertConst(c1, DOUBLE), dst);
                case SignExtendIr(ValIr src,
                                  VarIr dst) when src instanceof StaticInit c1 ->
                        new Copy((ValIr) convertConst(c1, valToType(dst)), dst);
                case ZeroExtendIr(ValIr src,
                                  VarIr dst) when src instanceof StaticInit c1 ->
                        new Copy((ValIr) convertConst(c1, valToType(dst)), dst);
                case TruncateIr(ValIr src,
                                VarIr dst) when src instanceof StaticInit c1 ->
                        new Copy((ValIr) convertConst(c1, valToType(dst)), dst);
                case UIntToDouble(ValIr src,
                                  VarIr dst) when src instanceof StaticInit c1 ->
                        new Copy((ValIr) convertConst(c1, DOUBLE), dst);
                case BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                              VarIr dstName) when v1 instanceof Constant c1 && v2 instanceof Constant c2 -> {
                    Constant<?> co = c1.apply(op, c2);
                    if (co == null) yield null;
                    yield new Copy(co, dstName);
                }
                case JumpIfZero(ValIr v,
                                String label) when v instanceof Constant<?> c -> {
                    if (c.isZero()) {
                        yield new Jump(label);
                    }
                    yield new Ignore();
                }
                case JumpIfNotZero(ValIr v,
                                   String label) when v instanceof Constant<?> c -> {
                    if (!c.isZero()) {
                        yield new Jump(label);
                    }
                    yield new Ignore();
                }
                default -> null;
            };
            if (newIn != null) {
                instructions.set(i, newIn);
                updated = true;
            }
        }
        return updated;
    }

}
