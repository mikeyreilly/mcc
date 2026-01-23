package com.quaxt.mcc.parser;
import com.quaxt.mcc.semantic.Type;

sealed public interface Exp extends Statement, ForInit permits AddrOf, AlignofT,
        Alignof,
        Arrow,
        Assignment,
        BinaryOp,
        BuiltInFunctionCall,
        BuiltinVaArg,
        Cast,
        CompoundAssignment,
        Conditional,
        Constant,
        Dereference,
        Dot,
        ExpressionStatement,
        FunctionCall,
        Generic,
        Offsetof,
        SizeOf,
        SizeOfT,
        Str,
        Subscript,
        UnaryOp,
        Var {
    Type type();
}

