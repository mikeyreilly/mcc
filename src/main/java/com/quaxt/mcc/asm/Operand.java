package com.quaxt.mcc.asm;

public sealed interface Operand permits Data, DoubleReg, Imm, Pseudo, Reg, Memory {
}
