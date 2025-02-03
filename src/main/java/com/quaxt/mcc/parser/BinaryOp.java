package com.quaxt.mcc.parser;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.semantic.Type;

public record BinaryOp(BinaryOperator op, Exp left, Exp right, Type type) implements Exp {
}