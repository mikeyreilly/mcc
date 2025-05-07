package com.quaxt.mcc.registerallocator;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.*;
import com.quaxt.mcc.optimizer.BasicBlock;
import com.quaxt.mcc.optimizer.CfgNode;
import com.quaxt.mcc.optimizer.LivenessAnalyzer;
import com.quaxt.mcc.tacky.*;

import static com.quaxt.mcc.asm.HardReg.*;
import static com.quaxt.mcc.optimizer.LivenessAnalyzer.findUsedAndUpdated;
import static com.quaxt.mcc.optimizer.Optimizer.makeCFG;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class RegisterAllocator {

    /**
     * p. 630
     */
    public static void allocateRegisters(FunctionAsm functionAsm) {
        List<Instruction> instructions = functionAsm.instructions;
        var interferenceGraph = buildGraph(instructions);
        addSpillCosts(interferenceGraph, instructions);
        // k is 12 for general purpose registers, 14 for MMX
        colorGraph(interferenceGraph, 12);
        var registerMap = createRegisterMap(interferenceGraph, functionAsm);
        replacePseudoRegs(instructions, registerMap);
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
                case Push(Reg operand) -> new Push(operand);
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

    private static Map<Pseudo, Reg> createRegisterMap(List<Node> coloredGraph,
                                                      FunctionAsm function) {
        Map<Integer, HardReg> colorMap = new HashMap<>();
        for (Node node : coloredGraph) {
            if (node.operand instanceof HardReg reg) {
                colorMap.put(node.color, reg);
            }
        }
        Map<Pseudo, Reg> registerMap = new HashMap<>();
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
                    }
                }
                default -> {}
            }
        }
        function.calleeSavedRegs = calleeSavedRegs.toArray(new HardReg[0]);
        return registerMap;
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
                                      List<Instruction> instructions) {

        for (var instr : instructions) {
            switch (instr) {
                case Binary(ArithmeticOperator op, TypeAsm type, Operand src,
                            Operand dst) -> {
                    if (op == ArithmeticOperator.DIVIDE) {
                        throw new Todo();
                    }
                    incrementSpillCost(interferenceGraph, src, dst);
                }
                case Call(String name) -> {
                }
                case Cdq cdq -> {
                }
                case Cmp(TypeAsm type, Operand subtrahend, Operand minuend) -> {
                    incrementSpillCost(interferenceGraph, subtrahend, minuend);
                }
                case Cvtsi2sd(TypeAsm srcType, Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph, src, dst);
                }
                case Cvttsd2si(TypeAsm dstType, Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph, src, dst);
                }
                case Lea(Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph, src, dst);
                }
                case Mov(TypeAsm type, Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph, src, dst);
                }
                case MovZeroExtend(TypeAsm srcType, TypeAsm dstType,
                                   Operand src, Operand dst) -> {
                    incrementSpillCost(interferenceGraph, src, dst);
                }
                case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                           Operand dst) -> {
                    incrementSpillCost(interferenceGraph, src, dst);
                }
                case Pop(Reg operand) -> {
                    incrementSpillCost(interferenceGraph, operand);
                }
                case SetCC(CmpOperator cmpOperator, boolean unsigned,
                           Operand operand) -> {
                    incrementSpillCost(interferenceGraph, operand);
                }
                case Unary(UnaryOperator op, TypeAsm type, Operand operand) -> {
                    incrementSpillCost(interferenceGraph, operand);


                }
                default -> {}
            }
        }

    }

    private static void incrementSpillCost(List<Node> interferenceGraph,
                                           Operand... ops) {
        for (var op : ops) {
            switch (op) {
                case Pseudo p -> {
                    if (isStatic(p)) return;
                    Node n = findExisting(op, interferenceGraph);
                    n.spillCost += 1;
                }
                case Reg _ -> {
                    Node n = findExisting(op, interferenceGraph);
                    n.spillCost = Double.POSITIVE_INFINITY;
                }
                default -> {
                }
            }
        }
    }

    /* p. 632*/
    private static List<Node> buildGraph(List<Instruction> instructions) {
        var interferenceGraph = baseGraph();
        addPseudoRegisters(interferenceGraph, instructions);
        List<CfgNode> cfg = makeCFG(instructions);
        HashMap<Integer, Set<Operand>[]> instructionAnnotations =
                livenessAnalysis(cfg);
        addEdges(cfg, interferenceGraph, instructionAnnotations);
        // MR-TODO handling other types while constructing the graph p. 637
        return interferenceGraph;
    }

    /* p. 636*/
    private static void addEdges(List<CfgNode> cfg,
                                 List<Node> interferenceGraph,
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
                                                 Operand _) && l == src) {
                            continue;
                        }
                        for (Operand u : updated) {
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
            if (n.operand.equals(u))
            {
                return n;
            }
        }
        return null;
    }

    public static HashMap<Integer, Set<Operand>[]> livenessAnalysis(
            List<CfgNode> cfg) {

        HashMap<Integer, Set<Operand>[]> instructionAnnotations =
                new HashMap<>();
        HashMap<Integer, Set<Operand>> blockAnnotations = new HashMap<>();


        LivenessAnalyzer.<Operand, Instruction>analyzeLiveness(cfg,
                instructionAnnotations, blockAnnotations,
                Collections.emptySet(),
                LivenessAnalyzer::livenessAsmMeetFunction,
                LivenessAnalyzer::livenessAsmTransferFunction);
        return instructionAnnotations;
    }

    /**
     * p. 633
     */
    private static void addPseudoRegisters(List<Node> interferenceGraph,
                                           List<Instruction> instructions) {
        for (var instr : instructions) {
            switch (instr) {
                case Binary(ArithmeticOperator _, TypeAsm _, Operand src,
                            Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph);
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case Cdq _ -> {
                }
                case Cmp(TypeAsm _, Operand subtrahend, Operand minuend) -> {
                    maybeAddPseudo(subtrahend, interferenceGraph);
                    maybeAddPseudo(minuend, interferenceGraph);
                }
                case Cvtsi2sd(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph);
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case Cvttsd2si(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph);
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case Lea(Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph);
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case Mov(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph);
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case MovZeroExtend(TypeAsm _, TypeAsm _, Operand src,
                                   Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph);
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case Movsx(TypeAsm _, TypeAsm _, Operand src, Operand dst) -> {
                    maybeAddPseudo(src, interferenceGraph);
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case Pop(Reg dst) -> {
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case Push(Operand src) -> {
                    maybeAddPseudo(src, interferenceGraph);
                }
                case SetCC(CmpOperator _, boolean _, Operand dst) -> {
                    maybeAddPseudo(dst, interferenceGraph);
                }
                case Unary(UnaryOperator op, TypeAsm type, Operand dst) -> {
                    maybeAddPseudo(dst, interferenceGraph);
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

    private static void maybeAddPseudo(Operand op, List<Node> interferenceGraph) {
        boolean shouldAdd = switch (op) {
            case Pseudo p -> !isStatic(p);
            default -> false;
        };
        if (shouldAdd) {
            add(op, interferenceGraph);
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

    private static boolean isStatic(Pseudo p) {
        SymTabEntryAsm e = Codegen.BACKEND_SYMBOL_TABLE.get(p.identifier());
        return e instanceof ObjEntry objEntry && objEntry.isStatic();
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

    private static final Reg[] BASE_GRAPH_REGISTERS = new Reg[]{AX, BX, CX,
            DX, DI, SI, R8, R9, R12, R13, R14, R15,};

    private static List<Node> baseGraph() {

        ArrayList<Node> nodes = new ArrayList<>(BASE_GRAPH_REGISTERS.length);
        int len = BASE_GRAPH_REGISTERS.length;
        for (int i = 0; i < len; i++) {
            var a = BASE_GRAPH_REGISTERS[i];
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
