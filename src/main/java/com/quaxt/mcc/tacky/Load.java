package com.quaxt.mcc.tacky;

public record Load(ValIr ptr, VarIr dst) implements InstructionIr {}
