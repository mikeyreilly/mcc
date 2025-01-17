package com.quaxt.mcc.semantic;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.parser.*;

import java.util.HashMap;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

public class SemanticAnalysis {

    record Entry(String name, boolean fromCurrentBlock) {
    }

    public static Program resolveVars(Program program) {
        Map<String, Entry> variableMap = new HashMap<>();
        return new Program(resolveVars(program.function(), variableMap));
    }

    private static Block resolveBlock(Block block, Map<String, Entry> variableMap) {
        Map<String, Entry> newVariableMap = copyVariableMap(variableMap);
        List<BlockItem> blockItems = new ArrayList<>();
        for (BlockItem i : block.blockItems()) {
            blockItems.add(resolveVarsBlockItem(i, newVariableMap));
        }
        return new Block(blockItems);
    }

    private static Function resolveVars(Function function, Map<String, Entry> variableMap) {
        return new Function(function.name(), function.returnType(),
                resolveBlock(function.block(), variableMap));
    }

    private static BlockItem resolveVarsBlockItem(BlockItem blockItem, Map<String, Entry> variableMap) {
        return switch (blockItem) {
            case Declaration declaration ->
                    resolveDeclaration(declaration, variableMap);
            case Statement statement ->
                    resolveStatement(statement, variableMap);
        };
    }

    private static Statement resolveStatement(Statement blockItem, Map<String, Entry> variableMap) {
        return switch (blockItem) {
            case Exp exp -> resolveExp(exp, variableMap);
            case Return(Exp exp) -> new Return(resolveExp(exp, variableMap));
            case If(
                    Exp condition, Statement ifTrue, Optional<Statement> ifFalse
            ) ->
                    new If(resolveExp(condition, variableMap), resolveStatement(ifTrue, variableMap),
                            ifFalse.map(s -> resolveStatement(s, variableMap)));

            case Compound compound -> {

                yield new Compound(resolveBlock(compound.block(), variableMap));
            }
            case Block block -> resolveBlock(block, variableMap);
            case NullStatement nullStatement -> nullStatement;
        };
    }


    private static Map<String, Entry> copyVariableMap(Map<String, Entry> m) {
        Map<String, Entry> copy = HashMap.newHashMap(m.size());
        for (Map.Entry<String, Entry> e : m.entrySet()) {
            Entry v = e.getValue();
            copy.put(e.getKey(), new Entry(v.name(), false));
        }
        return copy;
    }

    private static BlockItem resolveDeclaration(Declaration d, Map<String, Entry> variableMap) {
        if (variableMap.get(d.name()) instanceof Entry e && e.fromCurrentBlock()) {
            fail("Duplicate variable declaration");
        }
        String uniqueName = Mcc.makeTemporary(d.name() + ".");
        variableMap.put(d.name(), new Entry(uniqueName, true));
        Optional<Exp> init = d.init();
        return init.map(exp -> new Declaration(uniqueName, Optional.of(resolveExp(exp, variableMap)))).orElseGet(() -> new Declaration(uniqueName, init));
    }

    private static Exp resolveExp(Exp exp, Map<String, Entry> variableMap) {
        return switch (exp) {
            case Assignment(Exp left, Exp right) ->
                    left instanceof Var v ? new Assignment(resolveExp(v, variableMap), resolveExp(right, variableMap)) : fail("Invalid lvalue");
            case BinaryOp(BinaryOperator op, Exp left, Exp right) ->
                    new BinaryOp(op, resolveExp(left, variableMap), resolveExp(right, variableMap));
            case Constant constant -> constant;
            case UnaryOp(UnaryOperator op, Exp arg) ->
                    new UnaryOp(op, resolveExp(arg, variableMap));
            case Var(String name) ->
                    variableMap.get(name) instanceof Entry e ? new Var(e.name()) : fail("Undeclared variable");
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse) ->
                    new Conditional(resolveExp(condition, variableMap), resolveExp(ifTrue, variableMap), resolveExp(ifFalse, variableMap));
        };
    }

    private static Exp fail(String s) {
        throw new RuntimeException(s);
    }
}
