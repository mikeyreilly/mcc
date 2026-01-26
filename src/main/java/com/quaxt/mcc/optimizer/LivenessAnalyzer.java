package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.*;
import com.quaxt.mcc.atomics.MemoryOrder;
import com.quaxt.mcc.semantic.FunType;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.*;

import java.util.*;

import static com.quaxt.mcc.asm.Codegen.DOUBLE_REGISTERS;
import static com.quaxt.mcc.asm.Codegen.INTEGER_RETURN_REGISTERS;
import static com.quaxt.mcc.asm.DoubleReg.*;
import static com.quaxt.mcc.asm.IntegerReg.*;
import static com.quaxt.mcc.optimizer.PropagateCopies.addNodesInReversePostOrder;

public class LivenessAnalyzer {

    /* the iterative algorithm described on p. 607*/
    public static <V extends AbstractValue, I extends AbstractInstruction> void analyzeLiveness(
            List<CfgNode> cfg,
            HashMap<Integer, Set<V>[]> instructionAnnotations,
            HashMap<Integer, Set<V>> blockAnnotations, Object otherArgs,
            MeetFunction<V> meet, TransferFunction<V> transfer) {

        Set<V> liveVars = new HashSet<>();

        var alreadySeen = new HashSet<Integer>();
        ArrayDeque<BasicBlock> workList =new ArrayDeque<>();
        addNodesInReversePostOrder(cfg.getFirst(), workList, alreadySeen);

        for (CfgNode n : cfg) {
            if (n instanceof BasicBlock<?> node) {
                annotateBlock(node.nodeId(), liveVars, blockAnnotations);
              if (!alreadySeen.contains(node.nodeId()))  workList.add(node);
            }
        }
        while (!workList.isEmpty()) {
            BasicBlock block = workList.removeFirst();
            var oldAnnotations = getBlockAnnotation(block.nodeId(),
                    blockAnnotations);
            Set<V> incomingLiveVars = meet.meet(block, blockAnnotations,
                    otherArgs);
            transfer.transfer(block, incomingLiveVars, instructionAnnotations
                    , blockAnnotations, otherArgs);
            if (!oldAnnotations.equals(getBlockAnnotation(block.nodeId(),
                    blockAnnotations))) {
                for (Object pred : block.predecessors()) {
                    switch (pred) {
                        case BasicBlock<?> basicBlock -> {
                            if (!workList.contains(basicBlock))
                                workList.add((BasicBlock<I>) basicBlock);
                        }
                        case ExitNode _ ->
                                throw new Err("Malformed control flow graph");
                        case EntryNode _ -> {}
                        default ->
                                throw new IllegalStateException("Unexpected " + "value: " + pred);
                    }
                }
            }
        }
    }

    /* p. 607 */
    public static Set<VarIr> livenessIrMeetFunction(BasicBlock block,
                                                    HashMap<Integer,
                                                            Set<VarIr>> blockAnnotations,
                                                    Object otherArg) {
        Pair<Set<VarIr>,Set<VarIr>> pair= (Pair<Set<VarIr>, Set<VarIr>>) otherArg;
        Set<VarIr> staticVars = (Set<VarIr>) pair.value();
        Set<VarIr> liveVars = new HashSet<>();
        for (var succ : block.successors()) {
            switch (succ) {
                case BasicBlock bb -> {
                    var succLiveVars = getBlockAnnotation(bb.nodeId(),
                            blockAnnotations);
                    liveVars.addAll(succLiveVars);
                }
                case EntryNode _ ->
                        throw new Err("Malformed control-flow graph");
                case ExitNode _ -> liveVars.addAll(staticVars);
                default ->
                        throw new IllegalStateException("Unexpected value: " + succ);
            }
        }
        return liveVars;
    }
   

        /*
See p. 606 */

