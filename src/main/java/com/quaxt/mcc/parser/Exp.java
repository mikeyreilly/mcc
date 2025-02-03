package com.quaxt.mcc.parser;

import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.semantic.Type;

sealed public interface Exp extends Statement, ForInit permits Assignment, BinaryOp, Cast, Conditional, Constant, FunctionCall, Identifier, UnaryOp {
    Type type();

    default Exp withType(Type type){
        throw new Todo();
    }
}

