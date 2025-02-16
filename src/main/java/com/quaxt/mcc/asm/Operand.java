package com.quaxt.mcc.asm;

public sealed interface Operand permits Imm, ImmDouble, Pseudo, Reg, Stack, Data {
}
