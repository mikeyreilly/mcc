package com.quaxt.mcc.parser;
import com.quaxt.mcc.semantic.Type;

sealed public interface Exp extends Statement, ForInit permits AddrOf, Arrow, Assignment, BinaryOp, Cast, CompoundAssignment, Conditional, Constant, Dereference, Dot, FunctionCall, SizeOf, SizeOfT, Str, Subscript, UnaryOp, Var {
    Type type();
}

