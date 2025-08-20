package com.quaxt.mcc.tacky;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.parser.StructOrUnionSpecifier;
import com.quaxt.mcc.semantic.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.IdentifierAttributes.LocalAttr.LOCAL_ATTR;
import static com.quaxt.mcc.Mcc.SYMBOL_TABLE;
import static com.quaxt.mcc.parser.StorageClass.EXTERN;
import static com.quaxt.mcc.parser.StorageClass.STATIC;
import static com.quaxt.mcc.semantic.Primitive.*;
import static com.quaxt.mcc.semantic.SemanticAnalysis.convertConst;

public class IrGen {

    public static ProgramIr programIr(Program program) {
        List<TopLevel> tackyDefs = new ArrayList<>();
        for (Function function : program.functions()) {
            if (function.body != null)
                tackyDefs.add(compileFunction(function));
        }
        convertSymbolsToTacky(tackyDefs);
        return new ProgramIr(tackyDefs);
    }

    private static void convertSymbolsToTacky(List<TopLevel> tackyDefs) {
        for (Map.Entry<String, SymbolTableEntry> e : SYMBOL_TABLE.entrySet()) {
            String name = e.getKey();
            SymbolTableEntry value = e.getValue();
            switch (value.attrs()) {
                case ConstantAttr(StaticInit init) -> {
                    tackyDefs.add(new StaticConstant(name, value.type(), init));
                }
                case FunAttributes funAttributes -> {}
                case IdentifierAttributes.LocalAttr localAttr -> {}
                case StaticAttributes(InitialValue init, boolean global, StorageClass _) -> {
                    if (init instanceof InitialValue.Tentative) {
                        tackyDefs.add(new StaticVariable(name, global,
                                value.type(),
                                Collections.singletonList(new ZeroInit(Mcc.size(value.type())))));
                    } else if (init instanceof Initial(
                            List<StaticInit> initList)) {
                        tackyDefs.add(new StaticVariable(name, global,
                                value.type(), initList));
                    }
                }
            }

        }
    }

    private static FunctionIr compileFunction(Function function) {
        List<InstructionIr> instructions = new ArrayList<>();
        compileBlock(function.body, instructions);
        FunctionIr f = new FunctionIr(function.name,
                SYMBOL_TABLE.get(function.name).attrs().global(), function.parameters, instructions, function.funType.ret(), function.callsVaStart);
        ReturnIr ret = new ReturnIr(IntInit.ZERO);
        instructions.add(ret);
        return f;
    }

    private static void compileBlock(Block block,
                                     List<InstructionIr> instructions) {
        compileBlockItems(block.blockItems(), instructions);
    }

    private static void compileDeclaration(Declaration d,
                                           List<InstructionIr> instructions) {
        switch (d) {
            case Function function -> {
                if (function.body != null) compileFunction(function);
            }
            case VarDecl(Var name, Initializer init, Type _,
                         StorageClass storageClass,
                         StructOrUnionSpecifier structOrUnionSpecifier) -> {
                if (storageClass == STATIC || storageClass == EXTERN) return;
                if (init != null) {
                    assign(new VarIr(name.name()), init, instructions, 0);
                    return;
                }
                emitTacky(null, instructions);
            }
            case StructOrUnionSpecifier _ -> {} // nothing to do: StructDecls are not in IR
        }
    }