    public static void livenessIrTransferFunction(
            BasicBlock<InstructionIr> block, Set<VarIr> endLiveVars,
            HashMap<Integer, Set<VarIr>[]> instructionAnnotations,
            HashMap<Integer, Set<VarIr>> blockAnnotations, Object otherArg) {
        Pair<Set<VarIr>,Set<VarIr>> pair= (Pair<Set<VarIr>, Set<VarIr>>) otherArg;

        Set<VarIr> aliasedVars = pair.key();
        HashSet<VarIr> currentLiveVars = new HashSet<>(endLiveVars);
        List<InstructionIr> instructions = block.instructions();
        instructionAnnotations.put(block.nodeId(),
                new HashSet[instructions.size()]);
        for (int i = instructions.size() - 1; i >= 0; i--) {
            var instruction = instructions.get(i);
            LivenessAnalyzer.annotateInstruction(block.nodeId(), i,
                    new HashSet<>(currentLiveVars), instructionAnnotations);
            switch (instruction) {
                case BinaryIr(BinaryOperator _, ValIr src1, ValIr src2,
                              VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src1 instanceof VarIr v1) {
                        currentLiveVars.add(v1);
                    }
                    if (src2 instanceof VarIr v2) {
                        currentLiveVars.add(v2);
                    }
                }
                case BinaryWithOverflowIr(BinaryOperator _, ValIr src1, ValIr src2, ValIr src3,
                              VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src1 instanceof VarIr v1) {
                        currentLiveVars.add(v1);
                    }
                    if (src2 instanceof VarIr v2) {
                        currentLiveVars.add(v2);
                    }
                    if (src3 instanceof VarIr v3) {
                        currentLiveVars.add(v3);
                    }
                }
                case Compare(Type _, ValIr src1, ValIr src2) -> {
                    if (src1 instanceof VarIr v1) {
                        currentLiveVars.add(v1);
                    }
                    if (src2 instanceof VarIr v2) {
                        currentLiveVars.add(v2);
                    }
                }
                case Copy(ValIr src, VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case FunCall(VarIr name, ArrayList<ValIr> args, boolean _, boolean indirect, ValIr dst) -> {
                    currentLiveVars.add(name);
                    currentLiveVars.remove(dst);
                    for (var src : args) {
                        if (src instanceof VarIr v) {
                            currentLiveVars.add(v);
                        }
                    }
                    currentLiveVars.addAll(aliasedVars);
                }
                case Store(ValIr src, ValIr dst) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                    if (dst instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case AtomicStore(ValIr src, ValIr dst, MemoryOrder _) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                    if (dst instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case UnaryIr(UnaryOperator _, ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case Load(ValIr src, VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                    currentLiveVars.addAll(aliasedVars);
                }
                case GetAddress(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                }
                case SignExtendIr(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case CopyFromOffset(ValIr src, long _, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case CopyBitsFromOffset(ValIr src, long _, int _, int _, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case CopyToOffset(ValIr src, VarIr dst, long _) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                        currentLiveVars.add(dst); // it's used for indirect addressing (so it is read)
                    }
                }
                case Memset(VarIr dst, int _, long _, boolean _) -> {
                    currentLiveVars.remove(dst);
                }
                case CopyBitsToOffset(ValIr src, VarIr dst, long _, int _, int _) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                        currentLiveVars.add(dst); // it's used for indirect addressing (so it is read)
                    }
                }
                case ZeroExtendIr(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case DoubleToInt(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case DoubleToUInt(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case IntToDouble(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case UIntToDouble(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case FloatToDouble(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case DoubleToFloat(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case TruncateIr(ValIr src, ValIr dst) -> {
                    currentLiveVars.remove(dst);
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case AddPtr(VarIr src1, ValIr src2, int _, VarIr dst) -> {
                    currentLiveVars.remove(dst);
                    currentLiveVars.add(src1);
                    if (src2 instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case JumpIfZero(ValIr src, String _) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }

                case JumpIfNotZero(ValIr src, String _) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case ReturnIr(ValIr src) -> {
                    if (src instanceof VarIr v) {
                        currentLiveVars.add(v);
                    }
                }
                case BuiltinVaArgIr(VarIr src, VarIr dst, Type type) -> {
                    currentLiveVars.remove(dst);
                    currentLiveVars.add(src);
                }
                case BuiltinC23VaStartIr(VarIr dst) -> {
                    currentLiveVars.remove(dst);
                }
                case LabelIr _, Jump _, Ignore _ -> {}

                default ->
                        throw new IllegalStateException("Unexpected value: " + instruction);
            }
        }
        annotateBlock(block.nodeId(), currentLiveVars, blockAnnotations);
    }

    ;


    private static <V extends AbstractValue> Set<V> getBlockAnnotation(
            int nodeId, HashMap<Integer, Set<V>> blockAnnotations) {
        return blockAnnotations.get(nodeId);
    }

    /* Annotate a block in the cfg with the vars that are live before the
    first instruction in the block*/
    private static <V extends AbstractValue> void annotateBlock(int nodeId,
                                                                Set<V> liveVars,
                                                                HashMap<Integer, Set<V>> blockAnnotations) {
        blockAnnotations.put(nodeId, liveVars);
    }

    /**
     * Listing 20.20 p. 634
     */
    public static Set<Operand> livenessAsmMeetFunction(
            BasicBlock<Instruction> block,
            HashMap<Integer, Set<Operand>> blockAnnotations, Object otherArg) {
        Set<Operand> liveVars = new HashSet<>();
        for (var succ : block.successors()) {
            switch (succ) {
                case BasicBlock bb -> {
                    var succLiveVars = getBlockAnnotation(bb.nodeId(),
                            blockAnnotations);
                    liveVars.addAll(succLiveVars);
                }
                case EntryNode _ ->
                        throw new Err("Malformed control-flow graph");
                case ExitNode _ -> {
                    Pair<Integer, Integer> returnRegsSize = (Pair<Integer,
                            Integer>) otherArg;
                    int intDestsSize = returnRegsSize.key();
                    int doubleDestsSize = returnRegsSize.value();
                    for (int i = 0; i < intDestsSize; i++) {
                        liveVars.add(INTEGER_RETURN_REGISTERS[i]);
                    }
                    for (int i = 0; i < doubleDestsSize; i++) {
                        liveVars.add(DOUBLE_REGISTERS[i]);
                    }

                }
                default ->
                        throw new IllegalStateException("Unexpected value: " + succ);
            }
        }
        return liveVars;
    }

    private static <V extends AbstractValue> void annotateInstruction(
            int blockId, int instructionIndex, Set<V> liveVars,
            HashMap<Integer, Set<V>[]> instructionAnnotations) {

        instructionAnnotations.get(blockId)[instructionIndex] = liveVars;
    }

    public static void livenessAsmTransferFunction(
            BasicBlock<Instruction> block, Set<Operand> endLiveRegisters,
            HashMap<Integer, Set<Operand>[]> instructionAnnotations,
            HashMap<Integer, Set<Operand>> blockAnnotations, Object otherArg) {

        HashSet<Operand> currentLiveRegisters = new HashSet<>(endLiveRegisters);
        List<Instruction> instructions = block.instructions();
        instructionAnnotations.put(block.nodeId(),
                new Set[instructions.size()]);


        for (int i = instructions.size() - 1; i >= 0; i--) {
            var instr = instructions.get(i);


            LivenessAnalyzer.annotateInstruction(block.nodeId(), i,
                    new HashSet<>(currentLiveRegisters),
                    instructionAnnotations);

            var usedAndUpdated = findUsedAndUpdated(instr);
            var used = usedAndUpdated.key();
            var updated = usedAndUpdated.value();

            for (var v : updated) {
                if (v instanceof Reg) {
                    currentLiveRegisters.remove(v);
                }
            }
            for (var v : used) {
                if (v instanceof Reg) {
                    currentLiveRegisters.add(v);
                }
            }

        }

        annotateBlock(block.nodeId(), currentLiveRegisters, blockAnnotations);
    }

    /* p. 634 */
    public static Pair<Set<? extends Operand>, Set<? extends Operand>> findUsedAndUpdated(
            Instruction instr) {
        switch (instr) {
            case Binary(ArithmeticOperator op, TypeAsm type, Operand src,
                        Operand dst) -> {
                if (op == ArithmeticOperator.DIVIDE) {
                    throw new Todo();
                }
                Set<Operand> used = new HashSet<>();
                used.add(src);
                used.add(dst);
                addMemoryAndIndexedRegsToUsed(src, used);
                addMemoryAndIndexedRegsToUsed(dst, used);
                return new Pair<>(used, Set.of(dst));
            }
            case Call(Operand address, FunType t)-> {
                Set<Operand> used = new HashSet<>();
                used.add(address);
                addMemoryAndIndexedRegsToUsed(address, used);
                ParameterClassification pc =
                        Codegen.PARAMETER_CLASSIFICATION_MAP.get(t);
                int len = pc.integerArguments().size();
                for (int i = 0; i < len; i++) {
                    used.add(Codegen.INTEGER_REGISTERS[i]);
                }
                len = pc.doubleArguments().size();
                for (int i = 0; i < len; i++) {
                    used.add(DOUBLE_REGISTERS[i]);
                }
                return new Pair<>(used, Set.of(DI, SI, DX, CX, R8, R9, AX,
                        XMM0, XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7, XMM8,
                        XMM9, XMM10, XMM11, XMM12, XMM13, XMM14, XMM15));

            }
            case Cdq cdq -> {
                return new Pair<>(EnumSet.of(AX), EnumSet.of(DX));

            }
            case Cmp(TypeAsm type, Operand subtrahend, Operand minuend) -> {
                Set<Operand> used = new HashSet<>();
                used.add(subtrahend);
                used.add(minuend);
                addMemoryAndIndexedRegsToUsed(subtrahend, used);
                addMemoryAndIndexedRegsToUsed(minuend, used);
                return new Pair<>(used, Set.of());
            }
            case Cvt(TypeAsm srcType, TypeAsm dstType, Operand src, Operand dst) -> {
                Set<Operand> used = new HashSet<>();
                used.add(src);
                addMemoryAndIndexedRegsToUsed(src, used);
                addMemoryAndIndexedRegsToUsed(dst, used);
                return new Pair<>(used, Set.of(dst));
            }
            case JmpCC jmpCC -> {}
            case Lea(Operand src, Operand dst) -> {
                Set<Operand> used = new HashSet<>();
                used.add(src);
                addMemoryAndIndexedRegsToUsed(src, used);
                addMemoryAndIndexedRegsToUsed(dst, used);
                return new Pair<>(used, Set.of(dst));
            }
            case Mov(TypeAsm type, Operand src, Operand dst) -> {
                Set<Operand> used = new HashSet<>();
                used.add(src);
                addMemoryAndIndexedRegsToUsed(src, used);
                addMemoryAndIndexedRegsToUsed(dst, used);
                return new Pair<>(used, Set.of(dst));
            }
            case Xchg(TypeAsm type, Operand src, Operand dst) -> {
                Set<Operand> used = new HashSet<>();
                used.add(src);
                addMemoryAndIndexedRegsToUsed(src, used);
                addMemoryAndIndexedRegsToUsed(dst, used);
                return new Pair<>(used, Set.of(dst));
            }
            case MovZeroExtend(TypeAsm srcType, TypeAsm dstType, Operand src,
                               Operand dst) -> {
                Set<Operand> used = new HashSet<>();
                used.add(src);
                addMemoryAndIndexedRegsToUsed(src, used);
                addMemoryAndIndexedRegsToUsed(dst, used);
                return new Pair<>(used, Set.of(dst));
            }
            case Movsx(TypeAsm srcType, TypeAsm dstType, Operand src,
                       Operand dst) -> {
                Set<Operand> used = new HashSet<>();
                used.add(src);
                addMemoryAndIndexedRegsToUsed(src, used);
                addMemoryAndIndexedRegsToUsed(dst, used);
                return new Pair<>(used, Set.of(dst));
            }

            case Pop(Reg operand) -> {
                Set<Operand> used = new HashSet<>();
                addMemoryAndIndexedRegsToUsed(operand, used);
                return new Pair<>(used, Set.of(operand));
            }
            case SetCC(CmpOperator cmpOperator, boolean unsigned,
                       Operand operand) -> {
                Set<Operand> used = new HashSet<>();
                addMemoryAndIndexedRegsToUsed(operand, used);
                return new Pair<>(used, Set.of(operand));
            }
            case Unary(UnaryOperator op, TypeAsm type, Operand operand) -> {
                Set<Operand> used = new HashSet<>();
                used.add(operand);
                addMemoryAndIndexedRegsToUsed(operand, used);

                switch (op) {
                    case UnaryOperator.DIV, UnaryOperator.IDIV -> {
                        used.add(AX);
                        used.add(DX);
                        return new Pair<>(used, Set.of(AX,
                                DX));
                    }
                    case BITWISE_NOT, NOT, UNARY_SHR, UNARY_MINUS -> {
                        return new Pair<>(used, Set.of(operand));
                    }
                }
            }
            case Push(Operand op) ->{
                Set<Operand> used = new HashSet<>();
                used.add(op);
                addMemoryAndIndexedRegsToUsed(op, used);
                return new Pair<>(used, Set.of());
            }
            case LabelIr _, Jump _, Comment _, Nullary _, Literal _,
                 SetStackOffset _ -> {}
            case Test test -> throw new Todo();
        }
        return EMPTY_PAIR;
    }

    private static void addMemoryAndIndexedRegsToUsed(Operand src, Set<Operand> used) {
        if (src instanceof Memory(IntegerReg reg, long _)) {
            used.add(reg);
        }
        if (src instanceof Indexed(IntegerReg base, IntegerReg index,
                                   int _)) {
            used.add(base);
            used.add(index);
        }
    }

    private static final Pair<Set<? extends Operand>, Set<? extends Operand>> EMPTY_PAIR = new Pair<>(Set.of(), Set.of());

}
