package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
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
                programIr.topLevels().set(i, optimizeFunction(f, optimizations));
            }
        }
        return programIr;
    }

    private static TopLevel optimizeFunction(FunctionIr f, EnumSet<Optimization> optimizations) {
        List<InstructionIr> instructions = f.instructions();
        boolean updated = true;
        while (updated) {
            updated = false;
            if (optimizations.contains(FOLD_CONSTANTS)) {
                updated = foldConstants(instructions);
            }
            List<Node> cfg = makeCFG(instructions);
            if (optimizations.contains(ELIMINATE_UNREACHABLE_CODE)) {
                updated |= eliminateUnreachableCode(cfg);
            }
            instructions = cfgToInstructions(cfg);

        }
        return new FunctionIr(f.name(), f.global(), f.type(), instructions, f.returnType());
    }


    private static List<InstructionIr> cfgToInstructions(List<Node> nodes) {
        ArrayList<InstructionIr> instructions = new ArrayList<>();
        for (Node n : nodes) {
            if (n instanceof BasicBlock(int _, List<InstructionIr> ins,
                                        ArrayList<Node> _, ArrayList<Node> _)) {
                instructions.addAll(ins);
            }
        }
        return instructions;
    }

    private static boolean eliminateUnreachableCode(List<Node> nodes) {
        boolean updated = eliminateUnreachableBlocks(nodes);
        updated |= removeUselessJumps(nodes);
        updated |= removeUselessLabels(nodes);
        return updated;
    }

    private static boolean removeUselessLabels(List<Node> nodes) {
        // iterate through all BasicBlocks (n.b. neither the first nor last nodes are BasicBlocks)
        boolean updated = false;
        for (int i = 1; i < nodes.size() - 1; i++) {
            BasicBlock b = (BasicBlock) nodes.get(i);
            var instrs = b.instructions();
            if (instrs.isEmpty()) continue;
            InstructionIr instr = instrs.getFirst();
            if (instr instanceof LabelIr) {
                boolean keepLabel = false;
                var defaultPredecessor = nodes.get(i - 1).nodeId();
                for (var pred : b.predecessors()) {
                    if (pred.nodeId() != defaultPredecessor) {
                        keepLabel = true;
                        break;
                    }
                }
                if (!keepLabel) {
                    instrs.removeFirst();
                    updated = true;
                }
            }

        }
        return updated;
    }

    private static boolean removeUselessJumps(List<Node> nodes) {
        // iterate through all but the last BasicBlocks (n.b. neither the first nor last nodes are BasicBlocks)
        boolean updated = false;
        for (int i = 1; i < nodes.size() - 2; i++) {
            BasicBlock b = (BasicBlock) nodes.get(i);
            var instrs = b.instructions();
            InstructionIr instr = instrs.getLast();
            if (instr instanceof Jump || instr instanceof JumpIfZero || instr instanceof JumpIfNotZero) {
                boolean keepJump = false;
                var defaultSuccessor = nodes.get(i + 1).nodeId();
                for (var succ : b.successors()) {
                    if (succ.nodeId() != defaultSuccessor) {
                        keepJump = true;
                        break;
                    }
                }
                if (!keepJump) {
                    instrs.removeLast();
                    updated = true;
                }
            }

        }
        return updated;
    }

    private static boolean eliminateUnreachableBlocks(List<Node> nodes) {
        Set<Integer> nodesToKeep = new HashSet<>();
        nodesToKeep.add(nodes.getLast().nodeId());// always keep the exit node, even if there's an infinite loop
        nodesToKeep(nodesToKeep, nodes, nodes.getFirst());
        return nodes.removeIf(n -> !nodesToKeep.contains(n.nodeId()));
    }

    private static void nodesToKeep(Set<Integer> nodesToKeep, List<Node> nodes, Node node) {
        nodesToKeep.add(node.nodeId());
        for (var n : node.successors()) {
            if (!nodesToKeep.contains(n.nodeId()))
                nodesToKeep(nodesToKeep, nodes, n);
        }
    }

    private static List<Node> makeCFG(List<InstructionIr> instructions) {
        List<List<InstructionIr>> blocks = partitionIntoBasicBlocks(instructions);
        EntryNode entryNode = new EntryNode(new ArrayList<>());
        List<Node> nodes = new ArrayList<>();
        ExitNode exitNode = new ExitNode(new ArrayList<>());
        nodes.add(entryNode);
        int max = blocks.size() - 1;
        Map<String, Node> labelToNodeId = new HashMap<>();
        for (int i = 0; i <= max; i++) {
            var block = blocks.get(i);
            int blockId = i + 1;
            var bb = new BasicBlock(blockId, block, new ArrayList<>(), new ArrayList<>());
            nodes.add(bb);
            if (block.getFirst() instanceof LabelIr(String label)) {
                labelToNodeId.put(label, bb);
            }
        }
        nodes.add(exitNode);
        addEdge(nodes, entryNode, nodes.get(1));
        for (int i = 0; i <= max; i++) {
            Node nodeId = nodes.get(i + 1);
            Node nextId;
            if (i == max) {
                nextId = exitNode;
            } else {
                nextId = nodes.get(i + 2);
            }
            BasicBlock node = (BasicBlock) nodeId;
            InstructionIr instr = node.instructions().getLast();
            switch (instr) {
                case ReturnIr _ -> {
                    addEdge(nodes, nodeId, exitNode);
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

    private static void addEdge(List<Node> nodes, Node from, Node to) {
        from.successors().add(to);
        to.predecessors().add(from);
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