    private static long assign(VarIr name, Initializer init,
                               List<InstructionIr> instructions, long offset) {
        switch (init) {
            case CompoundInit(ArrayList<Initializer> inits,
                              Type compoundInitType) when compoundInitType instanceof Structure(boolean isUnion,
                                                                                                String tag,
            StructDef _) -> {
                ArrayList<MemberEntry> members =
                        Mcc.TYPE_TABLE.get(tag).members();
                int limit = inits.size();
                if (isUnion && limit > 1) limit = 1;
                for (int i = 0; i < limit; i++) {
                    Initializer memInit = inits.get(i);
                    MemberEntry member = members.get(i);
                    switch (memInit) {
                        case CompoundInit _ ->
                                assign(name, memInit, instructions,
                                        offset + member.offset());
                        case SingleInit(Exp exp, Type targetType) -> {
                            if (exp instanceof Str(String s,
                                                   Type type) && type instanceof Array(
                                    Type _, Constant arraySize)) {
                                initializeArrayWithStringLiteral(name,
                                        instructions,
                                        offset + member.offset(), s, arraySize);
                            } else {
                                var val = emitTackyAndConvert(exp,
                                        instructions);
                                instructions.add(new CopyToOffset(val, name,
                                        offset + member.offset()));
                            }

                        }
                    }
                }
                return offset + Mcc.size(compoundInitType);
            }
            case CompoundInit(ArrayList<Initializer> inits,
                              Type compoundInitType) -> {
                for (Initializer innerInit : inits) {
                    switch (innerInit) {
                        case CompoundInit _ ->
                                offset = assign(name, innerInit, instructions
                                        , offset);
                        case SingleInit(Exp exp, Type targetType) -> {
                            if (exp instanceof Str(String s,
                                                   Type type) && type instanceof Array(
                                    Type _, Constant arraySize)) {
                                initializeArrayWithStringLiteral(name,
                                        instructions, offset, s, arraySize);
                            } else {
                                var val = emitTackyAndConvert(exp,
                                        instructions);
                                instructions.add(new CopyToOffset(val, name,
                                        offset));
                            }
                            offset += Mcc.size(exp.type());
                        }

                    }
                }
                return offset;
            }
            case SingleInit(Exp exp, Type targetType) -> {
                // String literal as array initializer p. 440
                if (exp instanceof Str(String s,
                                       Type type) && type instanceof Array(
                        Type _, Constant arraySize)) {
                    initializeArrayWithStringLiteral(name, instructions,
                            offset, s, arraySize);
                } else {
                    assign(name, exp, instructions);
                }
                return offset + Mcc.size(targetType);
            }

        }

    }


    private static void initializeArrayWithStringLiteral(VarIr dst,
                                                         List<InstructionIr> instructions,
                                                         long offset, String s,
                                                         Constant arraySize) {
        long arrayLen = arraySize.toLong();
        long howManyCharsToCopy = Math.min(s.length(), arrayLen);
        for (int i = 0; i < howManyCharsToCopy; i++) {
            instructions.add(new CopyToOffset(new CharInit((byte) (s.charAt(i) & 0xff)), dst, offset + i));
        }
        for (long i = howManyCharsToCopy; i < arrayLen; i++) {
            instructions.add(new CopyToOffset(CharInit.zero(), dst,
                    offset + i));
        }
    }


    private static void compileBlockItems(List<BlockItem> blockItems,
                                          List<InstructionIr> instructions) {
        for (BlockItem i : blockItems) {
            switch (i) {

                case Declaration d -> compileDeclaration(d, instructions);
                case Statement statement ->
                        compileStatement(statement, instructions);
            }
        }
    }

    private static void compileIfElse(Exp condition, Statement ifTrue,
                                      Statement ifFalse,
                                      List<InstructionIr> instructions) {
        ValIr c = emitTackyAndConvert(condition, instructions);
        LabelIr e2Label = newLabel(Mcc.makeTemporary(".Le2."));
        instructions.add(new JumpIfZero(c, e2Label.label()));
        compileStatement(ifTrue, instructions);
        LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
        instructions.add(new Jump(endLabel.label()));
        instructions.add(e2Label);
        compileStatement(ifFalse, instructions);
        instructions.add(endLabel);
    }

    private static void compileIf(Exp condition, Statement ifTrue,
                                  List<InstructionIr> instructions) {
        ValIr c = emitTackyAndConvert(condition, instructions);
        LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
        instructions.add(new JumpIfZero(c, endLabel.label()));
        compileStatement(ifTrue, instructions);
        instructions.add(endLabel);
    }


