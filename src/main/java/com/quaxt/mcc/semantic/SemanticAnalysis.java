package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.InitialValue.NoInitializer.NO_INITIALIZER;
import static com.quaxt.mcc.InitialValue.Tentative.TENTATIVE;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.parser.StorageClass.EXTERN;
import static com.quaxt.mcc.parser.StorageClass.STATIC;
import static com.quaxt.mcc.semantic.Int.INT;

public class SemanticAnalysis {


    public static Program loopLabelProgram(Program program) {
        ArrayList<Function> functions = new ArrayList<>();
        ArrayList<Declaration> decls = program.declarations();
        for (int i = 0; i < decls.size(); i++) {
            switch (decls.get(i)) {
                case Function f -> decls.set(i, loopLabelFunction(f));
                default -> {
                }
            }
        }
        return program;
    }

    private static Function loopLabelFunction(Function function) {
        return new Function(function.name(), function.parameters(), loopLabelStatement(function.body(), null), function.storageClass());

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
        return new VarDecl(declaration.name(), loopLabelStatement(init, currentLabel), declaration.storageClass());
    }

    public static void typeCheckProgram(Program program) {
        for (Function f : program.functions()) {

        }
        for (Declaration d : program.declarations()) {
            switch (d) {
                case Function function -> {
                    typeCheckFunctionDeclaration(function, false);
                }
                case VarDecl varDecl -> {
                    typeCheckFileScopeVariableDeclaration(varDecl);
                }
            }
        }
    }

