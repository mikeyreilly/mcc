package com.quaxt.mcc.registerallocator;

import com.quaxt.mcc.ArithmeticOperator;
import com.quaxt.mcc.CmpOperator;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.asm.*;
import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.LabelIr;

import static com.quaxt.mcc.asm.Reg.*;

import java.util.ArrayList;
import java.util.List;

public class RegisterAllocator {

    /**
     * p. 630
     */
    public static void allocateRegisters(List<Instruction> instructions) {
        var interferenceGraph = buildGraph(instructions);
    }

    private static List<Node> buildGraph(List<Instruction> instructions) {
        var interferenceGraph = baseGraph();
        addPseudoRegisters(interferenceGraph, instructions);
        return interferenceGraph;
    }

    private static void addPseudoRegisters(List<Node> interferenceGraph, List<Instruction> instructions) {
        for (var instr : instructions) {
            switch (instr) {
                case Binary(ArithmeticOperator _, TypeAsm _, Operand src,
                            Operand dst) -> {
                    maybeAdd(src, interferenceGraph);
                    maybeAdd(dst, interferenceGraph);
                }
                case Cdq _ -> {
                    add(AX, interferenceGraph);
                    add(DX, interferenceGraph);
                }
                case Cmp(TypeAsm _, Operand subtrahend,
                         Operand minuend) -> {
                    maybeAdd(subtrahend, interferenceGraph);
                    maybeAdd(minuend, interferenceGraph);
                }
                case Cvtsi2sd(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAdd(src, interferenceGraph);
                    maybeAdd(dst, interferenceGraph);
                }
                case Cvttsd2si(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAdd(src, interferenceGraph);
                    maybeAdd(dst, interferenceGraph);
                }
                case Lea(Operand src, Operand dst)-> {
                    maybeAdd(src, interferenceGraph);
                    maybeAdd(dst, interferenceGraph);
                }
                case Mov(TypeAsm _, Operand src, Operand dst) -> {
                    maybeAdd(src, interferenceGraph);
                    maybeAdd(dst, interferenceGraph);
                }
                case MovZeroExtend(TypeAsm _, TypeAsm _, Operand src, Operand dst) -> {
                    maybeAdd(src, interferenceGraph);
                    maybeAdd(dst, interferenceGraph);
                }
                case Movsx(TypeAsm _, TypeAsm _, Operand src, Operand dst) -> {
                    maybeAdd(src, interferenceGraph);
                    maybeAdd(dst, interferenceGraph);
                }
                case Pop(Reg dst)-> {
                    maybeAdd(dst, interferenceGraph);
                }
                case Push(Operand src) -> {
                    maybeAdd(src, interferenceGraph);
                }
                case SetCC(CmpOperator _, boolean _,
                           Operand dst) -> {
                    maybeAdd(dst, interferenceGraph);
                }
                case Unary(UnaryOperator op, TypeAsm type,
                           Operand dst) -> {
                    maybeAdd(dst, interferenceGraph);
                    switch (op) {
                        case DIV, IDIV -> {
                            add(AX, interferenceGraph);
                            add(DX, interferenceGraph);
                        }
                        default -> {
                        }
                    }
                }
                case Jump _, LabelIr _, Call _, Nullary _, JmpCC _, Comment _ -> {}
            }
        }
    }

    private static void maybeAdd(Operand op, List<Node> interferenceGraph) {
        boolean shouldAdd = switch (op) {
            case Pseudo p -> !isStatic(p);
            case Reg _ -> true;
            default -> false;
        };
        if (shouldAdd) {
            add(op, interferenceGraph);
        }
    }

    private static void add(Operand op, List<Node> interferenceGraph) {
        var existing = findExisting(op, interferenceGraph);
        if (existing == null) {
            interferenceGraph.add(newNode(op));
        }
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

    private static final Reg[] BASE_GRAPH_REGISTERS = new Reg[]{AX, BX, CX, DX, DI, SI, R8, R9, R12, R13, R14, R15,};

    private static List<Node> baseGraph() {

        ArrayList<Node> nodes = new ArrayList<>(BASE_GRAPH_REGISTERS.length);
        int len = BASE_GRAPH_REGISTERS.length;
        for (int i = 0; i < len; i++) {
            var a = BASE_GRAPH_REGISTERS[i];
            var n = new Node();
            nodes.add(n);
            n.operand = a;
            n.color = -1;
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
