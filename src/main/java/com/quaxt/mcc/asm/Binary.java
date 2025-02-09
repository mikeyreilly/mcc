package com.quaxt.mcc.asm;

import com.quaxt.mcc.ArithmeticOperator;

public record Binary(ArithmeticOperator op, TypeAsm type, Operand src, Operand dst) implements Instruction {
}
