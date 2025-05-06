package com.quaxt.mcc.registerallocator;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.*;
import com.quaxt.mcc.optimizer.BasicBlock;
import com.quaxt.mcc.optimizer.CfgNode;
import com.quaxt.mcc.optimizer.LivenessAnalyzer;
import com.quaxt.mcc.tacky.*;

import static com.quaxt.mcc.asm.DoubleReg.*;
import static com.quaxt.mcc.asm.HardReg.*;
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
        var graphs = buildGraph(functionAsm);
        var interferenceGraphGpr = graphs.key();
        var interferenceGraphMmx = graphs.value();
        addSpillCosts(interferenceGraphGpr, interferenceGraphMmx, instructions);
        // k is 12 for general purpose registers, 14 for MMX
        colorGraph(interferenceGraphGpr, 12);
        colorGraph(interferenceGraphMmx, 14);
        var registerMaps = createRegisterMap(interferenceGraphGpr,
                interferenceGraphMmx, functionAsm);
        replacePseudoRegs(instructions, registerMaps.key());
        replacePseudoRegs(instructions, registerMaps.value());
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
        Map<Integer, HardReg> colorMap = new HashMap<>();
        Map<Integer, DoubleReg> colorMapMmx = new HashMap<>();
        for (Node node : coloredGraph) {
            if (node.operand instanceof HardReg reg) {
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
        Set<HardReg> calleeSavedRegs = EnumSet.noneOf(HardReg.class);
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
        function.calleeSavedRegs = calleeSavedRegs.toArray(new HardReg[0]);
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
            //MR-TODO same for MMX reg
            boolean chooseHighestAvailableColor =
                    chosenNode.operand instanceof HardReg r && r.isCalleeSaved;
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

                case Comment comment -> {}
                case JmpCC jmpCC -> {}
                case Nullary nullary -> {}
                case Push(Operand operand) -> {
                    incrementSpillCost(interferenceGraph,
                            interferenceGraphMmx, operand);
                }
                case Jump jump -> {}
                case LabelIr labelIr -> {}
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
                    Node n = findExisting(op, p.type == DOUBLE ?
                            interferenceGraphMmx : interferenceGraph);
                    n.spillCost += 1;
                }
                case DoubleReg _ -> {
                    Node n = findExisting(op, interferenceGraphMmx);
                    if (n != null) n.spillCost = Double.POSITIVE_INFINITY;
                }
                case HardReg _ -> {
                    Node n = findExisting(op, interferenceGraph);
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
        //debugAnnotations(functionAsm, cfg, instructionAnnotations);
        addEdges(cfg, interferenceGraphGpr, interferenceGraphMmx,
                instructionAnnotations);
        // MR-TODO handling other types while constructing the graph p. 637
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
                                    u instanceof DoubleReg || u instanceof Pseudo p && p.type == DOUBLE ? interferenceGraphMmx : interferenceGraphGpr;
                            Node nodeForU =
                                    findNodeForOperand(interferenceGraph, u);
                            if (nodeForU != null && findNodeForOperand(interferenceGraph, l) != null && !Objects.equals(l, u)) {
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
        var existing = findExisting(op, interferenceGraph);
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

    private static Node findExisting(Operand op, List<Node> interferenceGraph) {
        for (var n : interferenceGraph) {
            if (n.operand.equals(op)) return n;
        }
        return null;
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
