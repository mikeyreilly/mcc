package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.*;

import java.util.*;

import static com.quaxt.mcc.Mcc.valToType;
import static com.quaxt.mcc.optimizer.Optimizer.removeIf;

public class EliminateDeadStores {

    /* the iterative algorithm described on p. 607*/
    private static void iterativeAlgorithm(List<Node> cfg, HashMap<Integer, HashSet<VarIr>[]> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<VarIr>> blockAnnotations, Set<VarIr> aliasedVars) {
        var staticVars = new HashSet<VarIr>();
        for (var v : aliasedVars) {
            if (v.isStatic()) {
                staticVars.add(v);
            }
        }
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
            Set<VarIr> incomingLiveVars = meet(block, blockAnnotations, staticVars);
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
                        case EntryNode _ -> {}
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


    private static Set<VarIr> getInstructionAnnotation(int nodeId, int i, HashMap<Integer, HashSet<VarIr>[]> INSTRUCTION_ANNOTATIONS) {
        return INSTRUCTION_ANNOTATIONS.get(nodeId)[i];
    }


    /*
    See p. 606 */
    private static void transfer(BasicBlock block, Set<VarIr> endLiveVars, HashMap<Integer, HashSet<VarIr>[]> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<VarIr>> blockAnnotations, Set<VarIr> aliasedVars) {
        HashSet<VarIr> currentLiveVars = new HashSet<>(endLiveVars);
        List<InstructionIr> instructions = block.instructions();
        INSTRUCTION_ANNOTATIONS.put(block.nodeId(), new HashSet[instructions.size()]);
        for (int i = instructions.size() - 1; i >= 0; i--) {
            var instruction = instructions.get(i);
            annotateInstruction(block.nodeId(), i, new HashSet<>(currentLiveVars), INSTRUCTION_ANNOTATIONS);
            switch (instruction) {
                case BinaryIr(BinaryOperator _, ValIr src1, ValIr src2,
                              VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src1 instanceof VarIr v1) {
                        currentLiveVars.add(v1);
                    }
                    if (src2 instanceof VarIr v2) {
                        currentLiveVars.add(v2);
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
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                    if (dst instanceof VarIr v) {
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
                    currentLiveVars.addAll(aliasedVars);
                }
                case GetAddress(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
//                    if (src instanceof VarIr v) {
//                        currentLiveVars.add(v);
//                    }
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
    private static Set<VarIr> meet(BasicBlock block, HashMap<Integer, HashSet<VarIr>> BLOCK_ANNOTATIONS, Set<VarIr> staticVars) {
        Set<VarIr> liveVars = new HashSet<>();
        for (var succ : block.successors()) {
            switch (succ) {
                case BasicBlock _ -> {
                    HashSet<VarIr> succLiveVars = getBlockAnnotation(succ.nodeId(), BLOCK_ANNOTATIONS);
                    liveVars.addAll(succLiveVars);
                }
                case EntryNode _ ->
                        throw new Err("Malformed control-flow graph");
                case ExitNode _ -> liveVars.addAll(staticVars);
            }
        }
        return liveVars;
    }

    static boolean DEBUG = false;

    /**
     * based on rewriteInstructions p. 598
     */
    public static boolean eliminateDeadStores(List<Node> cfg, Set<VarIr> aliasedVars) {
        if (DEBUG)
            System.out.println("===========eliminateDeadStores============== " + Optimizer.CURRENT_FUNCTION_NAME);
        HashMap<Integer, HashSet<VarIr>[]> INSTRUCTION_ANNOTATIONS = new HashMap<>();
        HashMap<Integer, HashSet<VarIr>> BLOCK_ANNOTATIONS = new HashMap<>();


        iterativeAlgorithm(cfg, INSTRUCTION_ANNOTATIONS, BLOCK_ANNOTATIONS, aliasedVars);


        boolean updated = false;
        for (int i = 0; i < cfg.size(); i++) {
            Node n = cfg.get(i);
            if (n instanceof BasicBlock(int _, List<InstructionIr> ins,
                                        ArrayList<Node> _, ArrayList<Node> _)) {
                BasicBlock b = (BasicBlock) n;
                HashSet<VarIr>[] annotations = INSTRUCTION_ANNOTATIONS.get(b.nodeId());
                if (annotations == null)
                    continue; // because we initialize the worklist by doing a traversal of cfg, we don't annotate orphan nodes
                int copyTo = 0;
                for (int j = 0; j < ins.size(); j++) {
                    InstructionIr instr = ins.get(j);
                    HashSet<VarIr> liveVars = annotations[j];
                    boolean isDeadStore = switch (instr) {
                        case BinaryIr(BinaryOperator _, ValIr src1, ValIr src2,
                                      VarIr dst) -> !liveVars.contains(dst);
                        case Copy(ValIr src, VarIr dst) ->
                                !liveVars.contains(dst);
                        case UnaryIr(UnaryOperator _, ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case Load(ValIr src, VarIr dst) ->
                                !liveVars.contains(dst);
                        case GetAddress(ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case SignExtendIr(ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case CopyFromOffset(ValIr src, long _, ValIr dst) ->
                                !liveVars.contains(dst);
                        case CopyToOffset(ValIr src, VarIr dst, long _) ->
                                !liveVars.contains(dst);
                        case ZeroExtendIr(ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case DoubleToInt(ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case DoubleToUInt(ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case IntToDouble(ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case UIntToDouble(ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case TruncateIr(ValIr src, ValIr dst) ->
                                !liveVars.contains(dst);
                        case AddPtr(VarIr src1, ValIr src2, int _, VarIr dst) ->
                                !liveVars.contains(dst);
                        default -> false;
                    };
                    if (!isDeadStore) {
                        if (copyTo != j) ins.set(copyTo, instr);
                        copyTo++;
                    }
                    if (DEBUG)
                        System.out.println((isDeadStore ? "KILL" : "    ") + "\tBLOCK " + i + "\t INSTR" + j + "\t" + instr + "\t" + liveVars);
                }
                int oldSize = ins.size();
                int newSize = copyTo;
                if (newSize != oldSize) {
                    ins.subList(newSize, oldSize).clear();
                    updated = true;
                }
            }
        }
        return updated;
    }


}
