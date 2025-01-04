package com.quaxt.mcc.asm;

import com.quaxt.mcc.BinaryOperator;
import com.quaxt.mcc.tacky.ValIr;

public record Binary(BinaryOperator op, Operand src, Operand dst) implements Instruction {
}
