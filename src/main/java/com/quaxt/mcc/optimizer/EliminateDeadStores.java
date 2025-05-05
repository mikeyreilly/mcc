package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.tacky.*;

import java.util.*;

public class EliminateDeadStores {

    /**
     * based on rewriteInstructions p. 598
     */
    public static boolean eliminateDeadStores(List<Node> cfg, Set<VarIr> aliasedVars) {
        HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS = new HashMap<>();
        HashMap<Integer, HashSet<VarIr>> BLOCK_ANNOTATIONS = new HashMap<>();
        iterativeAlgorithm(cfg, INSTRUCTION_ANNOTATIONS, BLOCK_ANNOTATIONS, aliasedVars);
        boolean updated = false;
        return updated;
    }

    /* the iterative algorithm described on p. 607*/
    private static void iterativeAlgorithm(List<Node> cfg, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<VarIr>> blockAnnotations, Set<VarIr> aliasedVars) {
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
                        case EntryNode _ ->
                                throw new Err("Malformed control flow graph");
                        case ExitNode _ -> {}
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
    private static void transfer(BasicBlock block, Set<VarIr> endLiveVars, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<VarIr>> BLOCK_ANNOTATIONS, Set<VarIr> aliasedVars) {
//        HashSet<VarIr> currentLiveVars = new HashSet<>(endLiveVars);
//       var foo= new ArrayList<>();
//       foo.ensureCapacity(1000);
//        for(var instr:)
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
