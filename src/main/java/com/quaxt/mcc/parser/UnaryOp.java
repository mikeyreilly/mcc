package com.quaxt.mcc.parser;

import com.quaxt.mcc.UnaryOperator;
import com.quaxt.mcc.semantic.Type;

public record UnaryOp(UnaryOperator op, Exp exp, Type type) implements Exp {
}