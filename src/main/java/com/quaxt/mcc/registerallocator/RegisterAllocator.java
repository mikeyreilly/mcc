package com.quaxt.mcc.registerallocator;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.*;
import com.quaxt.mcc.optimizer.BasicBlock;
import com.quaxt.mcc.optimizer.CfgNode;
import com.quaxt.mcc.optimizer.LivenessAnalyzer;
import com.quaxt.mcc.tacky.*;

import static com.quaxt.mcc.asm.DoubleReg.*;
import static com.quaxt.mcc.asm.IntegerReg.*;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.DOUBLE;
import static com.quaxt.mcc.optimizer.LivenessAnalyzer.findUsedAndUpdated;
import static com.quaxt.mcc.optimizer.Optimizer.makeCFG;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RegisterAllocator {

    /**
     * p. 630
     */
    public static void allocateRegisters(FunctionAsm functionAsm) {
        List<Instruction> instructions = functionAsm.instructions;
        List<Node> interferenceGraphGpr;
        List<Node> interferenceGraphMmx;
        while (true) {
            var graphs = buildGraph(functionAsm);
            interferenceGraphGpr = graphs.key();
            interferenceGraphMmx = graphs.value();
            Map<Operand, Operand> coalescedRegs = initDisjointSets();
            coalesce(interferenceGraphGpr, instructions, 12, coalescedRegs);
            coalesce(interferenceGraphMmx, instructions, 14, coalescedRegs);
            if (nothingWasCoalesced(coalescedRegs)) break;
            rewriteCoalesced(instructions, coalescedRegs);
        }
        addSpillCosts(interferenceGraphGpr, interferenceGraphMmx, instructions);
        // k is 12 for general purpose registers, 14 for MMX
        colorGraph(interferenceGraphGpr, 12);
        colorGraph(interferenceGraphMmx, 14);
        var registerMaps = createRegisterMap(interferenceGraphGpr,
                interferenceGraphMmx, functionAsm);
        replacePseudoRegs(instructions, registerMaps.key());
        replacePseudoRegs(instructions, registerMaps.value());
    }

    private static void debugInstructions(List<Instruction> instructions) {
        for (var in : instructions) {
            System.out.print(ProgramAsm.formatInstruction(in));
        }
    }

    private static void rewriteCoalesced(List<Instruction> instructions,
                                         Map<Operand, Operand> coalescedRegs) {
        int copyTo = 0;
        for (var oldInst : instructions) {
            switch (oldInst) {
                case Mov(TypeAsm type, Operand src, Operand dst) -> {
                    src = find(src, coalescedRegs);
                    dst = find(dst, coalescedRegs);
                    if (!src.equals(dst)) {
                        instructions.set(copyTo++, new Mov(type, src, dst));
                    }
                }
                case Nullary _, Cdq _, Jump _, JmpCC _, LabelIr _, Call _,
                     Comment _ -> instructions.set(copyTo++, oldInst);
                case Unary(UnaryOperator op, TypeAsm typeAsm,
                           Operand operand) ->
                        instructions.set(copyTo++, new Unary(op, typeAsm,
                                find(operand, coalescedRegs)));
                case Binary(ArithmeticOperator op, TypeAsm typeAsm, Operand src,
                            Operand dst) ->
                        instructions.set(copyTo++, new Binary(op, typeAsm,
                                find(src, coalescedRegs), find(dst,
                                coalescedRegs)));

                case Cmp(TypeAsm typeAsm, Operand subtrahend,
                         Operand minuend) ->
                        instructions.set(copyTo++, new Cmp(typeAsm,
                                find(subtrahend, coalescedRegs), find(minuend
                                , coalescedRegs)));
                case SetCC(CmpOperator cmpOperator, boolean signed,
                           Operand operand) ->
                        instructions.set(copyTo++, new SetCC(cmpOperator,
                                signed, find(operand, coalescedRegs)));
                case Pop(IntegerReg operand) ->
                        instructions.set(copyTo++,
                                new Pop((IntegerReg) find(operand,
                                        coalescedRegs)));
                case Push(Operand operand) ->
                        instructions.set(copyTo++, new Push(find(operand,
                                coalescedRegs)));
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) ->
                        instructions.set(copyTo++, new Movsx(srcType, dstType
                                , find(src, coalescedRegs), find(dst,
                                coalescedRegs)));
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) ->
                        instructions.set(copyTo++, new MovZeroExtend(srcType,
                                dstType, find(src, coalescedRegs), find(dst,
                                coalescedRegs)));
                case Cvttsd2si(TypeAsm dstType, Operand src, Operand dst) ->
                        instructions.set(copyTo++, new Cvttsd2si(dstType,
                                find(src, coalescedRegs), find(dst,
                                coalescedRegs)));
                case Cvtsi2sd(TypeAsm dstType, Operand src, Operand dst) ->
                        instructions.set(copyTo++, new Cvtsi2sd(dstType,
                                find(src, coalescedRegs), find(dst,
                                coalescedRegs)));
                case Lea(Operand src, Operand dst) ->
                        instructions.set(copyTo++, new Lea(find(src,
                                coalescedRegs), find(dst, coalescedRegs)));


            }
        }
        int oldSize = instructions.size();
        if (copyTo != oldSize) {
            instructions.subList(copyTo, oldSize).clear();
        }
    }

    private static boolean nothingWasCoalesced(
            Map<Operand, Operand> coalescedRegs) {
        return coalescedRegs.isEmpty();
    }

    /* p. 665 */
    private static Map<Operand, Operand> coalesce(List<Node> graph,
                                                  List<Instruction> instructions,
                                                  int k,
                                                  Map<Operand, Operand> coalescedRegs) {
        for (var instr : instructions) {
            switch (instr) {
                case Mov(TypeAsm type, Operand src, Operand dst) -> {
                    src = find(src, coalescedRegs);
                    dst = find(dst, coalescedRegs);

                    if (graphContains(graph, src) && graphContains(graph,
                            dst) && !src.equals(dst)) {
                        var sourceNode = findNodeForOperand(graph, src);
                        if ((sourceNode == null || !graphContains(sourceNode.neighbours, dst)) && conservativeCoalesceable(graph, src, dst, k)) {
                            Operand toKeep;
                            Operand toMerge;

                            if (src instanceof HardReg) {
                                toKeep = src;
                                toMerge = dst;
                            } else {
                                toKeep = dst;
                                toMerge = src;
                            }
                            union(toMerge, toKeep, coalescedRegs);
                            updateGraph(graph, toMerge, toKeep);

                        }
                    }

                }
                default -> {
                    continue;
                }
            }
        }
        return coalescedRegs;
    }

    private static void updateGraph(List<Node> graph, Operand x, Operand y) {
        var nodeToRemove = findNodeForOperand(graph, x);
        for (var neighbour : new ArrayList<>(nodeToRemove.neighbours)) {
            addEdge(graph, neighbour, y);
            removeEdge(graph, neighbour, x);
        }
        graph.remove(nodeToRemove);
    }


    private static Operand find(Operand r, Map<Operand, Operand> regMap) {
        var result = regMap.get(r);
        if (result == null) return r;
        return find(result, regMap);
    }

    private static void union(Operand x, Operand y,
                              Map<Operand, Operand> regMap) {
        regMap.put(x, y);
    }

    private static boolean conservativeCoalesceable(List<Node> graph,
                                                    Operand src, Operand dst,
                                                    int k) {
        if (briggsTest(graph, src, dst, k)) return true;
        if (src instanceof HardReg h) return georgeTest(graph, h, dst, k);
        if (dst instanceof HardReg h) return georgeTest(graph, h, src, k);
        return false;
    }

    /**
     * Return true if merged node will have fewer than k neighbours of
     * significant degree. p. 666
     */
    private static boolean briggsTest(List<Node> graph, Operand x, Operand y,
                                      int k) {
        int significantNeighbours = 0;
        var xNode = findNodeForOperand(graph, x);
        var yNode = findNodeForOperand(graph, y);
        Set<Node> combinedNeighbours = new HashSet<>(xNode.neighbours);
        combinedNeighbours.addAll(yNode.neighbours);

        for (var neighbourNode : combinedNeighbours) {
            int degree = neighbourNode.neighbours.size();
            if (neighbourNode.neighbours.contains(xNode) && neighbourNode.neighbours.contains(yNode)) {
                degree--;
            }
            if (degree >= k) {
                significantNeighbours++;
                if (significantNeighbours >= k) return false;
            }
        }

        return true;
    }

    /* The George test says that you can coalesce a pseudoregister p into a
    hard register h if each of p's neighbours meets either of two conditions:
       1. It has fewer than k neighbours
       2. It already interferes with h
       p. 667 */
    private static boolean georgeTest(List<Node> graph, HardReg hardReg,
                                      Operand pseudoReg, int k) {
        var pseudoNode = findNodeForOperand(graph, pseudoReg);
        for (Node n : pseudoNode.neighbours) {
            if (!graphContains(n.neighbours, hardReg) && n.neighbours.size() >= k) {
                return false;
            }
        }
        return true;


    }

    private static void debugGraph(List<Node> graph, Operand hardReg,
                                   Operand pseudoReg, boolean b) {
        System.out.println("----------------------------------------");
        for (var n : graph) {

            boolean highlight =
                    n.operand.equals(hardReg) || n.operand.equals(pseudoReg);
            System.out.println((b ? String.valueOf(n.color) + "\t" : "") + (highlight ? "[" + summary(n) + "]" : summary(n)));
        }
    }

    private static String summary(Node n) {
        Operand op = n.operand;
        return summaryOp(op) + "->" + n.neighbours.stream().map(node -> summaryOp(node.operand)).collect(Collectors.joining(", "));
    }

    private static String summaryOp(Operand op) {
        return switch (op) {
            case Pseudo pseudo -> pseudo.identifier;
            default -> String.valueOf(op);
        };
    }


    private static boolean graphContains(List<Node> graph, Operand operand) {
        return findNodeForOperand(graph, operand) != null;
    }


    private static Map<Operand, Operand> initDisjointSets() {
        return new HashMap<>();
    }


    /**
     * Does an in-place transformation of instructions so that each
     * instruction has its operands replaced by the operand mapped in
     * registerMap. Mov instructions from X to X are removed.
     * p. 647
     */
    private static void replacePseudoRegs(List<Instruction> instructions,
                                          Map<Pseudo, Reg> registerMap) {
        int copyTo = 0;
        for (int copyFrom = 0; copyFrom < instructions.size(); copyFrom++) {
            Instruction instr = instructions.get(copyFrom);
            if (instr instanceof Mov(TypeAsm type, Operand src, Operand dst)) {
                var newSrc = replaceOperand(src, registerMap);
                var newDst = replaceOperand(dst, registerMap);
                if (newSrc != newDst) {
                    instructions.set(copyTo++, new Mov(type, newSrc, newDst));
                }

            } else instructions.set(copyTo++, switch (instr) {
                case Binary(ArithmeticOperator op, TypeAsm type, Operand src,
                            Operand dst) ->
                        new Binary(op, type, replaceOperand(src, registerMap)
                                , replaceOperand(dst, registerMap));
                case Cmp(TypeAsm type, Operand subtrahend, Operand minuend) ->
                        new Cmp(type, replaceOperand(subtrahend, registerMap)
                                , replaceOperand(minuend, registerMap));
                case Cvtsi2sd(TypeAsm srcType, Operand src, Operand dst) ->
                        new Cvtsi2sd(srcType, replaceOperand(src,
                                registerMap), replaceOperand(dst, registerMap));
                case Cvttsd2si(TypeAsm dstType, Operand src, Operand dst) ->
                        new Cvttsd2si(dstType, replaceOperand(src,
                                registerMap), replaceOperand(dst, registerMap));
                case Lea(Operand src, Operand dst) ->
                        new Lea(replaceOperand(src, registerMap),
                                replaceOperand(dst, registerMap));
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) ->
                        new MovZeroExtend(srcType, dstType,
                                replaceOperand(src, registerMap),
                                replaceOperand(dst, registerMap));
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) ->
                        new Movsx(srcType, dstType, replaceOperand(src,
                                registerMap), replaceOperand(dst, registerMap));
                case Push(Reg operand) ->
                        new Push(replaceOperand(operand, registerMap));
                case SetCC(CmpOperator cmpOperator, boolean unsigned,
                           Operand operand) ->
                        new SetCC(cmpOperator, unsigned,
                                replaceOperand(operand, registerMap));
                case Unary(UnaryOperator op, TypeAsm type, Operand operand) ->
                        new Unary(op, type, replaceOperand(operand,
                                registerMap));
                default -> instr;
            });
        }
        instructions.subList(copyTo, instructions.size()).clear();
    }

    private static Operand replaceOperand(Operand op,
                                          Map<Pseudo, Reg> registerMap) {
        if (op instanceof Pseudo p) {
            Reg r = registerMap.get(p);
            if (r != null) return r;
        }
        return op;
    }

    private static Pair<Map<Pseudo, Reg>, Map<Pseudo, Reg>> createRegisterMap(
            List<Node> coloredGraph, List<Node> coloredGraphMmx,
            FunctionAsm function) {
        Map<Integer, IntegerReg> colorMap = new HashMap<>();
        Map<Integer, DoubleReg> colorMapMmx = new HashMap<>();
        for (Node node : coloredGraph) {
            if (node.operand instanceof IntegerReg reg) {
                colorMap.put(node.color, reg);
            }
        }
        for (Node node : coloredGraphMmx) {
            if (node.operand instanceof DoubleReg reg) {
                colorMapMmx.put(node.color, reg);
            }
        }
        Map<Pseudo, Reg> registerMap = new HashMap<>();
        Map<Pseudo, Reg> registerMapMmx = new HashMap<>();
        Set<IntegerReg> calleeSavedRegs = EnumSet.noneOf(IntegerReg.class);
        for (Node node : coloredGraph) {
            switch (node.operand) {
                case Pseudo p -> {
                    if (node.color != -1) {
                        var hardReg = colorMap.get(node.color);
                        registerMap.put(p, hardReg);
                        if (hardReg.isCalleeSaved) {
                            calleeSavedRegs.add(hardReg);
                        }
                    } else {
                        //System.out.println("SPILLING " + node);
                    }
                }
                default -> {}
            }
        }
        for (Node node : coloredGraphMmx) {
            switch (node.operand) {
                case Pseudo p -> {
                    if (node.color != -1) {
                        DoubleReg doubleReg = colorMapMmx.get(node.color);
                        registerMapMmx.put(p, doubleReg);
                    }
                }
                default -> {}
            }
        }
        function.calleeSavedRegs = calleeSavedRegs.toArray(new IntegerReg[0]);
        return new Pair<>(registerMap, registerMapMmx);
    }

    /* p. 644*/
    private static void colorGraph(List<Node> g, int k) {
        var remaining = g.stream().filter(n -> !n.pruned).toList();
        if (remaining.isEmpty()) return;
        Node chosenNode = null;
        for (Node node : remaining) {
            long degree =
                    node.neighbours.stream().filter(n -> !n.pruned).count();

            if (degree < k) {
                chosenNode = node;
                break;
            }

        }

        if (chosenNode == null) {
            double bestSpillMetric = Double.POSITIVE_INFINITY;
            for (Node node : remaining) {
                long degree =
                        node.neighbours.stream().filter(n -> !n.pruned).count();
                double spillMetric = node.spillCost / degree;
                if (spillMetric < bestSpillMetric) {
                    chosenNode = node;
                    bestSpillMetric = spillMetric;
                }
            }

        }
        chosenNode.pruned = true;
        colorGraph(g, k);
        BitSet b = new BitSet(k + 1);
        b.set(1, k + 1);
        for (var neighbour : chosenNode.neighbours) {
            if (neighbour.color != -1) b.clear(neighbour.color);
        }
        int lowestAvailableColor = b.nextSetBit(0);
        if (lowestAvailableColor != -1) {
            boolean chooseHighestAvailableColor =
                    chosenNode.operand instanceof IntegerReg r && r.isCalleeSaved;
            chosenNode.color = chooseHighestAvailableColor ? b.length() - 1 :
                    lowestAvailableColor;
            chosenNode.pruned = false;
        }
    }

    private static void addSpillCosts(List<Node> interferenceGraph,
                                      List<Node> interferenceGraphMmx,
                                      List<Instruction> instructions) {

        for (var instr : instructions) {
            switch (instr) {
                case Binary(ArithmeticOperator op, TypeAsm type, Operand src,
                            Operand dst) -> {
                    if (op == ArithmeticOperator.DIVIDE) {
                        throw new Todo();
                    }
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, src, dst);
                }
                case Call(String name) -> {
                }
                case Cdq cdq -> {
                }
                case Cmp(TypeAsm type, Operand subtrahend, Operand minuend) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, subtrahend, minuend);
                }
                case Cvtsi2sd(TypeAsm srcType, Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, src, dst);
                }
                case Cvttsd2si(TypeAsm dstType, Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, src, dst);
                }
                case Lea(Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, src, dst);
                }
                case Mov(TypeAsm type, Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, src, dst);
                }
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, src, dst);
                }
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, src, dst);
                }
                case Pop(Reg operand) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, operand);
                }
                case SetCC(CmpOperator cmpOperator, boolean unsigned,
                           Operand operand) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, operand);
                }
                case Unary(UnaryOperator op, TypeAsm type, Operand operand) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, operand);
                }
                case Push(Operand operand) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, operand);
                }
                case Comment _, JmpCC _, Nullary _, Jump _, LabelIr _ -> {}
            }
        }

    }

    private static void incrementSpillCost(List<Node> interferenceGraph,
                                           List<Node> interferenceGraphMmx,
                                           Operand... ops) {
        for (var op : ops) {
            switch (op) {
                case Pseudo p -> {
                    if (p.isStatic || p.isAliased) continue;
                    Node n = findNodeForOperand(p.type == DOUBLE ?
                            interferenceGraphMmx : interferenceGraph, op);
                    n.spillCost += 1;
                }
                case DoubleReg _ -> {
                    Node n = findNodeForOperand(interferenceGraphMmx, op);
                    if (n != null) n.spillCost = Double.POSITIVE_INFINITY;
                }
                case IntegerReg _ -> {
                    Node n = findNodeForOperand(interferenceGraph, op);
                    if (n != null) n.spillCost = Double.POSITIVE_INFINITY;
                }
                default -> {
                }
            }
        }
    }

    /* returns a pair of graphs. The first graph is the one for General Purpose
     Registers and the second one is for MMX. See p. 632 for how GPR graph is
     constructed. See p. 637 for MMX.
    */

    private static Pair<List<Node>, List<Node>> buildGraph(
            FunctionAsm functionAsm) {
        List<Instruction> instructions = functionAsm.instructions;
        Pair<Integer, Integer> returnRegisters = functionAsm.returnRegisters;
        var interferenceGraphGpr = baseGraph(BASE_GRAPH_GPRS);
        var interferenceGraphMmx = baseGraph(BASE_GRAPH_DOUBLE_REGS);
        addPseudoRegisters(instructions, interferenceGraphGpr,
                interferenceGraphMmx);
        List<CfgNode> cfg = makeCFG(instructions);
        HashMap<Integer, Set<Operand>[]> instructionAnnotations =
                livenessAnalysis(cfg, returnRegisters);
        addEdges(cfg, interferenceGraphGpr, interferenceGraphMmx,
                instructionAnnotations);
        return new Pair<>(interferenceGraphGpr, interferenceGraphMmx);
    }

    private static void debugAnnotations(FunctionAsm functionAsm,
                                         List<CfgNode> cfg,
                                         HashMap<Integer, Set<Operand>[]> instructionAnnotations) {
        System.out.println("-----------------" + functionAsm.name);

        for (int i = 0; i < cfg.size(); i++) {
            var node = cfg.get(i);
            if (node instanceof BasicBlock<?>) {
                var block = (BasicBlock<Instruction>) node;
                List<Instruction> instructions = block.instructions();
                for (int j = 0; j < instructions.size(); j++) {
                    Set<Operand> liveRegisters =
                            instructionAnnotations.get(i)[j];
                    System.out.println(liveRegisters.stream().map(x -> x instanceof Pseudo p ? p.identifier : String.valueOf(x)).sorted().collect(Collectors.joining(", ")));

                    System.out.print(ProgramAsm.formatInstruction(instructions.get(j)));
                }
            }
        }
    }

    /* p. 636*/
    private static void addEdges(List<CfgNode> cfg,
                                 List<Node> interferenceGraphGpr,
                                 List<Node> interferenceGraphMmx,
                                 HashMap<Integer, Set<Operand>[]> instructionAnnotations) {
        for (int i = 0; i < cfg.size(); i++) {
            var node = cfg.get(i);
            if (node instanceof BasicBlock<?>) {
                var block = (BasicBlock<Instruction>) node;
                List<Instruction> instructions = block.instructions();
                for (int j = 0; j < instructions.size(); j++) {
                    var instr = instructions.get(j);
                    var usedAndUpdated = findUsedAndUpdated(instr);
                    var updated = usedAndUpdated.value();
                    Set<Operand> liveRegisters =
                            instructionAnnotations.get(i)[j];
                    for (Operand l : liveRegisters) {
                        if (instr instanceof Mov(TypeAsm _, Operand src,
                                                 Operand _) && l.equals(src)) {
                            continue;
                        }
                        for (Operand u : updated) {
                            var interferenceGraph =
                                    u instanceof DoubleReg || (u instanceof Pseudo p && p.type == DOUBLE) ? interferenceGraphMmx : interferenceGraphGpr;
                            Node nodeForU =
                                    findNodeForOperand(interferenceGraph, u);
                            if (nodeForU != null && graphContains(interferenceGraph, l) && !Objects.equals(l, u)) {
                                addEdge(interferenceGraph, nodeForU, l);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Mcc.class.getName());

    private static void addEdge(List<Node> graph, Node aNode, Operand bOp) {
        var bNode = add(bOp, graph);
        boolean found = false;
        for (var n : aNode.neighbours) {
            if (n.operand.equals(bOp)) {
                found = true;
                break;
            }
        }
        if (!found) {
            aNode.neighbours.add(bNode);
            bNode.neighbours.add(aNode);
        }
    }

    private static void removeEdge(List<Node> graph, Node aNode, Operand bOp) {
        Node bNode = findNodeForOperand(graph, bOp);
        aNode.neighbours.remove(bNode);
        bNode.neighbours.remove(aNode);
    }

    private static Node findNodeForOperand(List<Node> interferenceGraph,
                                           Operand u) {
        for (Node n : interferenceGraph) {
            if (n.operand.equals(u)) {
                return n;
            }
        }
        return null;
    }

    public static HashMap<Integer, Set<Operand>[]> livenessAnalysis(
            List<CfgNode> cfg, Pair<Integer, Integer> returnRegisters) {

        HashMap<Integer, Set<Operand>[]> instructionAnnotations =
                new HashMap<>();
        HashMap<Integer, Set<Operand>> blockAnnotations = new HashMap<>();


        LivenessAnalyzer.<Operand, Instruction>analyzeLiveness(cfg,
                instructionAnnotations, blockAnnotations, returnRegisters,
                LivenessAnalyzer::livenessAsmMeetFunction,
                LivenessAnalyzer::livenessAsmTransferFunction);
        return instructionAnnotations;
    }

    /**
     * p. 633
     */
    private static void addPseudoRegisters(List<Instruction> instructions,
                                           List<Node> interferenceGraph,
                                           List<Node> inteferenceGraphMmx) {
        for (var instr : instructions) {
            switch (instr) {
                case Binary(ArithmeticOperator _, TypeAsm _, Operand src,
                            Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph, inteferenceGraphMmx);
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case Cdq _ -> {
                }
                case Cmp(TypeAsm _, Operand subtrahend, Operand minuend) -> {
                    maybeAddPseudo(subtrahend, interferenceGraph,
                            inteferenceGraphMmx);
                    maybeAddPseudo(minuend, interferenceGraph,
                            inteferenceGraphMmx);
                }
                case Cvtsi2sd(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph, inteferenceGraphMmx);
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case Cvttsd2si(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph, inteferenceGraphMmx);
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case Lea(Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph, inteferenceGraphMmx);
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case Mov(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph, inteferenceGraphMmx);
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case MovZeroExtend(TypeAsm _, TypeAsm _, Operand src,
                                   Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph, inteferenceGraphMmx);
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case Movsx(TypeAsm _, TypeAsm _, Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph, inteferenceGraphMmx);
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case Pop(Reg dst) -> {
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case Push(Operand src) -> {
                    maybeAddPseudo(src, interferenceGraph, inteferenceGraphMmx);
                }
                case SetCC(CmpOperator _, boolean _, Operand dst) -> {
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                }
                case Unary(UnaryOperator op, TypeAsm type, Operand dst) -> {
                    maybeAddPseudo(dst, interferenceGraph, inteferenceGraphMmx);
                    switch (op) {
                        case DIV, IDIV -> {
                            add(AX, interferenceGraph);
                            add(DX, interferenceGraph);
                        }
                        default -> {
                        }
                    }
                }
                case Jump _, LabelIr _, Call _, Nullary _, JmpCC _,
                     Comment _ -> {}
            }
        }
    }

    private static void maybeAddPseudo(Operand op,
                                       List<Node> interferenceGraphGpr,
                                       List<Node> interferenceGraphMmx) {
        if (op instanceof Pseudo p) {
            if (!p.isStatic && !p.isAliased) {
                add(op, p.type == DOUBLE ? interferenceGraphMmx :
                        interferenceGraphGpr);
            }
        }

    }

    private static Node add(Operand op, List<Node> interferenceGraph) {
        var existing = findNodeForOperand(interferenceGraph, op);
        if (existing == null) {
            existing = newNode(op);
            interferenceGraph.add(existing);
        }
        return existing;
    }

    private static Node newNode(Operand op) {
        var n = new Node();
        n.operand = op;
        n.color = -1;
        n.neighbours = new ArrayList<>();
        return n;
    }


    private static final Reg[] BASE_GRAPH_GPRS = new Reg[]{AX, BX, CX, DX, DI
            , SI, R8, R9, R12, R13, R14, R15,};
    private static final Reg[] BASE_GRAPH_DOUBLE_REGS = new Reg[]{XMM0, XMM1,
            XMM2, XMM3, XMM4, XMM5, XMM6, XMM7, XMM8, XMM9, XMM10, XMM11,
            XMM12, XMM13};

    private static List<Node> baseGraph(Reg[] baseGraphRegs) {

        ArrayList<Node> nodes = new ArrayList<>(baseGraphRegs.length);
        int len = baseGraphRegs.length;
        for (int i = 0; i < len; i++) {
            var a = baseGraphRegs[i];
            var n = new Node();
            nodes.add(n);
            n.operand = a;
            n.color = -1;
            n.spillCost = Double.POSITIVE_INFINITY;
            n.neighbours = new ArrayList<>(len - 1);
        }
        for (int i = 0; i < len; i++) {
            var a = nodes.get(i);
            for (int j = 0; j < len; j++) {
                if (i != j) {
                    var b = nodes.get(j);
                    a.neighbours.add(b);
                }
            }
        }
        return nodes;

    }
}