    public static void compileStatement(Statement i,
                                         List<InstructionIr> instructions) {
        switch (i) {
            case BuiltinC23VaStart(Var exp) -> {
                ValIr retVal = emitTackyAndConvert(exp,
                        instructions);
                instructions.add(new BuiltinC23VaStartIr((VarIr) retVal));
            }
            case Switch switchStatement -> {
                compileSwitch(switchStatement, instructions);
            }
            case Return(Exp exp) -> {
                ValIr retVal = exp == null ? null : emitTackyAndConvert(exp,
                        instructions);
                ReturnIr ret = new ReturnIr(retVal);
                instructions.add(ret);
            }

            case Exp exp -> emitTacky(exp, instructions);

            case If(Exp condition, Statement ifTrue, Statement ifFalse) -> {
                if (ifFalse != null) {
                    compileIfElse(condition, ifTrue, ifFalse, instructions);
                } else {
                    compileIf(condition, ifTrue, instructions);

                }
            }
            case NullStatement _ -> {
            }
            case Block b -> compileBlock(b, instructions);

            case Break aBreak ->
                    instructions.add(new Jump(breakLabel(aBreak.label)));
            case Goto aGoto -> instructions.add(new Jump(".L" + aGoto.label));
            case Continue aContinue ->
                    instructions.add(new Jump(continueLabel(aContinue.label)));
            case DoWhile(Statement body, Exp condition, String label) -> {
                LabelIr start = newLabel(Mcc.makeTemporary(".Lstart."));
                instructions.add(start);
                compileStatement(body, instructions);
                LabelIr continueLabel = newLabel(continueLabel(label));
                instructions.add(continueLabel);
                ValIr v = emitTackyAndConvert(condition, instructions);
                instructions.add(new JumpIfNotZero(v, start.label()));
                LabelIr breakLabel = newLabel(breakLabel(label));
                instructions.add(breakLabel);
            }
            case For(ForInit init, Exp condition, Exp post, Statement body,
                     String label) -> {

                switch (init) {
                    case Exp e -> emitTacky(e, instructions);
                    case null -> {
                    }
                    case DeclarationList(List<Declaration> list) -> {
                        for (var d: list){
                            compileDeclaration(d, instructions);
                        }
                    }
                }

                LabelIr start = newLabel(startLabel(label));
                LabelIr continueLabel = newLabel(continueLabel(label));
                LabelIr breakLabel = newLabel(breakLabel(label));
                instructions.add(start);
                if (condition != null) {
                    ValIr v = emitTackyAndConvert(condition, instructions);
                    instructions.add(new JumpIfZero(v, breakLabel.label()));
                }
                compileStatement(body, instructions);
                instructions.add(continueLabel);
                emitTacky(post, instructions);
                instructions.add(new Jump(start.label()));
                instructions.add(breakLabel);
            }
            case While(Exp condition, Statement body, String label) -> {
                LabelIr continueLabel = newLabel(continueLabel(label));
                instructions.add(continueLabel);
                ValIr v = emitTackyAndConvert(condition, instructions);
                LabelIr breakLabel = newLabel(breakLabel(label));
                instructions.add(new JumpIfZero(v, breakLabel.label()));
                compileStatement(body, instructions);
                instructions.add(new Jump(continueLabel.label()));
                instructions.add(breakLabel);
            }
            case LabelledStatement(String label, Statement statement) -> {
                instructions.add(newLabel(label));
                compileStatement(statement, instructions);
            }
            case CaseStatement(Switch enclosingSwitch, Constant<?> label, Statement statement) -> {
                String s = enclosingSwitch.labelFor(label);
                instructions.add(newLabel(s));
                compileStatement(statement, instructions);
            }
            case BuiltinVaEnd builtinVaEnd -> {
                // it'structOrUnionSpecifier a NOOP
            }
        }
    }

    private static void compileSwitch(Switch switchStatement,
                                      List<InstructionIr> instructions) {
        ValIr switchVal = emitTackyAndConvert(switchStatement.exp,
                instructions);
        Type type = switchStatement.exp.type();
        for (Constant c : switchStatement.entries) {
            if (c != null) {
                Constant<?> converted =
                        (Constant<?>) convertConst((StaticInit) c, type);
                instructions.add(new Compare(type, converted, switchVal));
                instructions.add(new JumpIfZero(null,
                        switchStatement.labelFor(c)));
            } else instructions.add(new Jump(switchStatement.labelFor(null)));
        }
        String end = breakLabel(switchStatement.label);
        instructions.add(new Jump(end));
        compileStatement(switchStatement.body, instructions);
        instructions.add(newLabel(end));
    }

