package com.quaxt.mcc.asm;

public sealed interface HardReg extends Reg permits IntegerReg, DoubleReg {}
