package com.quaxt.mcc.semantic;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.parser.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SemanticAnalysis {
    public static Program resolveVars(Program program) {
        Map<String, String> variableMap = new HashMap<>();
        return new Program(resolveVars(program.function(), variableMap));
    }

    private static Function resolveVars(Function function, Map<String, String> variableMap) {
        return new Function(function.name(), function.returnType(),
                function.blockItems().stream()
                        .map((BlockItem blockItem) ->
                                resolveVarsBlockItem(blockItem, variableMap))
                        .collect(Collectors.toList()));
    }

    private static BlockItem resolveVarsBlockItem(BlockItem blockItem, Map<String, String> variableMap) {
        return switch (blockItem) {
            case Declaration declaration ->
                    resolveDeclaration(declaration, variableMap);
            case Statement statement ->
                    resolveStatement(statement, variableMap);
        };
    }

    private static BlockItem resolveStatement(Statement blockItem, Map<String, String> variableMap) {
        return switch (blockItem) {
            case Exp exp -> resolveExp(exp, variableMap);
            case Return(Exp exp) -> new Return(resolveExp(exp, variableMap));
            default -> blockItem;
        };
    }

    private static BlockItem resolveDeclaration(Declaration d, Map<String, String> variableMap) {
        if (variableMap.containsKey(d.name())) {
            fail("Duplicate variable declaration");
        }
        String uniqueName = Mcc.makeTemporary(d.name());
        variableMap.put(d.name(), uniqueName);
        Optional<Exp> init = d.init();
        return init.map(exp -> new Declaration(uniqueName, Optional.of(resolveExp(exp, variableMap)))).orElseGet(() -> new Declaration(uniqueName, init));

    }

    private static Exp resolveExp(Exp exp, Map<String, String> variableMap) {
        return switch (exp) {
            case Assignment(Exp left, Exp right) ->
                    left instanceof Var v ? new Assignment(resolveExp(v, variableMap), resolveExp(right, variableMap)) : fail("Invalid lvalue");
            case BinaryOp(BinaryOperator op, Exp left, Exp right) ->
                    new BinaryOp(op, resolveExp(left, variableMap), resolveExp(right, variableMap));
            case Constant constant -> constant;
            case UnaryOp(UnaryOperator op, Exp arg) -> new UnaryOp(op, resolveExp(arg, variableMap));
            case Var(String name) ->
                    variableMap.get(name) instanceof String s ? new Var(s) : fail("Undeclared variable");
        };
    }

    private static Exp fail(String s) {
        throw new RuntimeException(s);
    }
}
