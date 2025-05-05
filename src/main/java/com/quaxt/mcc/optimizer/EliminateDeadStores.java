package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.tacky.*;

import java.util.*;

public class EliminateDeadStores {

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
            Set<Copy> incomingLiveVars = meet(block, liveVars, blockAnnotations);
            transfer(block, incomingLiveVars, INSTRUCTION_ANNOTATIONS, blockAnnotations, aliasedVars);
            if (!oldAnnotations.equals(getBlockAnnotation(block.nodeId(), blockAnnotations))) {
                for (Node succ : block.successors()) {
                    switch (succ) {
                        case BasicBlock basicBlock -> {
                            if (!workList.contains(basicBlock))
                                workList.add(basicBlock);
                        }
                        case EntryNode _ -> throw new Err("Malformed control flow graph");
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

    /**
     * based on rewriteInstructions p. 598
     */
    public static boolean eliminateDeadStores(List<Node> cfg, Set<VarIr> aliasedVars) {

        return false;
    }


    private static Set<Copy> getInstructionAnnotation(int nodeId, int i, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS) {
        return INSTRUCTION_ANNOTATIONS.get(nodeId).get(i);
    }


    /*
    See p. 606 */
    private static void transfer(BasicBlock block, Set<Copy> initialReachingCopies, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<VarIr>> BLOCK_ANNOTATIONS, Set<VarIr> aliasedVars) {
    }

    /* p. 607 */
    private static Set<Copy> meet(BasicBlock block, HashSet<VarIr> allCopies, HashMap<Integer, HashSet<VarIr>> BLOCK_ANNOTATIONS) {
        return null;
    }


}
