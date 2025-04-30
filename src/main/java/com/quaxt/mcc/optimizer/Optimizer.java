package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.*;

import java.util.*;
import java.util.function.Predicate;

import static com.quaxt.mcc.Mcc.valToType;
import static com.quaxt.mcc.Optimization.*;
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

    //helps me set conditional breakpoints
    static String CURRENT_FUNCTION_NAME = "";

    private static TopLevel optimizeFunction(FunctionIr f, EnumSet<Optimization> optimizations) {
        CURRENT_FUNCTION_NAME = f.name();
        List<InstructionIr> instructions = f.instructions();
        boolean updated = true;
        while (updated) {
            updated = false;
            Set<VarIr> aliasedVars = addressTakenAnalysis(instructions);
            if (optimizations.contains(FOLD_CONSTANTS)) {
                updated = foldConstants(instructions);
            }
            List<Node> cfg = makeCFG(instructions);
            if (optimizations.contains(PROPAGATE_COPIES)) {
                updated |= propagateCopies(cfg, aliasedVars);
            }
            if (optimizations.contains(ELIMINATE_DEAD_STORES)) {
                updated |= eliminateDeadStores(cfg, aliasedVars);
            }
            if (optimizations.contains(ELIMINATE_UNREACHABLE_CODE)) {
                updated |= eliminateUnreachableCode(cfg);
            }
            instructions = cfgToInstructions(cfg);

        }
        return new FunctionIr(f.name(), f.global(), f.type(), instructions, f.returnType());
    }

    private static Set<VarIr> addressTakenAnalysis(List<InstructionIr> instructions) {
        Set<VarIr> aliasedVars = new HashSet<>();
        for (var instr : instructions) {
            switch (instr) {
                case GetAddress(VarIr obj, VarIr dst) -> aliasedVars.add(obj);
                case Ignore ignore -> {}
                case AddPtr(VarIr ptr, ValIr index, int scale, VarIr dst) -> {
                    if (ptr.isStatic()) aliasedVars.add(ptr);
                    if (dst.isStatic()) aliasedVars.add(ptr);
                }
                case BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                              VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (v2 instanceof VarIr var2 && var2.isStatic())
                        aliasedVars.add(var2);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case Copy(ValIr v1, VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case CopyFromOffset(ValIr v1, long offset, VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case CopyToOffset(ValIr v1, VarIr dstName, long offset) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case DoubleToInt(ValIr v1, VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case DoubleToUInt(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case FunCall(String name, ArrayList<ValIr> args, VarIr dst) -> {
                    for (var v1 : args) {
                        if (v1 instanceof VarIr var1 && var1.isStatic())
                            aliasedVars.add(var1);
                    }
                    if (dst != null && dst.isStatic()) aliasedVars.add(dst);
                }
                case IntToDouble(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case Jump jump -> {}
                case JumpIfNotZero(ValIr v1, String label) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                }
                case JumpIfZero(ValIr v1, String label) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                }
                case LabelIr labelIr -> {}
                case Load(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case ReturnIr(ValIr v1) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                }
                case SignExtendIr(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case Store(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case TruncateIr(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case UIntToDouble(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case UnaryIr(UnaryOperator op, ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case ZeroExtendIr(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
            }
        }
        return aliasedVars;
    }

    private static boolean eliminateDeadStores(List<Node> cfg, Set<VarIr> aliasedVars) {
        throw new Todo();
    }

    /**
     * based on rewriteInstructions p598
     */
    private static boolean propagateCopies(List<Node> cfg, Set<VarIr> aliasedVars) {
        HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS = new HashMap<>();
        HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS = new HashMap<>();
        findReachingCopies(cfg, INSTRUCTION_ANNOTATIONS, BLOCK_ANNOTATIONS, aliasedVars);
        boolean updated = false;
        for (int i = 0; i < cfg.size(); i++) {
            Node n = cfg.get(i);
            if (n instanceof BasicBlock(int _, List<InstructionIr> ins,
                                        ArrayList<Node> _, ArrayList<Node> _)) {
                BasicBlock b = (BasicBlock) n;
                for (int j = 0; j < ins.size(); j++) {
                    InstructionIr instr = b.instructions().get(j);
                    Set<Copy> reachingCopies = getInstructionAnnotation(b.nodeId(), j, INSTRUCTION_ANNOTATIONS);

                    var newInstr = switch (instr) {
                        case Copy(ValIr src, VarIr dst) -> {
                            for (Copy copy : reachingCopies) {
                                //MR-TODO uncomment last two compares
                                if (copy.equals(instr) || (copy.src().equals(dst) && copy.dst().equals(src))) {
                                    yield Ignore.IGNORE;
                                }
                            }
                            yield new Copy(replaceOperand(src, reachingCopies), dst);
                        }
                        case BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                                      VarIr dst) ->
                                new BinaryIr(op, replaceOperand(v1, reachingCopies), replaceOperand(v2, reachingCopies), dst);
                        case ReturnIr(ValIr v) ->
                                new ReturnIr(replaceOperand(v, reachingCopies));
                        case UnaryIr(UnaryOperator op, ValIr v1, VarIr dst) ->
                                new UnaryIr(op, replaceOperand(v1, reachingCopies), dst);
                        case FunCall(String name, ArrayList<ValIr> args,
                                     ValIr dst) -> {
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
                        case GetAddress(ValIr obj, VarIr dst) -> instr;
                        case LabelIr _, Jump _ -> instr;
                        case Ignore.IGNORE -> instr;

                        case AddPtr(VarIr ptr, ValIr index, int scale,
                                    VarIr dst) ->
                                new AddPtr(ptr, index, scale, dst);
                        case CopyFromOffset(VarIr v1, long offset,
                                            VarIr dstName) ->
                                new CopyFromOffset((VarIr) replaceOperand(v1, reachingCopies), offset, dstName);
                        case CopyToOffset(ValIr v1, VarIr dstName,
                                          long offset) ->
                                new CopyToOffset(replaceOperand(v1, reachingCopies), (VarIr) replaceOperand(dstName, reachingCopies), offset);
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

    private static Set<Copy> getInstructionAnnotation(int nodeId, int i, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS) {
        return INSTRUCTION_ANNOTATIONS.get(nodeId).get(i);
    }


    /*
    Takes all the copy instuctions that reach the beginning of a block and calculates which copies reach individual instructions within the block
    p591*/
    private static void transfer(BasicBlock block, Set<Copy> initialReachingCopies, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS, Set<VarIr> aliasedVars) {
        var currentReachingCopies = new HashSet<>(initialReachingCopies);
        List<InstructionIr> instructions = block.instructions();
        INSTRUCTION_ANNOTATIONS.put(block.nodeId(), new ArrayList<>());
        for (int i = 0; i < instructions.size(); i++) {
            var instruction = instructions.get(i);
            annotateInstruction(block.nodeId(), currentReachingCopies, INSTRUCTION_ANNOTATIONS);
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
                case FunCall(String _, ArrayList<ValIr> _, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> aliasedVars.contains(copy.src()) || aliasedVars.contains(copy.dst()) || (dst != null && (copy.src().equals(dst) || copy.dst().equals(dst))));
                }
                case Store(ValIr src, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> aliasedVars.contains(copy.src()) || aliasedVars.contains(copy.dst()));
                }
                case UnaryIr(UnaryOperator op, ValIr _, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                              VarIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case Load(ValIr ptr, VarIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }

                case GetAddress(ValIr v, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case SignExtendIr(ValIr v, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case CopyFromOffset(ValIr v, long _, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case CopyToOffset(ValIr src, VarIr dst, long offset) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case ZeroExtendIr(ValIr v, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case DoubleToInt(ValIr v, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case DoubleToUInt(ValIr v, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case IntToDouble(ValIr v, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case UIntToDouble(ValIr v, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case TruncateIr(ValIr v, ValIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case AddPtr(VarIr ptr, ValIr index, int scale, VarIr dst) -> {
                    currentReachingCopies = removeIf(currentReachingCopies, copy -> copy.src().equals(dst) || copy.dst().equals(dst));
                }
                case LabelIr _, Jump _, JumpIfZero _, JumpIfNotZero _,
                     ReturnIr _, Ignore _ -> {}
                default -> throw new Todo();
            }

        }
        annotateBlock(block.nodeId(), currentReachingCopies, BLOCK_ANNOTATIONS);
    }

    /**
     * return a new ArrayList like in but with items matching pred removed. Doesn't change in.
     */
    private static HashSet<Copy> removeIf(HashSet<Copy> in, Predicate<? super Copy> pred) {
        HashSet<Copy> out = new HashSet<>(in);
        out.removeIf(pred);
        return out;
    }

    private static void annotateInstruction(int blockId, HashSet<Copy> currentReachingCopies, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS) {
        INSTRUCTION_ANNOTATIONS.get(blockId).add(currentReachingCopies);
    }

    private static void findReachingCopies(List<Node> cfg, HashMap<Integer, ArrayList<HashSet<Copy>>> INSTRUCTION_ANNOTATIONS, HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS, Set<VarIr> aliasedVars) {
        HashSet<Copy> allCopies = findAllCopyInstructions(cfg);
        ArrayDeque<BasicBlock> workList = new ArrayDeque<>();//MR-TODO getting more bang for your block p.597
        for (Node n : cfg) {
            if (n instanceof BasicBlock node) {
                workList.add(node);
                annotateBlock(node.nodeId(), allCopies, BLOCK_ANNOTATIONS);
            }
        }
        while (!workList.isEmpty()) {
            BasicBlock block = workList.removeFirst();
            HashSet<Copy> oldAnnotations = getBlockAnnotation(block.nodeId(), BLOCK_ANNOTATIONS);
            Set<Copy> incomingCopies = meet(block, allCopies, BLOCK_ANNOTATIONS);
            transfer(block, incomingCopies, INSTRUCTION_ANNOTATIONS, BLOCK_ANNOTATIONS, aliasedVars);
            if (!oldAnnotations.equals(getBlockAnnotation(block.nodeId(), BLOCK_ANNOTATIONS))) {
                for (Node succ : block.successors()) {
                    switch (succ) {
                        case BasicBlock basicBlock -> {
                            if (!workList.contains(basicBlock))
                                workList.add(basicBlock);
                        }
                        case EntryNode _ -> {
                            throw new Err("Malformed control flow graph");
                        }
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
                    incomingCopies = intersection(incomingCopies, predOutCopies);
                }
                case EntryNode entryNode -> {return Collections.emptySet();}
                case ExitNode exitNode -> {
                    throw new Err("Malformed control flow graph");
                }
            }
        }
        return incomingCopies;
    }

    private static HashSet<Copy> intersection(HashSet<Copy> a, HashSet<Copy> b) {
        return removeIf(a, c -> !b.contains(c));
    }


    private static HashSet<Copy> getBlockAnnotation(int nodeId, HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS) {
        return BLOCK_ANNOTATIONS.get(nodeId);
    }

    private static void annotateBlock(int nodeId, HashSet<Copy> allCopies, HashMap<Integer, HashSet<Copy>> BLOCK_ANNOTATIONS) {
        BLOCK_ANNOTATIONS.put(nodeId, allCopies);
    }

    private static HashSet<Copy> findAllCopyInstructions(List<Node> cfg) {
        HashSet<Copy> allCopies = new HashSet<>();
        for (Node n : cfg) {
            if (n instanceof BasicBlock(int _, List<InstructionIr> ins,
                                        ArrayList<Node> _, ArrayList<Node> _)) {
                for (var in : ins) {
                    if (in instanceof Copy copy) {
                        allCopies.add(copy);
                    }
                }
            }
        }
        return allCopies;
    }


    private static List<InstructionIr> cfgToInstructions(List<Node> cfg) {
        ArrayList<InstructionIr> instructions = new ArrayList<>();
        for (Node n : cfg) {
            if (n instanceof BasicBlock(int _, List<InstructionIr> ins,
                                        ArrayList<Node> _, ArrayList<Node> _)) {
                for (var i : ins) {
                    if (!(i instanceof Ignore)) {
                        instructions.add(i);
                    }
                }
            }
        }
        return instructions;
    }

    private static boolean eliminateUnreachableCode(List<Node> cfg) {
        boolean updated = eliminateUnreachableBlocks(cfg);
        updated |= removeUselessJumps(cfg);
        updated |= removeUselessLabels(cfg);
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
            var instr = instructions.get(i);
            InstructionIr newIn = switch (instr) {
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
                    yield Ignore.IGNORE;
                }
                case JumpIfNotZero(ValIr v,
                                   String label) when v instanceof Constant<?> c -> {
                    if (!c.isZero()) {
                        yield new Jump(label);
                    }
                    yield Ignore.IGNORE;
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