    private static String startLabel(String label) {
        return label + ".start";
    }

    private static String continueLabel(String label) {
        return label + ".continue";
    }

    private static String breakLabel(String label) {
        return label + ".break";
    }

    private static ExpResult emitTacky(Exp expr,
                                       List<InstructionIr> instructions) {
        switch (expr) {

            case null:
                return null;
            case Constant<?> c:
                return new PlainOperand(c);
//            case IntInit c:
//                return new PlainOperand(c);
//            case LongInit c:
//                return new PlainOperand(c);
//            case UIntInit c:
//                return new PlainOperand(c);
//            case ULongInit c:
//                return new PlainOperand(c);
//            case DoubleInit c:
//                return new PlainOperand(c);
//            case CharInit c:
//                return new PlainOperand(c);
//            case UCharInit c:
//                return new PlainOperand(c);
            case Conditional(Exp condition, Exp ifTrue, Exp ifFalse,
                             Type type): {
                ValIr cond = emitTackyAndConvert(condition, instructions);
                LabelIr e2Label = newLabel(Mcc.makeTemporary(".Le2."));
                instructions.add(new JumpIfZero(cond, e2Label.label()));
                if (type == VOID) {
                    emitTackyAndConvert(ifTrue, instructions);
                    LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
                    instructions.add(new Jump(endLabel.label()));
                    instructions.add(e2Label);
                    emitTackyAndConvert(ifFalse, instructions);
                    instructions.add(endLabel);
                    return null;//not used by caller (see p. 480)
                } else {
                    ValIr e1 = emitTackyAndConvert(ifTrue, instructions);
                    VarIr result = makeTemporary("result.", type);
                    instructions.add(new Copy(e1, result));
                    LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend."));
                    instructions.add(new Jump(endLabel.label()));
                    instructions.add(e2Label);
                    ValIr e2 = emitTackyAndConvert(ifFalse, instructions);
                    instructions.add(new Copy(e2, result));
                    instructions.add(endLabel);
                    return new PlainOperand(result);
                }
            }
            case UnaryOp(UnaryOperator op, Exp exp, Type type): {
                if (op == UnaryOperator.POST_INCREMENT || op == UnaryOperator.POST_DECREMENT) {
                    boolean post = switch (op) {
                        case POST_INCREMENT, POST_DECREMENT -> true;
                        default -> false;
                    };
                    if (!post) throw new Todo();
                    ExpResult lval = emitTacky(exp, instructions);
                    var newOp = switch (op) {
                        case POST_INCREMENT -> ADD;
                        case POST_DECREMENT -> SUB;
                        default -> throw new Todo();
                    };
                    ValIr right = type == DOUBLE ? DoubleInit.ONE : IntInit.ONE;
                    return applyOperatorAndAssign(instructions, exp, lval,
                            right, newOp, post, exp.type(), exp.type());
                }
                ValIr src = emitTackyAndConvert(exp, instructions);
                VarIr dst = makeTemporary("tmp.", type);
                instructions.add(new UnaryIr(op, src, dst));
                return new PlainOperand(dst);
            }
            case CompoundAssignment(CompoundAssignmentOperator op, Exp left,
                                    Exp right, Type tempType,
                                    Type lvalueType): {


                boolean post = false;

                ExpResult lval = emitTacky(left, instructions);
                ArithmeticOperator newOp = switch (op) {
                    case SUB_EQ -> SUB;
                    case ADD_EQ -> ADD;
                    case IMUL_EQ -> IMUL;
                    case DIVIDE_EQ -> DIVIDE;
                    case REMAINDER_EQ -> REMAINDER;
                    case AND_EQ -> AND;
                    case BITWISE_AND_EQ -> BITWISE_AND;
                    case OR_EQ -> OR;
                    case BITWISE_OR_EQ -> BITWISE_OR;
                    case BITWISE_XOR_EQ -> BITWISE_XOR;
                    case SHL_EQ -> SHL;
                    case SAR_EQ -> SAR;
                };
                ValIr rightVal = emitTackyAndConvert(right, instructions);
                return applyOperatorAndAssign(instructions, left, lval,
                        rightVal, newOp, post, tempType, lvalueType);


            }

            case BinaryOp(BinaryOperator op, Exp left, Exp right,
                          Type lvalueType):
                switch (op) {
                    case AND -> {
                        VarIr result = makeTemporary("tmp.", INT);

                        LabelIr falseLabel = newLabel(Mcc.makeTemporary(
                                ".LandFalse."));
                        LabelIr endLabel = newLabel(Mcc.makeTemporary(
                                ".LandEnd."));
                        ValIr v1 = emitTackyAndConvert(left, instructions);
                        instructions.add(new JumpIfZero(v1,
                                falseLabel.label()));

                        ValIr v2 = emitTackyAndConvert(right, instructions);
                        instructions.add(new JumpIfZero(v2,
                                falseLabel.label()));
                        instructions.add(new Copy(IntInit.ONE, result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(falseLabel);
                        instructions.add(new Copy(IntInit.ZERO, result));
                        instructions.add(endLabel);

                        return new PlainOperand(result);
                    }
                    case OR -> {
                        VarIr result = makeTemporary("tmp.", INT);

                        LabelIr trueLabel = newLabel(Mcc.makeTemporary(
                                ".Ltrue."));
                        LabelIr endLabel = newLabel(Mcc.makeTemporary(".Lend" +
                                "."));
                        ValIr v1 = emitTackyAndConvert(left, instructions);
                        instructions.add(new JumpIfNotZero(v1,
                                trueLabel.label()));

                        ValIr v2 = emitTackyAndConvert(right, instructions);
                        instructions.add(new JumpIfNotZero(v2,
                                trueLabel.label()));
                        instructions.add(new Copy(IntInit.ZERO, result));
                        instructions.add(new Jump(endLabel.label()));

                        instructions.add(trueLabel);
                        instructions.add(new Copy(IntInit.ONE, result));
                        instructions.add(endLabel);

                        return new PlainOperand(result);
                    }
                    default -> {
                        ValIr v1 = emitTackyAndConvert(left, instructions);
                        ValIr v2 = emitTackyAndConvert(right, instructions);
                        VarIr dstName = makeTemporary("tmp.", expr.type());
                        VarIr ptr = null;
                        ValIr other = null;
                        Type ptrRefType = null;
                        if (left.type() instanceof Pointer(Type referenced)) {
                            ptr = (VarIr) v1;
                            other = v2;
                            ptrRefType = referenced;
                        } else if (right.type() instanceof Pointer(
                                Type referenced)) {
                            ptr = (VarIr) v2;
                            other = v1;
                            ptrRefType = referenced;
                        }
                        if (ptr != null && op != COMMA) {
                            switch (op) {
                                case SUB -> {
                                    if (right.type() instanceof Pointer) {
                                        // ptr - ptr (left has to be pointer
                                        // because type checker doesn't allow
                                        // non-ptr - ptr)
                                        var diff = makeTemporary("tmp.", LONG);
                                        instructions.add(new BinaryIr(SUB,
                                                ptr, other, diff));
                                        instructions.add(new BinaryIr(DIVIDE,
                                                diff,
                                                new IntInit((int) (long) Mcc.size(ptrRefType)), dstName));
                                    } else { // ptr - int
                                        var j = makeTemporary("tmp.", LONG);
                                        instructions.add(new UnaryIr(UnaryOperator.UNARY_MINUS, other, j));
                                        instructions.add(new AddPtr(ptr, j,
                                                (int) (long) Mcc.size(ptrRefType), dstName));
                                    }
                                }
                                case ADD ->
                                        instructions.add(new AddPtr(ptr,
                                                other,
                                                (int) (long) Mcc.size(ptrRefType), dstName));

                                case CmpOperator _ ->
                                        instructions.add(new BinaryIr(op, v1,
                                                v2, dstName));
                                default ->
                                        throw new IllegalStateException(
                                                "Unexpected value: " + op);
                            }
                        } else
                            instructions.add(new BinaryIr(op == SAR && !left.type().isSigned() ? UNSIGNED_RIGHT_SHIFT : op, v1, v2, dstName));
                        return new PlainOperand(dstName);
                    }
                }
            case Assignment(Exp left, Exp right, Type _):
                return assign(left, right, instructions);
            case Var(String name, Type _):
                return new PlainOperand(new VarIr(name));
            case FunctionCall(Var name, List<Exp> args, boolean varargs, Type type): {
                VarIr result = type == VOID ? null : makeTemporary("tmp.",
                        type);
                ArrayList<ValIr> argVals = new ArrayList<>();
                for (Exp e : args) {
                    argVals.add(emitTackyAndConvert(e, instructions));
                }
                boolean indirect = Mcc.SYMBOL_TABLE.get(name.name()).type() instanceof Pointer;
                instructions.add(new FunCall(name.name(), argVals, varargs,  indirect, result));
                return new PlainOperand(result);
            }
            case Cast(Type t, Exp inner): {
                ValIr result = emitTackyAndConvert(inner, instructions);
                Type innerType = inner.type();
                // for the purposes of casting we treat pointers exactly like
                // unsigned long (p. 375)
                if (t instanceof Pointer) t = ULONG;
                if (innerType instanceof Pointer) innerType = ULONG;
                if (t == innerType || t == VOID) {
                    return new PlainOperand(result);
                }
                VarIr dst = makeTemporary("dst.", t);
                emitCast(instructions, t, innerType, result, dst);
                return new PlainOperand(dst);
            }
            case Dereference(Exp exp, Type _): {
                ValIr result = emitTackyAndConvert(exp, instructions);
                return new DereferencedPointer((VarIr) result);
            }
            case AddrOf(Exp inner, Type _): {
                ExpResult v = emitTacky(inner, instructions);
                return switch (v) {
                    case PlainOperand(ValIr obj) -> {
                        assert (expr.type() instanceof Pointer);
                        VarIr dst = makeTemporary("addr.", expr.type());
                        instructions.add(new GetAddress((VarIr) obj, dst));
                        yield new PlainOperand(dst);
                    }
                    case DereferencedPointer(ValIr ptr) ->
                            new PlainOperand(ptr);
                    case SubObject(VarIr base, int offset) -> {
                        var dst = makeTemporary("dst.", expr.type());
                        instructions.add(new GetAddress(base, dst));
                        if (offset != 0)
                            instructions.add(new AddPtr(dst,
                                    new LongInit(offset), 1, dst));
                        yield new PlainOperand(dst);
                    }
                };
            }

            case Subscript(Exp left, Exp right, Type type): {
                ValIr v1 = emitTackyAndConvert(left, instructions);
                ValIr v2 = emitTackyAndConvert(right, instructions);
                VarIr dstName = makeTemporary("tmp.", new Pointer(expr.type()));
                VarIr ptr;
                ValIr other;
                long scale;
                // type checker ensures either left or right will be pointer
                // but it could also swap the left and right when they are in
                // the
                // "wrong" (index-first) order and it would make this code
                // simpler.
                // On the other hand,  it'structOrUnionSpecifier not all that complicated and it
                // is nice
                // having the AST closely resemble the corresponding code
                if (left.type() instanceof Pointer(Type referenced)) {
                    ptr = (VarIr) v1;
                    other = v2;
                    scale = Mcc.size(referenced);
                } else if (right.type() instanceof Pointer(
                        Type referenced)) { // else condition just for
                    // pattern match
                    ptr = (VarIr) v2;
                    other = v1;
                    scale = Mcc.size(referenced);
                } else throw new AssertionError("");
                instructions.add(new AddPtr(ptr, other, (int) scale, dstName));
                return new DereferencedPointer(dstName);
            }

            case Str(String s, Type type): {
                //string literals in expressions p. 441
                String uniqueName = Mcc.makeTemporary("string.");
                SYMBOL_TABLE.put(uniqueName, new SymbolTableEntry(type,
                        new ConstantAttr(new StringInit(s, true))));
                return emitTacky(SemanticAnalysis.typeCheckExpression(new Var(uniqueName, type)), instructions);
            }
            case SizeOf(Exp exp): {
                return new PlainOperand(new ULongInit(Mcc.size(exp.type())));
            }
            case SizeOfT(Type t): {
                return new PlainOperand(new ULongInit(Mcc.size(t)));
            }
            case Dot(Exp structure, String member, Type type): {
                StructDef structDef = Mcc.TYPE_TABLE.get(tag(structure));
                int memberOffset = structDef.findMember(member).offset();
                ExpResult innerObject = emitTacky(structure, instructions);
                return switch (innerObject) {
                    case DereferencedPointer(ValIr ptr) -> {
                        if (memberOffset == 0) yield innerObject;
                        VarIr dstPtr = makeTemporary("ptr",
                                new Pointer(expr.type()));
                        instructions.add(new AddPtr((VarIr) ptr,
                                new LongInit(memberOffset), 1, dstPtr));
                        yield new DereferencedPointer(dstPtr);
                    }
                    case PlainOperand(VarIr v) ->
                            new SubObject(v, memberOffset);
                    case SubObject(VarIr base, int offset) ->
                            new SubObject(base, memberOffset + offset);
                    default ->
                            throw new IllegalStateException("Unexpected " +
                                    "value: " + innerObject);
                };

            }
            case Arrow(Exp pointer, String member, Type type): {
                StructDef structDef = Mcc.TYPE_TABLE.get(ptrTag(pointer));
                int memberOffset = structDef.findMember(member).offset();
                ValIr innerObject = emitTackyAndConvert(pointer, instructions);
                if (memberOffset == 0)
                    return new DereferencedPointer((VarIr) innerObject);
                VarIr dstPtr = makeTemporary("ptr", new Pointer(expr.type()));
                instructions.add(new AddPtr((VarIr) innerObject,
                        new LongInit(memberOffset), 1, dstPtr));
                return new DereferencedPointer(dstPtr);
            }
            case BuiltinVaArg(Var identifier, Type type): {
                VarIr src = (VarIr) emitTackyAndConvert(identifier,
                        instructions);
                VarIr dst = makeTemporary("tmp.", type);
                instructions.add(new BuiltinVaArgIr(src, dst, type));
                return new PlainOperand(dst);
            }

            default:
                throw new IllegalStateException("Unexpected value: " + expr);
        }
    }

    private static void emitCast(List<InstructionIr> instructions, Type to,
                                 Type innerType, ValIr src, VarIr dst) {
        if (to == DOUBLE || to == FLOAT) {
            instructions.add(innerType.isSigned() ?
                    new IntToDouble(src, dst) : new UIntToDouble(src, dst));
        } else if (innerType == DOUBLE || innerType == FLOAT) {
            instructions.add(to.isSigned() ? new DoubleToInt(src, dst) :
                    new DoubleToUInt(src, dst));
        } else if (Mcc.size(to) == Mcc.size(innerType)) {
            instructions.add(new Copy(src, dst));
        } else {
            if (Mcc.size(to) < Mcc.size(innerType)) {
                instructions.add(new TruncateIr(src, dst));
            } else if (innerType.isSigned()) {
                instructions.add(new SignExtendIr(src, dst));
            } else {
                instructions.add(new ZeroExtendIr(src, dst));
            }
        }
    }

    private static String tag(Exp structure) {
        if (structure.type() instanceof Structure(boolean isUnion, String tag, StructDef _)) return tag;
        throw new AssertionError();
    }

    private static String ptrTag(Exp structure) {
        if (structure.type() instanceof Pointer(
                Type s) && s instanceof Structure(boolean isUnion, String tag, StructDef _)) return tag;
        throw new AssertionError();
    }

    public static ValIr emitTackyAndConvert(Exp e,
                                             List<InstructionIr> instructions) {
        ExpResult result = emitTacky(e, instructions);
        return switch (result) {
            case null -> null;
            case DereferencedPointer(ValIr ptr) -> {
                VarIr dst = makeTemporary("ptr.", e.type());
                instructions.add(new Load(ptr, dst));
                yield dst;
            }
            case PlainOperand(ValIr v) -> v;
            case SubObject(VarIr base, int offset) -> {
                VarIr dst = makeTemporary("dst.", e.type());
                instructions.add(new CopyFromOffset(base, offset, dst));
                yield dst;
            }
        };
    }

    private static void assign(VarIr dst, Exp right,
                               List<InstructionIr> instructions) {
        ValIr rval = emitTackyAndConvert(right, instructions);
        instructions.add(new Copy(rval, dst));
    }

    private static ExpResult applyOperatorAndAssign(
            List<InstructionIr> instructions, Exp exp, ExpResult lval,
            ValIr right, ArithmeticOperator newOp, boolean post,
            Type commonType, Type lvalueType) {
        return switch (lval) {
            case PlainOperand(VarIr obj) -> {
                VarIr old = makeTemporary("old.", exp.type());
                instructions.add(new Copy(obj, old));
                handleCompoundOperatorHelper(newOp, instructions, obj, right,
                        commonType, lvalueType);
                yield post ? new PlainOperand(old) : lval;
            }
            case DereferencedPointer(VarIr ptr) -> {
                VarIr newVal = makeTemporary("newVal.", exp.type());
                VarIr old = makeTemporary("old.", exp.type());
                instructions.add(new Load(ptr, newVal));
                instructions.add(new Copy(newVal, old));
                handleCompoundOperatorHelper(newOp, instructions, newVal,
                        right, commonType, lvalueType);

                instructions.add(new Store(newVal, ptr));
                yield post ? new PlainOperand(old) : lval;
            }
            case SubObject(VarIr base, int offset) -> {
                VarIr newVal = makeTemporary("newVal.", exp.type());
                VarIr old = makeTemporary("old.", exp.type());

                instructions.add(new CopyFromOffset(base, offset, newVal));
                instructions.add(new Copy(newVal, old));
                handleCompoundOperatorHelper(newOp, instructions, newVal,
                        right, commonType, lvalueType);

                instructions.add(new CopyToOffset(newVal, base, offset));
                yield post ? new PlainOperand(old) : lval;
            }
            default -> throw new Todo();
        };
    }

    private static void handleCompoundOperatorHelper(ArithmeticOperator newOp,
                                                     List<InstructionIr> instructions,
                                                     VarIr left, ValIr right,
                                                     Type commonType,
                                                     Type lvalueType) {

        if (lvalueType instanceof Pointer(Type ptrRefType)) {
            if (newOp == SUB) {
                var minusRight = makeTemporary("neg.", LONG);
                instructions.add(new UnaryIr(UnaryOperator.UNARY_MINUS, right
                        , minusRight));
                right = minusRight;
            }
            instructions.add(new AddPtr(left, right,
                    (int) Mcc.size(ptrRefType), left));
        } else {
            // var leftType=Mcc.valToType(left);
            if (!commonType.equals(lvalueType)) {
                VarIr tmp = makeTemporary("tmp.", commonType);
                VarIr newLeft = makeTemporary("left.", commonType);
                emitCast(instructions, commonType, lvalueType, left, newLeft);
                instructions.add(new BinaryIr(newOp, newLeft, right, tmp));
                emitCast(instructions, lvalueType, commonType, tmp, left);
            } else instructions.add(new BinaryIr(newOp, left, right, left));
        }
    }

    private static ExpResult assign(Exp left, Exp right,
                                    List<InstructionIr> instructions) {
        ExpResult lval = emitTacky(left, instructions);
        ValIr rval = emitTackyAndConvert(right, instructions);
        return switch (lval) {
            case PlainOperand(VarIr obj) -> {
                instructions.add(new Copy(rval, obj));
                yield lval;
            }
            case DereferencedPointer(VarIr ptr) -> {
                instructions.add(new Store(rval, ptr));
                yield new PlainOperand(rval);
            }
            case SubObject(VarIr base, int offset) -> {
                instructions.add(new CopyToOffset(rval, base, offset));
                yield new PlainOperand(rval);
            }
            default ->
                    throw new IllegalStateException("Unexpected value: " + lval);
        };

    }

    static AtomicLong labelCount = new AtomicLong(0L);

    public static LabelIr newLabel(String prefix) {
        return new LabelIr(prefix);
    }

    private static VarIr makeTemporary(String prefix, Type t) {
        String name = Mcc.makeTemporary(prefix);
        SYMBOL_TABLE.put(name, new SymbolTableEntry(t, LOCAL_ATTR));
        return new VarIr(name);
    }

}
