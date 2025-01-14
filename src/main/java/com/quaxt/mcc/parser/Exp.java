package com.quaxt.mcc.parser;

sealed public interface Exp extends Statement permits Assignment, BinaryOp, Constant, Var, UnaryOp {
}

