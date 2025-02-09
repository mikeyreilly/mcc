package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.parser.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.InitialValue.NoInitializer.NO_INITIALIZER;
import static com.quaxt.mcc.InitialValue.Tentative.TENTATIVE;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.parser.StorageClass.EXTERN;
import static com.quaxt.mcc.parser.StorageClass.STATIC;
import static com.quaxt.mcc.semantic.Primitive.INT;
import static com.quaxt.mcc.semantic.Primitive.LONG;

public class SemanticAnalysis {


    public static Program loopLabelProgram(Program program) {
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
        return new Function(function.name(), function.parameters(), loopLabelStatement(function.body(), null), function.funType(), function.storageClass());

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
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String _) -> {
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
        return new VarDecl(declaration.name(), loopLabelStatement(init, currentLabel), declaration.varType(), declaration.storageClass());
    }

    public static void typeCheckProgram(Program program) {
        for (int i = 0; i < program.declarations().size(); i++) {
            Declaration d = program.declarations().get(i);

            program.declarations().set(i, switch (d) {
                case Function function ->
                        typeCheckFunctionDeclaration(function, false);
                case VarDecl varDecl ->
                        typeCheckFileScopeVariableDeclaration(varDecl);
            });
        }
    }

    private static VarDecl typeCheckFileScopeVariableDeclaration(VarDecl decl) {
        InitialValue initialValue = switch (decl.init()) {
            case ConstInt(int i) ->
                    convertConst(new IntInit(i), decl.varType());
            case ConstLong(long l) -> new LongInit(l);
            case null ->
                    decl.storageClass() == EXTERN ? NO_INITIALIZER : TENTATIVE;
            default -> throw new RuntimeException("Non constant initializer");
        };
        boolean global = decl.storageClass() != STATIC;
        if (SYMBOL_TABLE.get(decl.name()) instanceof SymbolTableEntry oldDecl) {
            if (oldDecl.type() != decl.varType())
                fail("variable declared with inconsistent type");
            if (decl.storageClass() == EXTERN)
                global = oldDecl.attrs().global();
            else if (oldDecl.attrs().global() != global)
                fail("conflicting variable linkage");

            if (oldDecl.attrs() instanceof StaticAttributes(
                    InitialValue oldInit, boolean _)) {
                if (oldInit instanceof IntInit oldInitialConstant) {
                    if (initialValue instanceof IntInit)
                        fail("Conflicting file scope variable definitions");
                    else initialValue = oldInitialConstant;
                } else if (!(initialValue instanceof IntInit) && oldInit == TENTATIVE)
                    initialValue = TENTATIVE;
            }
        }
        StaticAttributes attrs = new StaticAttributes(initialValue, global);
        SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(decl.varType(), attrs));
        return decl;
    }

    private static InitialValue convertConst(InitialValue init, Type type) {
        return switch (init) {
            case InitialValue.NoInitializer _, InitialValue.Tentative _ -> init;
            case IntInit(int i) -> switch (type) {
                case LONG -> new LongInit(i);
                default -> init;
            };
            case LongInit(long l) -> switch (type) {
                case INT -> new IntInit((int) l);
                default -> init;
            };
        };
    }

    private static Function typeCheckFunctionDeclaration(Function decl, boolean blockScope) {
        if (blockScope && decl.storageClass() == STATIC) {
            fail("invalid storage class for block scope function declaration ‘" + decl.name() + "’");
        }
        boolean defined = decl.body() != null;
        boolean global = decl.storageClass() != STATIC;
        SymbolTableEntry oldEntry = SYMBOL_TABLE.get(decl.name());
        boolean alreadyDefined = false;
        if (oldEntry instanceof SymbolTableEntry(Type oldType,
                                                 IdentifierAttributes attrs)) {
            if (oldType instanceof FunType(List<Type> params, Type ret)) {
                alreadyDefined = oldEntry.attrs().defined();
                if (alreadyDefined && defined)
                    fail("already defined: " + decl.name());

                if (oldEntry.attrs().global() && decl.storageClass() == STATIC)
                    fail("Static function declaration follows non-static");
                global = oldEntry.attrs().global();
                if (!decl.funType().equals(oldType))
                    fail("Incompatible function declarations for " + decl.name());
            } else {
                fail("Incompatible function declarations for " + decl.name());
            }
        }
        FunAttributes attrs = new FunAttributes(alreadyDefined || decl.body() != null, global);
        FunType funType = decl.funType();
        SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(funType, attrs));

        Block typeCheckedBody;
        if (decl.body() != null) {
            for (int i = 0; i < decl.parameters().size(); i++) {
                Identifier param = decl.parameters().get(i);
                SYMBOL_TABLE.put(param.name(), new SymbolTableEntry(decl.funType().params().get(i), LOCAL_ATTR));
            }
            typeCheckedBody = typeCheckBlock(decl.body(), decl);
        } else typeCheckedBody = null;
        List<Identifier> declParams = decl.parameters();
        for (int i = 0; i < declParams.size(); i++) {
            Identifier oldParam = declParams.get(i);
            declParams.set(i, new Identifier(oldParam.name(), decl.funType().params().get(i)));
        }
        return new Function(decl.name(), decl.parameters(), typeCheckedBody, decl.funType(), decl.storageClass());
    }

    private static Block typeCheckBlock(Block body, Function enclosingFunction) {
        for (int i = 0; i < body.blockItems().size(); i++) {
            BlockItem blockItem = body.blockItems().get(i);
            body.blockItems().set(i, typeCheckBlockItem(blockItem, enclosingFunction));
        }
        return body;
    }

    private static BlockItem typeCheckBlockItem(BlockItem blockItem, Function enclosingFunction) {
        return switch (blockItem) {
            case VarDecl declaration ->
                    typeCheckLocalVariableDeclaration(declaration);
            case Exp exp -> typeCheckExpression(exp);
            case Function function -> {
                if (function.body() != null)
                    throw new RuntimeException("nested function definition not allowed");
                else yield typeCheckFunctionDeclaration(function, true);
            }
            case Block block -> typeCheckBlock(block, enclosingFunction);
            case DoWhile(Statement whileBody, Exp condition, String label) ->
                    new DoWhile((Statement) typeCheckBlockItem(whileBody, enclosingFunction), typeCheckExpression(condition), label);
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> new For(switch (init) {
                case null -> null;
                case Exp exp -> typeCheckExpression(exp);
                case VarDecl varDecl ->
                        typeCheckLocalVariableDeclaration(varDecl);
            }, typeCheckExpression(condition), typeCheckExpression(post), (Statement) typeCheckBlockItem(body, enclosingFunction), label);
            case If(Exp condition, Statement ifTrue, Statement ifFalse) ->
                    new If(typeCheckExpression(condition), (Statement) typeCheckBlockItem(ifTrue, enclosingFunction), (Statement) typeCheckBlockItem(ifFalse, enclosingFunction));

            case Return(Exp exp) -> {
                Type returnType = enclosingFunction.funType().ret();
                yield new Return(convertTo(typeCheckExpression(exp), returnType));

            }
            case While(Exp condition, Statement whileBody, String label) ->
                    new While(typeCheckExpression(condition), (Statement) typeCheckBlockItem(whileBody, enclosingFunction), label);
            case NullStatement _, Continue _, Break _ -> blockItem;
            case null -> null;

        };
    }

    private static VarDecl typeCheckLocalVariableDeclaration(VarDecl decl) {
        if (decl.storageClass() == EXTERN) {
            if (decl.init() != null)
                fail("Initializer on local extern variable declaration");
            if (SYMBOL_TABLE.get(decl.name()) instanceof SymbolTableEntry(
                    Type oldType, IdentifierAttributes oldAttrs)) {
                if (oldType != decl.varType())
                    fail("inconsistent variable redefenition");

            } else {
                SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(INT, new StaticAttributes(NO_INITIALIZER, true)));
            }
            return decl;
        } else if (decl.storageClass() == STATIC) {
            InitialValue initialValue;
            if (decl.init() instanceof ConstInt(int i))
                initialValue = new IntInit(i);
            else if (decl.init() == null) initialValue = new IntInit(0);
            else
                throw new RuntimeException("Non-constant initializer on local static variable");
            initialValue = convertConst(initialValue, decl.varType());
            SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(INT, new StaticAttributes(initialValue, false)));
            return new VarDecl(decl.name(), switch (initialValue) {
                case IntInit(int i) -> new ConstInt(i);
                case LongInit(long l) -> new ConstLong(l);
                default -> null;
            }, decl.varType(), decl.storageClass());
        } else {
            SYMBOL_TABLE.put(decl.name(), new SymbolTableEntry(decl.varType(), LOCAL_ATTR));
            Exp typeCheckedInit = (decl.init() != null) ? typeCheckExpression(decl.init()) : null;
            return new VarDecl(decl.name(),
                    convertTo(typeCheckedInit, decl.varType()),
                    decl.varType(), decl.storageClass());
        }
    }

    private static Exp convertTo(Exp e, Type t) {
        if (e == null || e.type() == t) return e;
        return new Cast(t, e);
    }

    private static Exp typeCheckExpression(Exp exp) {
        return switch (exp) {
            case null -> null;
            case Assignment(Exp left, Exp right, Type type) -> {
                Exp typedLeft = typeCheckExpression(left);
                Exp typedRight = typeCheckExpression(right);
                Type leftType = typedLeft.type();
                Exp convertedRight = convertTo(typedRight, leftType);
                yield new Assignment(typedLeft, convertedRight, leftType);
            }
            case BinaryOp(BinaryOperator op, Exp e1, Exp e2, Type type) -> {
                Exp typedE1 = typeCheckExpression(e1);
                Exp typedE2 = typeCheckExpression(e2);
                if (op == AND || op == OR) {
                    yield new BinaryOp(op, typedE1, typedE2, INT);
                }
                Type t1 = typedE1.type();
                Type t2 = typedE2.type();
                Type commonType = getCommonType(t1, t2);
                Exp convertedE1 = convertTo(typedE1, commonType);
                Exp convertedE2 = convertTo(typedE2, commonType);

                yield new BinaryOp(op, convertedE1, convertedE2, switch (op) {
                    case SUB, ADD, IMUL, DIVIDE, REMAINDER -> commonType;
                    default -> INT;
                });

            }
            case Cast(Type type, Exp inner) -> {
                Exp typedInner = typeCheckExpression(inner);
                yield new Cast(type, typedInner);
            }
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type) -> {
                Exp typedCondition = typeCheckExpression(condition);
                Exp typedIfTrue = typeCheckExpression(ifTrue);
                Exp typedIfFalse = typeCheckExpression(ifFalse);
                Type t1 = typedIfTrue.type();
                Type t2 = typedIfTrue.type();
                Type commonType = getCommonType(t1, t2);
                yield new Conditional(typedCondition, convertTo(typedIfTrue, commonType), convertTo(typedIfFalse, commonType), commonType);
            }
            case Constant constant -> constant;
            case FunctionCall(Identifier name, List<Exp> args, Type type) -> {
                Type fType = SYMBOL_TABLE.get(name.name()).type();
                yield switch (fType) {
                    case FunType(List<Type> params, Type ret) -> {
                        if (params.size() != args.size())
                            fail("Function called with wrong number of arguments");
                        ArrayList<Exp> convertedArgs = new ArrayList<>();
                        for (int i = 0; i < params.size(); i++) {
                            Exp arg = args.get(i);
                            Type paramType = params.get(i);
                            Exp typedArg = typeCheckExpression(arg);
                            convertedArgs.add(convertTo(typedArg, paramType));
                        }
                        yield new FunctionCall(name, convertedArgs, ret);
                    }
                    default ->
                            fail("variable " + name.name() + " used as function");
                };

            }
            case Identifier(String name, Type type) -> {
                Type t = SYMBOL_TABLE.get(name).type();
                if (t instanceof FunType)
                    fail("Function " + name + " used as a variable");
                yield new Identifier(name, t);
            }
            case UnaryOp(UnaryOperator op, Exp inner, Type type) -> {
                Exp typedInner = typeCheckExpression(inner);
                yield switch (op) {
                    case NOT -> new UnaryOp(op, typedInner, INT);
                    default -> new UnaryOp(op, typedInner, typedInner.type());
                };

            }
        };
    }

    private static Type getCommonType(Type t1, Type t2) {
        return t1 == t2 ? t1 : LONG;
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
            case VarDecl(String name, Exp init, Type varType,
                         StorageClass storageClass) -> {
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
        return new Function(function.name(), newArgs, newBody, function.funType(), function.storageClass());
    }

    private static List<Identifier> resolveParams(List<Identifier> parameters, Map<String, Entry> identifierMap) {
        List<Identifier> newParams = new ArrayList<>();
        for (Identifier d : parameters) {
            if (identifierMap.get(d.name()) instanceof Entry e && e.fromCurrentScope()) {
                fail("Duplicate variable declaration");
            }
            String uniqueName = Mcc.makeTemporary(d.name() + ".");
            identifierMap.put(d.name(), new Entry(uniqueName, true, false));
            newParams.add(new Identifier(uniqueName, d.type()));
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
            case If(Exp condition, Statement ifTrue, Statement ifFalse) ->
                    new If(resolveExp(condition, identifierMap), resolveStatement(ifTrue, identifierMap), resolveStatement(ifFalse, identifierMap));
            case Block block ->
                    resolveBlock(block, copyIdentifierMap(identifierMap));
            case NullStatement nullStatement -> nullStatement;
            case Break _, Continue _ -> blockItem;
            case DoWhile(Statement body, Exp condition, String label) ->
                    new DoWhile(resolveStatement(body, identifierMap), resolveExp(condition, identifierMap), label);
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> {
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

        }
        if (decl.storageClass() == EXTERN) {
            identifierMap.put(decl.name(), new Entry(decl.name(), true, true));
            return decl;
        }
        String uniqueName = Mcc.makeTemporary(decl.name() + ".");
        identifierMap.put(decl.name(), new Entry(uniqueName, true, false));
        Exp init = decl.init();
        return new VarDecl(uniqueName, resolveExp(init, identifierMap), decl.varType(), decl.storageClass());
    }

    private static Exp resolveExp(Exp exp, Map<String, Entry> identifierMap) {
        return switch (exp) {
            case null -> null;
            case Assignment(Exp left, Exp right, Type type) ->
                    left instanceof Identifier v ? new Assignment(resolveExp(v, identifierMap), resolveExp(right, identifierMap), type) : fail("Invalid lvalue");
            case BinaryOp(BinaryOperator op, Exp left, Exp right, Type type) ->
                    new BinaryOp(op, resolveExp(left, identifierMap), resolveExp(right, identifierMap), type);
            case Constant constant -> constant;
            case UnaryOp(UnaryOperator op, Exp arg, Type type) ->
                    new UnaryOp(op, resolveExp(arg, identifierMap), type);
            case Identifier(String name, Type type) ->
                    identifierMap.get(name) instanceof Entry e ? new Identifier(e.name(), type) : fail("Undeclared variable:" + exp);
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type) ->
                    new Conditional(resolveExp(condition, identifierMap), resolveExp(ifTrue, identifierMap), resolveExp(ifFalse, identifierMap), type);
            case FunctionCall(Identifier name, List<Exp> args, Type type) ->
                    identifierMap.get(name.name()) instanceof Entry newFunctionName ? new FunctionCall(new Identifier(newFunctionName.name(), type), resolveArgs(identifierMap, args), type) : fail("Undeclared function:" + name);
            case Cast(Type type, Exp e) ->
                    new Cast(type, resolveExp(e, identifierMap));
            default ->
                    throw new IllegalStateException("Unexpected value: " + exp);
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
