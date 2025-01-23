package com.quaxt.mcc.semantic;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.parser.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticAnalysis {


    public static Program loopLabelProgram(Program program) {
        ArrayList<Function> functions = new ArrayList<>();
        for (Function f : program.functions()) {
            functions.add(loopLabelFunction(f));
        }
        return new Program(functions);
    }

    private static Function loopLabelFunction(Function function) {
        return new Function(function.name(), function.parameters(), loopLabelStatement(function.block(), null));

    }

    private static <T extends Statement> T loopLabelStatement(T statement, String currentLabel) {
        return switch (statement) {
            case null -> null;
            case Block block -> {//update the blockItems in-place
                ArrayList<BlockItem> blockItems = block.blockItems();
                blockItems.replaceAll(blockItem -> switch (blockItem) {
                    case VarDecl declaration ->
                            loopLabelVarDecl(declaration, currentLabel);
                    case Statement innerStatement ->
                            loopLabelStatement(innerStatement, currentLabel);
                    case Function function -> loopLabelFunction(function);
                });
                yield (T) block;
            }
            case Break aBreak -> {
                if (currentLabel == null) {
                    fail("break statement outside of loop");
                }
                aBreak.label = currentLabel;
                yield statement;
            }
            case Compound compound ->
                    throw new RuntimeException("todo: get rid of compound");
            case Continue aContinue -> {
                if (currentLabel == null) {
                    fail("continue statement outside of loop");
                }
                aContinue.label = currentLabel;
                yield statement;
            }
            case DoWhile(Statement body, Exp condition, String _) -> {
                String newLabel = Mcc.makeTemporary("do");
                yield (T) new DoWhile(loopLabelStatement(body, newLabel), condition, newLabel);

            }
            case Exp exp -> statement;
            case For(
                    ForInit init, Exp condition, Exp post, Statement body,
                    String _
            ) -> {
                String newLabel = Mcc.makeTemporary("for");
                ForInit labeledForInit = switch (init) {
                    case null -> null;
                    case VarDecl declaration ->
                            loopLabelVarDecl(declaration, newLabel);
                    case Exp exp -> loopLabelStatement(exp, newLabel);
                };
                Exp labeledCondition = loopLabelStatement(condition, newLabel);
                Exp labelledPost = loopLabelStatement(post, newLabel);
                Statement labelledBody = loopLabelStatement(body, newLabel);
                yield (T) new For(labeledForInit, labeledCondition, labelledPost, labelledBody, newLabel);


            }
            case If(Exp condition, Statement ifTrue, Statement ifFalse) ->
                    (T) new If(condition, loopLabelStatement(ifTrue, currentLabel), loopLabelStatement(ifFalse, currentLabel));
            case NullStatement nullStatement -> statement;
            case Return(Exp exp) ->
                    (T) new Return(loopLabelStatement(exp, currentLabel));
            case While(Exp condition, Statement body, String _) -> {
                String newLabel = Mcc.makeTemporary("while");
                yield (T) new While(condition, loopLabelStatement(body, newLabel), newLabel);

            }
        };
    }

    private static VarDecl loopLabelVarDecl(VarDecl declaration, String currentLabel) {
        Exp init = declaration.init();
        return new VarDecl(declaration.name(), loopLabelStatement(init, currentLabel));
    }


    record Entry(String name, boolean fromCurrentScope, boolean hasLinkage) {
    }

    public static Program resolveProgram(Program program) {
        Map<String, Entry> identifierMap = new HashMap<>();
        List<Function> functionList = new ArrayList<>();
        for (Function f : program.functions()) {
            Function function = resolveFunctionDeclaration(f, identifierMap);
            functionList.add(function);
        }
        return new Program(functionList);
    }

    private static Block resolveBlock(Block block, Map<String, Entry> identifierMap) {
        Map<String, Entry> newVariableMap = copyIdentifierMap(identifierMap);
        ArrayList<BlockItem> blockItems = new ArrayList<>();
        for (BlockItem i : block.blockItems()) {
            blockItems.add(resolveIdentifiersBlockItem(i, newVariableMap));
        }
        return new Block(blockItems);
    }

    private static Function resolveFunctionDeclaration(Function function, Map<String, Entry> identifierMap) {
        String name = function.name();
        if (identifierMap.get(name) instanceof Entry previousEntry) {
            if (previousEntry.fromCurrentScope() && !previousEntry.hasLinkage()) {
                throw new RuntimeException("Duplicate declaration: " + name);
            }
        }
        identifierMap.put(name, new Entry(name, true, true));
        Map<String, Entry> innerMap = copyIdentifierMap(identifierMap);

        List<Identifier> newArgs = resolveParams(function.parameters(), innerMap);

        Block newBody = function.block() instanceof Block block ? resolveBlock(block, innerMap) : null;
        return new Function(function.name(), newArgs, newBody);
    }

    private static List<Identifier> resolveParams(List<Identifier> parameters, Map<String, Entry> identifierMap) {
        List<Identifier> newParams = new ArrayList<>();
        for (Identifier d : parameters) {
            if (identifierMap.get(d.name()) instanceof Entry e && e.fromCurrentScope()) {
                fail("Duplicate variable declaration");
            }
            String uniqueName = Mcc.makeTemporary(d.name() + ".");
            identifierMap.put(d.name(), new Entry(uniqueName, true, false));


            newParams.add(new Identifier(uniqueName));
        }
        return newParams;
    }

    private static BlockItem resolveIdentifiersBlockItem(BlockItem blockItem, Map<String, Entry> identifierMap) {
        return switch (blockItem) {
            case VarDecl declaration ->
                    resolveIdentifierDeclaration(declaration, identifierMap);
            case Statement statement ->
                    resolveStatement(statement, identifierMap);
            case Function function -> resolveFunctionDeclaration(function,  identifierMap);
        };
    }

    private static Statement resolveStatement(Statement blockItem, Map<String, Entry> identifierMap) {
        return switch (blockItem) {
            case null -> null;
            case Exp exp -> resolveExp(exp, identifierMap);
            case Return(Exp exp) -> new Return(resolveExp(exp, identifierMap));
            case If(
                    Exp condition, Statement ifTrue, Statement ifFalse
            ) ->
                    new If(resolveExp(condition, identifierMap), resolveStatement(ifTrue, identifierMap), resolveStatement(ifFalse, identifierMap));

            case Compound compound ->
                    new Compound(resolveBlock(compound.block(), identifierMap));
            case Block block -> resolveBlock(block, identifierMap);
            case NullStatement nullStatement -> nullStatement;
            case Break _, Continue _ -> blockItem;
            case DoWhile(Statement body, Exp condition, String label) ->
                    new DoWhile(resolveStatement(body, identifierMap), resolveExp(condition, identifierMap), label);
            case For(
                    ForInit init, Exp condition, Exp post, Statement body,
                    String label
            ) -> {
                Map<String, Entry> newVariableMap = copyIdentifierMap(identifierMap);
                yield new For(resolveForInit(init, newVariableMap), resolveExp(condition, newVariableMap), resolveExp(post, newVariableMap), resolveStatement(body, newVariableMap), label);
            }
            case While(Exp condition, Statement body, String label) ->
                    new While(resolveExp(condition, identifierMap), resolveStatement(body, identifierMap), label);
        };

    }

    private static ForInit resolveForInit(ForInit init, Map<String, Entry> identifierMap) {
        return switch (init) {
            case VarDecl declaration ->
                    resolveIdentifierDeclaration(declaration, identifierMap);
            case Exp exp -> resolveExp(exp, identifierMap);
            case null -> null;
        };
    }


    private static Map<String, Entry> copyIdentifierMap(Map<String, Entry> m) {
        Map<String, Entry> copy = HashMap.newHashMap(m.size());
        for (Map.Entry<String, Entry> e : m.entrySet()) {
            Entry v = e.getValue();
            copy.put(e.getKey(), new Entry(v.name(), false, v.hasLinkage()));
        }
        return copy;
    }

    private static VarDecl resolveIdentifierDeclaration(VarDecl d, Map<String, Entry> identifierMap) {
        if (identifierMap.get(d.name()) instanceof Entry e && e.fromCurrentScope()) {
            fail("Duplicate variable declaration");
        }
        String uniqueName = Mcc.makeTemporary(d.name() + ".");
        identifierMap.put(d.name(), new Entry(uniqueName, true, false));
        Exp init = d.init();
        return new VarDecl(uniqueName, resolveExp(init, identifierMap));
    }

    private static Exp resolveExp(Exp exp, Map<String, Entry> identifierMap) {
        return switch (exp) {
            case null -> null;
            case Assignment(Exp left, Exp right) ->
                    left instanceof Identifier v ? new Assignment(resolveExp(v, identifierMap), resolveExp(right, identifierMap)) : fail("Invalid lvalue");
            case BinaryOp(BinaryOperator op, Exp left, Exp right) ->
                    new BinaryOp(op, resolveExp(left, identifierMap), resolveExp(right, identifierMap));
            case Constant constant -> constant;
            case UnaryOp(UnaryOperator op, Exp arg) ->
                    new UnaryOp(op, resolveExp(arg, identifierMap));
            case Identifier(String name) ->
                    identifierMap.get(name) instanceof Entry e ? new Identifier(e.name()) : fail("Undeclared variable:" + exp);
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse) ->
                    new Conditional(resolveExp(condition, identifierMap), resolveExp(ifTrue, identifierMap), resolveExp(ifFalse, identifierMap));
            case FunctionCall(Identifier name, List<Exp> args) ->
                    identifierMap.get(name.name()) instanceof Entry newFunctionName
                            ? new FunctionCall(new Identifier(newFunctionName.name()), resolveArgs(identifierMap, args))
                            : fail("Undeclared function:" + name);


        };
    }

    private static <T extends Exp> List<T> resolveArgs(Map<String, Entry> identifierMap, List<T> args) {
        List<T> newArgs = new ArrayList<>();
        for (T arg : args) {
            newArgs.add((T) resolveExp(arg, identifierMap));
        }
        return newArgs;
    }

    private static Exp fail(String s) {
        throw new RuntimeException(s);
    }
}
