package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;

public record Binary(ArithmeticOperator op, Operand src, Operand dst) implements Instruction {
}
