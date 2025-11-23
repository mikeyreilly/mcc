package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.atomics.MemoryOrder;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.*;

import java.util.*;

import static com.quaxt.mcc.Mcc.valToType;
import static com.quaxt.mcc.optimizer.Optimizer.removeIf;

public class PropagateCopies {

    /**
     * based on rewriteInstructions p. 598
     */
    public static boolean propagateCopies(List<CfgNode> cfg, Set<VarIr> aliasedVars) {
        HashMap<Integer, HashSet<Copy>[]>  INSTRUCTION_ANNOTATIONS= new HashMap<>();
        HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS = new HashMap<>();
        findReachingCopies(cfg, INSTRUCTION_ANNOTATIONS, BLOCK_ANNOTATIONS, aliasedVars);
        boolean updated = false;
        for (int i = 0; i < cfg.size(); i++) {
            CfgNode n = cfg.get(i);
            if (n instanceof BasicBlock(int _, List ins,
                                        ArrayList<CfgNode> _, ArrayList<CfgNode> _)) {
                BasicBlock b = (BasicBlock) n;
                HashSet<Copy>[] annotations = INSTRUCTION_ANNOTATIONS.get(b.nodeId());
                if (annotations == null)
                    continue; // because we initialize the worklist by doing a traversal of cfg, we don't annotate orphan nodes
                for (int j = 0; j < ins.size(); j++) {


                    InstructionIr instr = (InstructionIr) b.instructions().get(j);
                    Set<Copy> reachingCopies = annotations[j];

                    var newInstr = switch (instr) {
                        case Copy(ValIr src, VarIr dst) -> {
                            for (Copy copy : reachingCopies) {
                                if (copy.equals(instr) || (copy.src().equals(dst) && copy.dst().equals(src))) {
                                    yield Ignore.IGNORE;
                                }
                            }
                            yield new Copy(replaceOperand(src, reachingCopies), dst);
                        }
                        case BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                                      VarIr dst) ->
                                new BinaryIr(op, replaceOperand(v1, reachingCopies), replaceOperand(v2, reachingCopies), dst);
                        case BinaryWithOverflowIr(ArithmeticOperator op, ValIr v1,
                                                  ValIr v2, ValIr v3,
                                                  VarIr dst) ->
                                new BinaryWithOverflowIr(op,
                                        replaceOperand(v1, reachingCopies),
                                        replaceOperand(v2, reachingCopies),
                                        replaceOperand(v3, reachingCopies),
                                        dst);
                        case Compare(Type t, ValIr v1, ValIr v2) ->
                                new Compare(t, replaceOperand(v1, reachingCopies), replaceOperand(v2, reachingCopies));
                        case ReturnIr(ValIr v) ->
                                new ReturnIr(replaceOperand(v, reachingCopies));
                        case UnaryIr(UnaryOperator op, ValIr v1, VarIr dst) ->
                                new UnaryIr(op, replaceOperand(v1, reachingCopies), dst);
                        case FunCall(String _, ArrayList<ValIr> args, boolean _,
                                     boolean _, ValIr _) -> {
                            args.replaceAll(op -> replaceOperand(op, reachingCopies));
                            yield instr;
                        }
                        case JumpIfZero(ValIr v, String label) ->
                                new JumpIfZero(replaceOperand(v, reachingCopies), label);
                        case JumpIfNotZero(ValIr v, String label) ->
                                new JumpIfNotZero(replaceOperand(v, reachingCopies), label);
                        case Load(ValIr ptr, VarIr dst) ->
                                new Load(replaceOperand(ptr, reachingCopies), dst);
                        case Store(ValIr v, VarIr dst) ->
                                new Store(replaceOperand(v, reachingCopies), dst);
                        case AtomicStore(ValIr v, VarIr dst, MemoryOrder memOrder) ->
                                new AtomicStore(replaceOperand(v, reachingCopies), dst, memOrder);
                        case GetAddress _, LabelIr _, Jump _, BuiltinC23VaStartIr _ -> instr;
                        case Ignore.IGNORE -> instr;

                        case AddPtr(VarIr ptr, ValIr index, int scale,
                                    VarIr dst) ->
                                new AddPtr(ptr, index, scale, dst);
                        case CopyFromOffset(VarIr v1, long offset,
                                            VarIr dstName) ->
                                new CopyFromOffset((VarIr) replaceOperand(v1, reachingCopies), offset, dstName);
                        case CopyBitsFromOffset(VarIr v1, int byteOffset, int bitOffset,
                                                int bitWidth,
                                            VarIr dstName) ->
                                new CopyBitsFromOffset((VarIr) replaceOperand(v1, reachingCopies), byteOffset, bitOffset, bitWidth, dstName);
                        case CopyToOffset(ValIr v1, VarIr dstName,
                                          long offset) ->
                                new CopyToOffset(replaceOperand(v1, reachingCopies),
                                        dstName, offset);
                        case CopyBitsToOffset(ValIr v1, VarIr dstName,
                                          long byteOffset, int bitOffset, int bitWidth) ->
                                new CopyBitsToOffset(replaceOperand(v1, reachingCopies), dstName,
                                         byteOffset, bitOffset, bitWidth);
                        case DoubleToInt(ValIr v1, VarIr dstName) ->
                                new DoubleToInt(replaceOperand(v1, reachingCopies), dstName);
                        case DoubleToUInt(ValIr v1, VarIr dst) ->
                                new DoubleToUInt(replaceOperand(v1, reachingCopies), dst);
                        case IntToDouble(ValIr v1, VarIr dst) ->
                                new IntToDouble(replaceOperand(v1, reachingCopies), dst);
                        case SignExtendIr(ValIr v1, VarIr dst) ->
                                new SignExtendIr(replaceOperand(v1, reachingCopies), dst);
                        case TruncateIr(ValIr v1, VarIr dst) ->
                                new TruncateIr(replaceOperand(v1, reachingCopies), dst);
                        case UIntToDouble(ValIr v1, VarIr dst) ->
                                new UIntToDouble(replaceOperand(v1, reachingCopies), dst);
                        case ZeroExtendIr(ValIr v1, VarIr dst) ->
                                new ZeroExtendIr(replaceOperand(v1, reachingCopies), dst);
                        case BuiltinVaArgIr(VarIr v1, VarIr dst, Type type) ->
                                new BuiltinVaArgIr((VarIr) replaceOperand(v1, reachingCopies), dst, type);

                        default ->
                                throw new IllegalStateException("Unexpected value: " + instr);
                    };
                    if (!instr.equals(newInstr)) {
                        ins.set(j, newInstr);
                        updated = true;
                    }
                }
            }
        }
        return updated;
    }

    private static ValIr replaceOperand(ValIr op, Set<Copy> reachingCopies) {
        if (op == null || op instanceof Constant) return op;
        for (Copy copy : reachingCopies) {
            if (copy.dst().equals(op)) {
                return copy.src();
            }
        }
        return op;
    }


    /*
    Takes all the copy instructions that reach the beginning of a block and
    calculates which copies reach individual instructions within the block.
    See p. 591*/
    private static void transfer(BasicBlock block, Set<Copy> initialReachingCopies, HashMap<Integer, HashSet<Copy>[]>  INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS, Set<VarIr> aliasedVars) {
        var currentReachingCopies = new HashSet<>(initialReachingCopies);
        List<InstructionIr> instructions = block.instructions();
        INSTRUCTION_ANNOTATIONS.put(block.nodeId(), new HashSet[instructions.size()]);
        for (int i = 0; i < instructions.size(); i++) {
            var instruction = instructions.get(i);
            annotateInstruction(block.nodeId(), i, currentReachingCopies, INSTRUCTION_ANNOTATIONS);
            switch (instruction) {
                case Copy(ValIr src, VarIr dst) -> {
                    if (currentReachingCopies.contains(instruction)) {
                        continue;
                    }
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                    Type srcT = valToType(src);
                    Type dstT = valToType(dst);
                    if (srcT.equals(dstT) || Mcc.isSigned(srcT) == Mcc.isSigned(dstT))
                        currentReachingCopies.add((Copy) instruction);
                }
                case FunCall(String _, ArrayList<ValIr> _, boolean _, boolean _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> aliasedVars.contains(copy.src()) || aliasedVars.contains(copy.dst()) || ((copy.src().equals(dst) || copy.dst().equals(dst))));
                case Store(ValIr _, ValIr _) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> aliasedVars.contains(copy.src()) || aliasedVars.contains(copy.dst()));
                case AtomicStore(ValIr _, ValIr _, MemoryOrder _) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> aliasedVars.contains(copy.src()) || aliasedVars.contains(copy.dst()));
                case UnaryIr(UnaryOperator _, ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case BinaryIr(BinaryOperator _, ValIr _, ValIr _, VarIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case BinaryWithOverflowIr(BinaryOperator _, ValIr _, ValIr _, ValIr _, VarIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case Load(ValIr _, VarIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case GetAddress(ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case SignExtendIr(ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case CopyFromOffset(ValIr _, long _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case CopyBitsFromOffset(ValIr _, long _, int _, int _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case CopyToOffset(ValIr _, VarIr dst, long _) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case CopyBitsToOffset(ValIr _, VarIr dst, long _, int _, int _) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case ZeroExtendIr(ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case DoubleToInt(ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case DoubleToUInt(ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case IntToDouble(ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case UIntToDouble(ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case TruncateIr(ValIr _, ValIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case AddPtr(VarIr _, ValIr _, int _, VarIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case BuiltinC23VaStartIr(VarIr dst) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case BuiltinVaArgIr(VarIr _, VarIr dst, Type _) ->
                        currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                case LabelIr _, Jump _, JumpIfZero _, JumpIfNotZero _,
                     ReturnIr _, Ignore _, Compare _ -> {}


                default ->
                        throw new IllegalStateException("Unexpected value: " + instruction);
            }

        }
        annotateBlock(block.nodeId(), currentReachingCopies, BLOCK_ANNOTATIONS);
    }


    private static void annotateInstruction(int blockId, int instructionIndex, HashSet<Copy> currentReachingCopies, HashMap<Integer, HashSet<Copy>[]> INSTRUCTION_ANNOTATIONS) {
        INSTRUCTION_ANNOTATIONS.get(blockId)[instructionIndex]=currentReachingCopies;
    }

    public static void addNodesInReversePostOrder(CfgNode node, ArrayDeque<BasicBlock> workList, Set<Integer> alreadySeen) {
        if (alreadySeen.contains(node.nodeId())) return;
        alreadySeen.add(node.nodeId());
        for (var succ : node.successors()) {
            addNodesInReversePostOrder(succ, workList, alreadySeen);
        }
        if (node instanceof BasicBlock bb) {
            workList.addFirst(bb);
        }
    }


    private static void findReachingCopies(List<CfgNode> cfg,
                                           HashMap<Integer, HashSet<Copy>[]>  INSTRUCTION_ANNOTATIONS
            , HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS, Set<VarIr> aliasedVars) {
        HashSet<Copy> allCopies = findAllCopyInstructions(cfg);

        var alreadySeen = new HashSet<Integer>();
        ArrayDeque<BasicBlock> workList =new ArrayDeque<>();
        addNodesInReversePostOrder(cfg.getFirst(), workList, alreadySeen);
        for (CfgNode n : cfg) {
            if (n instanceof BasicBlock node) {
                annotateBlock(node.nodeId(), allCopies, BLOCK_ANNOTATIONS);
            }
        }
        while (!workList.isEmpty()) {
            BasicBlock block = workList.removeFirst();
            HashSet<Copy> oldAnnotations = getBlockAnnotation(block.nodeId(), BLOCK_ANNOTATIONS);
            Set<Copy> incomingCopies = meet(block, allCopies, BLOCK_ANNOTATIONS);
            transfer(block, incomingCopies, INSTRUCTION_ANNOTATIONS, BLOCK_ANNOTATIONS, aliasedVars);
            if (!oldAnnotations.equals(getBlockAnnotation(block.nodeId(), BLOCK_ANNOTATIONS))) {
                for (Object succObj : block.successors()) {
                    CfgNode succ = (CfgNode)succObj;
                    switch (succ) {
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


    /*p592*/
    private static Set<Copy> meet(BasicBlock block, HashSet<Copy> allCopies, HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS) {
        var incomingCopies = allCopies;
        for (var pred : block.predecessors()) {
            switch (pred) {
                case BasicBlock basicBlock -> {
                    var predOutCopies = getBlockAnnotation(basicBlock.nodeId(), BLOCK_ANNOTATIONS);
                    incomingCopies = Optimizer.intersection(incomingCopies, predOutCopies);
                }
                case EntryNode _ -> {return Collections.emptySet();}
                case ExitNode _ -> {
                    throw new Err("Malformed control flow graph");
                }
                default ->
                        throw new IllegalStateException("Unexpected value: " + pred);
            }
        }
        return incomingCopies;
    }

    private static HashSet<Copy> getBlockAnnotation(int nodeId, HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS) {
        return BLOCK_ANNOTATIONS.get(nodeId);
    }

    private static void annotateBlock(int nodeId, HashSet<Copy> allCopies, HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS) {
        BLOCK_ANNOTATIONS.put(nodeId, allCopies);
    }

    private static HashSet<Copy> findAllCopyInstructions(List<CfgNode> cfg) {
        HashSet<Copy> allCopies = new HashSet<>();
        for (CfgNode n : cfg) {
            if (n instanceof BasicBlock(int _, List ins,
                                        ArrayList<CfgNode> _, ArrayList<CfgNode> _)) {
                for (var in : ins) {
                    if (in instanceof Copy copy) {
                        allCopies.add(copy);
                    }
                }
            }
        }
        return allCopies;
    }

}
