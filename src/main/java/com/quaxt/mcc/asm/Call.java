package com.quaxt.mcc.asm;

import com.quaxt.mcc.semantic.FunType;

public record Call(Operand address, FunType type) implements Instruction {

}
