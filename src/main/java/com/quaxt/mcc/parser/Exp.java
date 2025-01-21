package com.quaxt.mcc.parser;

sealed public interface Exp extends Statement, ForInit permits Assignment, BinaryOp, Conditional, Constant, FunctionCall, Identifier, UnaryOp {
}

