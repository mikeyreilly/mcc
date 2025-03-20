package com.quaxt.mcc.parser;
import com.quaxt.mcc.semantic.Type;

sealed public interface Exp extends Statement, ForInit permits AddrOf,
        Assignment, BinaryOp, Cast, Conditional, Constant, Dereference,
        FunctionCall, Str, Subscript, UnaryOp, Var {
    Type type();
}

