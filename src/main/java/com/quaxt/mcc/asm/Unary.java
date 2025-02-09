package com.quaxt.mcc.asm;

import com.quaxt.mcc.UnaryOperator;

public record Unary(UnaryOperator op, TypeAsm type, Operand operand) implements Instruction {
}
