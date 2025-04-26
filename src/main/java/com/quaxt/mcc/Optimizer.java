package com.quaxt.mcc;

import com.quaxt.mcc.asm.Cvttsd2si;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.tacky.*;

import java.util.EnumSet;
import java.util.List;

import static com.quaxt.mcc.ArithmeticOperator.SUB;
import static com.quaxt.mcc.Mcc.valToType;
import static com.quaxt.mcc.Optimization.FOLD_CONSTANTS;
import static com.quaxt.mcc.semantic.Primitive.*;
import static com.quaxt.mcc.semantic.SemanticAnalysis.convertConst;

public class Optimizer {
    public static ProgramIr optimize(ProgramIr programIr, EnumSet<Optimization> optimizations) {
        for (int i = 0; i < programIr.topLevels().size(); i++) {
            TopLevel topLevel = programIr.topLevels().get(i);
            if (topLevel instanceof FunctionIr f) {
                programIr.topLevels().set(i, optimizeFunction2(f, optimizations));
            }
        }
        return programIr;
    }

    private static TopLevel optimizeFunction2(FunctionIr f, EnumSet<Optimization> optimizations) {
        return new FunctionIr(f.name(), f.global(), f.type(), optimizeFunction(f.instructions(), optimizations), f.returnType());
    }

    private static List<InstructionIr> optimizeFunction(List<InstructionIr> instructions, EnumSet<Optimization> optimizations) {
        boolean updated = true;
        while (updated) {
            updated = false;
            if (optimizations.contains(FOLD_CONSTANTS)) {
                updated |= foldConstants(instructions);
            }
        }
        return instructions;
    }

    @SuppressWarnings("unchecked")
    private static boolean foldConstants(List<InstructionIr> instructions) {
        boolean updated = false;
        for (int i = 0; i < instructions.size(); i++) {
            var in = instructions.get(i);
            InstructionIr newIn = switch (in) {
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
                    yield new Ignore();
                }
                case JumpIfNotZero(ValIr v,
                                   String label) when v instanceof Constant<?> c -> {
                    if (!c.isZero()) {
                        yield new Jump(label);
                    }
                    yield new Ignore();
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