    private static void typeCheckFileScopeVariableDeclaration(VarDecl decl) {
        InitialValue initialValue
                = switch (decl.init()) {
            case com.quaxt.mcc.parser.Int(int i) -> new InitialConstant(i);
            case null ->
                    decl.storageClass() == EXTERN ? NO_INITIALIZER : TENTATIVE;
            default -> throw new RuntimeException("Non constant initializer");
        };
        boolean global = decl.storageClass() != STATIC;
        if (SYMBOL_TABLE.get(decl.name()) instanceof SymbolTableEntry oldDecl) {
            if (oldDecl.type() != INT) fail("function redeclared as variable");
            if (decl.storageClass() == EXTERN)
                global = oldDecl.attrs().global();
            else if (oldDecl.attrs().global() != global)
                fail("conflicting variable linkage");

            if (oldDecl.attrs() instanceof StaticAttributes(
                    InitialValue oldInit, boolean _
            )) {
                if (oldInit instanceof InitialConstant oldInitialConstant) {
                    if (initialValue instanceof InitialConstant)
                        fail("Conflicting file scope variable definitions");
                    else initialValue = oldInitialConstant;
                } else if (!(initialValue instanceof InitialConstant) && oldInit == TENTATIVE)
                    initialValue = TENTATIVE;
            }
        }
        StaticAttributes attrs = new StaticAttributes(initialValue, global);
        SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(INT, attrs));
    }

    private static void typeCheckFunctionDeclaration(Function decl, boolean blockScope) {
        if (blockScope && decl.storageClass() == STATIC) {
            fail("invalid storage class for block scope function declaration ‘" + decl.name() + "’");
        }
        boolean defined = decl.body() != null;
        boolean global = decl.storageClass() != STATIC;
        SymbolTableEntry oldEntry = SYMBOL_TABLE.get(decl.name());
        if (oldEntry instanceof SymbolTableEntry(
                Type oldType, IdentifierAttributes attrs
        )) {
            if (oldType instanceof FunType(int paramCount)) {
                boolean alreadyDefined = oldEntry.attrs().defined();
                if (alreadyDefined && defined)
                    fail("already defined: " + decl.name());

                if (oldEntry.attrs().global() && decl.storageClass() == STATIC)
                    fail("Static function declaration follows non-static");
                global = oldEntry.attrs().global();
                if (decl.parameters().size() != paramCount)
                    fail("Incompatible function declarations for " + decl.name());
            } else {
                fail("Incompatible function declarations for " + decl.name());
            }
        }
        FunAttributes attrs = new FunAttributes(defined || decl.body() != null, global);
        FunType funType = new FunType(decl.parameters().size());
        SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(funType, attrs));

        if (decl.body() != null) {
            for (Identifier param : decl.parameters()) {
                SYMBOL_TABLE.put(param.name(), new SymbolTableEntry(INT, LOCAL_ATTR));
            }
            typeCheckBlock(decl.body());
        }

    }

    private static void typeCheckBlock(Block body) {
        for (BlockItem blockItem : body.blockItems()) {
            typeCheckBlockItem(blockItem);
        }
    }

    private static void typeCheckBlockItem(BlockItem blockItem) {
        switch (blockItem) {
            case VarDecl declaration ->
                    typeCheckLocalVariableDeclaration(declaration);
            case Exp exp -> typeCheckExpression(exp);
            case Function function -> {
                if (function.body() != null)
                    fail("nested function definition not allowed");
                else typeCheckFunctionDeclaration(function, true);
            }
            case Block block -> typeCheckBlock(block);
            case DoWhile(Statement whileBody, Exp condition, String _) -> {
                typeCheckBlockItem(whileBody);
                typeCheckExpression(condition);
            }
            case For(
                    ForInit init, Exp condition, Exp post, Statement body,
                    String _label
            ) -> {
                typeCheckExpression(condition);
                typeCheckExpression(post);
                typeCheckBlockItem(body);
                switch (init) {
                    case null -> {
                    }
                    case Exp exp -> {
                        typeCheckExpression(exp);
                    }
                    case VarDecl varDecl -> {
                        typeCheckLocalVariableDeclaration(varDecl);
                    }
                }
            }
            case If(
                    Exp condition, Statement ifTrue,
                    Statement ifFalse
            ) -> {
                typeCheckExpression(condition);
                typeCheckBlockItem(ifTrue);
                typeCheckBlockItem(ifFalse);
            }

            case Return(Exp exp) -> {
                typeCheckExpression(exp);
            }
            case While(Exp condition, Statement whileBody, String _) -> {
                typeCheckBlockItem(whileBody);
                typeCheckExpression(condition);
            }
            case NullStatement _, Continue _, Break _ -> {
            }
            case null -> {
            }
        }
    }

    private static void typeCheckLocalVariableDeclaration(VarDecl decl) {
        if (decl.storageClass() == EXTERN) {
            if (decl.init() != null)
                fail("Initializer on local extern variable declaration");
            if (SYMBOL_TABLE.get(decl.name()) instanceof SymbolTableEntry(
                    Type oldType, IdentifierAttributes oldAttrs
            )) {
                if (oldType != INT) fail("function redeclared as variable");

            } else {
                SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(INT, new StaticAttributes(NO_INITIALIZER, true)));
            }
        } else if (decl.storageClass() == STATIC) {
            InitialValue initialValue;
            if (decl.init() instanceof com.quaxt.mcc.parser.Int(int i))
                initialValue = new InitialConstant(i);
            else if (decl.init() == null)
                initialValue = new InitialConstant(0);
            else
                throw new RuntimeException("Non-constant initializer on local static variable");
            SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(INT, new StaticAttributes(initialValue, false)));
        } else {
            SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(INT, LOCAL_ATTR));
            if (decl.init() != null) typeCheckExpression(decl.init());
        }
    }

    private static void typeCheckExpression(Exp exp) {
        switch (exp) {
            case null -> {
            }
            case Assignment(Exp left, Exp right) -> {
                typeCheckExpression(left);
                typeCheckExpression(right);
            }
            case BinaryOp(BinaryOperator _, Exp left, Exp right) -> {
                typeCheckExpression(left);
                typeCheckExpression(right);
            }
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse) -> {
                typeCheckExpression(condition);
                typeCheckExpression(ifTrue);
                typeCheckExpression(ifFalse);

            }
            case Constant constant -> {
            }
            case FunctionCall(Identifier name, List<Exp> args) -> {

                if (SYMBOL_TABLE.get(name.name()) instanceof SymbolTableEntry(
                        Type type, IdentifierAttributes _
                )) {
                    if (type instanceof FunType(int paramCount)) {
                        if (paramCount != args.size()) {
                            fail("Attempt to call " + paramCount + "-arity function " + name.name() + " with " + args.size() + " args");
                        }
                    } else {
                        fail("Attempt to call " + name + " which is of type " + type + ", not function");
                    }
                }
                for (Exp a : args) {
                    typeCheckExpression(a);
                }

            }
            case Identifier identifier -> {

                if (SYMBOL_TABLE.get(identifier.name()) instanceof SymbolTableEntry(
                        Type type, IdentifierAttributes _
                ) && type instanceof FunType) {
                    fail("Function " + identifier.name() + " used as varaible");
                }
            }
            case UnaryOp(UnaryOperator _op, Exp e) -> {
                typeCheckExpression(e);
            }
        }
    }


    record Entry(String name, boolean fromCurrentScope, boolean hasLinkage) {
    }

    public static Program resolveProgram(Program program) {
        Map<String, Entry> identifierMap = new HashMap<>();
        ArrayList<Declaration> decls = program.declarations();
        for (int i = 0; i < decls.size(); i++) {
            switch (decls.get(i)) {
                case Function f ->
                        decls.set(i, resolveFunctionDeclaration(f, identifierMap));

                case VarDecl varDecl ->
                        decls.set(i, resolveFileScopeVariableDeclaration(varDecl, identifierMap));

            }
        }
        return program;
    }

    private static Declaration resolveFileScopeVariableDeclaration(VarDecl varDecl, Map<String, Entry> identifierMap) {
        return switch (varDecl) {
            case VarDecl(
                    String name,
                    Exp init, StorageClass storageClass
            ) -> {
                identifierMap.put(name, new Entry(name, true, true));
                yield varDecl;
            }
        };
    }

    private static Block resolveBlock(Block block, Map<String, Entry> identifierMap) {
        ArrayList<BlockItem> blockItems = new ArrayList<>();
        for (BlockItem i : block.blockItems()) {
            blockItems.add(resolveIdentifiersBlockItem(i, identifierMap));
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

        Block newBody = function.body() instanceof Block block ? resolveBlock(block, innerMap) : null;
        return new Function(function.name(), newArgs, newBody, function.storageClass());
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
                    resolveLocalIdentifierDeclaration(declaration, identifierMap);
            case Statement statement ->
                    resolveStatement(statement, identifierMap);
            case Function function ->
                    resolveFunctionDeclaration(function, identifierMap);
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
            case Block block ->
                    resolveBlock(block, copyIdentifierMap(identifierMap));
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
                    resolveLocalIdentifierDeclaration(declaration, identifierMap);
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

    private static VarDecl resolveLocalIdentifierDeclaration(VarDecl decl, Map<String, Entry> identifierMap) {
        if (identifierMap.get(decl.name()) instanceof Entry prevEntry) {
            if (prevEntry.fromCurrentScope()) {
                if (!(prevEntry.hasLinkage() && decl.storageClass() == EXTERN)) {
                    fail("Conflicting local declaration");
                }
            }
            if (decl.storageClass() == EXTERN) {
                identifierMap.put(decl.name(), new Entry(decl.name(), true, true));
                return decl;
            }
        }
        String uniqueName = Mcc.makeTemporary(decl.name() + ".");
        identifierMap.put(decl.name(), new Entry(uniqueName, true, false));
        Exp init = decl.init();
        return new VarDecl(uniqueName, resolveExp(init, identifierMap), decl.storageClass());
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
