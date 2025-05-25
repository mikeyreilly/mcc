package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.tacky.*;

import java.util.*;

public class EliminateDeadStores {


    static boolean DEBUG = true;

    /**
     * based on rewriteInstructions p. 598
     */
    public static boolean eliminateDeadStores(List<CfgNode> cfg,
                                              Set<VarIr> aliasedVars) {
        if (DEBUG)
            System.out.println("===========eliminateDeadStores============== "
                    + Optimizer.CURRENT_FUNCTION_NAME);
        HashMap<Integer, Set<VarIr>[]> instructionAnnotations = new HashMap<>();
        HashMap<Integer, Set<VarIr>> blockAnnotations = new HashMap<>();

        var staticVars = new HashSet<VarIr>();

        for (var v : aliasedVars) {
            if (v instanceof VarIr varIr && varIr.isStatic()) {
                staticVars.add(varIr);
            }
        }
        LivenessAnalyzer.analyzeLiveness(cfg, instructionAnnotations,
                blockAnnotations, new Pair<>(aliasedVars,staticVars),
                LivenessAnalyzer::livenessIrMeetFunction,
                LivenessAnalyzer::livenessIrTransferFunction);


        boolean updated = false;
        for (int i = 0; i < cfg.size(); i++) {
            CfgNode n = cfg.get(i);
            if (n instanceof BasicBlock(int _, List ins, ArrayList<CfgNode> _,
                                        ArrayList<CfgNode> _)) {

                var annotations = instructionAnnotations.get(n.nodeId());
                if (annotations == null)
                    continue; // because we initialize the worklist by doing
                // a traversal of cfg, we don't annotate orphan nodes
                int copyTo = 0;
                for (int j = 0; j < ins.size(); j++) {
                    InstructionIr instr = (InstructionIr) ins.get(j);
                    var liveVars = annotations[j];
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
                        System.out.println((isDeadStore ? "KILL" : "    ") +
                                "\tBLOCK " + i + "\t INSTR" + j + "\t" + instr + "\t" + liveVars);
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
