package com.quaxt.mcc.semantic;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.EQUALS;
import static com.quaxt.mcc.CmpOperator.NOT_EQUALS;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.InitialValue.NoInitializer.NO_INITIALIZER;
import static com.quaxt.mcc.InitialValue.Tentative.TENTATIVE;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.UnaryOperator.NOT;
import static com.quaxt.mcc.parser.StorageClass.EXTERN;
import static com.quaxt.mcc.parser.StorageClass.STATIC;
import static com.quaxt.mcc.semantic.Primitive.*;

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

    @SuppressWarnings("unchecked")
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
            case ConstLong(long i) ->
                    convertConst(new LongInit(i), decl.varType());
            case ConstUInt(int i) ->
                    convertConst(new UIntInit(i), decl.varType());
            case ConstULong(long i) ->
                    convertConst(new ULongInit(i), decl.varType());
            case ConstDouble(double d) ->
                    convertConst(new DoubleInit(d), decl.varType());
            case null ->
                    decl.storageClass() == EXTERN ? NO_INITIALIZER : TENTATIVE;
            default -> throw new RuntimeException("Non constant initializer");
        };
        if (decl.varType() instanceof Pointer && decl.init() instanceof Constant c && !isNullPointerConstant(c)) {
            throw new RuntimeException("Cannot convert type for assignment");
        }
        boolean global = decl.storageClass() != STATIC;
        if (SYMBOL_TABLE.get(decl.name()) instanceof SymbolTableEntry(Type type,
                                                                      IdentifierAttributes attrs)) {
            if (type != decl.varType())
                fail("variable declared with inconsistent type");
            if (decl.storageClass() == EXTERN)
                global = attrs.global();
            else if (attrs.global() != global)
                fail("conflicting variable linkage");

            if (attrs instanceof StaticAttributes(
                    InitialValue oldInit, boolean _)) {
                if (oldInit instanceof StaticInit oldInitialConstant) {
                    if (initialValue instanceof StaticInit)
                        fail("Conflicting file scope variable definitions");
                    else initialValue = oldInitialConstant;
                } else if (!(initialValue instanceof StaticInit) && oldInit == TENTATIVE)
                    initialValue = TENTATIVE;
            }
        }

        StaticAttributes attrs = new StaticAttributes(initialValue, global);
        SYMBOL_TABLE.put(decl.name().name(), new SymbolTableEntry(decl.varType(), attrs));
        return decl;
    }

    private static double unsignedLongToDouble(long ul) {
        double d = (double) (ul & 0x7fffffffffffffffL);
        double r = ul < 0 ? d + 0x1.0p63 : d;
        return r;
    }

    private static long doubleToUnsignedLong(double d) {
        if (d > 0x1.0p63) {
            return (long) (d - 0x1.0p63) + (1L << 63);
        }
        return (long) d;
    }

    private static InitialValue convertConst(InitialValue init, Type type) {
        if (init instanceof DoubleInit(double d)) {
            return switch (type) {
                case DOUBLE -> init;
                case LONG -> new LongInit((long) d);
                case INT -> new IntInit((int) d);
                case ULONG -> new ULongInit(doubleToUnsignedLong(d));
                // casting directly to int would be wrong result for doubles > 2^31
                case UINT -> new UIntInit((int) (long) d);
                default ->
                        throw new IllegalArgumentException("not a const:" + init);
            };
        }
        long initL = switch (init) {
            case IntInit(int i) -> i;
            case LongInit(long l) -> l;
            case UIntInit(int i) -> Integer.toUnsignedLong(i);
            case ULongInit(long l) -> l;
            default ->
                    throw new IllegalArgumentException("not a const:" + init);
        };
        return switch (type) {
            case DOUBLE -> {
                double d = init instanceof ULongInit ? unsignedLongToDouble(initL) : (double) initL;
                yield new DoubleInit(d);
            }
            case LONG -> new LongInit(initL);
            case INT -> new IntInit((int) initL);
            case ULONG -> new ULongInit(initL);
            case UINT -> new UIntInit((int) initL);
            case Pointer _ -> new UIntInit((int) initL);
            default -> null;
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
                Var param = decl.parameters().get(i);
                SYMBOL_TABLE.put(param.name(), new SymbolTableEntry(decl.funType().params().get(i), LOCAL_ATTR));
            }
            typeCheckedBody = typeCheckBlock(decl.body(), decl);
        } else typeCheckedBody = null;
        List<Var> declParams = decl.parameters();
        for (int i = 0; i < declParams.size(); i++) {
            Var oldParam = declParams.get(i);
            declParams.set(i, new Var(oldParam.name(), decl.funType().params().get(i)));
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
                yield new Return(convertByAssignment(typeCheckExpression(exp), returnType));

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
                SYMBOL_TABLE.put(decl.name().name(), new SymbolTableEntry(INT, new StaticAttributes(NO_INITIALIZER, true)));
            }
            return decl;
        } else if (decl.storageClass() == STATIC) {
            InitialValue initialValue = switch (decl.init()) {
                case ConstInt(int i) -> new IntInit(i);
                case ConstDouble(double d) -> new DoubleInit(d);
                case ConstLong(long l) -> new LongInit(l);
                case ConstUInt(int i) -> new UIntInit(i);
                case ConstULong(long l) -> new ULongInit(l);
                case null -> new IntInit(0);
                default ->
                        throw new RuntimeException("Non-constant initializer on local static variable");
            };
            initialValue = convertConst(initialValue, decl.varType());
            SYMBOL_TABLE.put(decl.name().name(), new SymbolTableEntry(decl.varType(), new StaticAttributes(initialValue, false)));
            return new VarDecl(decl.name(), switch (initialValue) {
                case IntInit(int i) -> new ConstInt(i);
                case LongInit(long l) -> new ConstLong(l);
                case UIntInit(int i) -> new ConstUInt(i);
                case ULongInit(long l) -> new ConstULong(l);
                default -> null;
            }, decl.varType(), decl.storageClass());
        } else {
            SYMBOL_TABLE.put(decl.name().name(), new SymbolTableEntry(decl.varType(), LOCAL_ATTR));
            Exp init = decl.init() != null ? convertByAssignment(typeCheckExpression(decl.init()), decl.varType()) : null;
            return new VarDecl(decl.name(), init, decl.varType(), decl.storageClass());
        }
    }

    private static Exp convertTo(Exp e, Type t) {
        if (e == null || e.type() == t) return e;
        return new Cast(t, e);
    }

    private static Exp convertByAssignment(Exp e, Type targetType) {
        Type t = e.type();
        if (t.equals(targetType)) return e;
        if ((isArithmeticType(t) && isArithmeticType(targetType)) || (isNullPointerConstant(e) && targetType instanceof Pointer))
            return convertTo(e, targetType);
        throw new RuntimeException("Cannot convert type for assignment");
    }

    private static boolean isArithmeticType(Type t) {
        return t instanceof Primitive;
    }

    private static Exp typeCheckExpression(Exp exp) {
        return switch (exp) {
            case null -> null;
            case Assignment(Exp left, Exp right, Type type) -> {
                if (!isLvalue(left))
                    throw new RuntimeException("cannot assign to non-lvalue");
                Exp typedLeft = typeCheckExpression(left);
                Exp typedRight = typeCheckExpression(right);
                Type leftType = typedLeft.type();
                Exp convertedRight = convertByAssignment(typedRight, leftType);
                yield new Assignment(typedLeft, convertedRight, leftType);
            }
            case BinaryOp(BinaryOperator op, Exp e1, Exp e2,
                          Type type) when op == EQUALS || op == NOT_EQUALS -> {
                Exp typedE1 = typeCheckExpression(e1);
                Exp typedE2 = typeCheckExpression(e2);
                Type t1 = typedE1.type();
                Type t2 = typedE2.type();
                Type commonType = t1 instanceof Pointer || t2 instanceof Pointer ? getCommonPointerType(typedE1, typedE2) : getCommonType(t1, t2);
                Exp convertedE1 = convertTo(typedE1, commonType);
                Exp convertedE2 = convertTo(typedE2, commonType);
                yield new BinaryOp(op, convertedE1, convertedE2, INT);
            }
            case BinaryOp(BinaryOperator op, Exp e1, Exp e2, Type type) -> {
                Exp typedE1 = typeCheckExpression(e1);
                Exp typedE2 = typeCheckExpression(e2);
                if (op == AND || op == OR) {
                    yield new BinaryOp(op, typedE1, typedE2, INT);
                }
                Type t1 = typedE1.type();
                Type t2 = typedE2.type();
                if ((op == REMAINDER && (t1 == DOUBLE || t2 == DOUBLE))
                        || ((op == REMAINDER || op == DIVIDE || op == IMUL) && (t1 instanceof Pointer || t2 instanceof Pointer)))
                    fail("invalid operands to binary % (have ‘" + t1 + "’ and ‘" + t2 + "’");

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
                Type innerType = typedInner.type();
                if (type instanceof Pointer && innerType == DOUBLE || innerType instanceof Pointer && type == DOUBLE)
                    throw new RuntimeException("illegal cast:" + innerType + "->" + type);
                yield new Cast(type, typedInner);
            }
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type) -> {
                Exp typedCondition = typeCheckExpression(condition);
                Exp typedIfTrue = typeCheckExpression(ifTrue);
                Exp typedIfFalse = typeCheckExpression(ifFalse);
                Type t1 = typedIfTrue.type();
                Type t2 = typedIfFalse.type();
                Type commonType = t1 instanceof Pointer || t2 instanceof Pointer ? getCommonPointerType(typedIfTrue, typedIfFalse) : getCommonType(t1, t2);
                yield new Conditional(typedCondition, convertTo(typedIfTrue, commonType), convertTo(typedIfFalse, commonType), commonType);
            }
            case Constant constant -> constant;
            case FunctionCall(Var name, List<Exp> args, Type type) -> {
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
                            convertedArgs.add(convertByAssignment(typedArg, paramType));
                        }
                        yield new FunctionCall(name, convertedArgs, ret);
                    }
                    default ->
                            fail("variable " + name.name() + " used as function");
                };
            }
            case Var(String name, Type type) -> {
                Type t = SYMBOL_TABLE.get(name).type();
                if (t instanceof FunType)
                    fail("Function " + name + " used as a variable");
                yield new Var(name, t);
            }
            case UnaryOp(UnaryOperator op, Exp inner, Type type) -> {
                Exp typedInner = typeCheckExpression(inner);
                if (op == UnaryOperator.BITWISE_NOT && typedInner.type() == DOUBLE) {
                    fail("can't apply ~ to double");
                }
                if (typedInner.type() instanceof Pointer && op != NOT) {
                    fail("Can't apply " + op + " to pointer");
                }
                yield switch (op) {
                    case NOT -> new UnaryOp(op, typedInner, INT);
                    default -> new UnaryOp(op, typedInner, typedInner.type());
                };

            }
            case Dereference(Exp inner, Type type) -> {
                Exp typedInner = typeCheckExpression(inner);
                yield switch (typedInner.type()) {
                    case Pointer(Type referenced) ->
                            new Dereference(typedInner, referenced);
                    default -> fail("Cannot dereference non-pointer");
                };

            }
            case AddrOf(Exp inner, Type type) -> {
                if (isLvalue(inner)) {
                    Exp typedInner = typeCheckExpression(inner);
                    Type referencedType = typedInner.type();
                    yield new AddrOf(typedInner, new Pointer(referencedType));
                } else {
                    yield fail("Cannot take address of non-lvalue");
                }

            }
            case Subscript subscript -> throw new Todo("mr-todo typecheck subscript");
        };
    }

    private static Type getCommonPointerType(Exp e1, Exp e2) {
        Type t1 = e1.type();
        Type t2 = e2.type();
        if (t1.equals(t2)) return t1;
        if (isNullPointerConstant(e1)) return t2;
        if (isNullPointerConstant(e2)) return t1;
        throw new RuntimeException("Expressions have incompatible types");
    }

    private static boolean isNullPointerConstant(Exp e) {
        return switch (e) {
            case ConstInt(int i) -> i == 0;
            case ConstLong(long l) -> l == 0L;
            case ConstUInt(int i) -> i == 0;
            case ConstULong(long l) -> l == 0;
            default -> false;
        };
    }

    private static boolean isLvalue(Exp exp) {
        return exp instanceof Dereference || exp instanceof Var;
    }

    private static Type getCommonType(Type t1, Type t2) {
        return t1 == t2 ? t1 : t1 == DOUBLE || t2 == DOUBLE ? DOUBLE : t1.size() == t2.size() ? (t1.isSigned() ? t2 : t1) : t1.size() > t2.size() ? t1 : t2;
    }

    record Entry(String name, boolean fromCurrentScope, boolean hasLinkage) {}

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
            case VarDecl(Var name, Exp init, Type varType,
                         StorageClass storageClass) -> {
                identifierMap.put(name.name(), new Entry(name.name(), true, true));
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

        List<Var> newArgs = resolveParams(function.parameters(), innerMap);

        Block newBody = function.body() instanceof Block block ? resolveBlock(block, innerMap) : null;
        return new Function(function.name(), newArgs, newBody, function.funType(), function.storageClass());
    }

    private static List<Var> resolveParams(List<Var> parameters, Map<String, Entry> identifierMap) {
        List<Var> newParams = new ArrayList<>();
        for (Var d : parameters) {
            if (identifierMap.get(d.name()) instanceof Entry e && e.fromCurrentScope()) {
                fail("Duplicate variable declaration");
            }
            String uniqueName = Mcc.makeTemporary(d.name() + ".");
            identifierMap.put(d.name(), new Entry(uniqueName, true, false));
            newParams.add(new Var(uniqueName, d.type()));
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
            identifierMap.put(decl.name().name(), new Entry(decl.name().name(), true, true));
            return decl;
        }
        String uniqueName = Mcc.makeTemporary(decl.name().name() + ".");
        identifierMap.put(decl.name().name(), new Entry(uniqueName, true, false));
        Exp init = decl.init();
        return new VarDecl(new Var(uniqueName, null), resolveExp(init, identifierMap), decl.varType(), decl.storageClass());
    }

    private static <T extends Exp> T resolveExp(T exp, Map<String, Entry> identifierMap) {
        @SuppressWarnings("unchecked") T r = (T) switch (exp) {
            case null -> null;
            case Assignment(Exp left, Exp right, Type type) ->
                    isLvalue(left) ? new Assignment(resolveExp(left, identifierMap), resolveExp(right, identifierMap), type) : fail("Invalid lvalue");
            case BinaryOp(BinaryOperator op, Exp left, Exp right, Type type) ->
                    new BinaryOp(op, resolveExp(left, identifierMap), resolveExp(right, identifierMap), type);
            case Constant constant -> constant;
            case UnaryOp(UnaryOperator op, Exp arg, Type type) ->
                    new UnaryOp(op, resolveExp(arg, identifierMap), type);
            case AddrOf(Exp arg, Type type) ->
                    new AddrOf(resolveExp(arg, identifierMap), type);
            case Dereference(Exp arg, Type type) ->
                    new Dereference(resolveExp(arg, identifierMap), type);
            case Var(String name, Type type) ->
                    identifierMap.get(name) instanceof Entry e ? new Var(e.name(), type) : fail("Undeclared variable:" + exp);
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type) ->
                    new Conditional(resolveExp(condition, identifierMap), resolveExp(ifTrue, identifierMap), resolveExp(ifFalse, identifierMap), type);
            case FunctionCall(Var name, List<Exp> args, Type type) ->
                    identifierMap.get(name.name()) instanceof Entry newFunctionName ? new FunctionCall(new Var(newFunctionName.name(), type), resolveArgs(identifierMap, args), type) : fail("Undeclared function:" + name);
            case Cast(Type type, Exp e) ->
                    new Cast(type, resolveExp(e, identifierMap));
            case Subscript subscript -> throw new Todo("mr-todo resolveExp for subscript");
        };
        return r;
    }

    private static <T extends Exp> List<T> resolveArgs(Map<String, Entry> identifierMap, List<T> args) {
        List<T> newArgs = new ArrayList<>();
        for (T arg : args) {
            newArgs.add(resolveExp(arg, identifierMap));
        }
        return newArgs;
    }

    private static Exp fail(String s) {
        throw new RuntimeException(s);
    }
}
