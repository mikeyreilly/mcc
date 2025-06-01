package com.quaxt.mcc.parser;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.semantic.Type;

public record BinaryOp(BinaryOperator op, Exp left, Exp right, Type type) implements Exp {
    public BinaryOp(BinaryOperator op, Exp left, Exp right, Type type){
        this.op=op;
        this.left=left;
        this.right=right;
        this.type=type;
    }
}