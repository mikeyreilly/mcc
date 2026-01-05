package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.*;
import com.quaxt.mcc.atomics.MemoryOrder;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.*;

import java.util.*;
import java.util.function.Predicate;

import static com.quaxt.mcc.Optimization.*;
import static com.quaxt.mcc.asm.Nullary.MFENCE;
import static com.quaxt.mcc.semantic.Primitive.DOUBLE;
import static com.quaxt.mcc.semantic.SemanticAnalysis.convertConst;

public class Optimizer {
    public static ProgramIr optimize(ProgramIr programIr,
                                     EnumSet<Optimization> optimizations) {
        for (int i = 0; i < programIr.topLevels().size(); i++) {
            TopLevel topLevel = programIr.topLevels().get(i);
            if (topLevel instanceof FunctionIr f) {
                programIr.topLevels().set(i, optimizeFunction(f,
                        optimizations));
            }
        }
        return programIr;
    }

    //helps me set conditional breakpoints
    static String CURRENT_FUNCTION_NAME = "";

    public static FunctionIr optimizeFunction(FunctionIr f,
                                            EnumSet<Optimization> optimizations) {
        CURRENT_FUNCTION_NAME = f.name();
        List<InstructionIr> instructions = f.instructions();
        instructions = optimizeInstructions(optimizations, instructions);
        return new FunctionIr(f.name(), f.global(), f.type(), instructions,
                f.funType(), f.callsVaStart(), f.inline());
    }

    public static List<InstructionIr> optimizeInstructions(
            EnumSet<Optimization> optimizations,
            List<InstructionIr> instructions) {
        boolean updated = true;
        while (updated) {
            updated = false;
            Set<VarIr> aliasedVars = addressTakenAnalysis(instructions);
            if (optimizations.contains(FOLD_CONSTANTS)) {
                updated = foldConstants(instructions);
            }
            List<CfgNode> cfg = makeCFG(instructions);
            if (optimizations.contains(PROPAGATE_COPIES)) {
                updated |= PropagateCopies.propagateCopies(cfg, aliasedVars);
            }
            if (optimizations.contains(ELIMINATE_DEAD_STORES)) {
                updated |= EliminateDeadStores.eliminateDeadStores(cfg,
                        aliasedVars);
            }
            if (optimizations.contains(ELIMINATE_UNREACHABLE_CODE)) {
                updated |= eliminateUnreachableCode(cfg);
            }
            instructions = cfgToInstructions(cfg);

        }
        return instructions;
    }

