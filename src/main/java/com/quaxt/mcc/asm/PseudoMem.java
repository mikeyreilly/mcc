package com.quaxt.mcc.asm;

public record PseudoMem(String identifier, int offset) implements Operand {}
