package com.quaxt.mcc.tacky;

import com.quaxt.mcc.ArithmeticOperator;

public record BinaryWithOverflowIr(ArithmeticOperator op, ValIr v1, ValIr v2, ValIr result,
                                   VarIr overflow) implements InstructionIr {

}