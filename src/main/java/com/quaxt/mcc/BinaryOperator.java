package com.quaxt.mcc;

public sealed interface BinaryOperator extends Token permits ArithmeticOperator, CmpOperator, CompoundAssignmentOperator {
}
