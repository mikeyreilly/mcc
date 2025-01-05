package com.quaxt.mcc.asm;

import com.quaxt.mcc.BinaryOperator;

public record Binary(BinaryOperator op, Operand src, Operand dst) implements Instruction {
}
