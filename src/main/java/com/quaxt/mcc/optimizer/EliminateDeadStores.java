package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.*;

import java.util.*;

import static com.quaxt.mcc.Mcc.valToType;
import static com.quaxt.mcc.optimizer.Optimizer.removeIf;

public class EliminateDeadStores {

    /**
     * based on rewriteInstructions p. 598
     */
    public static boolean eliminateDeadStores(List<Node> cfg, Set<VarIr> aliasedVars) {
        HashMap<Integer, HashSet<VarIr>[]> INSTRUCTION_ANNOTATIONS = new HashMap<>();
        HashMap<Integer, HashSet<VarIr>> BLOCK_ANNOTATIONS = new HashMap<>();


        iterativeAlgorithm(cfg, INSTRUCTION_ANNOTATIONS, BLOCK_ANNOTATIONS, aliasedVars);
        boolean updated = false;
        return updated;
    }

    /* the iterative algorithm described on p. 607*/
    private static void iterativeAlgorithm(List<Node> cfg, HashMap<Integer, HashSet<VarIr>[]> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<VarIr>> blockAnnotations, Set<VarIr> aliasedVars) {
        HashSet<VarIr> liveVars = new HashSet<>();
        ArrayDeque<BasicBlock> workList = new ArrayDeque<>();
        // MR-TODO initialize the worklist in reverse post order
        for (Node n : cfg) {
            if (n instanceof BasicBlock node) {
                workList.add(node);
                annotateBlock(node.nodeId(), liveVars, blockAnnotations);
            }
        }
        while (!workList.isEmpty()) {
            BasicBlock block = workList.removeFirst();
            HashSet<VarIr> oldAnnotations = getBlockAnnotation(block.nodeId(), blockAnnotations);
            Set<VarIr> incomingLiveVars = meet(block, blockAnnotations, aliasedVars);
            transfer(block, incomingLiveVars, INSTRUCTION_ANNOTATIONS, blockAnnotations, aliasedVars);
            if (!oldAnnotations.equals(getBlockAnnotation(block.nodeId(), blockAnnotations))) {
                for (Node pred : block.predecessors()) {
                    switch (pred) {
                        case BasicBlock basicBlock -> {
                            if (!workList.contains(basicBlock))
                                workList.add(basicBlock);
                        }
                        case ExitNode _ ->
                                throw new Err("Malformed control flow graph");
                        case EntryNode  _ -> {}
                    }
                }
            }
        }
    }

    private static HashSet<VarIr> getBlockAnnotation(int nodeId, HashMap<Integer, HashSet<VarIr>> blockAnnotations) {
        return blockAnnotations.get(nodeId);
    }

    /* Annotate a block in the cfg with the vars that are live before the first instruction in the block*/
    private static void annotateBlock(int nodeId, HashSet<VarIr> liveVars, HashMap<Integer, HashSet<VarIr>> blockAnnotations) {
        blockAnnotations.put(nodeId, liveVars);
    }


    private static Set<Copy> getInstructionAnnotation(int nodeId, int i, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS) {
        return INSTRUCTION_ANNOTATIONS.get(nodeId).get(i);
    }


    /*
    See p. 606 */
    private static void transfer(BasicBlock block, Set<VarIr> endLiveVars, HashMap<Integer, HashSet<VarIr>[]> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<VarIr>> blockAnnotations, Set<VarIr> aliasedVars) {
        HashSet<VarIr> currentLiveVars = new HashSet<>(endLiveVars);
        List<InstructionIr> instructions = block.instructions();
        INSTRUCTION_ANNOTATIONS.put(block.nodeId(), new HashSet[instructions.size()]);
        for (int i = instructions.size() - 1; i >= 0; i--) {
            var instruction = instructions.get(i);
            annotateInstruction(block.nodeId(), i, currentLiveVars, INSTRUCTION_ANNOTATIONS);
            switch (instruction) {
                case BinaryIr(BinaryOperator _, ValIr src1, ValIr src2,
                              VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src1 instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                    if (src2 instanceof VarIr v) {
                        currentLiveVars.remove(v);
                    }
                }
                case Copy(ValIr src, VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case FunCall(String _, ArrayList<ValIr> args, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    for (var src : args) {
                        if (src instanceof VarIr v) {
                            currentLiveVars.add(v);
                        }
                    }
                    currentLiveVars.addAll(aliasedVars);
                }
                case Store(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case UnaryIr(UnaryOperator _, ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case Load(ValIr src, VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case GetAddress(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case SignExtendIr(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case CopyFromOffset(ValIr src, long _, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case CopyToOffset(ValIr src, VarIr dst, long _) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case ZeroExtendIr(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case DoubleToInt(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case DoubleToUInt(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case IntToDouble(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case UIntToDouble(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case TruncateIr(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case AddPtr(VarIr src1, ValIr src2, int _, VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    currentLiveVars.add(src1);
                    if (src2 instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case JumpIfZero(ValIr src, String _) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }

                case JumpIfNotZero(ValIr src, String _) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case ReturnIr(ValIr src) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case LabelIr _, Jump _, Ignore _ -> {}

            }
        }
        annotateBlock(block.nodeId(), currentLiveVars, blockAnnotations);
    }

    private static void annotateInstruction(int blockId, int instructionIndex, HashSet<VarIr> liveVars, HashMap<Integer, HashSet<VarIr>[]> INSTRUCTION_ANNOTATIONS) {
        INSTRUCTION_ANNOTATIONS.get(blockId)[instructionIndex] = liveVars;
    }

    /* p. 607 */
    private static Set<VarIr> meet(BasicBlock block, HashMap<Integer, HashSet<VarIr>> BLOCK_ANNOTATIONS, Set<VarIr> aliasedVars) {
        Set<VarIr> liveVars = new HashSet<>();
        for (var succ : block.successors()) {
            switch (succ) {
                case BasicBlock _ -> {
                    HashSet<VarIr> succLiveVars = getBlockAnnotation(succ.nodeId(), BLOCK_ANNOTATIONS);
                    liveVars.addAll(succLiveVars);
                }
                case EntryNode _ ->
                        throw new Err("Malformed control-flow graph");
                case ExitNode _ -> liveVars.addAll(aliasedVars);
            }
        }
        return liveVars;
    }


}
