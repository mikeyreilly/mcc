package com.quaxt.mcc.asm;

public sealed interface Reg extends Operand permits DoubleReg, HardReg, Pseudo {

}