    public static Set<VarIr> addressTakenAnalysis(
            List<InstructionIr> instructions) {
        Set<VarIr> aliasedVars = new HashSet<>();
        for (var instr : instructions) {
            switch (instr) {
                case GetAddress(VarIr obj, VarIr _) -> aliasedVars.add(obj);
                case Ignore ignore -> {}
                case AddPtr(VarIr ptr, ValIr _, int _, VarIr dst) -> {
                    if (ptr.isStatic()) aliasedVars.add(ptr);
                    if (dst.isStatic()) aliasedVars.add(ptr);
                }
                case BinaryIr(BinaryOperator _, ValIr v1, ValIr v2,
                              VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (v2 instanceof VarIr var2 && var2.isStatic())
                        aliasedVars.add(var2);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case BinaryWithOverflowIr(BinaryOperator _, ValIr v1, ValIr v2, ValIr result,
                              VarIr overflow) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (v2 instanceof VarIr var2 && var2.isStatic())
                        aliasedVars.add(var2);
                    if (result instanceof VarIr var2 && var2.isStatic())
                        aliasedVars.add(var2);
                    if (overflow.isStatic()) aliasedVars.add(overflow);
                }
                case Copy(ValIr v1, VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case CopyFromOffset(ValIr v1, long _, VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case CopyBitsFromOffset(ValIr v1, long _,int _, int _, VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case CopyBitsFromOffsetViaPointer(ValIr v1, long _,int _, int _, VarIr dstName) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case CopyToOffset(ValIr v1, VarIr dstName, long _) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case CopyBitsToOffset(ValIr v1, VarIr dstName, long _, int _, int _) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dstName.isStatic()) aliasedVars.add(dstName);
                }
                case CopyBitsToOffsetViaPointer(ValIr v1, VarIr dstName, long _, int _, int _) -> {
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
                case FunCall(VarIr name, ArrayList<ValIr> args, boolean varargs,
                             boolean indirect, VarIr dst) -> {

                        if (name.isStatic()) aliasedVars.add(dst);

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
                case FloatToDouble(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case DoubleToFloat(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case Jump _ -> {}
                case MFENCE -> {}
                case JumpIfNotZero(ValIr v1, String _) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                }
                case JumpIfZero(ValIr v1, String _) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                }
                case LabelIr _ -> {}
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
                case AtomicStore(ValIr v1, VarIr dst, MemoryOrder _) -> {
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
                case UnaryIr(UnaryOperator _, ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case ZeroExtendIr(ValIr v1, VarIr dst) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (dst.isStatic()) aliasedVars.add(dst);
                }
                case Compare(Type _, ValIr v1, ValIr v2) -> {
                    if (v1 instanceof VarIr var1 && var1.isStatic())
                        aliasedVars.add(var1);
                    if (v2 instanceof VarIr var2 && var2.isStatic())
                        aliasedVars.add(var2);
                }
                case BuiltinC23VaStartIr(VarIr var1) -> {
                    if (var1.isStatic()) aliasedVars.add(var1);
                }
                case BuiltinVaArgIr _ -> {
                    // NOOP because already taken care of by va_start
                }
                default ->
                        throw new IllegalStateException("Unexpected value: " + instr);
            }
        }
        return aliasedVars;
    }

    private static boolean eliminateDeadStores(List<CfgNode> cfg,
                                               Set<VarIr> aliasedVars) {
        throw new Todo();
    }


    /**
     * return a new ArrayList like in but with items matching pred removed.
     * Doesn't change in.
     */
    public static HashSet<Copy> removeIf(HashSet<Copy> in,
                                         Predicate<? super Copy> pred) {
        HashSet<Copy> out = new HashSet<>(in);
        out.removeIf(pred);
        return out;
    }

    public static HashSet<Copy> intersection(HashSet<Copy> a, HashSet<Copy> b) {
        return removeIf(a, c -> !b.contains(c));
    }

    private static <T extends AbstractInstruction> List<T> cfgToInstructions(
            List<CfgNode> cfg) {
        ArrayList<T> instructions = new ArrayList<>();
        for (CfgNode n : cfg) {
            if (n instanceof BasicBlock(int _, List ins, ArrayList<CfgNode> _,
                                        ArrayList<CfgNode> _)) {
                for (var i : ins) {
                    if (!(i instanceof Ignore)) {
                        instructions.add((T) i);
                    }
                }
            }
        }
        return instructions;
    }

    private static boolean eliminateUnreachableCode(List<CfgNode> cfg) {
        boolean updated = eliminateUnreachableBlocks(cfg);
        updated |= removeUselessJumps(cfg);
        updated |= removeUselessLabels(cfg);
        return updated;
    }

    private static <T extends AbstractInstruction> boolean removeUselessLabels(
            List<CfgNode> nodes) {
        // iterate through all BasicBlocks (n.b. neither the first nor last
        // nodes are BasicBlocks)
        boolean updated = false;
        for (int i = 1; i < nodes.size() - 1; i++) {
            BasicBlock<T> b = (BasicBlock<T>) nodes.get(i);
            List<T> instrs = b.instructions();
            if (instrs.isEmpty()) continue;
            T instr = instrs.getFirst();
            if (instr instanceof LabelIr) {
                boolean keepLabel = false;
                var defaultPredecessor = nodes.get(i - 1).nodeId();
                for (CfgNode pred : b.predecessors()) {
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

    private static <T extends AbstractInstruction> boolean removeUselessJumps(
            List<CfgNode> nodes) {
        // iterate through all but the last BasicBlocks (n.b. neither the
        // first nor last nodes are BasicBlocks)
        boolean updated = false;
        for (int i = 1; i < nodes.size() - 2; i++) {
            BasicBlock<T> b = (BasicBlock<T>) nodes.get(i);
            var instrs = b.instructions();
            if (instrs.isEmpty()) continue;
            T instr = instrs.getLast();
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

    private static boolean eliminateUnreachableBlocks(List<CfgNode> nodes) {
        Set<Integer> nodesToKeep = new HashSet<>();
        nodesToKeep.add(nodes.getLast().nodeId());// always keep the exit
        // node, even if there's an infinite loop
        nodesToKeep(nodesToKeep, nodes, nodes.getFirst());
        return nodes.removeIf(n -> !nodesToKeep.contains(n.nodeId()));
    }

    private static void nodesToKeep(Set<Integer> nodesToKeep,
                                    List<CfgNode> nodes, CfgNode node) {
        nodesToKeep.add(node.nodeId());
        for (var n : node.successors()) {
            if (!nodesToKeep.contains(n.nodeId()))
                nodesToKeep(nodesToKeep, nodes, n);
        }
    }

    public static <T extends AbstractInstruction> List<CfgNode> makeCFG(
            List<T> instructions) {
        List<List<T>> blocks = partitionIntoBasicBlocks(instructions);
        EntryNode entryNode = new EntryNode(new ArrayList<>());
        List<CfgNode> nodes = new ArrayList<>();
        ExitNode exitNode = new ExitNode(new ArrayList<>());
        nodes.add(entryNode);
        int max = blocks.size() - 1;
        Map<String, CfgNode> labelToNodeId = new HashMap<>();
        for (int i = 0; i <= max; i++) {
            var block = blocks.get(i);
            int blockId = i + 1;
            var bb = new BasicBlock<T>(blockId, block, new ArrayList<>(),
                    new ArrayList<>());
            nodes.add(bb);
            if (block.getFirst() instanceof LabelIr(String label)) {
                labelToNodeId.put(label, bb);
            }
        }
        nodes.add(exitNode);
        addEdge(nodes, entryNode, nodes.get(1));
        for (int i = 0; i <= max; i++) {
            CfgNode nodeId = nodes.get(i + 1);
            CfgNode nextId;
            if (i == max) {
                nextId = exitNode;
            } else {
                nextId = nodes.get(i + 2);
            }
            BasicBlock<T> node = (BasicBlock<T>) nodeId;
            T instr = node.instructions().getLast();
            switch (instr) {
                /** RET is the only Nullary */
                case Nullary _, ReturnIr _ -> addEdge(nodes, nodeId, exitNode);
                case Jump(String label) ->
                        addEdge(nodes, nodeId, labelToNodeId.get(label));
                case JmpCC(CC _,
                           String label) -> {
                    addEdge(nodes, nodeId, labelToNodeId.get(label));
                    addEdge(nodes, nodeId, nextId);
                }
                case JumpIfNotZero(ValIr _, String label) -> {
                    addEdge(nodes, nodeId, labelToNodeId.get(label));
                    addEdge(nodes, nodeId, nextId);
                }
                case JumpIfZero(ValIr _, String label) -> {
                    addEdge(nodes, nodeId, labelToNodeId.get(label));
                    addEdge(nodes, nodeId, nextId);
                }
                default -> addEdge(nodes, nodeId, nextId);
            }
        }
        return nodes;
    }

    private static void addEdge(List<CfgNode> nodes, CfgNode from, CfgNode to) {
        from.successors().add(to);
        to.predecessors().add(from);
    }

    private static <T extends AbstractInstruction> List<List<T>> partitionIntoBasicBlocks(
            List<T> instructions) {
        List<List<T>> finishedBlocks = new ArrayList<>();
        List<T> currentBlock = new ArrayList<>();
        for (T instr : instructions) {
            if (instr instanceof LabelIr) {
                if (!currentBlock.isEmpty()) {
                    finishedBlocks.add(currentBlock);
                }
                currentBlock = new ArrayList<>();
                currentBlock.add(instr);
            } else switch (instr) {
                case Nullary _, JmpCC _, Jump _, JumpIfNotZero _, JumpIfZero _,
                     ReturnIr _ -> {
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
                    yield new Copy((ValIr) convertConst(co, Mcc.type(dstName)), dstName);
                }

                case DoubleToInt(ValIr src,
                                 VarIr dst) when src instanceof Constant c1 -> {
                    yield new Copy((ValIr) convertConst(c1, Mcc.type(dst)), dst);
                }
                case DoubleToUInt(ValIr src,
                                  VarIr dst) when src instanceof Constant c1 -> {
                    yield new Copy((ValIr) convertConst(c1, Mcc.type(dst)), dst);
                }
                case IntToDouble(ValIr src,
                                 VarIr dst) when src instanceof Constant c1 ->
                        new Copy((ValIr) convertConst(c1, DOUBLE), dst);
                case SignExtendIr(ValIr src,
                                  VarIr dst) when src instanceof Constant c1 -> {
                    yield new Copy((ValIr) convertConst(c1, Mcc.type(dst)), dst);
                }
                case ZeroExtendIr(ValIr src,
                                  VarIr dst) when src instanceof Constant c1 -> {
                    yield new Copy((ValIr) convertConst(c1, Mcc.type(dst)), dst);
                }
                case TruncateIr(ValIr src,
                                VarIr dst) when src instanceof Constant c1 -> {
                    yield new Copy((ValIr) convertConst(c1, Mcc.type(dst)), dst);
                }
                case UIntToDouble(ValIr src,
                                  VarIr dst) when src instanceof Constant c1 ->
                        new Copy((ValIr) convertConst(c1, DOUBLE), dst);
                case BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                              VarIr dstName) when v1 instanceof Constant c1 && v2 instanceof Constant c2 -> {
                    Constant<?> co = c1.apply1(op, c2);
                    if (co == null) {
                        c1.apply1(op, c2);
                        throw new Todo();
                        //yield null;
                    }
                    yield new Copy((ValIr) convertConst(co, Mcc.type(dstName)), dstName);
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

                case Copy(ValIr src,
                          VarIr dst) when src instanceof Constant c1 -> {
                    var dstT = Mcc.type(dst);
                    var srcT = Mcc.type(src);
                    if (srcT.equals(dstT) || (srcT.isSigned() == dstT.isSigned()))
                        yield null;
                    yield new Copy((ValIr) convertConst(c1, dstT), dst);
                }
                case Compare(Type type, ValIr v1,
                             ValIr v2) when v1 instanceof Constant c1 && v2 instanceof Constant c2 -> {
                    Constant c3 = c1.apply1(ArithmeticOperator.SUB, c2);
                    var nextInstruction = instructions.get(i + 1);
                    if (nextInstruction instanceof JumpIfZero jiz) {
                        instructions.set(i, Ignore.IGNORE);
                        if (!c3.isZero()) {
                            instructions.set(++i, Ignore.IGNORE);
                        } else {
                            instructions.set(++i, new Jump(jiz.label()));
                        }
                    }
                    yield null;
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
