package com.quaxt.mcc.parser;
import com.quaxt.mcc.semantic.Type;

sealed public interface Exp extends Statement, ForInit permits AddrOf,
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
        FunctionCall,
        Offsetof,
        SizeOf,
        SizeOfT,
        Str,
        Subscript,
        UnaryOp,
        Var {
    Type type();
}

